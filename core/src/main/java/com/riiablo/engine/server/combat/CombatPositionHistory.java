package com.riiablo.engine.server.combat;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** Bounded authoritative position history used by tick-stable combat checks. */
public final class CombatPositionHistory {
  public static final int DEFAULT_CAPACITY = 64;

  public enum RangeResult {
    IN_RANGE,
    OUT_OF_RANGE,
    BLOCKED,
    DIFFERENT_ZONE,
    MISSING_SNAPSHOT
  }

  public static final class Snapshot {
    public final int entityId;
    public final long tick;
    public final int x;
    public final int y;
    public final int size;
    public final Map.Zone zone;
    public final int roomId;

    Snapshot(int entityId, long tick, int x, int y, int size,
        Map.Zone zone, int roomId) {
      this.entityId = entityId;
      this.tick = tick;
      this.x = x;
      this.y = y;
      this.size = size;
      this.zone = zone;
      this.roomId = roomId;
    }
  }

  private final int capacity;
  private final long[] ticks;
  private final IntMap<Snapshot>[] frames;
  private final Map map;
  private long latestTick;

  @SuppressWarnings("unchecked")
  public CombatPositionHistory(Map map, int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.map = map;
    this.capacity = capacity;
    ticks = new long[capacity];
    frames = (IntMap<Snapshot>[]) new IntMap<?>[capacity];
    for (int i = 0; i < capacity; i++) frames[i] = new IntMap<>();
  }

  public CombatPositionHistory(Map map) {
    this(map, DEFAULT_CAPACITY);
  }

  public CombatPositionHistory() {
    this(null, DEFAULT_CAPACITY);
  }

  /** Captures every positioned entity after due movement input and before combat input. */
  public void capture(World world, long tick) {
    if (world == null) throw new NullPointerException("world");
    if (tick <= 0L) throw new IllegalArgumentException("tick must be positive");
    int slot = slot(tick);
    IntMap<Snapshot> frame = frames[slot];
    frame.clear();
    ticks[slot] = tick;

    ComponentMapper<Position> positions = world.getMapper(Position.class);
    ComponentMapper<Size> sizes = world.getMapper(Size.class);
    ComponentMapper<MapWrapper> maps = world.getMapper(MapWrapper.class);
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class)).getEntities();
    int[] data = entities.getData();
    for (int i = 0, n = entities.size(); i < n; i++) {
      int entityId = data[i];
      Position position = positions.get(entityId);
      if (position == null || !Float.isFinite(position.position.x)
          || !Float.isFinite(position.position.y)) continue;
      Size size = sizes.get(entityId);
      MapWrapper wrapper = maps.get(entityId);
      Map.Zone zone = wrapper == null ? null : wrapper.zone;
      if (zone == null && map != null) zone = map.getZone(position.position);
      frame.put(entityId, new Snapshot(entityId, tick,
          Math.round(position.position.x), Math.round(position.position.y),
          size == null ? Size.INSIGNIFICANT : Math.max(0, size.size),
          zone, wrapper == null ? -1 : wrapper.roomId));
    }
    latestTick = Math.max(latestTick, tick);
  }

  public Snapshot snapshot(int entityId, long tick) {
    if (entityId == Engine.INVALID_ENTITY || tick <= 0L) return null;
    int slot = slot(tick);
    return ticks[slot] == tick ? frames[slot].get(entityId) : null;
  }

  public long latestTick() {
    return latestTick;
  }

  public int capacity() {
    return capacity;
  }

  public RangeResult meleeRange(
      int attackerId, int targetId, int meleeRange, int rangeBonus, long tick) {
    Snapshot attacker = snapshot(attackerId, tick);
    Snapshot target = snapshot(targetId, tick);
    if (attacker == null || target == null) return RangeResult.MISSING_SNAPSHOT;
    if (attacker.zone != null && target.zone != null && attacker.zone != target.zone) {
      return RangeResult.DIFFERENT_ZONE;
    }
    if (!NativeMeleeDistance.isInRange(
        attacker.x, attacker.y, attacker.size,
        target.x, target.y, target.size,
        meleeRange, rangeBonus)) {
      return RangeResult.OUT_OF_RANGE;
    }
    return hasPlayerFlyingBarrier(attacker, target)
        ? RangeResult.BLOCKED : RangeResult.IN_RANGE;
  }

  /** D2Common UNITS_TestCollision endpoint projection + PLAYER_FLYING ray mask. */
  boolean hasPlayerFlyingBarrier(Snapshot attacker, Snapshot target) {
    if (map == null) return false;
    int x1 = attacker.x, y1 = attacker.y, x2 = target.x, y2 = target.y;
    int size1 = Math.min(attacker.size, 2);
    int size2 = Math.min(target.size, 2);
    int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
    if (dx + dy < size1 + size2) return false;
    if (size1 != 0 || size2 != 0) {
      if (dx >= dy) {
        if (x2 <= x1) { x1 -= size1; x2 += size2; }
        else { x1 += size1; x2 -= size2; }
      }
      if (dy >= dx) {
        if (y2 <= y1) { y1 -= size1; y2 += size2; }
        else { y1 += size1; y2 -= size2; }
      }
    }

    int rayDx = Math.abs(x2 - x1), rayDy = Math.abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
    int error = rayDx - rayDy;
    while (true) {
      if ((map.playerFlyingFlags(x1, y1) & DT1.Tile.FLAG_BLOCK_JUMP) != 0) return true;
      if (x1 == x2 && y1 == y2) return false;
      int twice = error << 1;
      if (twice > -rayDy) { error -= rayDy; x1 += sx; }
      if (twice < rayDx) { error += rayDx; y1 += sy; }
    }
  }

  private int slot(long tick) {
    return (int) Math.floorMod(tick, capacity);
  }
}
