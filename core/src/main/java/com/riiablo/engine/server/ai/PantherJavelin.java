package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn095_PantherJavelin (0x6FCDFB80). */
public class PantherJavelin extends NativeDecisionAI {
  static final int PARAM_APPROACH_CHANCE = 0;
  static final int PARAM_THROW_CHANCE = 1;
  static final int PARAM_GROUP_DISTANCE = 2;
  static final int PARAM_WALK_AWAY_CHANCE = 3;
  static final int PARAM_STALL_DURATION = 4;
  static final int PARAM_THROW_DISTANCE = 5;

  private final Vector2 escape = new Vector2();

  public PantherJavelin(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (distance < 8f && rollAiChance(params[PARAM_WALK_AWAY_CHANCE])) {
      escape.set(mPosition.get(entityId).position)
          .sub(mPosition.get(targetId).position);
      if (escape.isZero(0.0001f)) escape.set(1f, 0f);
      escape.nor().scl(16f).add(mPosition.get(entityId).position);
      if (!walkTo(escape, 0, Engine.INVALID_ENTITY)) idle(params[PARAM_STALL_DURATION]);
      return;
    }

    if (distance > params[PARAM_THROW_DISTANCE] - 6
        && rollAiChance(params[PARAM_APPROACH_CHANCE])) {
      approach(targetId, 0);
      return;
    }

    if (distance >= params[PARAM_THROW_DISTANCE]) {
      idle(params[PARAM_STALL_DURATION]);
      return;
    }

    if (rollAiChance(params[PARAM_THROW_CHANCE])) {
      // Panther javelins are native MissA1 attacks; Actioneer resolves the
      // MonStats missile from the ordinary A1 sequence.
      attack(targetId);
    } else {
      idle(params[PARAM_STALL_DURATION]);
    }
  }
}
