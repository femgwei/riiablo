package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn097_Spirit (AiThink.cpp, 0x6FCE28A0). */
public class Spirit extends NativeDecisionAI {
  private boolean attacked;

  public Spirit(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (attacked) {
      idle(50);
    } else if (combat) {
      // Native commits the one-shot flag before requesting the attack mode.
      attacked = true;
      attack(targetId);
    } else {
      idle(10);
    }
  }

  @Override protected void thinkWithoutTarget() {
    idle(attacked ? 50 : 10);
  }
}
