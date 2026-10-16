package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.skill.SkillCodes;

/** D2MOO AITHINK_Fn088_7TIllusion, 0x6FCE9AF0. */
public class SevenTIllusion extends NativeDecisionAI {
  private boolean attacked;

  public SevenTIllusion(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() { act(); }

  @Override protected void think(int targetId, float distance, boolean combat) { act(); }

  private void act() {
    if (attacked) {
      mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
      kill();
    } else {
      Vector2 point = new Vector2(mPosition.get(entityId).position).add(-10, 0);
      castFixed(SkillCodes.attack, Engine.Monster.MODE_A1, Engine.INVALID_ENTITY, point);
      attacked = true;
    }
  }
}
