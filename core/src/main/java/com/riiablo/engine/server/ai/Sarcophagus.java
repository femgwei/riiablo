package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;

/** D2MOO AiThink.cpp AITHINK_Fn045_Sarcophagus (0x6FCD91F0). */
public class Sarcophagus extends NativeDecisionAI {
  static final int PARAM_SPAWN_INTERVAL = 0;
  static final int PARAM_NUM_TO_SPAWN = 2;

  private int spawned;
  private float nextSpawn;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(spawned);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, nextSpawn);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedSpawned = in.readInt();
    float savedNextSpawn = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    if (apply) {
      spawned = savedSpawned;
      nextSpawn = savedNextSpawn;
    }
  }
  public Sarcophagus(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    nextSpawn = Math.max(0f, nextSpawn - Math.max(0f, delta));
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (distance > 25f) {
      idle(25);
      return;
    }
    if (spawned <= params[PARAM_NUM_TO_SPAWN]) {
      if (hasSkill(0) && nextSpawn <= 0f && castAt(0, targetId,
          mPosition.get(targetId).position)) {
        ++spawned;
        nextSpawn = Math.max(0, params[PARAM_SPAWN_INTERVAL]) * Animation.FRAME_DURATION;
      } else {
        idle(20);
      }
      return;
    }
    // Native marks the generator non-targetable and leaves it in neutral mode.
    stopMovement();
    idle(1);
  }
}
