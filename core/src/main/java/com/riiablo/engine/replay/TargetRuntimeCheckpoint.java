package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.*;
import java.io.*;
import java.util.*;

/**
 * One world's TARGETS event domain, raw memberships and small AI control overlays.
 * Includes host command stacks and native path observations, not arbitrary AI objects or movement paths.
 * Structural listeners must settle before applying this image.
 * Detached component-owned memberships are not imported into this registry.
 */
public final class TargetRuntimeCheckpoint {
  private static final int VERSION = 3;
  public static final int MAX_BYTES = 64 * 1024 * 1024;
  private final byte[] bytes;
  private TargetRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public Set<Integer> entityIds() { return Collections.unmodifiableSet(decode().entities.keySet()); }
  /** False for v1: absence means uncovered, not an empty command/path image. */
  public boolean hasAiControlRuntime() { return decode().aiControl != null; }
  /** Null for v1/v2, which did not cover hireling components or system retry clocks. */
  public CompanionRuntimeCheckpoint companionRuntime() { return decode().companions; }

  public static TargetRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("target runtime length");
    }
    TargetRuntimeCheckpoint result = new TargetRuntimeCheckpoint(bytes.clone());
    result.decode(); return result;
  }

  public static TargetRuntimeCheckpoint capture(World world, int[] ids) {
    NativePlayerTargetSlots slots = world.getSystem(NativePlayerTargetSlots.class);
    if (slots == null) return null; // Detached/custom-world compatibility, no registry claim.
    Set<Integer> covered = new TreeSet<>();
    for (int id : ids) covered.add(id);
    for (int id : EntityAllocatorCheckpoint.capture(world).activeIds()) {
      if (!covered.contains(id) && (world.getMapper(Player.class).has(id)
          || world.getMapper(NativeTargetNode.class).has(id)
          || world.getMapper(SummonedPet.class).has(id)
          || world.getMapper(Mercenary.class).has(id)
          || world.getMapper(UnitStates.class).has(id)
          || world.getMapper(NativeAiTargetOverride.class).has(id)
          || world.getMapper(NativeAiState.class).has(id))) {
        throw new IllegalStateException("local target runtime requires owner checkpoint: " + id);
      }
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      NativePlayerTargetSlots.Checkpoint state = slots.captureCheckpoint();
      out.writeInt(VERSION); out.writeLong(state.sequence);
      int[] heads = state.heads(); long[] orders = state.orders();
      for (int i = 0; i < heads.length; i++) { out.writeInt(heads[i]); out.writeLong(orders[i]); }
      int[] attempts = state.attempts(); out.writeInt(attempts.length);
      for (int id : attempts) {
        if (!covered.contains(id)) throw new IllegalStateException("uncovered target head");
        out.writeInt(id);
      }
      out.writeInt(covered.size());
      for (int id : covered) {
        out.writeInt(id);
        NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(id);
        SummonedPet pet = world.getMapper(SummonedPet.class).get(id);
        Mercenary merc = world.getMapper(Mercenary.class).get(id);
        UnitStates states = world.getMapper(UnitStates.class).get(id);
        if (pet != null && (pet.hasDetachedTargetOrder() || pet.checkpointTargetNode() != node)
            || merc != null && (merc.hasDetachedTargetOrder() || merc.checkpointTargetNode() != node)
            || states != null && states.stateList != null && (states.stateList.hasDetachedTargetOrder()
                || states.stateList.checkpointTargetNode() != node)) {
          throw new IllegalStateException("detached target owner requires owner checkpoint");
        }
        out.writeBoolean(node != null);
        if (node != null) {
          if (!node.boundTo(slots)) throw new IllegalStateException("target node is outside world order domain");
          long[] raw = node.checkpoint();
          out.writeInt((int) raw[0]); out.writeInt((int) raw[1]); out.writeLong(raw[2]);
        }
        NativeAiState ai = world.getMapper(NativeAiState.class).get(id);
        out.writeBoolean(ai != null);
        if (ai != null) out.writeInt(ai.previousMode);
        NativeAiTargetOverride override = world.getMapper(NativeAiTargetOverride.class).get(id);
        out.writeBoolean(override != null);
        if (override != null) {
          out.writeInt(override.mode); out.writeInt(override.targetId);
          out.writeInt(override.sourceEntityId); out.writeInt(override.sourceSkillId);
          out.writeInt(override.remainingFrames); out.writeInt(override.rngState);
        }
      }
      byte[] ai = AiControlRuntimeCheckpoint.capture(world, ids).bytes();
      out.writeInt(ai.length); out.write(ai);
      byte[] companions = CompanionRuntimeCheckpoint.capture(world, ids).bytes();
      out.writeInt(companions.length); out.write(companions);
      out.flush(); return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public void restore(World world) {
    Decoded d = decode();
    NativePlayerTargetSlots slots = world.getSystem(NativePlayerTargetSlots.class);
    if (slots == null) throw new IllegalStateException("target slots system missing");
    slots.validateCheckpoint(d.slots);
    for (int id : d.entities.keySet()) {
      if (world.getEntity(id) == null || !world.getEntityManager().isActive(id)) {
        throw new IllegalStateException("target runtime host missing: " + id);
      }
    }
    if (d.aiControl != null) d.aiControl.validateRestore(world);
    if (d.companions != null) d.companions.validateRestore(world);
    if (d.companions != null) d.companions.restore(world);
    slots.restoreCheckpoint(d.slots);
    for (Map.Entry<Integer, Entry> entry : d.entities.entrySet()) {
      int id = entry.getKey(); Entry e = entry.getValue();
      NativeTargetNode node = null;
      if (e.node == null) world.getMapper(NativeTargetNode.class).remove(id);
      else {
        node = world.getMapper(NativeTargetNode.class).create(id);
        node.restoreCheckpoint((int) e.node[0], (int) e.node[1], e.node[2], slots);
      }
      // Shared membership identity, not independent component-owned copies.
      UnitStates states = world.getMapper(UnitStates.class).get(id);
      if (states != null && states.stateList != null) states.stateList.bindNativeTargetNode(node);
      SummonedPet pet = world.getMapper(SummonedPet.class).get(id);
      if (pet != null) pet.bindNativeTargetNode(node);
      Mercenary merc = world.getMapper(Mercenary.class).get(id);
      if (merc != null) merc.bindNativeTargetNode(node);
      if (e.previousMode == null) world.getMapper(NativeAiState.class).remove(id);
      else world.getMapper(NativeAiState.class).create(id).previousMode = e.previousMode;
      if (e.override == null) world.getMapper(NativeAiTargetOverride.class).remove(id);
      else {
        NativeAiTargetOverride override = world.getMapper(NativeAiTargetOverride.class).create(id);
        override.mode = e.override[0]; override.targetId = e.override[1];
        override.sourceEntityId = e.override[2]; override.sourceSkillId = e.override[3];
        override.remainingFrames = e.override[4]; override.rngState = e.override[5];
      }
    }
    if (d.aiControl != null) d.aiControl.restore(world);
  }

  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      int version = in.readInt();
      if (version < 1 || version > VERSION) throw new IllegalArgumentException("target runtime version");
      long sequence = in.readLong();
      int[] heads = new int[NativePlayerTargetSlots.SLOT_COUNT];
      long[] orders = new long[heads.length];
      Set<Long> events = new HashSet<>();
      for (int i = 0; i < heads.length; i++) {
        heads[i] = in.readInt(); orders[i] = in.readLong();
        if (orders[i] != 0) events.add(orders[i]);
      }
      int[] attempts = new int[CheckpointIO.count(in, 1000000)];
      for (int i = 0; i < attempts.length; i++) attempts[i] = in.readInt();
      Decoded d = new Decoded();
      d.slots = new NativePlayerTargetSlots.Checkpoint(sequence, heads, orders, attempts);
      int count = CheckpointIO.count(in, 1000000), previous = -1;
      for (int i = 0; i < count; i++) {
        int id = in.readInt();
        if (id < 0 || id <= previous) throw new IllegalArgumentException("target entity identity/order");
        previous = id; Entry e = new Entry();
        if (CheckpointIO.readBoolean(in)) {
          int kind = in.readInt(), owner = in.readInt(); long order = in.readLong();
          new NativeTargetNode().restoreCheckpoint(kind, owner, order, null);
          if (order > sequence || order != 0 && !events.add(order)) {
            throw new IllegalArgumentException("target event identity/order");
          }
          e.node = new long[] {kind, owner, order};
        }
        if (CheckpointIO.readBoolean(in)) e.previousMode = in.readInt();
        if (CheckpointIO.readBoolean(in)) {
          e.override = new int[6];
          for (int j = 0; j < e.override.length; j++) e.override[j] = in.readInt();
          if (e.override[0] != NativeAiTargetOverride.NONE && e.override[0] != NativeAiTargetOverride.ATTRACT
              && e.override[0] != NativeAiTargetOverride.CONFUSE || e.override[1] < -1
              || e.override[2] < -1 || e.override[3] < -1 || e.override[4] < 0) {
            throw new IllegalArgumentException("AI target override");
          }
        }
        d.entities.put(id, e);
      }
      for (int id : attempts) if (!d.entities.containsKey(id)) {
        throw new IllegalArgumentException("uncovered target allocation attempt");
      }
      if (version >= 2) {
        int length = CheckpointIO.count(in, AiControlRuntimeCheckpoint.MAX_BYTES);
        if (length == 0 || length > in.available()) throw new IllegalArgumentException("AI control runtime length");
        byte[] ai = new byte[length]; in.readFully(ai);
        d.aiControl = AiControlRuntimeCheckpoint.fromBytes(ai);
        if (!d.aiControl.entityIds().equals(d.entities.keySet())) {
          throw new IllegalArgumentException("AI control host coverage mismatch");
        }
      }
      if (version >= 3) {
        int length = CheckpointIO.count(in, CompanionRuntimeCheckpoint.MAX_BYTES);
        if (length == 0 || length > in.available()) throw new IllegalArgumentException("companion runtime length");
        byte[] image = new byte[length]; in.readFully(image);
        d.companions = CompanionRuntimeCheckpoint.fromBytes(image);
        if (!d.companions.entityIds().equals(d.entities.keySet())) {
          throw new IllegalArgumentException("companion host coverage mismatch");
        }
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing target runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated target runtime", e); }
  }
  private static final class Entry {
    long[] node;
    Integer previousMode;
    int[] override;
  }
  private static final class Decoded {
    CompanionRuntimeCheckpoint companions;
    AiControlRuntimeCheckpoint aiControl;
    NativePlayerTargetSlots.Checkpoint slots;
    final Map<Integer, Entry> entities = new TreeMap<>();
  }
}
