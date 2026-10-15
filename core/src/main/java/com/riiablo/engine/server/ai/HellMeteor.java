package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn033_HellMeteor (AiThink.cpp, 0x6FCD7EB0). */
public class HellMeteor extends NativeDecisionAI {
  public HellMeteor(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    decide();
  }

  @Override protected void thinkWithoutTarget() {
    decide();
  }

  private void decide() {
    if (hasSkill(0) && rollAiChance(params[0])) {
      Vector2 position = mPosition.get(entityId).position;
      int range = params[2];
      int x = (int) position.x + limited(2 * range) - range;
      int y = (int) position.y + limited(2 * range) - range;
      castAt(0, Engine.INVALID_ENTITY, new Vector2(x, y));
      return;
    }
    idle(params[1]);
  }

  // ITEMS_RollLimitedRandomNumber returns zero without a draw for max <= 0.
  private int limited(int max) {
    return max <= 0 ? 0 : (int) (rollAiRandomNumber() % max);
  }
}
