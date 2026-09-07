package com.riiablo.engine.client;

/**
 * Accumulates render time and invokes a simulation callback at a fixed rate.
 *
 * <p>The accumulator deliberately has a bounded catch-up count.  A window
 * which was suspended must not cause the client to execute an unbounded number
 * of simulation steps when it becomes visible again.</n+ */
public final class FixedStepAccumulator {
  @FunctionalInterface
  public interface StepListener {
    void step(float stepSeconds);
  }

  private final float stepSeconds;
  private final int maxStepsPerAdvance;
  private float accumulated;

  public FixedStepAccumulator(float stepSeconds, int maxStepsPerAdvance) {
    if (!Float.isFinite(stepSeconds) || stepSeconds <= 0f) {
      throw new IllegalArgumentException("stepSeconds must be finite and positive");
    }
    if (maxStepsPerAdvance <= 0) {
      throw new IllegalArgumentException("maxStepsPerAdvance must be positive");
    }
    this.stepSeconds = stepSeconds;
    this.maxStepsPerAdvance = maxStepsPerAdvance;
  }

  /** Adds render time and runs at most {@code maxStepsPerAdvance} steps. */
  public int advance(float delta, StepListener listener) {
    if (listener == null) throw new NullPointerException("listener");
    if (!Float.isFinite(delta) || delta <= 0f) return 0;

    // Never allow a single delayed frame to create an unbounded backlog.
    accumulated += Math.min(delta, stepSeconds * maxStepsPerAdvance);

    int steps = 0;
    while (accumulated + 0.000001f >= stepSeconds
        && steps < maxStepsPerAdvance) {
      listener.step(stepSeconds);
      accumulated -= stepSeconds;
      steps++;
    }

    // If the cap was reached, discard whole overdue ticks. Keep only a
    // fractional remainder so the next visible frame starts smoothly.
    if (steps == maxStepsPerAdvance && accumulated >= stepSeconds) {
      accumulated %= stepSeconds;
    }
    return steps;
  }

  public void reset() {
    accumulated = 0f;
  }

  public float getAccumulated() {
    return accumulated;
  }

  public float getStepSeconds() {
    return stepSeconds;
  }
}
