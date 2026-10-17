package com.riiablo.engine.server;

import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Size;

/** Shared range contract for entity interaction movement and activation. */
public final class InteractionRange {
  private static final float MINIMUM_RANGE = 0.5f;
  private static final float POSITION_EPSILON = 0.125f;

  private InteractionRange() {}

  public static float effective(Interactable interactable, Size sourceSize) {
    float targetRange = interactable == null ? 0f : interactable.range;
    int footprint = sourceSize == null ? Size.MEDIUM : Math.max(0, sourceSize.size);
    return Math.max(MINIMUM_RANGE, targetRange) + footprint;
  }

  public static boolean contains(float distance, Interactable interactable, Size sourceSize) {
    return distance <= effective(interactable, sourceSize) + POSITION_EPSILON;
  }
}
