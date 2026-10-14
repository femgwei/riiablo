package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn136_BaalTaunt (0x6FCF0180). */
public class BaalTaunt extends NativeDecisionAI {
  static final int PARAM_IN_RANGE_DISTANCE = 0;
  static final int PARAM_FRAMES_IN_SIGHT_BEFORE_TAUNT = 1;
  static final int PARAM_MAX_TARGET_DISTANCE = 2;

  private int neutralFrames;

  public BaalTaunt(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (isNeutral(targetId)) {
      if (++neutralFrames > params[PARAM_FRAMES_IN_SIGHT_BEFORE_TAUNT]) {
        neutralFrames = 0;
        // BaalTaunt is a native fixed skill, not necessarily a MonStats slot.
        // startMode preserves the authoritative attack animation until the
        // dedicated server skill entry is available to the monster table.
        startMode(Engine.Monster.MODE_A1, targetId);
        return;
      }
    } else {
      neutralFrames = 0;
    }

    if (distance <= params[PARAM_IN_RANGE_DISTANCE]) {
      idle(25);
    } else {
      // The native branch idles only when a RoomEx path probe reports a
      // blocked route beyond max distance. The current pathfinder exposes no
      // equivalent probe, so retain the target-bound approach action here.
      approach(targetId, 0);
    }
  }

  private boolean isNeutral(int targetId) {
    if (!mCofReference.has(targetId)) return true;
    int mode = mCofReference.get(targetId).mode;
    return mPlayer.has(targetId)
        ? mode == Engine.Player.MODE_NU : mode == Engine.Monster.MODE_NU;
  }
}
