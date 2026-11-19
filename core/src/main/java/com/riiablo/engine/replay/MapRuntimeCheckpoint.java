package com.riiablo.engine.replay;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.CofManager;
import com.riiablo.map.Map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Portable projection of the transient map binding held by networked units.
 * Map and RoomEx object graphs remain host-owned; a restore must resolve the
 * recorded stable identity against already prepared map runtime.
 */
public final class MapRuntimeCheckpoint {
  private static final int VERSION = 2;
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
    TreeMap<Integer, ZoneHistory> histories = new TreeMap<>();
    IdentityHashMap<Map.Zone, Integer> seenZones = new IdentityHashMap<>();
    CofManager modes = world.getSystem(CofManager.class);
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
      if (!seenZones.containsKey(wrapper.zone)) {
        seenZones.put(wrapper.zone, id);
        ZoneHistory history = new ZoneHistory(wrapper.zone.getRoomsEx().size);
        if (modes != null) for (int r = 0; r < history.roomCount; r++) {
          int[] ring = modes.captureRoomDeathHistory(wrapper.zone.getRoomsEx().get(r));
          if (ring == null) continue;
          // A partial entity image must never bind an outside lifetime to an
          // unrelated entity allocated at the same id in the restored world.
          for (int slot = 0; slot < 4; slot++) {
            if (ring[1 + slot] >= 0 && Arrays.binarySearch(ids, ring[1 + slot]) < 0)
              ring[5 + slot] = 2;
          }
          history.rooms.put(r, ring);
        }
        histories.put(id, history);
      }
    }
    boolean any = false;
    for (EntryState state : entries.values()) if (state.present) { any = true; break; }
    return any ? encode(entries, histories) : null;
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
    java.util.Map<String, Binding> resolvedZones = new java.util.HashMap<>();
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
      Binding previousBinding = resolvedZones.putIfAbsent(state.zoneKey(), binding);
      if (previousBinding != null && (previousBinding.zone != binding.zone || previousBinding.map != binding.map))
        throw new IllegalStateException("inconsistent map zone resolver");
      resolved.put(entry.getKey(), binding);
    }
    CofManager modes = world.getSystem(CofManager.class);
    for (Entry<Integer, ZoneHistory> history : decoded.histories.entrySet()) {
      Binding binding = resolved.get(history.getKey());
      if (binding == null || binding.zone.getRoomsEx().size != history.getValue().roomCount)
        throw new IllegalStateException("death history room coverage mismatch");
      if (modes == null && !history.getValue().rooms.isEmpty())
        throw new IllegalStateException("death history requires CofManager");
      if (modes != null) for (int[] ring : history.getValue().rooms.values())
        modes.validateRoomDeathHistory(ring);
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
    if (modes != null) {
      // Empty images (including legacy v1) must clear future history too.
      IdentityHashMap<Map.Zone, Boolean> cleared = new IdentityHashMap<>();
      for (Binding binding : resolved.values()) if (cleared.put(binding.zone, true) == null)
        for (Map.RoomEx room : binding.zone.getRoomsEx()) modes.clearRoomDeathHistory(room);
      for (Entry<Integer, ZoneHistory> history : decoded.histories.entrySet()) {
        Map.Zone zone = resolved.get(history.getKey()).zone;
        for (Entry<Integer, int[]> ring : history.getValue().rooms.entrySet())
          modes.restoreRoomDeathHistory(zone.getRoomsEx().get(ring.getKey()), ring.getValue());
      }
    }
  }

  private static MapRuntimeCheckpoint encode(TreeMap<Integer, EntryState> entries,
      TreeMap<Integer, ZoneHistory> histories) {
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
      out.writeInt(histories.size());
      for (Entry<Integer, ZoneHistory> entry : histories.entrySet()) {
        out.writeInt(entry.getKey());
        out.writeInt(entry.getValue().roomCount);
        out.writeInt(entry.getValue().rooms.size());
        for (Entry<Integer, int[]> ring : entry.getValue().rooms.entrySet()) {
          out.writeInt(ring.getKey());
          for (int word : ring.getValue()) out.writeInt(word);
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
      int version = in.readInt();
      if (version != 1 && version != VERSION) throw new IllegalArgumentException("map runtime version");
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
      TreeMap<Integer, ZoneHistory> histories = new TreeMap<>();
      if (version >= 2) {
        int zones = CheckpointIO.count(in, count);
        int previousAnchor = -1;
        for (int z = 0; z < zones; z++) {
          int anchor = in.readInt();
          if (anchor <= previousAnchor || !entries.containsKey(anchor) || !entries.get(anchor).present)
            throw new IllegalArgumentException("death history anchor");
          previousAnchor = anchor;
          ZoneHistory history = new ZoneHistory(CheckpointIO.count(in, 1_000_000));
          int rooms = CheckpointIO.count(in, history.roomCount), previousRoom = -1;
          for (int r = 0; r < rooms; r++) {
            int room = in.readInt();
            if (room <= previousRoom || room >= history.roomCount)
              throw new IllegalArgumentException("death history room ordering");
            previousRoom = room;
            int[] ring = new int[9];
            for (int i = 0; i < 9; i++) ring[i] = in.readInt();
            if (ring[0] < 0 || ring[0] > 3) throw new IllegalArgumentException("death history cursor");
            for (int i = 0; i < 4; i++) {
              int id = ring[1 + i], kind = ring[5 + i];
              if (id < -1 || kind < 0 || kind > 2 || id < 0 && kind != 0
                  || kind == 1 && !entries.containsKey(id))
                throw new IllegalArgumentException("death history lifetime");
            }
            history.rooms.put(room, ring);
          }
          histories.put(anchor, history);
        }
        java.util.Set<String> zoneKeys = new java.util.HashSet<>();
        for (int anchor : histories.keySet())
          if (!zoneKeys.add(entries.get(anchor).zoneKey()))
            throw new IllegalArgumentException("duplicate death history zone");
        for (EntryState state : entries.values())
          if (state.present && !zoneKeys.contains(state.zoneKey()))
            throw new IllegalArgumentException("missing death history zone");
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing map runtime");
      return new Decoded(entries, histories);
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
    final TreeMap<Integer, ZoneHistory> histories;
    Decoded(TreeMap<Integer, EntryState> entries, TreeMap<Integer, ZoneHistory> histories) {
      this.entries = entries; this.histories = histories;
    }
  }

  private static final class ZoneHistory {
    final int roomCount;
    final TreeMap<Integer, int[]> rooms = new TreeMap<>();
    ZoneHistory(int roomCount) { this.roomCount = roomCount; }
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
    String zoneKey() { return mapSeed + ":" + difficulty + ":" + levelId + ":" + levelSeed; }
  }
}
