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
  private static final float RETRY_SECONDS = 0.75f;
  private final IntMap<Float> cooldown = new IntMap<>();
  private int decisionTick;

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
      blockStage = 1;
      return;
    }
    if (actioneer == null || table == null || table.size() == 0) {
      blockStage = 2;
      return;
    }

    Mercenary merc = mMercenary.get(entityId);
    int target = nearestHostile(entityId, merc.ownerId);
    if (target < 0) {
      blockStage = 3;
      return;
    }
    NativeHirelingExperienceTable.Row row = table.row(merc.mercType, merc.level);
    if (row == null) {
      blockStage = 4;
      return;
    }
    int slot = table.selectSkill(merc.mercType, merc.level,
        entityId * 1103515245 + decisionTick++);
    if (slot < 0 || slot >= row.skills.length || row.skills[slot] < 0
        || row.skillModes[slot] >= 16 || row.skillLevels[slot] <= 0) {
      blockStage = 5;
      return;
    }
    actioneer.castWithMode(entityId, row.skills[slot], (byte) row.skillModes[slot], target,
        mPosition.get(target).position.cpy());
    castCount++;
    lastTarget = target;
    lastSkill = row.skills[slot];
    blockStage = 6;
    cooldown.put(entityId, RETRY_SECONDS);
    log.info("[MERC_SKILL] phase=cast entity={} owner={} target={} slot={} skill={} level={} mode={}",
        entityId, merc.ownerId, target, slot + 1, row.skills[slot], row.skillLevels[slot],
        row.skillModes[slot]);
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
      float distance = source.dst2(mPosition.get(candidate).position);
      if (distance < bestDistance) {
        bestDistance = distance;
        bestId = candidate;
      }
    }
    return bestId;
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
}
