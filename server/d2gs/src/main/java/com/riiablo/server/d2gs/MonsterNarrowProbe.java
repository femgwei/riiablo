package com.riiablo.server.d2gs;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Diagnostic state only. Never requests paths, changes targets or moves units. */
final class MonsterNarrowProbe {
  static final class State {
    int entity, target, queryX, queryY;
    long lifetime, targetLifetime;
    NativePathIdentity.Token identity, targetIdentity;
    MapTourProbe.Sample movement = new MapTourProbe.Sample();
    boolean dynamicBlocked, queryFree;
  }
  private static final class Hold {
    State first, last;
    int axis;
  }
  private final java.util.Map<Integer, State> previous = new HashMap<>();
  private final java.util.Map<Integer, Hold> holds = new HashMap<>();
  private final java.util.IdentityHashMap<NativePathIdentity.Token, Long> lifetimes = new java.util.IdentityHashMap<>();

  List<String> observe(World world, Map map, int player, long tick) {
    List<String> events = new ArrayList<>();
    java.util.Map<Integer, State> current = new HashMap<>();
    MapWrapper owner = world.getMapper(MapWrapper.class).get(player);
    DynamicUnitCollisionSystem collision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (owner == null || owner.zone == null || collision == null) return events;
    com.artemis.utils.IntBag units = world.getAspectSubscriptionManager().get(Aspect.all(
        Monster.class, Position.class, MapWrapper.class, Size.class, Pathfind.class,
        AttributesWrapper.class, NativePathIdentity.class)).getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      Monster monster = world.getMapper(Monster.class).get(id);
      MapWrapper wrapper = world.getMapper(MapWrapper.class).get(id);
      Pathfind path = world.getMapper(Pathfind.class).get(id);
      AttributesWrapper attrs = world.getMapper(AttributesWrapper.class).get(id);
      NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(id);
      if (!MapTourProbe.hostileBlocker(monster) || wrapper.zone != owner.zone
          || wrapper.zone.isSyntheticArena() || wrapper.zone.nativeTileGrid() == null
          || NativeGroundPathProfile.forUnit(false, monster.monstats) != NativeGroundPathProfile.MONSTER
          || attrs.attrs == null || attrs.attrs.getValue(Stat.hitpoints, 0) <= 0
          || world.getMapper(Mercenary.class).has(id) || world.getMapper(SummonedPet.class).has(id)
          || path.targetEntityId != player || path.targetIdentity == null
          || !path.targetIdentity.isCurrent(world, player) || identity.token() == null) continue;
      State state = new State();
      state.entity = id; state.target = player;
      state.identity = identity.token(); state.targetIdentity = path.targetIdentity;
      state.lifetime = lifetimes.computeIfAbsent(state.identity, ignored -> (long) lifetimes.size() + 1);
      state.targetLifetime = lifetimes.computeIfAbsent(state.targetIdentity, ignored -> (long) lifetimes.size() + 1);
      MapTourProbe.Sample sample = state.movement;
      sample.tick = tick; sample.level = wrapper.zone.level.Id;
      sample.nativeTerrain = true;
      sample.unitSize = world.getMapper(Size.class).get(id).size;
      sample.position = new Vector2(world.getMapper(Position.class).get(id).position);
      Velocity velocity = world.getMapper(Velocity.class).get(id);
      sample.maximumMovementSpeed = velocity == null ? 0 : Math.max(velocity.speed(false), velocity.speed(true));
      int x = Math.round(sample.position.x), y = Math.round(sample.position.y);
      sample.blocked = !clear(map, wrapper.zone, x, y, sample.unitSize)
          || !collision.isFreeForPath(id, -1, x, y, sample.unitSize);
      if (!sample.blocked) {
        sample.narrowSpanX = span(map, wrapper.zone, x, y, sample.unitSize, 1, 0);
        sample.narrowSpanY = span(map, wrapper.zone, x, y, sample.unitSize, 0, 1);
        sample.narrowSpanDiagonalPlus = span(map, wrapper.zone, x, y, sample.unitSize, 1, 1);
        sample.narrowSpanDiagonalMinus = span(map, wrapper.zone, x, y, sample.unitSize, 1, -1);
      }
      state.queryX = Math.round(path.target.x); state.queryY = Math.round(path.target.y);
      state.queryFree = collision.isFreeForPath(id, -1, state.queryX, state.queryY, sample.unitSize);
      state.dynamicBlocked = path.blockedByDynamic && !state.queryFree
          && clear(map, wrapper.zone, state.queryX, state.queryY, sample.unitSize);
      State before = previous.get(id);
      current.put(id, state);
      if (!same(before, state)) { holds.remove(id); continue; }
      for (int axis = 0; axis < 4; axis++) {
        if (MapTourProbe.narrowProgress(before.movement, sample, axis))
          events.add("MONSTER_NARROW_MOVE " + pair(before, state, axis));
      }
      Hold hold = holds.get(id);
      if (state.dynamicBlocked && stationary(before, state)) {
        if (hold == null || !same(hold.last, state) || hold.last.queryX != state.queryX
            || hold.last.queryY != state.queryY || hold.first.movement.position.dst2(sample.position) >= .01f) {
          hold = null;
          holds.remove(id);
          for (int axis = 0; axis < 4; axis++) {
            if (MapTourProbe.narrowSpan(sample, axis) <= 0) continue;
            hold = new Hold(); hold.first = before; hold.axis = axis;
            break;
          }
        }
        if (hold != null) {
          hold.last = state; holds.put(id, hold);
          if (sample.tick - hold.first.movement.tick >= 5)
            events.add("MONSTER_NARROW_BLOCK " + pair(hold.first, state, hold.axis)
                + " query=(" + state.queryX + "," + state.queryY + ") queryWasBlocked=true");
        }
      } else if (hold != null) {
        if (recovered(hold.first, hold.last, before, state, hold.axis)
            && collision.isFreeForPath(id, -1, hold.last.queryX, hold.last.queryY, sample.unitSize)) {
          events.add("MONSTER_NARROW_RECOVER " + pair(before, state, hold.axis)
              + " blockedFirstTick=" + hold.first.movement.tick + " blockedLastTick=" + hold.last.movement.tick
              + " blockedPosition=" + hold.last.movement.position
              + " query=(" + hold.last.queryX + "," + hold.last.queryY + ") queryWasBlocked=true queryNowFree=true");
          holds.remove(id);
        } else if (sample.tick - hold.last.movement.tick > 250) holds.remove(id);
      }
    }
    previous.clear(); previous.putAll(current);
    holds.keySet().retainAll(current.keySet());
    return events;
  }

  static boolean same(State a, State b) {
    return a != null && b != null && a.entity == b.entity && a.target == b.target
        && a.identity != null && a.identity == b.identity && a.targetIdentity != null
        && a.targetIdentity == b.targetIdentity && a.movement.level == b.movement.level
        && a.movement.unitSize == b.movement.unitSize;
  }
  static boolean stationary(State a, State b) {
    long ticks = b.movement.tick - a.movement.tick;
    return same(a, b) && ticks > 0 && ticks <= 10 && a.dynamicBlocked && b.dynamicBlocked
        && a.movement.position.dst2(b.movement.position) < .01f;
  }
  static boolean recovered(State first, State last, State before, State now, int axis) {
    return same(first, last) && same(last, now) && same(before, now)
        && last.movement.tick - first.movement.tick >= 5
        && before.movement.tick >= last.movement.tick
        && now.movement.tick > last.movement.tick && now.movement.tick - last.movement.tick <= 250
        && !now.dynamicBlocked && MapTourProbe.narrowProgress(before.movement, now.movement, axis)
        && last.movement.position.dst(now.movement.position) >= 1f;
  }
  private static String pair(State before, State now, int axis) {
    return "entity=" + now.entity + " target=" + now.target + " identity=SAME_LIFETIME level=" + now.movement.level
        + " lifetime=" + now.lifetime + " targetLifetime=" + now.targetLifetime
        + " crossAxis=" + new String[] {"X", "Y", "DPLUS", "DMINUS"}[axis]
        + " footprint=" + now.movement.unitSize + " firstTick=" + before.movement.tick
        + " lastTick=" + now.movement.tick + " from=" + before.movement.position + " to=" + now.movement.position
        + " beforeSpan=" + MapTourProbe.narrowSpan(before.movement, axis)
        + " afterSpan=" + MapTourProbe.narrowSpan(now.movement, axis)
        + " allowedDisplacement=" + (Math.max(before.movement.maximumMovementSpeed, now.movement.maximumMovementSpeed)
            * (now.movement.tick - before.movement.tick) * .04f + .1f)
        + " bound=3 movement=PRODUCTION_AI coverage=SAMPLED_LOCAL_PROGRESS";
  }
  private static int span(Map map, Map.Zone zone, int x, int y, int size, int dx, int dy) {
    if (!zone.contains(x - 3 * dx, y - 3 * dy) || !zone.contains(x + 3 * dx, y + 3 * dy)) return 0;
    return MapTourProbe.narrowCenterSpan(offset -> clear(map, zone, x + offset * dx, y + offset * dy, size));
  }
  private static boolean clear(Map map, Map.Zone zone, int x, int y, int size) {
    return zone.contains(x, y) && !map.isBlockedForPath(zone, x, y,
        NativeGroundPathProfile.MONSTER.terrainFlags, size, false);
  }
}
