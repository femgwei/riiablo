package com.riiablo.engine.client;

/**
 * Single-slot render-to-simulation pointer edge queue.
 *
 * <p>The render thread captures a press while the fixed simulation consumes it
 * later. Keeping the value immutable prevents a cursor move between those two
 * phases from changing the destination that will be acted on.</p>
 */
final class PointerClickQueue {
  static final class Click {
    final float screenX;
    final float screenY;
    final long capturedAtMillis;
    final long observedTick;

    Click(float screenX, float screenY, long capturedAtMillis, long observedTick) {
      this.screenX = screenX;
      this.screenY = screenY;
      this.capturedAtMillis = capturedAtMillis;
      this.observedTick = observedTick;
    }
  }

  private Click pending;

  void capture(float screenX, float screenY, long capturedAtMillis, long observedTick) {
    // A second render-frame edge cannot overwrite an unconsumed command; the
    // client sends one movement intent at a time and the next edge is sampled
    // after the first fixed Tick has consumed this slot.
    if (pending == null) {
      pending = new Click(screenX, screenY, capturedAtMillis, observedTick);
    }
  }

  Click poll() {
    Click click = pending;
    pending = null;
    return click;
  }

  /** Returns a temporarily unconsumed click to the queue unchanged. */
  void restore(Click click) {
    if (click != null && pending == null) pending = click;
  }

  boolean hasPending() {
    return pending != null;
  }
}
