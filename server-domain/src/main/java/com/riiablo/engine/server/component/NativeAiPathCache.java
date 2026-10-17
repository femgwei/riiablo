package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** Partial dynamic-path state for Baal target selection, NOT an ECS movement
 * path. Unknown is deliberately distinct from a known empty native path. */
@Transient
@PooledWeaver
public class NativeAiPathCache extends PooledComponent {
  public int targetX, targetY;
  public boolean coordinatesKnown;
  public boolean pointsKnown;
  public boolean hasPoints;

  /** Only the actual fresh-monster allocation boundary establishes zeroes. */
  public NativeAiPathCache allocated() {
    targetX = targetY = 0;
    coordinatesKnown = pointsKnown = true;
    hasPoints = false;
    return this;
  }

  public void targetCoordinates(int x, int y) {
    targetX = x;
    targetY = y;
    coordinatesKnown = true;
  }

  public void pathPoints(boolean present) {
    pointsKnown = true;
    hasPoints = present;
  }

  /** An unported native path/mode writer must not leave trusted stale data. */
  public void invalidate() {
    coordinatesKnown = pointsKnown = false;
  }

  @Override
  protected void reset() {
    targetX = targetY = 0;
    coordinatesKnown = pointsKnown = hasPoints = false;
  }
}
