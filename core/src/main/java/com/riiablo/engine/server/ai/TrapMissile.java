package com.riiablo.engine.server.ai;

import com.riiablo.engine.server.component.NativeUnitFlags;

/** D2MOO AITHINK_Fn077_TrapMissile (AiThink.cpp, 0x6FCDE4D0). */
public class TrapMissile extends NativeDecisionAI {
  private int shots;
  private boolean needsDelay;

  public TrapMissile(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() { expire(); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    // Native checks expiry before the alternating delay, including after the last shot.
    if (distance > params[0] || shots >= params[1]) {
      expire();
    } else if (needsDelay) {
      needsDelay = false;
      idle(params[2]);
    } else {
      attack(targetId);
      needsDelay = true;
      shots++;
    }
  }

  private void expire() {
    mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
    kill();
  }
}
