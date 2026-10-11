package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;

/** Defensive AI for Flavie at the Blood Moor/Cold Plains border. */
public final class Navi extends Npc {
  private static final Logger log = LogManager.getLogger(Navi.class);
  private float nextThink;

  public Navi(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    if (mInteractable.has(entityId) && mInteractable.get(entityId).count > 0) {
      super.update(delta);
      return;
    }
    if (monster == null || !mPosition.has(entityId)) return;
    nextThink -= delta;
    if (nextThink > 0f || mCasting.has(entityId) || mSequence.has(entityId)) return;
    nextThink = Math.max(0.15f, SLEEP);

    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) return;
    float attackRange = nativeAttackRange();
    if (distance[0] > attackRange) return;

    Vector2 target = mPosition.get(targetId).position;
    lookAt(targetId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(SkillCodes.attack, targetId, target);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
    log.info("[NAVI_COMBAT] entity={} target={} distance={} range={} action=attack",
        entityId, targetId, distance[0], attackRange);
  }

  private float nativeAttackRange() {
    String missileName = monster.monstats == null ? null : monster.monstats.MissA1;
    if (missileName != null && !missileName.isEmpty() && Riiablo.files != null
        && Riiablo.files.Missiles != null) {
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
      if (missile != null && missile.Range > 0) return Math.max(4f, missile.Range - 2f);
    }
    return 8f;
  }
}
