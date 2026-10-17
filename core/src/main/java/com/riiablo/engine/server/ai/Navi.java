package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.attributes.Stat;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;

/** Defensive AI for Flavie at the Blood Moor/Cold Plains border. */
public final class Navi extends Npc {
  private static final Logger log = LogManager.getLogger(Navi.class);
  private static final float BURST_PAUSE_MIN = 1f;
  private static final float BURST_PAUSE_MAX = 2f;
  private float nextThink;
  private float burstPause;
  private int burstRemaining;
  private NativeRng attackRng;

  public Navi(int entityId) {
    super(entityId);
  }

  @Override
  public void initialize() {
    super.initialize();
    // The retail navi row leaves A1MinD/A1MaxD empty and lets rogue1 inherit
    // the attacker's packet.  Preserve Flavie's stronger defensive attack in
    // the converted runtime; otherwise the empty fields become a 1-point
    // arrow.  Scale it with her native area level so the one/two-arrow feel
    // remains useful outside Normal as well.
    if (mAttributesWrapper.has(entityId)) {
      int level = Math.max(1, mAttributesWrapper.get(entityId).attrs.base()
          .getValue(Stat.level, 1));
      int levelBonus = Math.max(0, level - 1);
      mAttributesWrapper.get(entityId).attrs.base().put(Stat.mindamage,
          Math.max(35, 35 + levelBonus * 4));
      mAttributesWrapper.get(entityId).attrs.base().put(Stat.maxdamage,
          Math.max(55, 55 + levelBonus * 6));
      mAttributesWrapper.get(entityId).attrs.base().put(Stat.tohit,
          Math.max(101, 101 + levelBonus * 2));
      // Factory construction has already aggregated the initial base list;
      // rebuild it after replacing the native Navi attack profile so missile
      // snapshots read the new values from the aggregate list.
      mAttributesWrapper.get(entityId).attrs.reset();
    }
    attackRng = NativeRng.forUnit(gameSeed() ^ 0x4E415649, entityId);
    burstPause = 0f;
    burstRemaining = 0;
  }

  @Override
  public void update(float delta) {
    if (mInteractable.has(entityId) && mInteractable.get(entityId).count > 0) {
      super.update(delta);
      return;
    }
    if (monster == null || !mPosition.has(entityId)) return;
    if (attackRng == null) {
      attackRng = NativeRng.forUnit(gameSeed() ^ 0x4E415649, entityId);
    }
    if (burstPause > 0f) {
      burstPause = Math.max(0f, burstPause - delta);
      return;
    }
    nextThink -= delta;
    if (nextThink > 0f || mCasting.has(entityId) || mSequence.has(entityId)) return;
    nextThink = Math.max(0.15f, SLEEP);

    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) return;
    float attackRange = nativeAttackRange();
    if (distance[0] > attackRange) return;

    if (burstRemaining <= 0) {
      burstRemaining = 1 + attackRng.nextInt(3);
    }

    Vector2 target = mPosition.get(targetId).position;
    lookAt(targetId);
    com.riiablo.engine.server.component.Casting casting = mCasting.create(entityId)
        .set(SkillCodes.attack, targetId, target);
    // Flavie's native row is a ranged basic attack. Some converted Navi COFs
    // expose the A1 animation without an ATK/MIS callback, so create the
    // authoritative arrow at cast start as a safe fallback. Actioneer marks
    // the cast and will not emit a duplicate if a later keyframe is present.
    if (createNativeArrow(targetId, target)) casting.monsterMissileCreated = true;
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
    burstRemaining--;
    if (burstRemaining == 0) {
      burstPause = BURST_PAUSE_MIN
          + attackRng.nextInt((int) ((BURST_PAUSE_MAX - BURST_PAUSE_MIN) * 1000f) + 1)
              / 1000f;
    }
    log.info("[NAVI_COMBAT] entity={} target={} distance={} range={} action=attack "
            + "burstRemaining={} pause={}",
        entityId, targetId, distance[0], attackRange, burstRemaining, burstPause);
  }

  private boolean createNativeArrow(int targetId, Vector2 target) {
    if (factory == null || monster == null || monster.monstats == null
        || files() == null || files().Missiles == null
        || !mPosition.has(entityId)) return false;
    String missileName = monster.monstats.MissA1;
    if (missileName == null || missileName.isEmpty()) missileName = monster.monstats.MissA2;
    if (missileName == null || missileName.isEmpty()) {
      log.warn("[NAVI_MISSILE] entity={} reason=missing_monstats_missile", entityId);
      return false;
    }
    Missiles.Entry missile = files().Missiles.get(missileName);
    if (missile == null) {
      log.warn("[NAVI_MISSILE] entity={} missile={} reason=lookup_failed", entityId, missileName);
      return false;
    }
    Vector2 origin = mPosition.get(entityId).position;
    Vector2 direction = new Vector2(target).sub(origin);
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    direction.nor();
    int missileId = factory.createMissile(missile, direction, origin, entityId);
    if (missileId == Engine.INVALID_ENTITY) {
      log.warn("[NAVI_MISSILE] entity={} missile={} reason=create_failed", entityId, missileName);
      return false;
    }
    log.info("[NAVI_MISSILE] entity={} target={} missileId={} missile={} direction=({}, {})",
        entityId, targetId, missileId, missile.Missile, direction.x, direction.y);
    return true;
  }

  private float nativeAttackRange() {
    String missileName = monster.monstats == null ? null : monster.monstats.MissA1;
    if (missileName != null && !missileName.isEmpty() && files() != null
        && files().Missiles != null) {
      Missiles.Entry missile = files().Missiles.get(missileName);
      if (missile != null && missile.Range > 0) return Math.max(4f, missile.Range - 2f);
    }
    return 8f;
  }
}
