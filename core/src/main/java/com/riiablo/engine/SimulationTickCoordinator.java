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
    long next = tick + 1L;
    phase = Phase.COMMANDS;
    commands.drain(next, command -> {
      replayRecorder.recordCommand(command);
      commandHandler.accept(command);
    });
    phase = Phase.SIMULATION;
    simulation.run(next);
    phase = Phase.EVENTS;
    events.drain(next, eventHandler);
    phase = Phase.STRUCTURAL_COMMIT;
    structuralChanges.flush();
    tick = next;
    phase = Phase.IDLE;
  }

  public void clear() {
    commands.clear(); events.clear(); structuralChanges.clear();
    tick = 0L; phase = Phase.IDLE;
  }
}
