package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.codec.Animation;
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

  public static final int TICKS_PER_SECOND = (int) Animation.FRAMES_PER_SECOND;
  public static final float STEP_SECONDS = Animation.FRAME_DURATION;

  private final World world;
  private volatile Thread ownerThread;
  private volatile long tick;
  private volatile float lastStepSeconds;

  public AuthoritativeSimulation(World world) {
    if (world == null) throw new NullPointerException("world");
    this.world = world;
  }

  /** Runs one native authoritative frame in input/simulation/output order. */
  public void tick(Runnable applyIncoming, Runnable dispatchOutgoing) {
    assertOwnerThread();
    if (applyIncoming != null) applyIncoming.run();
    world.setDelta(STEP_SECONDS);
    lastStepSeconds = world.getDelta();
    world.process();
    if (dispatchOutgoing != null) dispatchOutgoing.run();
    tick++;
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
}
