package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Drives native Hireling.txt skills for friendly mercenary entities. */
@Wire(failOnNull = false)
@All({Mercenary.class, Monster.class, Position.class})
public final class MercenarySkillSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(MercenarySkillSystem.class);
  private static final float RETRY_SECONDS = 0.4f;
  private static final float BLOCK_LOG_SECONDS = 3f;
  /** AITHINK_Fn061_Hireable only enters the skill branch below 25 native cells. */
  private static final float HIRELING_COMBAT_DISTANCE = 24f;
  private final IntMap<Float> cooldown = new IntMap<>();
  private final IntMap<Float> blockLogCooldown = new IntMap<>();
  private final IntMap<Integer> lastBlockedStage = new IntMap<>();

  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<MapWrapper> mMap;
  @Wire protected Actioneer actioneer;

  private EntitySubscription targets;
  private NativeHirelingExperienceTable table;
  private volatile int castCount;
  private volatile int lastTarget = Engine.INVALID_ENTITY;
  private volatile int lastSkill = Engine.INVALID_ENTITY;
  private volatile int processCount;
  private volatile int blockStage;

  public MercenarySkillSystem() {
    super(Aspect.all(Mercenary.class, Monster.class, Position.class));
  }

  @Override
  protected void initialize() {
    targets = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, Position.class, AttributesWrapper.class));
    table = NativeHirelingExperienceTable.load();
  }

  @Override
  protected void process(int entityId) {
    processCount++;
    float logRemaining = blockLogCooldown.get(entityId, 0f)
        - Math.max(0f, world.getDelta());
    blockLogCooldown.put(entityId, Math.max(0f, logRemaining));
    Mercenary merc = mMercenary.get(entityId);
    Float remaining = cooldown.get(entityId);
    if (remaining != null) {
      remaining -= world.getDelta();
      if (remaining > 0f) {
        cooldown.put(entityId, remaining);
        return;
      }
      cooldown.remove(entityId);
    }
    if (mCasting.has(entityId) || mSequence.has(entityId)) {
      blocked(entityId, merc, 1, merc.targetId, Float.NaN, "busy");
      return;
    }
    if (actioneer == null || table == null || table.size() == 0) {
      blocked(entityId, merc, 2, merc.targetId, Float.NaN,
          actioneer == null ? "actioneer_missing" : "hireling_table_missing");
      return;
    }

    int target = targetWithContinuity(entityId, merc);
    if (target < 0) {
      merc.targetId = Engine.INVALID_ENTITY;
      blocked(entityId, merc, 3, target, Float.NaN, "no_hostile_target");
      return;
    }
    merc.targetId = target;
    NativeHirelingExperienceTable.Row row = table.row(merc.mercType, merc.level);
    if (row == null) {
      blocked(entityId, merc, 4, target, Float.NaN, "hireling_row_missing");
      return;
    }
    float distance = nativeAiDistance(mPosition.get(entityId).position,
        mPosition.get(target).position);
    if (distance >= HIRELING_COMBAT_DISTANCE) {
      // D2MOO can still retain a candidate from the wider 35-cell scan, but
      // does not attack it until the hireling is inside the 25-cell gate.
      if (merc.ownerId >= 0 && mPosition.has(merc.ownerId)) {
        actioneer.tryMoveTo(entityId, merc.ownerId);
      } else {
        actioneer.moveTo(entityId, Engine.INVALID_ENTITY);
      }
      blocked(entityId, merc, 5, target, distance, "target_outside_combat_gate");
      cooldown.put(entityId, 0.20f);
      return;
    }
    NativeRng rng = new NativeRng(merc.aiRngState);
    int chance = NativeHirelingExperienceTable.useSkillChance(
        merc.mercType, merc.level, merc.aiChanceParam);
    boolean useSkill = rng.nextInt(100) < chance;
    if (useSkill) {
      merc.aiChanceParam = 0;
    } else {
      // D2 increments the non-skill counter by ten after a failed AI roll.
      merc.aiChanceParam = Math.min(100, merc.aiChanceParam + 10);
    }

    int slot = -1;
    if (useSkill) {
      slot = table.selectSkill(merc.mercType, merc.level,
          rng.nextInt(table.skillRollBound(merc.mercType, merc.level)));
    }

    // sub_6FCE4610 distinguishes melee and ranged hirelings before invoking
    // sub_6FCE4830.  This is a property of the hireling class, not of the
    // currently selected skill (auras have no missile but are ranged AI).
    boolean melee = isMeleeMercenary(merc);
    if (melee && (distance >= 3f || !actioneer.isInMeleeRange(entityId, target, 0))) {
      actioneer.moveTo(entityId, target);
      blocked(entityId, merc, 7, target, distance, "melee_approach");
      cooldown.put(entityId, 0.20f);
      return;
    }

    // D2MOO's ranged branch does not fire at point-blank range on every tick:
    // when it declines the skill roll it first tries to regroup with its owner.
    // D2MOO makes a second 50% roll for a ranged hireling that is too close:
    // it first tries to return to the owner instead of repeatedly firing at
    // point blank.  If that path cannot be installed, it falls through to
    // sub_6FCE4830 and may still cast/attack.
    boolean rangedRegroup = !melee && distance < 4f && rng.nextInt(100) < 50;
    if (rangedRegroup) {
      if (merc.ownerId >= 0 && mPosition.has(merc.ownerId)) {
        if (actioneer.tryMoveTo(entityId, merc.ownerId)) {
          blocked(entityId, merc, 5, target, distance, "ranged_regroup");
          merc.aiRngState = rng.state();
          cooldown.put(entityId, RETRY_SECONDS);
          return;
        }
      } else {
        actioneer.moveTo(entityId, Engine.INVALID_ENTITY);
        blocked(entityId, merc, 5, target, distance, "ranged_regroup");
        merc.aiRngState = rng.state();
        cooldown.put(entityId, RETRY_SECONDS);
        return;
      }
      if (slot < 0) {
        slot = table.selectSkill(merc.mercType, merc.level,
            rng.nextInt(table.skillRollBound(merc.mercType, merc.level)));
      }
    } else if (slot >= 0 && slot < row.skills.length && row.skills[slot] >= 0
        && row.skillLevels[slot] > 0) {
      actioneer.castWithMode(entityId, row.skills[slot], (byte) row.skillModes[slot], target,
          mPosition.get(target).position.cpy());
      castCount++;
      lastTarget = target;
      lastSkill = row.skills[slot];
      blockStage = 6;
    } else if (merc.mercType == 0 && (useSkill || rangedRegroup)
        && castRogueFallback(entityId, target)) {
      // D2MOO sub_6FCE4830: Rogue hirelings fall back to MonStats.Skill1
      // when the Hireling.txt weighted roll selects no explicit skill.
      blockStage = 8;
    } else if ((useSkill || (!melee && distance < 4f))
        && actioneer.isInMeleeRange(entityId, target, 0)) {
      // sub_6FCE4830 falls back to the hireling's ordinary attack when its
      // skill roll selects no usable skill and the target is in melee range.
      actioneer.attack(entityId, (byte) 0, (byte) Engine.INVALID_MODE, target,
          mPosition.get(target).position.cpy());
      blockStage = 8;
    } else {
      actioneer.moveTo(entityId, Engine.INVALID_ENTITY);
      blockStage = 5;
    }
    merc.aiRngState = rng.state();
    cooldown.put(entityId, RETRY_SECONDS);
    lastBlockedStage.remove(entityId);
    blockLogCooldown.remove(entityId);
    log.info("[MERC_SKILL] phase={} entity={} owner={} target={} chance={} aiParam={} slot={} skill={} level={} mode={}",
        slot >= 0 ? "cast" : "normal_attack", entityId, merc.ownerId, target,
        chance, merc.aiChanceParam, slot + 1, slot >= 0 ? row.skills[slot] : -1,
        slot >= 0 ? row.skillLevels[slot] : 0, slot >= 0 ? row.skillModes[slot] : -1);
  }

  private void blocked(int entityId, Mercenary merc, int stage, int target,
      float distance, String reason) {
    blockStage = stage;
    int previous = lastBlockedStage.get(entityId, Integer.MIN_VALUE);
    float remaining = blockLogCooldown.get(entityId, 0f);
    if (remaining <= 0f) {
      MapWrapper wrapper = mMap.has(entityId) ? mMap.get(entityId) : null;
      int level = wrapper != null && wrapper.zone != null && wrapper.zone.level != null
          ? wrapper.zone.level.Id : -1;
      log.info("[MERC_SKILL_BLOCK] entity={} owner={} stage={} previousStage={} level={} "
              + "target={} distance={} mercType={} mercLevel={} reason={}",
          entityId, merc != null ? merc.ownerId : Engine.INVALID_ENTITY,
          stage, previous, level, target, distance,
          merc != null ? merc.mercType : -1, merc != null ? merc.level : -1, reason);
      blockLogCooldown.put(entityId, BLOCK_LOG_SECONDS);
    }
    lastBlockedStage.put(entityId, stage);
  }

  public int castCount() {
    return castCount;
  }

  public int lastTarget() {
    return lastTarget;
  }

  public int processCount() {
    return processCount;
  }

  public int lastSkill() {
    return lastSkill;
  }

  public int blockStage() {
    return blockStage;
  }

  private int nearestHostile(int entityId, int ownerId) {
    Vector2 source = mPosition.get(entityId).position;
    MapWrapper sourceMap = mMap.has(entityId) ? mMap.get(entityId) : null;
    int bestId = Engine.INVALID_ENTITY;
    float bestDistance = Float.MAX_VALUE;
    IntBag data = targets.getEntities();
    for (int i = 0; i < data.size(); i++) {
      int candidate = data.get(i);
      if (candidate == entityId || candidate == ownerId || mMercenary.has(candidate)) continue;
      if (!isHostile(candidate) || !isAlive(candidate) || !sameZone(sourceMap, candidate)) continue;
      float distance = nativeAiDistance(source, mPosition.get(candidate).position);
      if (distance > maxSearchDistance(entityId)) continue;
      if (distance < bestDistance) {
        bestDistance = distance;
        bestId = candidate;
      }
    }
    return bestId;
  }

  private int targetWithContinuity(int entityId, Mercenary merc) {
    if (merc.targetId >= 0 && isValidHostile(entityId, merc.ownerId, merc.targetId)
        && nativeAiDistance(mPosition.get(entityId).position,
            mPosition.get(merc.targetId).position) <= maxSearchDistance(entityId)) {
      return merc.targetId;
    }
    return nearestHostile(entityId, merc.ownerId);
  }

  /** D2Game's target-node scan uses MonStats.aiDist, not an unlimited screen scan. */
  private float maxSearchDistance(int entityId) {
    Monster monster = mMonster.get(entityId);
    if (monster != null && monster.monstats != null && monster.monstats.aidist != null
        && monster.monstats.aidist.length > 0) {
      int difficulty = 0;
      MapWrapper wrapper = mMap.has(entityId) ? mMap.get(entityId) : null;
      if (wrapper != null && wrapper.map != null) difficulty = wrapper.map.getDifficulty();
      int index = Math.min(Math.max(0, difficulty), monster.monstats.aidist.length - 1);
      int value = monster.monstats.aidist[index];
      if (value > 0) return value;
    }
    return 35f;
  }

  /** Same weighted grid metric used by D2MOO's AI target-node distance helper. */
  static float nativeAiDistance(Vector2 source, Vector2 target) {
    if (source == null || target == null) return Float.MAX_VALUE;
    float dx = Math.abs(target.x - source.x);
    float dy = Math.abs(target.y - source.y);
    float major = Math.max(dx, dy);
    float minor = Math.min(dx, dy);
    return (float) Math.floor((minor + 2f * major) / 2f);
  }

  private boolean isValidHostile(int sourceId, int ownerId, int candidate) {
    if (candidate == sourceId || candidate == ownerId || !mPosition.has(candidate)
        || !isHostile(candidate) || !isAlive(candidate)) return false;
    MapWrapper source = mMap.has(sourceId) ? mMap.get(sourceId) : null;
    return sameZone(source, candidate);
  }

  private static boolean isMeleeMercenary(Mercenary merc) {
    // Hireling.txt's ids are 0=Rogue, 1=Desert, 2=Iron Wolf, 3=Barbarian.
    return merc.mercType == 1 || merc.mercType == 3;
  }

  private boolean castRogueFallback(int entityId, int targetId) {
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats == null || Riiablo.files == null
        || Riiablo.files.skills == null) return false;
    String skillName = monster.monstats.Skill1;
    if (skillName == null || skillName.isEmpty()) return false;
    Skills.Entry skill = Riiablo.files.skills.get(skillName);
    if (skill == null) return false;
    int mode = Riiablo.files.MonMode.index(monster.monstats.Sk1mode);
    if (mode < 0) mode = Engine.Monster.MODE_S1;
    actioneer.castWithMode(entityId, skill.Id, (byte) mode, targetId,
        mPosition.get(targetId).position.cpy());
    castCount++;
    lastTarget = targetId;
    lastSkill = skill.Id;
    log.info("[MERC_SKILL] phase=rogue_fallback entity={} target={} skill={} mode={}",
        entityId, targetId, skill.Id, mode);
    return true;
  }

  private boolean isHostile(int entityId) {
    Monster monster = mMonster.get(entityId);
    // MonStats Align uses 0 for evil. Native hireling target acquisition only
    // returns units hostile to the good-aligned pet, so NPCs, neutral units,
    // and non-killable presentation monsters must not enter this candidate set.
    return monster != null && monster.monstats != null
        && monster.monstats.Align == 0 && monster.monstats.killable
        && !monster.monstats.npc && !monster.monstats.inTown;
  }

  private boolean isAlive(int entityId) {
    Attributes attrs = mAttributes.get(entityId).attrs;
    StatRef hp = attrs == null ? null : attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp == null || hp.asFixed() > 0f;
  }

  private boolean sameZone(MapWrapper source, int targetId) {
    if (source == null || !mMap.has(targetId)) return true;
    MapWrapper target = mMap.get(targetId);
    if (source.map != null && target.map != null && source.map != target.map) return false;
    return source.zone == null || target.zone == null || source.zone == target.zone;
  }

  private boolean requiresMeleeApproach(int skillId) {
    if (Riiablo.files == null || Riiablo.files.skills == null) return true;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return true;
    return empty(skill.srvmissile) && empty(skill.srvmissilea)
        && empty(skill.srvmissileb) && empty(skill.srvmissilec)
        && empty(skill.srvmissiled) && empty(skill.cltmissile)
        && empty(skill.cltmissilea) && empty(skill.cltmissileb)
        && empty(skill.cltmissilec) && empty(skill.cltmissiled);
  }

  private static boolean empty(String value) {
    return value == null || value.isEmpty();
  }
}
