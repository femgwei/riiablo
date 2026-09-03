package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Authoritative lifecycle for D2MOO SrvDo044/SrvDo045 assassin traps.
 *
 * <p>The native sentry is an owned monster unit. It searches hostile units,
 * fires its configured missile on a frame cadence, and disappears after the
 * native shot budget is exhausted. Keeping this outside ServerSkillSystem
 * prevents a trap's attack skill (which also carries SrvDo045 in Skills.txt)
 * from recursively creating another trap.</p>
 */
@All({SummonedPet.class, Monster.class, Position.class})
public class AssassinTrapSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(AssassinTrapSystem.class);
  private static final int ATTACK_INTERVAL_FRAMES = 15;
  private static final float SEARCH_RANGE = 25f;
  private static final float BLADE_FALLBACK_SPEED = 15f;
  private static final float BLADE_NATIVE_BASE_MULTIPLIER = 0.75f;
  private static final float BLADE_AI_SPEED_BONUS = 0.15f;

  protected ComponentMapper<SummonedPet> mTrap;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;
  @com.artemis.annotations.Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  protected net.mostlyoriginal.api.event.common.EventSystem events;

  @Override
  protected void process(int entityId) {
    SummonedPet trap = mTrap.get(entityId);
    if (trap == null || trap.petType == null
        || !trap.petType.toLowerCase(java.util.Locale.ROOT).contains("assassintrap")) return;
    if (!mMonster.has(entityId) || !mPosition.has(entityId)) return;
    if (trap.bladeSentinel) {
      processBladeSentinel(entityId, trap, mMonster.get(entityId));
      return;
    }
    if (trap.infernoChanneling) {
      processInfernoChannel(entityId, trap, mMonster.get(entityId));
      return;
    }
    if (!trap.bladeSentinel && trap.maxShots > 0 && trap.shotsFired >= trap.maxShots) {
      log.info("[ASSASSIN_TRAP] phase=remove entity={} owner={} reason=shots_exhausted shots={}",
          entityId, trap.ownerId, trap.shotsFired);
      world.delete(entityId);
      return;
    }
    trap.attackCooldownFrames -= Math.max(1, Math.round(world.delta * 25f));
    if (trap.attackCooldownFrames > 0) return;
    Monster monster = mMonster.get(entityId);
    Skills.Entry placementSkill = trap.skillId >= 0 ? Riiablo.files.skills.get(trap.skillId) : null;
    int target = nearestHostile(entityId, monster);
    if (target < 0) {
      trap.attackCooldownFrames = inactiveInterval(monster);
      return;
    }
    Skills.Entry corpseSkill = resolveCorpseSkill(monster);
    if (corpseSkill != null) {
      int corpseId = findDeathSentryCorpse(entityId, target, trap, corpseSkill);
      if (corpseId >= 0 && explodeCorpse(entityId, corpseId, trap, corpseSkill)) {
        trap.deathLastCorpseId = corpseId;
        trap.shotsFired++;
        trap.attackCooldownFrames = attackInterval(monster);
        log.info("[DEATH_SENTRY] phase=corpse_attack entity={} owner={} target={} corpse={} "
                + "shot={}/{} status=PASS",
            entityId, trap.ownerId, target, corpseId, trap.shotsFired, trap.maxShots);
        return;
      }
    }
    if (MathUtils.random(99) >= attackChance(monster)) {
      trap.attackCooldownFrames = attackInterval(monster);
      return;
    }
    Skills.Entry attackSkill = resolveAttackSkill(monster, placementSkill);
    String missileName = resolveMissile(attackSkill, monster);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null || factory == null) {
      log.warn("[ASSASSIN_TRAP] phase=stall entity={} skill={} target={} reason=missing_missile name={}",
          entityId, trap.skillId, target, missileName);
      trap.attackCooldownFrames = attackInterval(monster);
      return;
    }
    Vector2 origin = mPosition.get(entityId).position;
    Vector2 destination = mPosition.get(target).position;
    Vector2 direction = new Vector2(destination).sub(origin);
    if (direction.isZero(0.0001f)) return;
    int missileId = factory.createMissile(missile, direction.nor(), origin, entityId);
    if (missileId >= 0 && attackSkill != null && mAttributes.has(entityId)
        && mMissile.has(missileId)) {
      if (attackSkill.srvdofunc == 125) {
        Vector2 targetPoint = mPosition.has(target) ? mPosition.get(target).position
            : new Vector2(origin).add(direction);
        configureWakeMaker(mMissile.get(missileId), attackSkill, trap, entityId,
            origin, targetPoint, direction);
      } else if (attackSkill.srvdofunc == 95) {
        startInfernoChannel(mMissile.get(missileId), attackSkill, trap, target);
      }
      MissileDamageResolver.initializeSkill(
          mMissile.get(missileId), attackSkill, mAttributes.get(entityId).attrs,
          Math.max(1, trap.skillLevel));
    }
    trap.shotsFired++;
    trap.attackCooldownFrames = attackInterval(monster);
    log.info("[ASSASSIN_TRAP] phase=fire entity={} owner={} skill={} target={} missile={} "
            + "shot={}/{}", entityId, trap.ownerId, trap.skillId, target, missileName,
        trap.shotsFired, trap.maxShots);
  }

  private static Skills.Entry resolveCorpseSkill(Monster monster) {
    if (!isDeathSentry(monster) || monster.monstats.Skill1 == null
        || monster.monstats.Skill1.isEmpty()) return null;
    Skills.Entry skill = Riiablo.files.skills.get(monster.monstats.Skill1);
    return skill != null && skill.srvdofunc == 55 ? skill : null;
  }

  private static boolean isDeathSentry(Monster monster) {
    return monster != null && monster.monstats != null
        && "DeathSentry".equalsIgnoreCase(monster.monstats.AI);
  }

  /** Native sub_6FD15210 plus the Fn104 corpse-to-hostile distance gate. */
  private int findDeathSentryCorpse(int sentryId, int hostileId, SummonedPet trap,
      Skills.Entry skill) {
    if (!mPosition.has(hostileId)) return Engine.INVALID_ENTITY;
    Vector2 target = mPosition.get(hostileId).position;
    int level = Math.max(1, trap.skillLevel);
    int nativeRange = skillParam(skill, 3, 10)
        + (level - 1) * skillParam(skill, 4, 0);
    // sub_6FD15210 first performs a radius-10 unit search; Fn104 then applies
    // the tighter skill-specific corpse-to-hostile distance gate.
    float maximum = Math.min(10f, Math.max(1f, nativeRange / 2f));
    float bestDistance = maximum * maximum;
    int best = Engine.INVALID_ENTITY;
    IntBag corpses = world.getAspectSubscriptionManager()
        .get(Aspect.all(Corpse.class, Monster.class, Position.class, AttributesWrapper.class))
        .getEntities();
    for (int i = 0; i < corpses.size(); i++) {
      int corpseId = corpses.get(i);
      if (corpseId == trap.deathLastCorpseId || !selectableCorpse(corpseId)) continue;
      if (mMapWrapper.has(sentryId) && mMapWrapper.has(corpseId)
          && mMapWrapper.get(sentryId).zone != null
          && mMapWrapper.get(corpseId).zone != null
          && mMapWrapper.get(sentryId).zone != mMapWrapper.get(corpseId).zone) continue;
      float distance = target.dst2(mPosition.get(corpseId).position);
      if (distance >= bestDistance) continue;
      bestDistance = distance;
      best = corpseId;
    }
    return best;
  }

  /** D2COMMON_11021 / SKILLS_CanUnitCorpseBeSelected. */
  private boolean selectableCorpse(int entityId) {
    Corpse corpse = mCorpse.get(entityId);
    Monster monster = mMonster.get(entityId);
    Attributes attrs = mAttributes.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints) : null;
    boolean hidden = mUnitStates.has(entityId)
        && mUnitStates.get(entityId).stateList != null
        && mUnitStates.get(entityId).stateList.hasState(StateId.CORPSE_NODRAW);
    return corpse != null && corpse.usable && !corpse.fading && !hidden
        && monster != null && monster.monstats2 != null && monster.monstats2.corpseSel
        && hp != null && hp.asFixed() <= 0f;
  }

  /** Native SKILLS_SrvDo055_CorpseExplosion. */
  private boolean explodeCorpse(int sentryId, int corpseId, SummonedPet trap,
      Skills.Entry skill) {
    if (!selectableCorpse(corpseId) || !mPosition.has(corpseId)) return false;
    Corpse corpse = mCorpse.get(corpseId);
    corpse.usable = false; // reserve before damage so another sentry cannot consume it
    UnitStates states = mUnitStates.has(corpseId)
        ? mUnitStates.get(corpseId) : mUnitStates.create(corpseId).init(corpseId);
    if (states.stateList == null) states.init(corpseId);
    UnitState hidden = states.stateList.addState(StateId.CORPSE_NODRAW, 0,
        Math.max(1, trap.skillLevel), sentryId);
    if (hidden != null) {
      hidden.skillId = skill.Id;
      hidden.needsSync = true;
    }

    Attributes corpseAttrs = mAttributes.get(corpseId).attrs;
    int corpseMaxHp = Math.max(1, statInt(corpseAttrs, Stat.maxhp, 1));
    int minPercent = Math.max(0, SkillFormula.evaluate(skill.calc1, skill, trap.skillLevel));
    int maxPercent = Math.max(minPercent,
        SkillFormula.evaluate(skill.calc2, skill, trap.skillLevel));
    int baseDamage = MathUtils.random(
        corpseMaxHp * minPercent / 100, corpseMaxHp * maxPercent / 100);
    int corpseLevel = Math.max(1, statInt(corpseAttrs, Stat.level, 1));
    Attributes sentryAttrs = mAttributes.has(sentryId) ? mAttributes.get(sentryId).attrs : null;
    int sentryLevel = Math.max(1, statInt(sentryAttrs, Stat.level, 1));
    if (sentryLevel < corpseLevel) baseDamage = baseDamage * sentryLevel / corpseLevel;

    int firePercent = Math.max(0, Math.min(100,
        SkillFormula.evaluate(skill.calc3, skill, trap.skillLevel)));
    int fireDamage = baseDamage * firePercent / 100;
    int physicalDamage = baseDamage - fireDamage;
    int auraRange = Math.max(1,
        SkillFormula.evaluate(skill.aurarangecalc, skill, trap.skillLevel));
    float physicalRadius = Math.max(1f, auraRange / 2);
    float damageRadius = Math.max(1f, (auraRange + 1) / 2f);
    Vector2 origin = mPosition.get(corpseId).position;
    int hit = damageCorpseExplosion(sentryId, sentryLevel, origin, physicalRadius,
        damageRadius, physicalDamage, fireDamage);
    spawnCorpseExplosionVisual(sentryId, origin, skill);
    log.info("[DEATH_SENTRY] phase=corpse_explode entity={} owner={} corpse={} level={} "
            + "corpseMaxHp={} percent={}..{} damage={} physical={} fire={} radius={} hit={}",
        sentryId, trap.ownerId, corpseId, trap.skillLevel, corpseMaxHp,
        minPercent, maxPercent, baseDamage, physicalDamage, fireDamage, damageRadius, hit);
    return true;
  }

  private int damageCorpseExplosion(int sentryId, int sentryLevel, Vector2 origin,
      float physicalRadius, float damageRadius, int physicalDamage, int fireDamage) {
    IntBag targets = world.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class, Position.class, AttributesWrapper.class)).getEntities();
    int hit = 0;
    for (int i = 0; i < targets.size(); i++) {
      int targetId = targets.get(i);
      if (targetId == sentryId || mCorpse.has(targetId) || mTrap.has(targetId)) continue;
      float distance2 = origin.dst2(mPosition.get(targetId).position);
      if (distance2 > damageRadius * damageRadius) continue;
      Attributes targetAttrs = mAttributes.get(targetId).attrs;
      StatRef hp = targetAttrs != null
          ? targetAttrs.get(Stat.hitpoints, StatRef.obtain()) : null;
      if (hp == null || hp.asFixed() <= 0f) continue;

      int physical = distance2 <= physicalRadius * physicalRadius ? physicalDamage : 0;
      int[] elementalMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
      int[] elementalMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
      elementalMin[CombatSystem.DAMAGE_FIRE] = fireDamage;
      elementalMax[CombatSystem.DAMAGE_FIRE] = fireDamage;
      Attributes attack = Attributes.obtainStandard();
      attack.base().put(Stat.level, Math.max(1, sentryLevel));
      attack.base().put(Stat.mindamage, physical);
      attack.base().put(Stat.maxdamage, physical);
      attack.reset();
      CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(
          attack, targetAttrs, false, false, true, 0, 0, 0, true,
          elementalMin, elementalMax, 0, 0);
      if (!result.hit || result.blocked || result.totalDamage <= 0) continue;
      DamageEvent event = DamageEvent.obtain(sentryId, targetId, result.totalDamage);
      if (events != null) events.dispatch(event);
      hp.sub(Math.max(0f, event.damage));
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(sentryId, targetId));
      }
      hit++;
    }
    return hit;
  }

  private void spawnCorpseExplosionVisual(int sentryId, Vector2 origin, Skills.Entry skill) {
    if (factory == null || skill.cltmissilea == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (row == null) return;
    int missileId = factory.createMissile(row, Vector2.X, origin, sentryId);
    if (missileId < 0 || !mMissile.has(missileId)) return;
    Missile visual = mMissile.get(missileId);
    visual.persistent = true;
    visual.remainingFrames = Math.max(1, row.Range);
    visual.tickInterval = visual.remainingFrames + 1;
    visual.skillId = skill.Id;
  }

  private static int statInt(Attributes attrs, short stat, int fallback) {
    StatRef value = attrs != null ? attrs.get(stat) : null;
    return value != null ? value.asInt() : fallback;
  }

  /** Starts the SrvSt53/SrvDo095 repeat window after its first missile. */
  private void startInfernoChannel(Missile missile, Skills.Entry attackSkill,
      SummonedPet trap, int targetId) {
    if (missile == null || attackSkill == null) return;
    int level = Math.max(1, trap.skillLevel);
    int duration = SkillFormula.evaluate(attackSkill.calc2, attackSkill, level,
        name -> resolveOwnerSkillLevel(trap.ownerId, name));
    if (duration <= 0) duration = Math.max(1, skillParam(attackSkill, 1, 15));
    int tick = SkillFormula.evaluate(attackSkill.calc3, attackSkill, level,
        name -> resolveOwnerSkillLevel(trap.ownerId, name));
    if (tick <= 0) tick = 1;
    configureInfernoMissile(missile, attackSkill, trap);
    trap.infernoChanneling = true;
    trap.infernoRemainingFrames = Math.max(1, duration);
    trap.infernoPulseFrames = Math.max(1, tick);
    trap.infernoPulseCooldownFrames = trap.infernoPulseFrames;
    trap.infernoTargetId = targetId;
    log.info("[INFERNO_SENTRY] phase=channel_start owner={} target={} missile={} duration={} "
            + "pulse={} path={} status=PASS",
        trap.ownerId, targetId,
        missile.missile != null ? missile.missile.Missile : "unknown",
        trap.infernoRemainingFrames, trap.infernoPulseFrames, missile.range);
  }

  /** D2MOO SKILLS_UpdateInfernoAnimationParameters: calc1 is path length. */
  private void configureInfernoMissile(Missile missile, Skills.Entry attackSkill,
      SummonedPet trap) {
    int level = Math.max(1, trap.skillLevel);
    int path = SkillFormula.evaluate(attackSkill.calc1, attackSkill, level,
        name -> resolveOwnerSkillLevel(trap.ownerId, name));
    if (path <= 0 && missile.missile != null) {
      path = skillParam(missile.missile.Param, 2, 0) + level - 1;
    }
    missile.range = Math.max(1, Math.min(255, path));
    missile.pierceEnabled = true;
    missile.pierceChance = 100;
    missile.skillId = attackSkill.Id;
    missile.damageLevel = level;
  }

  /** Repeats SrvDo095 every calc3 frames and tracks the moving target direction. */
  private void processInfernoChannel(int entityId, SummonedPet trap, Monster monster) {
    int elapsed = Math.max(1, Math.round(Math.max(0f, world.delta) * 25f));
    trap.infernoRemainingFrames -= elapsed;
    trap.infernoPulseCooldownFrames -= elapsed;
    if (trap.infernoRemainingFrames <= 0 || !validTarget(trap.infernoTargetId)) {
      log.info("[INFERNO_SENTRY] phase=channel_end entity={} owner={} target={} "
              + "remaining={} reason={}",
          entityId, trap.ownerId, trap.infernoTargetId, trap.infernoRemainingFrames,
          trap.infernoRemainingFrames <= 0 ? "duration" : "target_lost");
      trap.infernoChanneling = false;
      trap.infernoTargetId = Engine.INVALID_ENTITY;
      trap.attackCooldownFrames = attackInterval(monster);
      return;
    }
    if (trap.infernoPulseCooldownFrames > 0) return;
    trap.infernoPulseCooldownFrames += Math.max(1, trap.infernoPulseFrames);

    Skills.Entry placement = trap.skillId >= 0 ? Riiablo.files.skills.get(trap.skillId) : null;
    Skills.Entry attackSkill = resolveAttackSkill(monster, placement);
    String missileName = resolveMissile(attackSkill, monster);
    Missiles.Entry missileRow = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (attackSkill == null || attackSkill.srvdofunc != 95 || missileRow == null || factory == null) {
      trap.infernoChanneling = false;
      trap.attackCooldownFrames = attackInterval(monster);
      log.warn("[INFERNO_SENTRY] phase=channel_stall entity={} owner={} reason=missing_skill_or_missile",
          entityId, trap.ownerId);
      return;
    }
    Vector2 origin = mPosition.get(entityId).position;
    Vector2 direction = new Vector2(mPosition.get(trap.infernoTargetId).position).sub(origin);
    if (direction.isZero(0.0001f)) return;
    int missileId = factory.createMissile(missileRow, direction.nor(), origin, entityId);
    if (missileId < 0 || !mMissile.has(missileId)) return;
    Missile missile = mMissile.get(missileId);
    configureInfernoMissile(missile, attackSkill, trap);
    Attributes attrs = mAttributes.has(entityId) ? mAttributes.get(entityId).attrs : null;
    MissileDamageResolver.initializeSkill(missile, attackSkill, attrs,
        Math.max(1, trap.skillLevel));
    log.debug("[INFERNO_SENTRY] phase=pulse entity={} owner={} target={} missileId={} "
            + "remaining={} direction=({}, {})",
        entityId, trap.ownerId, trap.infernoTargetId, missileId,
        trap.infernoRemainingFrames, direction.x, direction.y);
  }

  private boolean validTarget(int targetId) {
    if (targetId < 0 || !mPosition.has(targetId) || !mAttributes.has(targetId)) return false;
    Attributes attrs = mAttributes.get(targetId).attrs;
    com.riiablo.attributes.StatRef hp = attrs != null
        ? attrs.get(com.riiablo.attributes.Stat.hitpoints) : null;
    return hp != null && hp.asFixed() > 0f;
  }

  private int resolveOwnerSkillLevel(int ownerId, String name) {
    if (name == null || name.isEmpty() || ownerId < 0 || !mPlayer.has(ownerId)
        || mPlayer.get(ownerId).data == null) return 0;
    Skills.Entry skill = Riiablo.files.skills.get(name);
    return skill != null ? mPlayer.get(ownerId).data.getSkill(skill.Id) : 0;
  }

  private static int skillParam(Skills.Entry skill, int index, int fallback) {
    return skill != null && skill.Param != null && index > 0
        && index <= skill.Param.length && skill.Param[index - 1] > 0
        ? skill.Param[index - 1] : fallback;
  }

  private static int skillParam(int[] params, int index, int fallback) {
    return params != null && index > 0 && index <= params.length && params[index - 1] > 0
        ? params[index - 1] : fallback;
  }

  /** Configures the SrvDo125 maker to emit the two perpendicular fire waves. */
  private void configureWakeMaker(Missile maker, Skills.Entry attackSkill,
      SummonedPet trap, int entityId, Vector2 origin, Vector2 target, Vector2 travel) {
    if (maker == null || attackSkill == null) return;
    Vector2 direction = new Vector2(travel);
    if (direction.isZero(0.0001f)) direction.set(Vector2.X);
    direction.nor();
    maker.wakeMaker = true;
    maker.wakeSpawned = false;
    maker.wakeTargetX = target.x;
    maker.wakeTargetY = target.y;
    maker.wakeDirectionX = -direction.y;
    maker.wakeDirectionY = direction.x;
    maker.damageOwnerId = trap.ownerId;
    maker.skillId = attackSkill.Id;
    maker.damageLevel = Math.max(1, trap.skillLevel);
    log.info("[WAKE_OF_FIRE] phase=maker_create entity={} owner={} missile={} target=({}, {}) "
            + "waveDirection=({}, {}) skill={} status=PASS",
        entityId, trap.ownerId, maker.missile != null ? maker.missile.Missile : "unknown",
        target.x, target.y, maker.wakeDirectionX, maker.wakeDirectionY, attackSkill.skill);
  }

  /**
   * Native AITHINK_Fn102_BladeCreeper plus Missile SrvDo20 setup.
   *
   * <p>The monster-shaped controller travels between the caster coordinates
   * and the selected endpoint. It creates exactly one blade-creeper missile;
   * MissileCollisionSystem attaches that missile to this controller while
   * retaining the player as its authoritative damage owner.</p>
   */
  private void processBladeSentinel(int entityId, SummonedPet trap, Monster monster) {
    Position position = mPosition.get(entityId);
    if (trap.shotsFired == 0) createBladeMissile(entityId, trap, position.position, monster);

    float destinationX = trap.bladeMovingToTarget ? trap.trapTargetX : trap.bladeOriginX;
    float destinationY = trap.bladeMovingToTarget ? trap.trapTargetY : trap.bladeOriginY;
    Vector2 delta = new Vector2(destinationX - position.position.x,
        destinationY - position.position.y);
    float distance = delta.len();
    float step = bladeSpeed(monster) * Math.max(0f, world.delta);
    if (distance <= Math.max(0.001f, step)) {
      position.position.set(destinationX, destinationY);
      trap.bladeMovingToTarget = !trap.bladeMovingToTarget;
      if (trap.bladeMissileId >= 0 && mMissile.has(trap.bladeMissileId)) {
        // Native NextHit suppresses repeated contact while the blade overlaps
        // a unit. Resetting at a route endpoint also permits the return pass.
        mMissile.get(trap.bladeMissileId).hitTargets.clear();
      }
      if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
      log.debug("[BLADE_SENTINEL] phase=endpoint entity={} owner={} position=({}, {}) next={}",
          entityId, trap.ownerId, destinationX, destinationY,
          trap.bladeMovingToTarget ? "target" : "origin");
      return;
    }

    delta.scl(step / distance);
    position.position.add(delta);
    if (mVelocity.has(entityId)) {
      mVelocity.get(entityId).velocity.set(delta).scl(
          world.delta > 0f ? 1f / world.delta : 0f);
    }
  }

  private void createBladeMissile(int entityId, SummonedPet trap, Vector2 origin,
      Monster monster) {
    Skills.Entry skill = trap.skillId >= 0 ? Riiablo.files.skills.get(trap.skillId) : null;
    String missileName = resolveMissile(skill, monster);
    Missiles.Entry missileRow = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missileRow == null || factory == null) {
      log.warn("[BLADE_SENTINEL] phase=stall entity={} owner={} reason=missing_missile name={}",
          entityId, trap.ownerId, missileName);
      return;
    }
    Vector2 direction = new Vector2(trap.trapTargetX, trap.trapTargetY).sub(origin);
    if (direction.isZero(0.0001f)) direction.set(Vector2.X);
    int missileId = factory.createMissile(missileRow, direction.nor(), origin, trap.ownerId);
    if (missileId < 0 || !mMissile.has(missileId)) return;

    Missile blade = mMissile.get(missileId);
    blade.attached = true;
    blade.attachedEntityId = entityId;
    blade.range = 0f;
    blade.pierceEnabled = true;
    blade.pierceChance = 100;
    Attributes ownerAttrs = trap.ownerId >= 0 && mAttributes.has(trap.ownerId)
        ? mAttributes.get(trap.ownerId).attrs : null;
    MissileDamageResolver.initializeSkill(blade, skill, ownerAttrs,
        Math.max(1, trap.skillLevel));
    trap.bladeMissileId = missileId;
    trap.shotsFired = 1;
    log.info("[BLADE_SENTINEL] phase=missile_create entity={} owner={} missileId={} "
            + "missile={} skill={} status=PASS",
        entityId, trap.ownerId, missileId, missileName, trap.skillId);
  }

  private static float bladeSpeed(Monster monster) {
    int base = monster != null && monster.monstats != null ? monster.monstats.Velocity : 0;
    return base > 0
        ? base * (BLADE_NATIVE_BASE_MULTIPLIER + BLADE_AI_SPEED_BONUS)
        : BLADE_FALLBACK_SPEED;
  }

  static Skills.Entry resolveAttackSkill(Monster monster, Skills.Entry fallback) {
    if (monster != null && monster.monstats != null
        && "DeathSentry".equalsIgnoreCase(monster.monstats.AI)
        && monster.monstats.Skill2 != null && !monster.monstats.Skill2.isEmpty()) {
      // Corpse selection/explosion is a separate native branch. Until a dead
      // target is selected, Fn104 uses Skill2 as the ordinary lightning shot.
      Skills.Entry attack = Riiablo.files.skills.get(monster.monstats.Skill2);
      if (attack != null) return attack;
    }
    if (monster != null && monster.monstats != null
        && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
      Skills.Entry attack = Riiablo.files.skills.get(monster.monstats.Skill1);
      if (attack != null) return attack;
    }
    return fallback;
  }

  private static int attackChance(Monster monster) {
    if (isDeathSentry(monster)) {
      // Fn104 uses AI parameter 2 (MonStats Aip3) only for the lightning
      // fallback. A valid corpse always takes priority and skips this roll.
      return Math.max(0, Math.min(100, aiParam(monster.monstats.aip3, 100)));
    }
    return Math.max(0, Math.min(100, aiParam(monster != null && monster.monstats != null
        ? monster.monstats.aip1 : null, 100)));
  }

  private static int attackInterval(Monster monster) {
    return aiParam(monster != null && monster.monstats != null
        ? monster.monstats.aip2 : null, ATTACK_INTERVAL_FRAMES);
  }

  private static int inactiveInterval(Monster monster) {
    return aiParam(monster != null && monster.monstats != null
        ? monster.monstats.aip3 : null, ATTACK_INTERVAL_FRAMES);
  }

  private static int aiParam(int[] values, int fallback) {
    return values != null && values.length > 0 && values[0] > 0 ? values[0] : fallback;
  }

  private int nearestHostile(int sourceId, Monster monster) {
    if (!mPosition.has(sourceId)) return Engine.INVALID_ENTITY;
    Vector2 origin = mPosition.get(sourceId).position;
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class, Position.class)).getEntities();
    int best = Engine.INVALID_ENTITY;
    float range = aiParam(monster != null && monster.monstats != null
        ? monster.monstats.aip4 : null, (int) SEARCH_RANGE);
    float bestDistance = range * range;
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (id == sourceId || !mPosition.has(id) || !mAttributes.has(id)) continue;
      SummonedPet other = mTrap.has(id) ? mTrap.get(id) : null;
      if (other != null) continue;
      float distance = origin.dst2(mPosition.get(id).position);
      Attributes attrs = mAttributes.get(id).attrs;
      com.riiablo.attributes.StatRef hp = attrs != null
          ? attrs.get(com.riiablo.attributes.Stat.hitpoints) : null;
      if (distance >= bestDistance || hp == null || hp.asFixed() <= 0f) continue;
      bestDistance = distance;
      best = id;
    }
    return best;
  }

  private static String resolveMissile(Skills.Entry skill, Monster monster) {
    if (skill != null) {
      String[] names = {skill.srvmissilea, skill.srvmissileb, skill.srvmissile,
          skill.cltmissilea, skill.cltmissileb};
      for (String name : names) if (name != null && !name.isEmpty()) return name;
    }
    if (monster != null && monster.monstats != null) {
      String[] names = {monster.monstats.MissA1, monster.monstats.MissA2,
          monster.monstats.MissS1, monster.monstats.MissS2,
          monster.monstats.MissS3, monster.monstats.MissS4};
      for (String name : names) if (name != null && !name.isEmpty()) return name;
    }
    return null;
  }
}
