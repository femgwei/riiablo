package com.riiablo.engine.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.riiablo.engine.SimulationCommandEnvelope;

/**
 * Immutable deterministic command/checkpoint manifest for M8-A replay work.
 *
 * <p>This first M8 slice records accepted command identity and authoritative
 * checkpoints.  It intentionally does not serialize arbitrary payload objects
 * or restore an ECS world; callers must provide a canonical payload string and
 * a checkpoint digest, keeping replay data independent of Java object identity.
 */
public final class SimulationReplayLog {
  public static final class Command {
    public final long tick;
    public final long sequence;
    public final int actorId;
    public final long requestId;
    public final int targetEntityId;
    public final SimulationCommandEnvelope.Authority authority;
    public final String source;
    public final String kind;
    public final String payload;

    private Command(SimulationCommandEnvelope command, String payload) {
      this.tick = command.tick;
      this.sequence = command.sequence;
      this.actorId = command.actorId;
      this.requestId = command.requestId;
      this.targetEntityId = command.targetEntityId;
      this.authority = command.authority;
      this.source = command.source;
      this.kind = command.kind;
      this.payload = payload;
    }

    private String canonical() {
      return "command|" + tick + "|" + sequence + "|" + actorId + "|" + requestId
          + "|" + targetEntityId + "|" + authority + "|"
          + SimulationCheckpoint.encode(source) + "|" + SimulationCheckpoint.encode(kind)
          + "|" + SimulationCheckpoint.encode(payload);
    }
  }

  public static final class Builder {
    private final int gameSeed;
    private final List<Command> commands = new ArrayList<>();
    private final List<SimulationCheckpoint> checkpoints = new ArrayList<>();

    private Builder(int gameSeed) { this.gameSeed = gameSeed; }

    public Builder command(SimulationCommandEnvelope command, String canonicalPayload) {
      Objects.requireNonNull(command, "command");
      if (canonicalPayload == null) throw new NullPointerException("canonicalPayload");
      commands.add(new Command(command, canonicalPayload));
      return this;
    }

    public Builder checkpoint(SimulationCheckpoint checkpoint) {
      checkpoints.add(Objects.requireNonNull(checkpoint, "checkpoint"));
      return this;
    }

    public SimulationReplayLog build() {
      return new SimulationReplayLog(gameSeed, commands, checkpoints);
    }
  }

  private static final Comparator<Command> COMMAND_ORDER = Comparator
      .comparingLong((Command command) -> command.tick)
      .thenComparingLong(command -> command.sequence);
  private static final Comparator<SimulationCheckpoint> CHECKPOINT_ORDER =
      Comparator.comparingLong(SimulationCheckpoint::tick);

  private final int gameSeed;
  private final List<Command> commands;
  private final List<SimulationCheckpoint> checkpoints;

  private SimulationReplayLog(int gameSeed, List<Command> commands,
      List<SimulationCheckpoint> checkpoints) {
    this.gameSeed = gameSeed;
    ArrayList<Command> orderedCommands = new ArrayList<>(commands);
    orderedCommands.sort(COMMAND_ORDER);
    this.commands = Collections.unmodifiableList(orderedCommands);
    ArrayList<SimulationCheckpoint> orderedCheckpoints = new ArrayList<>(checkpoints);
    orderedCheckpoints.sort(CHECKPOINT_ORDER);
    for (int i = 1; i < orderedCheckpoints.size(); i++) {
      if (orderedCheckpoints.get(i - 1).tick() == orderedCheckpoints.get(i).tick()) {
        throw new IllegalArgumentException("duplicate checkpoint tick: "
            + orderedCheckpoints.get(i).tick());
      }
    }
    this.checkpoints = Collections.unmodifiableList(orderedCheckpoints);
  }

  public static Builder builder(int gameSeed) { return new Builder(gameSeed); }
  public int gameSeed() { return gameSeed; }
  public List<Command> commands() { return commands; }
  public List<SimulationCheckpoint> checkpoints() { return checkpoints; }

  public String canonical() {
    StringBuilder result = new StringBuilder("replay-v1|seed=").append(gameSeed);
    for (Command command : commands) result.append('\n').append(command.canonical());
    for (SimulationCheckpoint checkpoint : checkpoints) {
      result.append('\n').append(checkpoint.canonical());
    }
    return result.toString();
  }

  public String digest() { return SimulationCheckpoint.sha256(canonical()); }

  public SimulationCheckpoint checkpointAt(long tick) {
    for (SimulationCheckpoint checkpoint : checkpoints) {
      if (checkpoint.tick() == tick) return checkpoint;
    }
    return null;
  }
}
