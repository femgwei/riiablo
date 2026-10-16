package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

/** D2MOO AITHINK_Fn079_TrapLeftArrow (AiThink.cpp, 0x6FCDE710). */
public class TrapLeftArrow extends TrapRightArrow {
  public TrapLeftArrow(int entityId) { super(entityId); }

  @Override protected int alignmentDifference(Vector2 source, Vector2 target) {
    return Math.abs((int) source.y - (int) target.y);
  }
}
