package com.riiablo.engine.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded local movement history used to replay inputs after a server ACK. */
public final class ClientPredictionBuffer {
  public static final int DEFAULT_CAPACITY = 128;
  public static final float SMALL_ERROR = 0.125f;
  public static final float HARD_ERROR = 8f;

  private final int capacity;
  private final Deque<Input> pending = new ArrayDeque<>();
  private long lastSentSequence;
  private long lastAcknowledgedSequence;
  private float sendBaselineX;
  private float sendBaselineY;
  private boolean hasSendBaseline;
  private boolean replayIncomplete;

  public ClientPredictionBuffer() {
    this(DEFAULT_CAPACITY);
  }

  ClientPredictionBuffer(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
  }

  /** Starts a connection/reconnect epoch at the current predicted position. */
  public void reset(float x, float y) {
    pending.clear();
    lastSentSequence = 0L;
    lastAcknowledgedSequence = 0L;
    replayIncomplete = false;
    resetSendBaseline(x, y);
  }

  /** Applies a legacy authoritative frame without restarting the wire sequence. */
  public void rebaseLegacy(float x, float y) {
    pending.clear();
    lastAcknowledgedSequence = 0L;
    replayIncomplete = false;
    resetSendBaseline(x, y);
  }

  /** Prevents a reconciliation displacement from becoming a new input delta. */
  public void resetSendBaseline(float x, float y) {
    sendBaselineX = x;
    sendBaselineY = y;
    hasSendBaseline = true;
  }

  public boolean recordSent(long sequence, float predictedX, float predictedY) {
    if (sequence <= 0L || sequence <= lastSentSequence) return false;
    float dx = hasSendBaseline ? predictedX - sendBaselineX : 0f;
    float dy = hasSendBaseline ? predictedY - sendBaselineY : 0f;
    pending.addLast(new Input(sequence, dx, dy));
    lastSentSequence = sequence;
    resetSendBaseline(predictedX, predictedY);
    if (pending.size() > capacity) {
      pending.removeFirst();
      replayIncomplete = true;
    }
    return true;
  }

  /**
   * Rebases prediction on the authoritative position and replays unconfirmed
   * displacements. A rejected input, Warp, death or large divergence snaps.
   */
  public Reconciliation reconcile(long acknowledgedSequence,
      float authoritativeX, float authoritativeY,
      float currentPredictedX, float currentPredictedY,
      boolean forceHardCorrection) {
    if (acknowledgedSequence < lastAcknowledgedSequence) {
      return Reconciliation.stale(currentPredictedX, currentPredictedY);
    }

    while (!pending.isEmpty()
        && pending.peekFirst().sequence <= acknowledgedSequence) {
      pending.removeFirst();
    }
    lastAcknowledgedSequence = acknowledgedSequence;

    boolean hard = forceHardCorrection || replayIncomplete;
    float targetX = authoritativeX;
    float targetY = authoritativeY;
    if (!hard) {
      for (Input input : pending) {
        targetX += input.dx;
        targetY += input.dy;
      }
    }

    float offsetX = currentPredictedX - targetX;
    float offsetY = currentPredictedY - targetY;
    float error2 = offsetX * offsetX + offsetY * offsetY;
    if (error2 > HARD_ERROR * HARD_ERROR) hard = true;
    if (hard) {
      pending.clear();
      replayIncomplete = false;
      targetX = authoritativeX;
      targetY = authoritativeY;
      offsetX = 0f;
      offsetY = 0f;
    }
    resetSendBaseline(targetX, targetY);
    boolean smooth = !hard && error2 > SMALL_ERROR * SMALL_ERROR;
    return new Reconciliation(false, hard, smooth, targetX, targetY,
        smooth ? offsetX : 0f, smooth ? offsetY : 0f);
  }

  public int pendingCount() {
    return pending.size();
  }

  public long lastAcknowledgedSequence() {
    return lastAcknowledgedSequence;
  }

  private static final class Input {
    final long sequence;
    final float dx;
    final float dy;

    Input(long sequence, float dx, float dy) {
      this.sequence = sequence;
      this.dx = dx;
      this.dy = dy;
    }
  }

  public static final class Reconciliation {
    public final boolean stale;
    public final boolean hard;
    public final boolean smooth;
    public final float x;
    public final float y;
    public final float renderOffsetX;
    public final float renderOffsetY;

    Reconciliation(boolean stale, boolean hard, boolean smooth,
        float x, float y, float renderOffsetX, float renderOffsetY) {
      this.stale = stale;
      this.hard = hard;
      this.smooth = smooth;
      this.x = x;
      this.y = y;
      this.renderOffsetX = renderOffsetX;
      this.renderOffsetY = renderOffsetY;
    }

    static Reconciliation stale(float x, float y) {
      return new Reconciliation(true, false, false, x, y, 0f, 0f);
    }
  }
}
