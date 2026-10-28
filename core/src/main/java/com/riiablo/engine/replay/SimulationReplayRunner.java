package com.riiablo.engine.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.riiablo.engine.SimulationCommandEnvelope;
import com.riiablo.engine.SimulationEventQueue;
import com.riiablo.engine.SimulationTickCoordinator;

/**
 * Replays an immutable command log through the fixed-tick coordinator.
 *
 * <p>The runner deliberately accepts a checkpoint source instead of knowing
 * about ECS components.  That keeps playback deterministic and reusable while
 * allowing a later server adapter to provide the real authoritative world
 * summary and restore implementation.
 */
public final class SimulationReplayRunner {
  @FunctionalInterface
  public interface CheckpointSource {
    SimulationCheckpoint capture(long tick);
  }

  public static final class Result {
    public final long startTick;
    public final long endTick;
    public final int submittedCommands;
    public final int verifiedCheckpoints;
    private final List<SimulationCheckpoint.Mismatch> mismatches;

    private Result(long startTick, long endTick, int submittedCommands,
        int verifiedCheckpoints, List<SimulationCheckpoint.Mismatch> mismatches) {
      this.startTick = startTick;
      this.endTick = endTick;
      this.submittedCommands = submittedCommands;
      this.verifiedCheckpoints = verifiedCheckpoints;
      this.mismatches = Collections.unmodifiableList(new ArrayList<>(mismatches));
    }

    public List<SimulationCheckpoint.Mismatch> mismatches() { return mismatches; }
    public boolean success() { return mismatches.isEmpty(); }
  }

  private SimulationReplayRunner() {}

  /**
   * Plays commands after the coordinator's current tick and verifies every
   * checkpoint at or after that tick.  Commands already covered by a resume
   * checkpoint are intentionally skipped.
   */
  public static Result play(SimulationReplayLog log, SimulationTickCoordinator coordinator,
      Consumer<SimulationCommandEnvelope> commandHandler,
      SimulationTickCoordinator.TickStep simulation,
      Consumer<SimulationEventQueue.Event> eventHandler,
      CheckpointSource checkpoints) {
    Objects.requireNonNull(log, "log");
    Objects.requireNonNull(coordinator, "coordinator");
    Objects.requireNonNull(commandHandler, "commandHandler");
    Objects.requireNonNull(simulation, "simulation");
    Objects.requireNonNull(eventHandler, "eventHandler");
    if (!log.checkpoints().isEmpty()) Objects.requireNonNull(checkpoints, "checkpoints");
    if (coordinator.commands().pendingCount() != 0) {
      throw new IllegalStateException("coordinator has pending commands");
    }

    long startTick = coordinator.tick();
    long endTick = startTick;
    int submitted = 0;
    for (SimulationReplayLog.Command command : log.commands()) {
      validateReplayIdentity(command);
      if (command.tick <= startTick) continue;
      SimulationCommandEnvelope.Builder builder = command.toBuilder();
      if (coordinator.commands().submit(builder) == null) {
        throw new IllegalArgumentException("replay command rejected at tick " + command.tick
            + ": " + coordinator.commands().lastRejection());
      }
      submitted++;
      endTick = Math.max(endTick, command.tick);
    }
    for (SimulationCheckpoint checkpoint : log.checkpoints()) {
      if (checkpoint.tick() > startTick) endTick = Math.max(endTick, checkpoint.tick());
    }

    List<SimulationCheckpoint.Mismatch> mismatches = new ArrayList<>();
    int verified = 0;
    for (SimulationCheckpoint checkpoint : log.checkpoints()) {
      if (checkpoint.tick() != startTick) continue;
      mismatches.addAll(checkpoint.diff(checkpoints.capture(startTick)));
      verified++;
    }
    for (long tick = startTick + 1L; tick <= endTick; tick++) {
      coordinator.advance(commandHandler, simulation, eventHandler);
      SimulationCheckpoint expected = log.checkpointAt(tick);
      if (expected == null) continue;
      mismatches.addAll(expected.diff(checkpoints.capture(tick)));
      verified++;
    }
    return new Result(startTick, endTick, submitted, verified, mismatches);
  }

  private static void validateReplayIdentity(SimulationReplayLog.Command command) {
    if (command.authority == SimulationCommandEnvelope.Authority.NETWORK_INPUT) {
      if (command.actorId < 0) throw new IllegalArgumentException(
          "replay network command missing actor identity at tick " + command.tick);
      if (command.requestId <= 0L) throw new IllegalArgumentException(
          "replay network command missing request identity at tick " + command.tick);
      if (command.targetEntityId < -1) throw new IllegalArgumentException(
          "replay network command has malformed target at tick " + command.tick);
    }
    if (command.payload == null || command.kind == null || command.kind.trim().isEmpty()) {
      throw new IllegalArgumentException("replay command is malformed at tick " + command.tick);
    }
  }
}
