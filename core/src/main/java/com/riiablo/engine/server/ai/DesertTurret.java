package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;

/** D2MOO AiThink.cpp AITHINK_Fn094_DesertTurret (0x6FCDF410). */
public class DesertTurret extends NativeDecisionAI {
  static final int PARAM_SHORT_DELAY = 0;
  static final int PARAM_NUM_SHOTS = 1;
  static final int PARAM_LONG_DELAY = 2;
  static final int PARAM_RANGE = 3;
  static final int PARAM_SPREAD = 4;

  private static final int[] DIRECTION_TABLE = {
      0, 0, 1, 2, 5, 6, 7, 0,
      1, 1, 1, 2, 3, 6, 7, 0,
      1, 2, 2, 2, 3, 4, 7, 0,
      1, 2, 3, 3, 3, 4, 5, 6,
      1, 2, 3, 4, 4, 4, 5, 6,
      7, 2, 3, 4, 5, 5, 5, 6,
      7, 0, 3, 4, 5, 6, 6, 6,
      7, 0, 1, 4, 5, 6, 7, 7};
  private static final Vector2[] OFFSETS = {
      new Vector2(1, 1), new Vector2(0, 1), new Vector2(-1, 1), new Vector2(-1, 0),
      new Vector2(-1, -1), new Vector2(0, -1), new Vector2(1, -1), new Vector2(1, 0)};

  private int shotCount;
  private float nextAction;
  private boolean initialized;
  private final Vector2 aim = new Vector2();

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(shotCount);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, nextAction);
    out.writeBoolean(initialized);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedShotCount = in.readInt();
    float savedNextAction = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    boolean savedInitialized = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    if (apply) {
      shotCount = savedShotCount;
      nextAction = savedNextAction;
      initialized = savedInitialized;
    }
  }

  public DesertTurret(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    nextAction = Math.max(0f, nextAction - Math.max(0f, delta));
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (!initialized && hasSkill(0) && castAt(0, targetId,
        mPosition.get(entityId).position)) {
      initialized = true;
      // Native records the current frame only; the short delay starts after
      // the first directional shot, not after this initialization cast.
      nextAction = 0f;
      return;
    }
    if (nextAction > 0f) {
      idle(10);
      return;
    }
    if (distance > params[PARAM_RANGE]) {
      if (shotCount > 0) --shotCount;
      idle(15);
      return;
    }

    int directionOctant = targetOctant(targetId);
    int direction = DIRECTION_TABLE[(shotCount & 7) + 8 * directionOctant];
    aim.set(mPosition.get(entityId).position).add(
        OFFSETS[direction].x * params[PARAM_SPREAD],
        OFFSETS[direction].y * params[PARAM_SPREAD]);
    mTarget.create(entityId).target = targetId;
    if (hasSkill(0) && castAt(0, targetId, aim)) {
      ++shotCount;
      nextAction = framesToSeconds(shotCount <= params[PARAM_NUM_SHOTS]
          ? params[PARAM_SHORT_DELAY] : params[PARAM_LONG_DELAY]);
      if (shotCount > params[PARAM_NUM_SHOTS]) shotCount = 0;
    } else {
      idle(10);
    }
  }

  private static float framesToSeconds(int frames) {
    return Math.max(0, frames) * Animation.FRAME_DURATION;
  }

  private int targetOctant(int targetId) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    double radians = Math.atan2(target.y - source.y, target.x - source.x);
    int octant = (int) Math.round(radians / (Math.PI / 4d));
    return octant < 0 ? octant + 8 : octant & 7;
  }
}
