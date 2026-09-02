package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Position;

/** Native D2MOO AI 052: submerged ranged monster that emerges near prey. */
public final class FrogDemon extends AI {
  private static final float EMERGE_DISTANCE = 8f;
  private int nativeState;
  private int submergedTicks;
  private float nextThink;
  private String state = "SURFACED";

  protected com.artemis.ComponentMapper<Position> mPosition;
  public FrogDemon(int entityId) { super(entityId); }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    nextThink -= delta;
    if (nextThink > 0f || mCasting.has(entityId) || mSequence.has(entityId)) return;
    nextThink = Math.max(0.15f, SLEEP);
    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) return;
    Vector2 target = mPosition.get(targetId).position;

    if (nativeState == 0 && distance[0] > 12f && useMonsterSkill(0, targetId, target)) {
      nativeState = 1;
      state = "SUBMERGED";
      return;
    }
    if (nativeState == 1) {
      float emergeDistance = params[7] > 0 ? params[7] : EMERGE_DISTANCE;
      if ((distance[0] < emergeDistance || (distance[0] < 20f && submergedTicks > 64))
          && useMonsterSkill(1, entityId, mPosition.get(entityId).position)) {
        nativeState = 2;
        submergedTicks = 0;
        state = "EMERGING";
        return;
      }
      submergedTicks++;
      state = "SUBMERGED";
      return;
    }
    boolean melee = distance[0] <= 2f;
    boolean shoot = melee
        ? roll(params[1])
        : distance[0] < Math.max(2, params[5]) && roll(params[4]);
    if (shoot) {
      stopMovement();
      lookAt(targetId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, target);
      state = "SHOOT";
    } else if (melee && roll(params[0])) {
      lookAt(targetId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, target);
      state = "ATTACK";
    } else {
      walkTo(target, targetId);
      state = "APPROACH";
    }
    if (nativeState == 2) nativeState = 0;
  }

  private static boolean roll(int chance) {
    return chance > 0 && com.badlogic.gdx.math.MathUtils.randomBoolean(
        Math.min(100, chance) / 100f);
  }

  @Override public void kill() {
    stopMovement();
    state = "DEAD";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    Riiablo.audio.play(monsound + "_death_1", true);
  }
  @Override public String getState() { return state; }
}
