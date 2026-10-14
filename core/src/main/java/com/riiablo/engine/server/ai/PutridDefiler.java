package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn137_PutridDefiler (0x6FCF05B0). */
public class PutridDefiler extends NativeDecisionAI {
  static final int PARAM_TOO_CLOSE_DISTANCE = 0;
  static final int PARAM_WALK_AWAY_MAX_DISTANCE = 1;

  private final Vector2 escapePoint = new Vector2();

  public PutridDefiler(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      attack(targetId);
      return;
    }

    // The native callback searches a second hostile node within 25 units.
    // NativeDecisionAI's target scan already supplies the nearest valid node;
    // keep the same range gate before using the impregnate skill.
    if (distance <= 25f) {
      if (isInNativeMeleeRange(targetId) && hasSkill(0)) {
        cast(0, targetId);
      } else {
        approach(targetId, 0);
      }
      return;
    }

    if (distance < params[PARAM_TOO_CLOSE_DISTANCE]) {
      escape(targetId);
    } else {
      idle(25);
    }
  }

  private void escape(int targetId) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    escapePoint.set(source).sub(target);
    if (escapePoint.isZero(0.0001f)) escapePoint.set(1f, 0f);
    escapePoint.nor().scl(Math.max(1, params[PARAM_WALK_AWAY_MAX_DISTANCE]))
        .add(source);
    if (!walkToPoint(escapePoint, 0, Engine.INVALID_ENTITY)) idle(25);
  }
}
