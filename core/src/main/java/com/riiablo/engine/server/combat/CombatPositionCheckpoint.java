package com.riiablo.engine.server.combat;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.map.Map;
import java.io.*;
import java.util.*;

/** Complete occupied/empty ring slots, not a resampling of current entities or Map/RoomEx state. */
public final class CombatPositionCheckpoint {
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private static final int VERSION = 1;
  private final byte[] bytes;

  private CombatPositionCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }

  public static CombatPositionCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("position history length");
    }
    CombatPositionCheckpoint image = new CombatPositionCheckpoint(bytes.clone());
    image.decode(null); return image;
  }

  public static CombatPositionCheckpoint capture(CombatPositionHistory history) {
    Objects.requireNonNull(history, "history");
    if (history.capacity > 65536) throw new IllegalArgumentException("position history capacity");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION); out.writeInt(history.capacity); out.writeLong(history.latestTick);
      out.writeBoolean(history.map != null);
      IdentityHashMap<Map.Zone, int[]> zones = new IdentityHashMap<>();
      for (int slot = 0; slot < history.capacity; slot++) {
        out.writeLong(history.ticks[slot]); out.writeInt(history.frames[slot].size);
        TreeMap<Integer, CombatPositionHistory.Snapshot> sorted = new TreeMap<>();
        for (IntMap.Entry<CombatPositionHistory.Snapshot> entry : history.frames[slot]) {
          if (entry.key != entry.value.entityId || entry.value.tick != history.ticks[slot]) {
            throw new IllegalStateException("position history slot identity");
          }
          sorted.put(entry.key, entry.value);
        }
        for (CombatPositionHistory.Snapshot s : sorted.values()) {
          out.writeInt(s.entityId); out.writeInt(s.x); out.writeInt(s.y);
          out.writeInt(s.size); out.writeInt(s.roomId); out.writeBoolean(s.zone != null);
          if (s.zone != null) {
            int[] key = zones.get(s.zone);
            if (key == null) {
              key = zoneKey(s.zone);
              if (history.map == null || !history.map.getZones().contains(s.zone, true)
                  || resolve(history.map, key) != s.zone) {
                throw new IllegalStateException("foreign/ambiguous position history zone");
              }
              zones.put(s.zone, key);
            }
            for (int value : key) out.writeInt(value);
          }
        }
        if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("position history length");
      }
      out.flush(); return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public void validateRestore(CombatPositionHistory history) { decode(Objects.requireNonNull(history)); }

  /** Resolve every zone before writing any slot. Preserve history, array and IntMap identities. */
  public void restore(CombatPositionHistory history) {
    Decoded d = decode(Objects.requireNonNull(history));
    for (int i = 0; i < d.ticks.length; i++) {
      history.frames[i].clear(); history.ticks[i] = d.ticks[i];
      for (CombatPositionHistory.Snapshot s : d.frames.get(i)) history.frames[i].put(s.entityId, s);
    }
    history.latestTick = d.latest;
  }

  private Decoded decode(CombatPositionHistory history) {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("position history version");
      int capacity = in.readInt(); long latest = in.readLong(); boolean mapPresent = bool(in);
      if (capacity <= 0 || capacity > 65536 || capacity > in.available() / 12 || latest < 0) {
        throw new IllegalArgumentException("position history capacity/tick");
      }
      if (history != null && (history.capacity != capacity || (history.map != null) != mapPresent)) {
        throw new IllegalStateException("position history topology mismatch");
      }
      Decoded d = new Decoded(capacity, latest);
      java.util.Map<ZoneKey, Map.Zone> zones = new HashMap<>();
      boolean captured = false;
      for (int slot = 0; slot < capacity; slot++) {
        long tick = d.ticks[slot] = in.readLong(); int count = in.readInt();
        captured |= tick > 0;
        if (tick < 0 || tick > latest || tick > 0 && Math.floorMod(tick, capacity) != slot
            || count < 0 || count > in.available() / 21 || tick == 0 && count != 0) {
          throw new IllegalArgumentException("position history frame");
        }
        List<CombatPositionHistory.Snapshot> frame = new ArrayList<>(); d.frames.add(frame);
        int previous = -1;
        for (int i = 0; i < count; i++) {
          int id = in.readInt(), x = in.readInt(), y = in.readInt(), size = in.readInt(), room = in.readInt();
          if (id <= previous || size < 0) throw new IllegalArgumentException("position history entity/size");
          previous = id; Map.Zone zone = null;
          if (bool(in)) {
            if (!mapPresent) throw new IllegalArgumentException("position history zone without map");
            int[] key = new int[8]; for (int j = 0; j < key.length; j++) key[j] = in.readInt();
            if (key[6] < 0 || key[6] > 1 || key[7] < 0 || key[7] > 1) {
              throw new IllegalArgumentException("position history zone flags");
            }
            if (history != null) {
              ZoneKey identity = new ZoneKey(key);
              zone = zones.get(identity);
              if (zone == null) { zone = resolve(history.map, key); zones.put(identity, zone); }
            }
          }
          frame.add(new CombatPositionHistory.Snapshot(id, tick, x, y, size, zone, room));
        }
      }
      if (latest > 0 && !captured) throw new IllegalArgumentException("position history high-water mark without frames");
      if (in.read() != -1) throw new IllegalArgumentException("trailing position history");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated position history", e); }
  }

  private static boolean bool(DataInputStream in) throws IOException {
    int value = in.readUnsignedByte();
    if (value > 1) throw new IllegalArgumentException("position history boolean");
    return value != 0;
  }

  private static int[] zoneKey(Map.Zone z) {
    return new int[] {z.levelId(), z.levelSeed(), z.x(), z.y(), z.width(), z.height(),
        z.isSyntheticArena() ? 1 : 0, z.isTown() ? 1 : 0};
  }

  private static Map.Zone resolve(Map map, int[] key) {
    Map.Zone found = null;
    for (Map.Zone zone : map.getZones()) if (Arrays.equals(key, zoneKey(zone))) {
      if (found != null) throw new IllegalStateException("ambiguous position history zone");
      found = zone;
    }
    if (found == null) throw new IllegalStateException("position history zone missing");
    return found;
  }

  private static final class Decoded {
    final long latest;
    final long[] ticks;
    final List<List<CombatPositionHistory.Snapshot>> frames = new ArrayList<>();
    Decoded(int capacity, long latest) { this.latest = latest; ticks = new long[capacity]; }
  }

  private static final class ZoneKey {
    final int[] values;
    ZoneKey(int[] values) { this.values = values; }
    @Override public int hashCode() { return Arrays.hashCode(values); }
    @Override public boolean equals(Object other) {
      return other instanceof ZoneKey && Arrays.equals(values, ((ZoneKey) other).values);
    }
  }
}
