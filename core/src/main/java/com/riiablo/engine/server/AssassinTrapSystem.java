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
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.missile.MissileDamageResolver;
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
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Velocity> mVelocity;
  @com.artemis.annotations.Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;

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
    Skills.Entry attackSkill = resolveAttackSkill(monster, placementSkill);
    int target = nearestHostile(entityId, monster);
    if (target < 0) {
      trap.attackCooldownFrames = inactiveInterval(monster);
      return;
    }
    if (MathUtils.random(99) >= attackChance(monster)) {
      trap.attackCooldownFrames = attackInterval(monster);
      return;
    }
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

  private static Skills.Entry resolveAttackSkill(Monster monster, Skills.Entry fallback) {
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
