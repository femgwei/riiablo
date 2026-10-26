package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.SimulationTickCoordinator;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Fixed-step, single-writer boundary for an authoritative ECS world.
 *
 * <p>The dedicated D2GS is already hosted by a 25 Hz LibGDX headless loop.
 * This class makes the contract explicit: network input is applied first,
 * exactly one native game frame is simulated, then resulting snapshots are
 * dispatched. The first caller owns the world for its remaining lifetime.
 */
public final class AuthoritativeSimulation {
  private static final Logger log = LogManager.getLogger(AuthoritativeSimulation.class);
  private static final ThreadLocal<AuthoritativeSimulation> CURRENT = new ThreadLocal<>();

  public static final int TICKS_PER_SECOND = SimulationClock.TICKS_PER_SECOND;
  public static final float STEP_SECONDS = SimulationClock.STEP_SECONDS;

  private final World world;
  private volatile Thread ownerThread;
  private volatile long tick;
  private volatile float lastStepSeconds;
  private final SimulationTickCoordinator tickCoordinator = new SimulationTickCoordinator();
  private float accumulatedSeconds;
  private static final int MAX_CATCH_UP_STEPS = 8;
  private final long epochMillis = System.currentTimeMillis();
  private volatile long serverTimeMillis;

  public AuthoritativeSimulation(World world) {
    if (world == null) throw new NullPointerException("world");
    this.world = world;
  }

  /** Runs one native authoritative frame in input/simulation/output order. */
  public void tick(Runnable applyIncoming, Runnable dispatchOutgoing) {
    assertOwnerThread();
    long nextTick = tick + 1L;
    tick = nextTick;
    serverTimeMillis = epochMillis + nextTick * SimulationClock.STEP_MILLIS;
    AuthoritativeSimulation previous = CURRENT.get();
    CURRENT.set(this);
    try {
      tickCoordinator.advance(
          () -> { if (applyIncoming != null) applyIncoming.run(); },
          ignored -> {
            world.setDelta(STEP_SECONDS);
            lastStepSeconds = world.getDelta();
            world.process();
          },
          () -> { if (dispatchOutgoing != null) dispatchOutgoing.run(); });
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }

  /**
   * Advances the authoritative simulation from an arbitrary render/host delta.
   * The accumulator is bounded so a suspended process cannot execute an
   * unbounded catch-up burst when it becomes active again.
   */
  public int advance(float elapsedSeconds, Runnable applyIncoming,
      Runnable dispatchOutgoing) {
    assertOwnerThread();
    if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0f) return 0;
    accumulatedSeconds += Math.min(elapsedSeconds,
        STEP_SECONDS * MAX_CATCH_UP_STEPS);
    int steps = 0;
    while (accumulatedSeconds + 0.000001f >= STEP_SECONDS
        && steps < MAX_CATCH_UP_STEPS) {
      tick(applyIncoming, dispatchOutgoing);
      accumulatedSeconds -= STEP_SECONDS;
      steps++;
    }
    if (steps == MAX_CATCH_UP_STEPS && accumulatedSeconds >= STEP_SECONDS) {
      accumulatedSeconds %= STEP_SECONDS;
    }
    return steps;
  }

  public float accumulatedSeconds() { return accumulatedSeconds; }

  public SimulationTickCoordinator tickCoordinator() { return tickCoordinator; }

  /** Simulation clock visible to serializers running inside the current tick. */
  public static AuthoritativeSimulation current() {
    return CURRENT.get();
  }

  private synchronized void assertOwnerThread() {
    Thread current = Thread.currentThread();
    if (ownerThread == null) {
      ownerThread = current;
      log.info("[SIM_TICK] phase=bound thread={} rate={}Hz step={}s",
          current.getName(), TICKS_PER_SECOND, STEP_SECONDS);
      return;
    }
    if (ownerThread != current) {
      throw new IllegalStateException(
          "Authoritative ECS world is owned by thread '" + ownerThread.getName()
              + "', but tick was requested by '" + current.getName() + "'");
    }
  }

  public long tickNumber() {
    return tick;
  }

  public Thread ownerThread() {
    return ownerThread;
  }

  /** Last step actually supplied to the ECS world, or zero before the first tick. */
  public float lastStepSeconds() {
    return lastStepSeconds;
  }

  public long serverTimeMillis() {
    return serverTimeMillis;
  }

  /**
   * Restores the simulation clock after an authoritative ECS checkpoint has
   * been applied on the owning thread.  The world mutation itself remains the
   * responsibility of the checkpoint adapter.
   */
  public synchronized void restoreFromCheckpoint(long checkpointTick) {
    restoreFromCheckpoint(checkpointTick, false);
  }

  /**
   * Restores the simulation clock for an explicit replay/checkpoint transaction.
   *
   * <p>Normal callers retain the monotonic-clock guard from
   * {@link #restoreFromCheckpoint(long)}. A replay adapter may explicitly
   * allow a rewind, but it must still run on the owning simulation thread and
   * apply the ECS checkpoint before calling this method.
   */
  public synchronized void restoreFromCheckpoint(long checkpointTick,
      boolean allowTimeRewind) {
    if (checkpointTick < 0L) throw new IllegalArgumentException("checkpointTick");
    Thread current = Thread.currentThread();
    if (ownerThread != null && ownerThread != current) {
      throw new IllegalStateException("checkpoint restore must run on the owner thread");
    }
    if (ownerThread != null && checkpointTick < tick && !allowTimeRewind) {
      throw new IllegalStateException("checkpoint restore cannot move the clock backwards");
    }
    if (ownerThread == null) ownerThread = current;
    tick = checkpointTick;
    tickCoordinator.restore(checkpointTick);
    accumulatedSeconds = 0f;
    serverTimeMillis = epochMillis + checkpointTick * SimulationClock.STEP_MILLIS;
  }
}
