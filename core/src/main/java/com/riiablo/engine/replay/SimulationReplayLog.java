package com.riiablo.engine.replay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
  private static final int FORMAT_MAGIC = 0x52504C59; // RPLY
  private static final int FORMAT_VERSION = 1;
  private static final int MAX_RECORDS = 1_000_000;

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

    /** Recreates an admitted command for deterministic submission to another coordinator. */
    public SimulationCommandEnvelope.Builder toBuilder() {
      SimulationCommandEnvelope.Builder result = SimulationCommandEnvelope.builder(
          source, kind, payload).tick(tick).authority(authority);
      if (actorId >= 0) result.actor(actorId);
      if (requestId > 0L) result.request(requestId);
      if (targetEntityId >= 0) result.target(targetEntityId);
      return result;
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

  /** Encodes this replay in a process-independent, versioned binary format. */
  public byte[] toBytes() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      writeTo(bytes);
    } catch (IOException e) {
      throw new IllegalStateException("replay encoding failed", e);
    }
    return bytes.toByteArray();
  }

  public void writeTo(OutputStream output) throws IOException {
    Objects.requireNonNull(output, "output");
    DataOutputStream out = new DataOutputStream(output);
    out.writeInt(FORMAT_MAGIC);
    out.writeInt(FORMAT_VERSION);
    out.writeInt(gameSeed);
    writeCount(out, commands.size(), "commands");
    for (Command command : commands) {
      out.writeLong(command.tick);
      out.writeLong(command.sequence);
      out.writeInt(command.actorId);
      out.writeLong(command.requestId);
      out.writeInt(command.targetEntityId);
      out.writeByte(command.authority.ordinal());
      writeString(out, command.source);
      writeString(out, command.kind);
      writeString(out, command.payload);
    }
    writeCount(out, checkpoints.size(), "checkpoints");
    for (SimulationCheckpoint checkpoint : checkpoints) writeCheckpoint(out, checkpoint);
    out.flush();
  }

  /** Decodes a replay without loading Java classes or serialized object graphs. */
  public static SimulationReplayLog fromBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    try {
      return readFrom(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid replay encoding", e);
    }
  }

  public static SimulationReplayLog readFrom(InputStream input) throws IOException {
    Objects.requireNonNull(input, "input");
    DataInputStream in = new DataInputStream(input);
    if (in.readInt() != FORMAT_MAGIC) throw new IOException("invalid replay magic");
    int version = in.readInt();
    if (version != FORMAT_VERSION) throw new IOException("unsupported replay version: " + version);
    Builder builder = builder(in.readInt());
    int commandCount = readCount(in, "commands");
    for (int i = 0; i < commandCount; i++) {
      long tick = in.readLong();
      long sequence = in.readLong();
      int actor = in.readInt();
      long request = in.readLong();
      int target = in.readInt();
      int authority = in.readUnsignedByte();
      if (authority >= SimulationCommandEnvelope.Authority.values().length) {
        throw new IOException("invalid command authority");
      }
      String source = readString(in), kind = readString(in), payload = readString(in);
      SimulationCommandEnvelope.Builder command = SimulationCommandEnvelope
          .builder(source, kind, payload).tick(tick)
          .authority(SimulationCommandEnvelope.Authority.values()[authority]);
      if (actor >= 0) command.actor(actor);
      if (request > 0L) command.request(request);
      if (target >= 0) command.target(target);
      builder.command(command.build(sequence), payload);
    }
    int checkpointCount = readCount(in, "checkpoints");
    for (int i = 0; i < checkpointCount; i++) builder.checkpoint(readCheckpoint(in));
    return builder.build();
  }

  private static void writeCheckpoint(DataOutputStream out, SimulationCheckpoint checkpoint)
      throws IOException {
    out.writeLong(checkpoint.tick());
    writeString(out, checkpoint.worldDigest());
    writeCount(out, checkpoint.entityDigests().size(), "entities");
    for (java.util.Map.Entry<Integer, String> entity : checkpoint.entityDigests().entrySet()) {
      out.writeInt(entity.getKey());
      writeString(out, entity.getValue());
    }
    writeCount(out, checkpoint.rngStates().size(), "rng states");
    for (SimulationCheckpoint.RngState state : checkpoint.rngStates()) {
      writeString(out, state.stream);
      out.writeLong(state.contextId);
      out.writeInt(state.state);
      out.writeLong(state.drawCount);
    }
    writeCount(out, checkpoint.nativeUnitRngStates().size(), "native rng states");
    for (SimulationCheckpoint.NativeUnitRngState state : checkpoint.nativeUnitRngStates()) {
      writeString(out, state.stream);
      out.writeLong(state.contextId);
      out.writeInt(state.lowSeed);
      out.writeInt(state.highSeed);
    }
  }

  private static SimulationCheckpoint readCheckpoint(DataInputStream in) throws IOException {
    long tick = in.readLong();
    String worldDigest = readString(in);
    int entityCount = readCount(in, "entities");
    LinkedHashMap<Integer, String> entities = new LinkedHashMap<>();
    for (int i = 0; i < entityCount; i++) entities.put(in.readInt(), readString(in));
    int rngCount = readCount(in, "rng states");
    ArrayList<SimulationCheckpoint.RngState> rng = new ArrayList<>();
    for (int i = 0; i < rngCount; i++) {
      rng.add(new SimulationCheckpoint.RngState(readString(in), in.readLong(),
          in.readInt(), in.readLong()));
    }
    int nativeCount = readCount(in, "native rng states");
    ArrayList<SimulationCheckpoint.NativeUnitRngState> nativeRng = new ArrayList<>();
    for (int i = 0; i < nativeCount; i++) {
      nativeRng.add(new SimulationCheckpoint.NativeUnitRngState(readString(in), in.readLong(),
          in.readInt(), in.readInt()));
    }
    return SimulationCheckpoint.of(tick, worldDigest, entities, rng, nativeRng);
  }

  private static void writeString(DataOutputStream out, String value) throws IOException {
    byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 16 * 1024 * 1024) throw new IOException("replay string too large");
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static String readString(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("invalid replay string length");
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void writeCount(DataOutputStream out, int count, String name) throws IOException {
    if (count < 0 || count > MAX_RECORDS) throw new IOException("invalid " + name + " count");
    out.writeInt(count);
  }

  private static int readCount(DataInputStream in, String name) throws IOException {
    int count;
    try { count = in.readInt(); } catch (EOFException e) { throw new IOException("missing " + name, e); }
    if (count < 0 || count > MAX_RECORDS) throw new IOException("invalid " + name + " count");
    return count;
  }

  public SimulationCheckpoint checkpointAt(long tick) {
    for (SimulationCheckpoint checkpoint : checkpoints) {
      if (checkpoint.tick() == tick) return checkpoint;
    }
    return null;
  }
}
