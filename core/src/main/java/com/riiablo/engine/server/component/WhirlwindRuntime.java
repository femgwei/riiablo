package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

/** Server-only state for D2MOO SKILLS_SrvSt38/SrvDo076. */
@PooledWeaver
public class WhirlwindRuntime extends PooledComponent {
  public int skillId = -1;
  public int skillLevel;
  public final Vector2 destination = new Vector2();
  public final Vector2 lastPosition = new Vector2();
  public boolean positionInitialized;
  public float elapsedFrames;
  public float nextAttackFrame;
  public int previousTargetId = Engine.INVALID_ENTITY;
  public int strikeIndex;
  public int stalledFrames;

  public WhirlwindRuntime set(
      int skillId, int skillLevel, Vector2 destination, Vector2 start) {
    this.skillId = skillId;
    this.skillLevel = Math.max(1, skillLevel);
    this.destination.set(destination);
    lastPosition.set(start);
    positionInitialized = true;
    elapsedFrames = 0f;
    nextAttackFrame = 4f;
    previousTargetId = Engine.INVALID_ENTITY;
    strikeIndex = 0;
    stalledFrames = 0;
    return this;
  }

  @Override
  protected void reset() {
    skillId = -1;
    skillLevel = 0;
    destination.setZero();
    lastPosition.setZero();
    positionInitialized = false;
    elapsedFrames = 0f;
    nextAttackFrame = 0f;
    previousTargetId = Engine.INVALID_ENTITY;
    strikeIndex = 0;
    stalledFrames = 0;
  }
}
