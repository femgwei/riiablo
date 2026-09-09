package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.Engine;

/**
 * Java projection of the temporary target fields written by D2Game's
 * {@code sub_6FC61E30}. Attract writes a fixed monster target while Confuse
 * asks native target selection to choose another hostile monster.
 */
@Transient
@PooledWeaver
public class NativeAiTargetOverride extends PooledComponent {
  public static final int NONE = 0;
  public static final int ATTRACT = 2;
  public static final int CONFUSE = 3;

  public int mode;
  @EntityId public int targetId = Engine.INVALID_ENTITY;
  @EntityId public int sourceEntityId = Engine.INVALID_ENTITY;
  public int sourceSkillId = -1;
  public int remainingFrames;
  public int rngState;

  public NativeAiTargetOverride setAttract(
      int targetId, int sourceEntityId, int sourceSkillId, int duration) {
    mode = ATTRACT;
    this.targetId = targetId;
    this.sourceEntityId = sourceEntityId;
    this.sourceSkillId = sourceSkillId;
    remainingFrames = Math.max(1, duration);
    rngState = 0;
    return this;
  }

  public NativeAiTargetOverride setConfuse(
      int sourceEntityId, int sourceSkillId, int duration, int seed) {
    mode = CONFUSE;
    targetId = Engine.INVALID_ENTITY;
    this.sourceEntityId = sourceEntityId;
    this.sourceSkillId = sourceSkillId;
    remainingFrames = Math.max(1, duration);
    rngState = seed;
    return this;
  }

  @Override
  protected void reset() {
    mode = NONE;
    targetId = Engine.INVALID_ENTITY;
    sourceEntityId = Engine.INVALID_ENTITY;
    sourceSkillId = -1;
    remainingFrames = 0;
    rngState = 0;
  }
}
