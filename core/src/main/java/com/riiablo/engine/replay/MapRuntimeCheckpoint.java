package com.riiablo.engine.replay;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.map.Map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Portable projection of the transient map binding held by networked units.
 * Map and RoomEx object graphs remain host-owned; a restore must resolve the
 * recorded stable identity against already prepared map runtime.
 */
public final class MapRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private final byte[] bytes;

  private MapRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }

  public byte[] bytes() { return bytes.clone(); }

  public Set<Integer> entityIds() {
    return Collections.unmodifiableSet(decode().entries.keySet());
  }

  public static MapRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("map runtime length");
    }
    MapRuntimeCheckpoint result = new MapRuntimeCheckpoint(bytes.clone());
    result.decode();
    return result;
  }

  /** Captures map/zone identity for the supplied networked entity set. */
  public static MapRuntimeCheckpoint capture(World world, int[] ids) {
    if (world == null) throw new NullPointerException("world");
    if (ids == null) throw new NullPointerException("ids");
    TreeMap<Integer, EntryState> entries = new TreeMap<>();
    int previous = -1;
    com.artemis.ComponentMapper<MapWrapper> wrappers = world.getMapper(MapWrapper.class);
    for (int id : ids) {
      if (id < 0 || id <= previous) throw new IllegalArgumentException("map entity ordering");
      previous = id;
      MapWrapper wrapper = wrappers.get(id);
      if (wrapper == null || wrapper.zone == null) {
        entries.put(id, EntryState.absent());
        continue;
      }
      if (wrapper.map == null) {
        throw new IllegalStateException("partial map wrapper: " + id);
      }
      if (wrapper.zone.map != null && wrapper.zone.map != wrapper.map) {
        throw new IllegalStateException("map/zone identity mismatch: " + id);
      }
      int roomId = wrapper.roomId;
      if (roomId < -1 || roomId >= wrapper.zone.getRoomsEx().size) {
        throw new IllegalStateException("map room id out of range: " + id);
      }
      entries.put(id, new EntryState(true, wrapper.map.seed(), wrapper.map.getDifficulty(),
          wrapper.zone.levelId(), wrapper.zone.levelSeed(), roomId, wrapper.roomIdPinned));
    }
    boolean any = false;
    for (EntryState state : entries.values()) if (state.present) { any = true; break; }
    return any ? encode(entries) : null;
  }

  /** Convenience capture for all current networked entities. */
  public static MapRuntimeCheckpoint capture(World world) {
    IntBag bag = world.getAspectSubscriptionManager().get(Aspect.all(Networked.class)).getEntities();
    int[] ids = Arrays.copyOf(bag.getData(), bag.size());
    Arrays.sort(ids);
    return capture(world, ids);
  }

  /** Host supplied resolver for prebuilt Map/Zone runtime. */
  public interface Resolver {
    Binding resolve(int mapSeed, int difficulty, int levelId, int levelSeed);
  }

  public static final class Binding {
    public final Map map;
    public final Map.Zone zone;

    public Binding(Map map, Map.Zone zone) {
      if (map == null || zone == null) throw new NullPointerException("map/zone");
      if (zone.map != null && zone.map != map) throw new IllegalArgumentException("map/zone identity");
      this.map = map;
      this.zone = zone;
    }
  }

  /** Restores all bindings atomically after resolving every required zone. */
  public void restore(World world, Resolver resolver) {
    if (world == null) throw new NullPointerException("world");
    if (resolver == null) throw new NullPointerException("resolver");
    Decoded decoded = decode();
    com.artemis.ComponentMapper<MapWrapper> wrappers = world.getMapper(MapWrapper.class);
    TreeMap<Integer, Binding> resolved = new TreeMap<>();
    for (Entry<Integer, EntryState> entry : decoded.entries.entrySet()) {
      if (!isActive(world, entry.getKey())) throw new IllegalStateException("map runtime host missing: " + entry.getKey());
      EntryState state = entry.getValue();
      if (!state.present) continue;
      Binding binding = resolver.resolve(state.mapSeed, state.difficulty, state.levelId, state.levelSeed);
      if (binding == null) throw new IllegalStateException("map zone resolver returned null: " + entry.getKey());
      if (binding.map.seed() != state.mapSeed || binding.map.getDifficulty() != state.difficulty
          || binding.zone.levelId() != state.levelId || binding.zone.levelSeed() != state.levelSeed
          || state.roomId >= binding.zone.getRoomsEx().size) {
        throw new IllegalStateException("map zone identity mismatch: " + entry.getKey());
      }
      resolved.put(entry.getKey(), binding);
    }
    for (Entry<Integer, EntryState> entry : decoded.entries.entrySet()) {
      int id = entry.getKey();
      MapWrapper current = wrappers.get(id);
      if (current == null) current = wrappers.create(id);
      if (current.map != null && current.zone != null && current.roomId >= 0
          && current.zone.isRoomActivationTracking()) current.zone.leaveClientRoom(current.roomId);
      EntryState state = entry.getValue();
      if (!state.present) {
        current.map = null;
        current.zone = null;
        current.roomId = -1;
        current.roomIdPinned = false;
        continue;
      }
      Binding binding = resolved.get(id);
      current.set(binding.map, binding.zone);
      current.roomId = state.roomId;
      current.roomIdPinned = state.roomIdPinned;
      if (current.roomId >= 0 && current.zone.isRoomActivationTracking()) {
        current.zone.enterClientRoom(current.roomId);
      }
    }
  }

  private static MapRuntimeCheckpoint encode(TreeMap<Integer, EntryState> entries) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      out.writeInt(entries.size());
      for (Entry<Integer, EntryState> entry : entries.entrySet()) {
        EntryState state = entry.getValue();
        out.writeInt(entry.getKey());
        out.writeBoolean(state.present);
        if (state.present) {
          out.writeInt(state.mapSeed);
          out.writeInt(state.difficulty);
          out.writeInt(state.levelId);
          out.writeInt(state.levelSeed);
          out.writeInt(state.roomId);
          out.writeBoolean(state.roomIdPinned);
        }
      }
      out.flush();
      return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("map runtime version");
      int count = CheckpointIO.count(in, 1_000_000);
      TreeMap<Integer, EntryState> entries = new TreeMap<>();
      int previous = -1;
      for (int i = 0; i < count; i++) {
        int id = in.readInt();
        if (id < 0 || id <= previous) throw new IllegalArgumentException("map entity identity/order");
        previous = id;
        boolean present = CheckpointIO.readBoolean(in);
        EntryState state = present
            ? new EntryState(true, in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                CheckpointIO.readBoolean(in))
            : EntryState.absent();
        if (state.present && (state.difficulty < 0 || state.difficulty > 2 || state.levelId < -1
            || state.roomId < -1)) {
          throw new IllegalArgumentException("map runtime identity");
        }
        entries.put(id, state);
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing map runtime");
      return new Decoded(entries);
    } catch (EOFException e) {
      throw new IllegalArgumentException("truncated map runtime", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid map runtime", e);
    }
  }

  private static boolean isActive(World world, int id) {
    try {
      return world.getEntity(id) != null && world.getEntityManager().isActive(id);
    } catch (IndexOutOfBoundsException e) {
      return false;
    }
  }

  private static final class Decoded {
    final TreeMap<Integer, EntryState> entries;
    Decoded(TreeMap<Integer, EntryState> entries) { this.entries = entries; }
  }

  private static final class EntryState {
    final boolean present;
    final int mapSeed, difficulty, levelId, levelSeed, roomId;
    final boolean roomIdPinned;
    EntryState(boolean present, int mapSeed, int difficulty, int levelId, int levelSeed,
        int roomId, boolean roomIdPinned) {
      this.present = present; this.mapSeed = mapSeed; this.difficulty = difficulty;
      this.levelId = levelId; this.levelSeed = levelSeed; this.roomId = roomId;
      this.roomIdPinned = roomIdPinned;
    }
    static EntryState absent() { return new EntryState(false, 0, 0, -1, 0, -1, false); }
  }
}
