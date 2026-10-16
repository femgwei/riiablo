package com.riiablo.engine.server.ai;

import com.riiablo.engine.server.component.NativeUnitFlags;

/** D2MOO AITHINK_Fn080_092_TrapPoison_TrapNova (AiThink.cpp, 0x6FCDE8B0). */
public class TrapNova extends NativeDecisionAI {
  private int casts;
  private boolean needsDelay;

  public TrapNova(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() { expire(); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (distance > params[0] || casts >= params[1]) {
      expire();
    } else if (hasSkill(0) && !needsDelay) {
      cast(0, targetId);
      // Native consumes a use after UseSkill even when the action fails.
      needsDelay = true;
      casts++;
    } else {
      needsDelay = false;
      idle(params[2]);
    }
  }

  private void expire() {
    mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
    kill();
  }
}
