package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.codec.excel.MonStats;

/** D2MOO AiThink.cpp AITHINK_Fn066_SandMaggotQueen (0x6FCDC600). */
public class SandMaggotQueen extends NativeDecisionAI {
  static final int PARAM_MAX_SPAWN = 0;
  static final int PARAM_DELAY = 1;

  private int spawned;
  private float nextSpawn;

  public SandMaggotQueen(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    nextSpawn = Math.max(0f, nextSpawn - Math.max(0f, delta));
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (spawned >= params[PARAM_MAX_SPAWN] || nextSpawn > 0f) {
      idle(params[PARAM_DELAY]);
      return;
    }
    MonStats.Entry minion = monster.monstats.minion1 == null
        ? null : files().monstats.get(monster.monstats.minion1);
    if (minion == null || factory == null || !mPosition.has(entityId)) {
      idle(params[PARAM_DELAY]);
      return;
    }
    com.badlogic.gdx.math.Vector2 position = mPosition.get(entityId).position;
    int child = factory.createMonster(minion.hcIdx, position.x, position.y);
    if (child != Engine.INVALID_ENTITY) ++spawned;
    nextSpawn = Math.max(0, params[PARAM_DELAY]) * Animation.FRAME_DURATION;
    idle(params[PARAM_DELAY]);
  }
}
