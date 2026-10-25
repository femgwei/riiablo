package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.NativeAiPathCache;
import java.io.*;
import java.util.*;

/** Host command stack and partial native path observations, NOT arbitrary AI or ECS movement. */
public final class AiControlRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 64 * 1024 * 1024;
  private final byte[] bytes;
  private AiControlRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public Set<Integer> entityIds() { return Collections.unmodifiableSet(decode().keySet()); }

  public static AiControlRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("AI control runtime length");
    }
    AiControlRuntimeCheckpoint result = new AiControlRuntimeCheckpoint(bytes.clone());
    result.decode(); return result;
  }

  public static AiControlRuntimeCheckpoint capture(World world, int[] ids) {
    Set<Integer> covered = new TreeSet<>();
    for (int id : ids) {
      if (id < 0 || !covered.add(id)) throw new IllegalArgumentException("AI control host identity");
    }
    Set<Integer> active = new HashSet<>();
    for (int id : EntityAllocatorCheckpoint.capture(world).activeIds()) {
      active.add(id);
      if (!covered.contains(id) && (world.getMapper(NativeAiCommand.class).has(id)
          || world.getMapper(NativeAiPathCache.class).has(id))) {
        throw new IllegalStateException("local AI control requires owner checkpoint: " + id);
      }
    }
    if (!active.containsAll(covered)) throw new IllegalStateException("AI control host inactive");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION); out.writeInt(covered.size());
      for (int id : covered) {
        out.writeInt(id);
        NativeAiCommand command = world.getMapper(NativeAiCommand.class).get(id);
        out.writeBoolean(command != null);
        if (command != null) {
          List<NativeAiCommand.Checkpoint> stack = command.captureCheckpoint(world);
          out.writeInt(stack.size());
          for (NativeAiCommand.Checkpoint e : stack) {
            requireCovered(e, covered);
            out.writeInt(e.ownerId); out.writeInt(e.command); out.writeInt(e.targetId);
            out.writeLong(e.expiresAtFrame);
            out.writeByte(e.ownerLifetime); out.writeByte(e.targetLifetime);
          }
        }
        NativeAiPathCache path = world.getMapper(NativeAiPathCache.class).get(id);
        out.writeBoolean(path != null);
        if (path != null) {
          out.writeInt(path.targetX); out.writeInt(path.targetY);
          out.writeBoolean(path.coordinatesKnown); out.writeBoolean(path.pointsKnown);
          out.writeBoolean(path.hasPoints); // Preserve unknown's raw value too; don't infer a path.
        }
      }
      out.flush(); return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  private static void requireCovered(NativeAiCommand.Checkpoint e, Set<Integer> covered) {
    if (e.ownerLifetime == 1 && !covered.contains(e.ownerId)
        || e.targetLifetime == 1 && !covered.contains(e.targetId)) {
      throw new IllegalArgumentException("AI command current lifetime outside checkpoint");
    }
  }

  /** Does not allocate lifetimes: native factory owners must already be assembled. */
  public void validateRestore(World world) {
    Map<Integer, Entry> entries = decode();
    Set<Integer> active = new HashSet<>();
    for (int id : EntityAllocatorCheckpoint.capture(world).activeIds()) active.add(id);
    if (!active.containsAll(entries.keySet())) throw new IllegalStateException("AI control host missing");
    for (Entry entry : entries.values()) {
      if (entry.stack != null) NativeAiCommand.validateCheckpoint(world, entry.stack);
    }
  }

  public void restore(World world) {
    validateRestore(world);
    for (Map.Entry<Integer, Entry> host : decode().entrySet()) {
      int id = host.getKey(); Entry e = host.getValue();
      if (e.stack == null) world.getMapper(NativeAiCommand.class).remove(id);
      else world.getMapper(NativeAiCommand.class).create(id).restoreCheckpoint(world, e.stack);
      if (e.path == null) world.getMapper(NativeAiPathCache.class).remove(id);
      else {
        NativeAiPathCache path = world.getMapper(NativeAiPathCache.class).create(id);
        path.targetX = e.path[0]; path.targetY = e.path[1];
        path.coordinatesKnown = e.path[2] != 0; path.pointsKnown = e.path[3] != 0;
        path.hasPoints = e.path[4] != 0;
      }
    }
  }

  private Map<Integer, Entry> decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("AI control runtime version");
      Map<Integer, Entry> result = new TreeMap<>();
      int count = CheckpointIO.count(in, 1000000), previous = -1;
      for (int i = 0; i < count; i++) {
        int id = in.readInt();
        if (id < 0 || id <= previous) throw new IllegalArgumentException("AI control identity/order");
        previous = id; Entry e = new Entry();
        if (CheckpointIO.readBoolean(in)) {
          int size = CheckpointIO.count(in, NativeAiCommand.MAX_CHECKPOINT_ENTRIES);
          if (size == 0 || size > in.available() / 22) throw new IllegalArgumentException("AI stack length");
          e.stack = new ArrayList<>();
          for (int j = 0; j < size; j++) e.stack.add(new NativeAiCommand.Checkpoint(
              in.readInt(), in.readInt(), in.readInt(), in.readLong(), in.readUnsignedByte(), in.readUnsignedByte()));
        }
        if (CheckpointIO.readBoolean(in)) e.path = new int[] {in.readInt(), in.readInt(),
            CheckpointIO.readBoolean(in) ? 1 : 0, CheckpointIO.readBoolean(in) ? 1 : 0,
            CheckpointIO.readBoolean(in) ? 1 : 0};
        result.put(id, e);
      }
      for (Entry e : result.values()) if (e.stack != null) {
        for (NativeAiCommand.Checkpoint command : e.stack) requireCovered(command, result.keySet());
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing AI control runtime");
      return result;
    } catch (IOException e) { throw new IllegalArgumentException("truncated AI control runtime", e); }
  }
  private static final class Entry {
    List<NativeAiCommand.Checkpoint> stack;
    int[] path;
  }
}
