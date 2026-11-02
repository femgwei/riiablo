package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.SummonedPet;

/** D2MOO AITHINK_Fn086_Hydra: stationary summon which fires HydraMissile. */
public final class Hydra extends NativeDecisionAI {
  public Hydra(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    SummonedPet pet = mSummonedPet.has(entityId) ? mSummonedPet.get(entityId) : null;
    if (pet != null && pet.durationFrames > 0
        && pet.elapsedFrames >= pet.durationFrames) {
      idle(1);
      return;
    }
    // Native Fn086 uses target-distance < 25 and a 60% roll, then idles 10 frames.
    if (targetId != Engine.INVALID_ENTITY && distance < 25f && rollAiPercentage() < 60
        && hasSkill(0) && mPosition.has(targetId)
        && useMonsterSkill(0, targetId, mPosition.get(targetId).position,
            Engine.INVALID_MODE)) return;
    idle(10);
  }
}
