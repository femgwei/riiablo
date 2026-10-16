package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn123_Catapult, 0x6FCE2570: a targetless mode action. */
public class Catapult extends NativeDecisionAI {
  public Catapult(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() { act(); }

  @Override protected void think(int targetId, float distance, boolean combat) { act(); }

  private void act() {
    if (rollAiChance(params[0])) startMode(Engine.Monster.MODE_A1, Engine.INVALID_ENTITY);
    else idle(15);
  }
}
