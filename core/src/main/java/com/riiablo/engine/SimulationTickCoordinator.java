package com.riiablo.engine;

import java.util.Objects;
import java.util.function.Consumer;

import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.replay.SimulationReplayRecorder;

/** Defines one fixed-tick phase order without changing existing D2 rule systems. */
public final class SimulationTickCoordinator {
  public enum Phase { IDLE, COMMANDS, SIMULATION, EVENTS, STRUCTURAL_COMMIT }
  @FunctionalInterface public interface TickStep { void run(long tick); }

  private final SimulationCommandQueue commands = new SimulationCommandQueue();
  private final SimulationEventQueue events = new SimulationEventQueue();
  private final StructuralChangeQueue structuralChanges = new StructuralChangeQueue();
  private final SimulationPhaseContract phaseContract;
  private SimulationReplayRecorder replayRecorder = SimulationReplayRecorder.NONE;
  private long tick;
  private Phase phase = Phase.IDLE;

  public SimulationTickCoordinator() {
    this(new SimulationPhaseContract());
  }

  public SimulationTickCoordinator(SimulationPhaseContract phaseContract) {
    this.phaseContract = Objects.requireNonNull(phaseContract, "phaseContract");
  }

  public long tick() { return tick; }
  public Phase phase() { return phase; }
  /**
   * Returns the first tick on which a newly submitted command can be drained.
   *
   * <p>Commands submitted by systems while the current fixed step is already
   * in {@link Phase#SIMULATION}, {@link Phase#EVENTS}, or
   * {@link Phase#STRUCTURAL_COMMIT} cannot be consumed by that step: its
   * command drain has already happened.  The old call sites blindly used
   * {@code tick + 1}, which is the tick currently being simulated and is
   * rejected as stale after the drain advances {@code lastDrainedTick}.
   */
  public long nextCommandTick() {
    long next = tick + 1L;
    if (phase == Phase.SIMULATION || phase == Phase.EVENTS
        || phase == Phase.STRUCTURAL_COMMIT) next++;
    // The queue owns the admission watermark.  During a render catch-up (or
    // when a host hook advances the queue before the coordinator publishes its
    // completed tick) that watermark can be one or more ticks ahead of the
    // coordinator's public clock.  Never manufacture a command that the queue
    // will immediately reject as stale in that interval.
    return Math.max(Math.max(1L, next), commands.lastDrainedTick() + 1L);
  }
  public SimulationCommandQueue commands() { return commands; }
  public SimulationEventQueue events() { return events; }
  public StructuralChangeQueue structuralChanges() { return structuralChanges; }
  public SimulationPhaseContract phaseContract() { return phaseContract; }

  /** Installs the sink that receives commands after queue admission and checkpoint publication. */
  public void setReplayRecorder(SimulationReplayRecorder recorder) {
    replayRecorder = Objects.requireNonNull(recorder, "recorder");
  }

  public SimulationReplayRecorder replayRecorder() { return replayRecorder; }

  /** Publishes a checkpoint for the last completed fixed tick. */
  public void publishReplayCheckpoint(SimulationCheckpoint checkpoint) {
    Objects.requireNonNull(checkpoint, "checkpoint");
    if (checkpoint.tick() != tick) {
      throw new IllegalArgumentException("checkpoint tick " + checkpoint.tick()
          + " does not match coordinator tick " + tick);
    }
    replayRecorder.recordCheckpoint(checkpoint);
  }

  public void advance(Consumer<SimulationCommandEnvelope> commandHandler,
      TickStep simulation, Consumer<SimulationEventQueue.Event> eventHandler) {
    Objects.requireNonNull(commandHandler, "commandHandler");
    Objects.requireNonNull(simulation, "simulation");
    Objects.requireNonNull(eventHandler, "eventHandler");
    advance(() -> {}, simulation, () -> events.drain(tick + 1L, eventHandler), commandHandler);
  }

  /** Runs host input/output hooks around the fixed command/simulation phases. */
  public void advance(Runnable commandPhase, TickStep simulation, Runnable eventPhase) {
    advance(commandPhase, simulation, eventPhase, command -> {});
  }

  private void advance(Runnable commandPhase, TickStep simulation, Runnable eventPhase,
      Consumer<SimulationCommandEnvelope> commandHandler) {
    Objects.requireNonNull(commandPhase, "commandPhase");
    Objects.requireNonNull(eventPhase, "eventPhase");
    Objects.requireNonNull(commandHandler, "commandHandler");
    long next = tick + 1L;
    phase = Phase.COMMANDS;
    commandPhase.run();
    commands.drain(next, command -> {
      replayRecorder.recordCommand(command);
      commandHandler.accept(command);
    });
    phase = Phase.SIMULATION;
    simulation.run(next);
    phase = Phase.EVENTS;
    eventPhase.run();
    phase = Phase.STRUCTURAL_COMMIT;
    structuralChanges.flush();
    tick = next;
    phase = Phase.IDLE;
  }

  public void clear() {
    commands.clear(); events.clear(); structuralChanges.clear();
    tick = 0L; phase = Phase.IDLE;
  }

  /** Restores the coordinator clock after an authoritative checkpoint apply. */
  public void restore(long checkpointTick) {
    if (checkpointTick < 0L) throw new IllegalArgumentException("checkpointTick");
    commands.clear(); events.clear(); structuralChanges.clear();
    tick = checkpointTick;
    phase = Phase.IDLE;
  }
}
