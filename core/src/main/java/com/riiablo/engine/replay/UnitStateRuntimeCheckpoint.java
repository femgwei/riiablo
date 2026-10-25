package com.riiablo.engine.replay;

import com.artemis.World;
import com.riiablo.attributes.*;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;
import java.io.*;
import java.util.*;

/**
 * Explicit authoritative state-list/runtime codec, not StateP reconstruction.
 * Per-network-entity records include component absence. Active native target
 * memberships and opaque removal hooks fail closed until their owners migrate.
 */
public final class UnitStateRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 1024 * 1024;
  private final byte[] bytes;
  private UnitStateRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }

  public static UnitStateRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("unit-state runtime length");
    }
    UnitStateRuntimeCheckpoint result = new UnitStateRuntimeCheckpoint(bytes.clone());
    result.decode();
    return result;
  }

  public static Map<Integer, UnitStateRuntimeCheckpoint> capture(World world, int[] ids) {
    Map<Integer, UnitStateRuntimeCheckpoint> result = new TreeMap<>();
    for (int id : ids) {
      UnitStates holder = world.getMapper(UnitStates.class).get(id);
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(VERSION); out.writeBoolean(holder != null);
        if (holder != null) {
          if (holder.stateList != null && (holder.stateList.nativeNode9Order() != 0
              || holder.stateList.nativeConversionOrder() != 0)) {
            throw new IllegalStateException("active state target membership requires registry checkpoint");
          }
          writeHolder(out, holder);
          AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(id);
          out.writeBoolean(wrapper != null && wrapper.attrs != null);
          if (wrapper != null && wrapper.attrs != null) writeAttributes(out, wrapper.attrs);
        }
        out.flush(); result.put(id, fromBytes(bytes.toByteArray()));
      } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    return Collections.unmodifiableMap(result);
  }

  /** All entries validate before any mutation. No gameplay callbacks or tick. */
  public static void restore(World world, Map<Integer, UnitStateRuntimeCheckpoint> records) {
    if (records == null) return; // Explicitly legacy/uncovered.
    Map<Integer, Decoded> decoded = new TreeMap<>();
    for (Map.Entry<Integer, UnitStateRuntimeCheckpoint> entry : records.entrySet()) {
      int id = entry.getKey();
      if (id < 0 || world.getEntity(id) == null || !world.getEntityManager().isActive(id)) {
        throw new IllegalStateException("unit-state host missing: " + id);
      }
      Decoded d = entry.getValue().decode();
      if (d.attributes != null) {
        AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(id);
        if (wrapper == null || wrapper.attrs == null || wrapper.attrs.type().ordinal() != d.type) {
          throw new IllegalStateException("unit-state attributes host mismatch: " + id);
        }
        for (int[][] list : d.attributes) for (int[] stat : list) {
          if (wrapper.attrs.table().entry((short) stat[0]) == null) {
            throw new IllegalArgumentException("unknown unit-state attribute");
          }
        }
      }
      decoded.put(id, d);
    }
    for (Map.Entry<Integer, Decoded> entry : decoded.entrySet()) {
      int id = entry.getKey(); Decoded d = entry.getValue();
      if (d.holder == null) {
        UnitStates old = world.getMapper(UnitStates.class).get(id);
        if (old != null && old.stateList != null) old.stateList.discardAll();
        world.getMapper(UnitStates.class).remove(id);
        continue;
      }
      UnitStates h = world.getMapper(UnitStates.class).create(id);
      copyHolder(d.holder, h);
      if (d.holder.stateList == null) {
        if (h.stateList != null) h.stateList.discardAll();
        h.stateList = null;
      } else {
        if (h.stateList == null) h.stateList = new StateList(id);
        h.stateList.setEntityId(id);
        h.stateList.replaceFromCheckpoint(d.states, d.flags,
            world.getMapper(NativeTargetNode.class).get(id));
      }
      if (d.attributes != null) applyAttributes(world.getMapper(AttributesWrapper.class).get(id).attrs, d);
    }
  }

  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("unit-state version");
      Decoded d = new Decoded();
      if (CheckpointIO.readBoolean(in)) {
        d.holder = readHolder(in, d);
        if (CheckpointIO.readBoolean(in)) readAttributes(in, d);
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing unit-state runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated unit-state runtime", e); }
  }

  private static void writeHolder(DataOutputStream out, UnitStates h) throws IOException {
    out.writeBoolean(h.snapshotOnly); out.writeBoolean(h.healthPotionAdvanced);
    out.writeInt(h.appliedMaxLifePercent); out.writeInt(h.appliedMaxManaPercent);
    out.writeInt(h.appliedMaxStaminaPercent);
    out.writeInt(h.baseMaxLifeEncoded); out.writeInt(h.baseMaxManaEncoded);
    out.writeInt(h.resolvedMaxLifeEncoded); out.writeInt(h.resolvedMaxManaEncoded);
    out.writeLong(h.observedAggregateRevision);
    out.writeBoolean(h.stateList != null);
    if (h.stateList == null) return;
    out.writeInt(h.stateList.size());
    for (UnitState state : h.stateList.getStates()) state.writeCheckpoint(out);
    StateFlags flags = h.stateList.getFlags();
    for (int id = 0; id < StateId.MAX_STATE_COUNT; id++) {
      out.writeBoolean(flags.check(id)); out.writeBoolean(flags.checkGfx(id));
    }
  }

  private static UnitStates readHolder(DataInputStream in, Decoded d) throws IOException {
    UnitStates h = new UnitStates();
    h.snapshotOnly = CheckpointIO.readBoolean(in); h.healthPotionAdvanced = CheckpointIO.readBoolean(in);
    h.appliedMaxLifePercent = in.readInt(); h.appliedMaxManaPercent = in.readInt();
    h.appliedMaxStaminaPercent = in.readInt();
    h.baseMaxLifeEncoded = in.readInt(); h.baseMaxManaEncoded = in.readInt();
    h.resolvedMaxLifeEncoded = in.readInt(); h.resolvedMaxManaEncoded = in.readInt();
    h.observedAggregateRevision = in.readLong();
    if (CheckpointIO.readBoolean(in)) {
      h.stateList = new StateList();
      int count = CheckpointIO.count(in, 4096);
      for (int i = 0; i < count; i++) d.states.add(UnitState.readCheckpoint(in));
      for (int id = 0; id < StateId.MAX_STATE_COUNT; id++) {
        d.flags.toggle(id, CheckpointIO.readBoolean(in));
        d.flags.toggleGfx(id, CheckpointIO.readBoolean(in));
      }
    }
    return h;
  }

  private static void copyHolder(UnitStates source, UnitStates target) {
    target.snapshotOnly = source.snapshotOnly; target.healthPotionAdvanced = source.healthPotionAdvanced;
    target.appliedMaxLifePercent = source.appliedMaxLifePercent;
    target.appliedMaxManaPercent = source.appliedMaxManaPercent;
    target.appliedMaxStaminaPercent = source.appliedMaxStaminaPercent;
    target.baseMaxLifeEncoded = source.baseMaxLifeEncoded; target.baseMaxManaEncoded = source.baseMaxManaEncoded;
    target.resolvedMaxLifeEncoded = source.resolvedMaxLifeEncoded;
    target.resolvedMaxManaEncoded = source.resolvedMaxManaEncoded;
    target.observedAggregateRevision = source.observedAggregateRevision;
  }

  private static void writeAttributes(DataOutputStream out, Attributes attrs) throws IOException {
    // Source lists belong to equipment owners; no opaque additional lists may be silently dropped.
    if (attrs.list().numLists() != 0) throw new IllegalStateException("unsupported unit attribute source lists");
    out.writeInt(attrs.type().ordinal()); out.writeLong(attrs.aggregateRevision());
    writeStats(out, attrs.base()); writeStats(out, attrs.aggregate()); writeStats(out, attrs.remaining());
    out.writeBoolean(attrs.hasCombatShieldContext()); out.writeBoolean(attrs.combatHasShield());
    out.writeInt(attrs.combatBlockFactor());
    out.writeBoolean(attrs.hasCombatRunningContext()); out.writeBoolean(attrs.combatRunning());
    out.writeBoolean(attrs.difficultyResistanceIncluded());
    for (long value : attrs.staminaMaximum().checkpoint()) out.writeLong(value);
  }

  private static void readAttributes(DataInputStream in, Decoded d) throws IOException {
    d.type = in.readInt(); d.revision = in.readLong();
    if (d.type < 0 || d.type >= Attributes.Type.values().length || d.revision < 0) {
      throw new IllegalArgumentException("unit attribute type/revision");
    }
    int max = d.type == Attributes.Type.LARGE.ordinal() ? 256 : 32;
    d.attributes = new int[][][] {readStats(in, max), readStats(in, max), readStats(in, max)};
    d.shieldKnown = CheckpointIO.readBoolean(in); d.shield = CheckpointIO.readBoolean(in);
    d.blockFactor = in.readInt();
    if (d.blockFactor < 0) throw new IllegalArgumentException("combat block factor");
    d.runningKnown = CheckpointIO.readBoolean(in); d.running = CheckpointIO.readBoolean(in);
    d.difficultyIncluded = CheckpointIO.readBoolean(in);
    for (int i = 0; i < d.stamina.length; i++) d.stamina[i] = in.readLong();
    new StaminaMaximum().restoreCheckpoint(d.stamina); // Resource-independent validation.
  }

  private static void applyAttributes(Attributes attrs, Decoded d) {
    attrs.clear();
    StatListRef[] lists = {attrs.base(), attrs.aggregate(), attrs.remaining()};
    for (int i = 0; i < lists.length; i++) for (int[] stat : d.attributes[i]) {
      lists[i].putEncoded((short) stat[0], stat[1], stat[2]);
    }
    attrs.restoreAggregateRevision(d.revision);
    attrs.restoreCombatContext(d.shieldKnown, d.shield, d.blockFactor, d.runningKnown, d.running);
    attrs.setDifficultyResistanceIncluded(d.difficultyIncluded);
    attrs.staminaMaximum().restoreCheckpoint(d.stamina);
  }

  private static void writeStats(DataOutputStream out, StatListRef list) throws IOException {
    out.writeInt(list.size());
    for (StatRef stat : list) {
      out.writeInt(stat.id()); out.writeInt(stat.encodedParams()); out.writeInt(stat.encodedValues());
    }
  }

  private static int[][] readStats(DataInputStream in, int max) throws IOException {
    int[][] stats = new int[CheckpointIO.count(in, max)][3];
    int previousId = -1, previousParams = 0;
    for (int[] stat : stats) {
      stat[0] = in.readInt(); stat[1] = in.readInt(); stat[2] = in.readInt();
      if (stat[0] < 0 || stat[0] > 511 || stat[0] < previousId
          || stat[0] == previousId && stat[1] <= previousParams) {
        throw new IllegalArgumentException("unit attribute identity/order");
      }
      previousId = stat[0]; previousParams = stat[1];
    }
    return stats;
  }

  private static final class Decoded {
    UnitStates holder;
    final List<UnitState> states = new ArrayList<>();
    final StateFlags flags = new StateFlags();
    int type, blockFactor;
    long revision;
    int[][][] attributes;
    boolean shieldKnown, shield, runningKnown, running, difficultyIncluded;
    final long[] stamina = new long[11];
  }
}
