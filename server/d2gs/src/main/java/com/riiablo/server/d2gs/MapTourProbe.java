package com.riiablo.server.d2gs;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.MapTourPlan;

/** Read-only probe. All access is on the authoritative simulation thread. */
final class MapTourProbe {
  static final class Sample {
    java.util.List<String> monsterEvidence = java.util.Collections.emptyList();
    int level = -1;
    long tick;
    float maximumMovementSpeed;
    int narrowSpanX, narrowSpanY;
    int narrowSpanDiagonalPlus, narrowSpanDiagonalMinus;
    String name = "missing";
    int[] diagnostics = new int[0];
    int objects, monsters, entities, pendingMonsters, spawnedMonsters, spawnedObjects, groundItems;
    boolean synthetic, walking, blocked, actionReady;
    boolean nativeTerrain;
    boolean blockedByDynamic;
    float stalledTime;
    String movementBlockReason = "none";
    Vector2 pathTarget, destination;
    Vector2 position, target;
    int warp = Engine.INVALID_ENTITY;
    int monsterClass = -1, unitSize;
    float authoritativeLife;
    boolean meleeReady;
    String transition = "JUMP_DETACHED";
  }

  /** Only nearby hostile blockers, never town NPCs, pets or units across walls. */
  static Sample blocker(World world, Map map, int playerId, Vector2 destination) {
    return blocker(world, map, playerId, destination, Engine.INVALID_ENTITY);
  }

  static Sample blocker(World world, Map map, int playerId, Vector2 destination, int targetId) {
    MapWrapper owner = world.getMapper(MapWrapper.class).get(playerId);
    Position player = world.getMapper(Position.class).get(playerId);
    if (owner == null || owner.zone == null || owner.zone.isTown() || player == null) return null;
    com.artemis.utils.IntBag units = world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.Monster.class, Position.class,
        MapWrapper.class, com.riiablo.engine.server.component.AttributesWrapper.class)).getEntities();
    Sample best = null;
    float nearest = Float.MAX_VALUE;
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (targetId != Engine.INVALID_ENTITY && id != targetId) continue;
      MapWrapper targetOwner = world.getMapper(MapWrapper.class).get(id);
      if (targetOwner.zone == null || targetOwner.map != null && targetOwner.map != map
          || targetOwner.zone != owner.zone && (targetOwner.zone.isTown()
              || !map.isNativeSeamConnection(owner.zone, targetOwner.zone))) continue;
      com.riiablo.engine.server.component.Monster monster = world.getMapper(
          com.riiablo.engine.server.component.Monster.class).get(id);
      if (!hostileBlocker(monster)
          || world.getMapper(com.riiablo.engine.server.component.Mercenary.class).has(id)
          || world.getMapper(com.riiablo.engine.server.component.SummonedPet.class).has(id)) continue;
      com.riiablo.attributes.Attributes attrs = world.getMapper(
          com.riiablo.engine.server.component.AttributesWrapper.class).get(id).attrs;
      com.riiablo.attributes.StatRef life = attrs == null ? null : attrs.get(
          com.riiablo.attributes.Stat.hitpoints, com.riiablo.attributes.StatRef.obtain());
      if (life == null || life.asFixed() <= 0f) continue;
      Vector2 position = world.getMapper(Position.class).get(id).position;
      float distance = player.position.dst2(position);
      if (distance > 25f) continue;
      // UNITS_IsInMeleeRange tests PLAYER_FLYING (door | missile barrier),
      // not PLAYER_PATH. Use the production endpoint projection: ordinary
      // walk-only objects cannot conceal a legitimate melee blocker.
      if (meleeBarrier(world, map, playerId, id)) continue;
      // Restrict to the intended direction, except immediate neighbours that
      // occupy the player's escape footprint in a narrow passage.
      com.riiablo.engine.server.Actioneer actioneer = world.getSystem(
          com.riiablo.engine.server.Actioneer.class);
      boolean meleeReady = actioneer != null && actioneer.isInMeleeRange(playerId, id, 0);
      if (!meleeReady && targetId == Engine.INVALID_ENTITY && destination != null && distance > 9f
          && new Vector2(position).sub(player.position).dot(
              new Vector2(destination).sub(player.position)) < 0f) continue;
      // A closer but out-of-range neighbour may not indefinitely conceal a
      // different blocker that production can actually attack now.
      if (best != null && (best.meleeReady && !meleeReady
          || best.meleeReady == meleeReady && distance >= nearest)) continue;
      nearest = distance;
      best = new Sample();
      best.level = owner.zone.level.Id;
      best.warp = id;
      best.position = new Vector2(position);
      best.monsterClass = monster.monstats.hcIdx;
      com.riiablo.engine.server.component.Size size = world.getMapper(
          com.riiablo.engine.server.component.Size.class).get(id);
      best.unitSize = size == null ? 0 : size.size;
      best.authoritativeLife = life.asFixed();
      best.meleeReady = meleeReady;
    }
    com.riiablo.engine.server.Actioneer actioneer = world.getSystem(
        com.riiablo.engine.server.Actioneer.class);
    if (best != null && !best.meleeReady && actioneer != null) {
      best.target = meleeApproach(world, map, playerId, best.warp, actioneer.getMeleeRange(playerId));
    }
    return best;
  }

  /** Find a real walking approach, never relocate the player or release collision. */
  static Vector2 meleeApproach(World world, Map map, int playerId, int targetId, int meleeRange) {
    Map.Zone zone = world.getMapper(MapWrapper.class).get(playerId).zone;
    Vector2 origin = world.getMapper(Position.class).get(playerId).position;
    Vector2 target = world.getMapper(Position.class).get(targetId).position;
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    int targetSize = world.getMapper(com.riiablo.engine.server.component.Size.class).get(targetId).size;
    java.util.BitSet connected = reachable(world, map, playerId, zone, origin, size);
    Vector2 best = null;
    float nearest = Float.POSITIVE_INFINITY;
    int tx = Math.round(target.x), ty = Math.round(target.y);
    for (int y = ty - 6; y <= ty + 6; y++) for (int x = tx - 6; x <= tx + 6; x++) {
      if (!zone.contains(x, y) || !connected.get((y - zone.y()) * zone.width() + x - zone.x())
          || !clear(map, zone, x, y, size) || !dynamicallyFree(world, playerId, zone, x, y, size)
          || !com.riiablo.engine.server.combat.NativeMeleeDistance.isInRange(
              x, y, size, tx, ty, targetSize, meleeRange, 0)
          || com.riiablo.engine.server.combat.CombatPositionHistory.hasPlayerFlyingBarrier(
              map, x, y, size, tx, ty, targetSize)) continue;
      float distance = origin.dst2(x, y);
      if (distance <= 1f || distance >= nearest) continue;
      nearest = distance;
      best = new Vector2(x, y);
    }
    return best;
  }

  static boolean meleeBarrier(World world, Map map, int playerId, int targetId) {
    Vector2 player = world.getMapper(Position.class).get(playerId).position;
    Vector2 target = world.getMapper(Position.class).get(targetId).position;
    com.riiablo.engine.server.component.Size playerSize = world.getMapper(
        com.riiablo.engine.server.component.Size.class).get(playerId);
    com.riiablo.engine.server.component.Size targetSize = world.getMapper(
        com.riiablo.engine.server.component.Size.class).get(targetId);
    return com.riiablo.engine.server.combat.CombatPositionHistory.hasPlayerFlyingBarrier(map,
        Math.round(player.x), Math.round(player.y), playerSize == null ? 0 : playerSize.size,
        Math.round(target.x), Math.round(target.y), targetSize == null ? 0 : targetSize.size);
  }

  static boolean hostileBlocker(com.riiablo.engine.server.component.Monster monster) {
    return monster != null && monster.monstats != null && monster.monstats.Align == 0
        && monster.monstats.killable && !monster.monstats.npc
        && !monster.converted && !monster.playerRevive;
  }

  static boolean mayPlanDoor(String mode, int openedDoors) {
    return openedDoors < 32 && !"DOOR_APPROACH".equals(mode);
  }

  static Sample inspect(World world, Map map, int playerId, int nextLevel, boolean local) {
    Sample sample = new Sample();
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(playerId);
    Position position = world.getMapper(Position.class).get(playerId);
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    if (zone == null || position == null) return sample;
    sample.level = zone.level.Id;
    com.riiablo.engine.server.component.Velocity velocity = world.getMapper(
        com.riiablo.engine.server.component.Velocity.class).get(playerId);
    if (velocity != null) sample.maximumMovementSpeed = Math.max(velocity.walkSpeed, velocity.runSpeed)
        * Math.max(1f, velocity.movementMultiplier());
    sample.name = zone.level.LevelName;
    sample.position = new Vector2(position.position);
    com.riiablo.engine.server.Actioneer actioneer = world.getSystem(com.riiablo.engine.server.Actioneer.class);
    sample.actionReady = actioneer == null || actioneer.canInterrupt(playerId);
    sample.diagnostics = zone.nativeDiagnostics();
    sample.objects = zone.getNativeObjects().size;
    sample.monsters = zone.getNativeMonsters().size;
    sample.entities = zone.getEntities().size;
    for (Map.RoomEx room : zone.getRoomsEx()) sample.pendingMonsters += room.getPendingMonsterSpawns().size;
    com.artemis.utils.IntBag units = world.getAspectSubscriptionManager()
        .get(Aspect.all(MapWrapper.class)).getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (world.getMapper(MapWrapper.class).get(id).zone != zone) continue;
      if (world.getMapper(com.riiablo.engine.server.component.Monster.class).has(id)) sample.spawnedMonsters++;
      if (world.getMapper(com.riiablo.engine.server.component.Object.class).has(id)) sample.spawnedObjects++;
      if (world.getMapper(com.riiablo.engine.server.component.Item.class).has(id)) sample.groundItems++;
    }
    sample.synthetic = zone.isSyntheticArena();
    sample.nativeTerrain = zone.nativeTileGrid() != null || zone.isTown()
        && zone.presetSourceFile() != null;
    sample.walking = world.getMapper(Pathfind.class).has(playerId);
    if (sample.walking) {
      Pathfind path = world.getMapper(Pathfind.class).get(playerId);
      sample.blockedByDynamic = path.blockedByDynamic;
      sample.stalledTime = path.stalledTime;
      sample.movementBlockReason = path.movementBlockReason;
      sample.pathTarget = new Vector2(path.target);
      sample.destination = new Vector2(path.destination);
    }
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    sample.blocked = !clear(map, zone, sample.position.x, sample.position.y, size);
    sample.unitSize = size;
    if (!sample.blocked && sample.nativeTerrain && !sample.synthetic) {
      int x = Math.round(sample.position.x), y = Math.round(sample.position.y);
      // A zone edge is not a wall. Require every probe to remain inside the level.
      if (zone.contains(x - 3, y) && zone.contains(x + 3, y))
        sample.narrowSpanX = narrowCenterSpan(offset -> clear(map, zone, x + offset, y, size));
      if (zone.contains(x, y - 3) && zone.contains(x, y + 3))
        sample.narrowSpanY = narrowCenterSpan(offset -> clear(map, zone, x, y + offset, size));
      if (zone.contains(x - 3, y - 3) && zone.contains(x + 3, y + 3))
        sample.narrowSpanDiagonalPlus = narrowCenterSpan(offset -> clear(map, zone, x + offset, y + offset, size));
      if (zone.contains(x - 3, y + 3) && zone.contains(x + 3, y - 3))
        sample.narrowSpanDiagonalMinus = narrowCenterSpan(offset -> clear(map, zone, x + offset, y - offset, size));
    }
    if (local) {
      sample.transition = "LOCAL_WALK";
      // Local smoke target must belong to the spawn's collision component;
      // an arbitrary clear tile across a wall is not a valid click fixture.
      sample.target = localTarget(world, map, playerId, zone, sample.position, size);
      return sample;
    }
    Map.Zone next = Riiablo.files.Levels.get(nextLevel) == null ? null
        : map.findZone(Riiablo.files.Levels.get(nextLevel));
    boolean vis = false;
    if (zone.level.Vis != null) for (int id : zone.level.Vis) if (id == nextLevel) vis = true;
    if (next != null && map.isNativeSeamConnection(zone, next)) {
      sample.transition = "MISSING_WALK_SEAM";
      planBoundaryWalk(world, map, playerId, zone, next, sample, size);
      return sample; // never recover a failed walk seam with a Warp
    }
    if (MapTourPlan.requiresTownExit(zone.level.Id, nextLevel)) {
      sample.transition = "MISSING_TOWN_WALK";
      return sample; // town exits must not pass via staging/teleport
    }
    com.artemis.utils.IntBag warps = world.getAspectSubscriptionManager()
        .get(Aspect.all(Warp.class, Position.class, MapWrapper.class)).getEntities();
    java.util.BitSet reachable = null;
    float best = Float.MAX_VALUE;
    boolean bestReachable = false;
    boolean foundWarp = false;
    for (int i = 0; i < warps.size(); i++) {
      int id = warps.get(i);
      Warp warp = world.getMapper(Warp.class).get(id);
      MapWrapper owner = world.getMapper(MapWrapper.class).get(id);
      if (owner.zone != zone || warp.dstLevel == null || warp.dstLevel.Id != nextLevel) continue;
      foundWarp = true;
      sample.transition = "WARP_WALK";
      Vector2 target = world.getMapper(Position.class).get(id).position;
      Interactable interaction = world.getMapper(Interactable.class).get(id);
      // Match D2GS.validateQuestWarp's footprint-aware interaction allowance.
      float range = warpApproachRange(interaction == null ? 1f : interaction.range);
      if (reachable == null) reachable = reachable(world, map, playerId, zone, sample.position, size);
      for (int dy = -(int) range; dy <= range; dy++) {
        for (int dx = -(int) range; dx <= range; dx++) {
          float x = Math.round(target.x) + dx, y = Math.round(target.y) + dy;
          if (target.dst2(x, y) > range * range || !clear(map, zone, x, y, size)) continue;
          float distance = sample.position.dst2(x, y);
          boolean connected = reachable.get((Math.round(y) - zone.y()) * zone.width()
              + Math.round(x) - zone.x()) && dynamicallyFree(world, playerId,
                  Math.round(x), Math.round(y), size);
          if (connected && !bestReachable || connected == bestReachable && distance < best) {
            bestReachable = connected; best = distance; sample.target = new Vector2(x, y);
            sample.warp = id;
          }
        }
      }
    }
    if (foundWarp) return sample;
    // A declared static Warp disappearing is a failure, never a successful jump.
    if (MapTourPlan.requiresTownExit(zone.level.Id, nextLevel)) sample.transition = "MISSING_TOWN_EXIT";
    else if (MapTourPlan.declaresWarp(zone.level, nextLevel)) sample.transition = "MISSING_WARP";
    else if (next != null && vis && map.areZonesAdjacent(zone, next)) {
      sample.transition = "MISSING_BOUNDARY_WARP";
    }
    return sample;
  }

  private static void planBoundaryWalk(World world, Map map, int playerId,
      Map.Zone source, Map.Zone destination, Sample sample, int size) {
    java.util.BitSet connected = reachable(world, map, playerId, source, sample.position, size, false);
    float best = Float.MAX_VALUE;
    boolean bestLive = false;
    int adjacent = 0, connectedEdges = 0, clearEdges = 0, clearGoals = 0;
    com.badlogic.gdx.ai.utils.Collision<Vector2> hit =
        new com.badlogic.gdx.ai.utils.Collision<>(new Vector2(), new Vector2());
    for (int y = source.y(); y < source.y() + source.height(); y++) {
      for (int x = source.x(); x < source.x() + source.width(); x++) {
        if (x != source.x() && x != source.x() + source.width() - 1
            && y != source.y() && y != source.y() + source.height() - 1) continue;
        for (int[] step : new int[][] {{-1,0},{1,0},{0,-1},{0,1}}) {
          if (!destination.contains(x + step[0], y + step[1])) continue;
          adjacent++;
          if (!connected.get((y - source.y()) * source.width() + x - source.x())) continue;
          connectedEdges++;
          Vector2 approach = new Vector2(x, y);
          // At least three cells inside the destination, beyond the normal
          // stop-click tolerance. An edge-only touch is not a level crossing.
          Vector2 goal = new Vector2(x + 3 * step[0], y + 3 * step[1]);
          if (!clear(map, source, x, y, size)) continue;
          clearEdges++;
          if (!clear(map, destination, goal.x, goal.y, size)) continue;
          clearGoals++;
          if (map.castRay(source, new com.badlogic.gdx.ai.utils.Ray<>(approach, goal),
                  com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags,
                  size, hit, false)) continue;
          float distance = sample.position.dst2(approach);
          boolean live = dynamicallyFree(world, playerId, source, x, y, size);
          for (int offset = 1; live && offset <= 3; offset++) {
            live = dynamicallyFree(world, playerId, destination,
                x + offset * step[0], y + offset * step[1], size);
          }
          // Prefer an actually unoccupied crossing, not the nearest static
          // endpoint inside an NPC/monster footprint. Keep static fallback
          // when all crossings are occupied so genuine combat can clear it.
          if (bestLive && !live || bestLive == live && distance >= best) continue;
          best = distance;
          bestLive = live;
          sample.target = approach;
          sample.destination = goal;
          sample.transition = "BOUNDARY_WALK";
        }
      }
    }
    if (sample.target == null) System.out.println("[MAP_SEAM_DIAG] from=" + source.levelId()
        + " to=" + destination.levelId() + " adjacent=" + adjacent + " reachableEdges=" + connectedEdges
        + " clearEdges=" + clearEdges + " clearGoals=" + clearGoals + " playerSize=" + size);
  }

  // Network movement treats clicks within one tile as stop. Leave that entire
  // arrival allowance plus half a tile inside validateQuestWarp's range.
  static float warpApproachRange(float interactionRange) {
    return Math.max(1f, interactionRange) + 2f - 1.5f;
  }

  static float doorApproachRange(float interactionRange) {
    // Reserve the one-cell production stop allowance. The warp's additional
    // half-cell margin is unnecessary for ordinary doors.
    return Math.max(1f, interactionRange) + 2f - 1f;
  }

  static float arrivalToleranceSquared(String mode) {
    return "WARP_WALK".equals(mode) || "DOOR_APPROACH".equals(mode) ? 1f : 2.25f;
  }

  /** Count legal footprint centers between the first blocked offsets on both sides.
   * Zero means no bounded cross-section within +/-3; this is not physical wall width. */
  static int narrowCenterSpan(java.util.function.IntPredicate clear) {
    if (!clear.test(0)) return 0;
    int negative = 1, positive = 1;
    while (negative <= 3 && clear.test(-negative)) negative++;
    while (positive <= 3 && clear.test(positive)) positive++;
    return negative <= 3 && positive <= 3 ? negative + positive - 1 : 0;
  }

  static boolean narrowProgress(Sample previous, Sample current, boolean crossX) {
    return narrowProgress(previous, current, crossX ? 0 : 1);
  }

  static int narrowSpan(Sample sample, int axis) {
    switch (axis) {
      case 0: return sample.narrowSpanX;
      case 1: return sample.narrowSpanY;
      case 2: return sample.narrowSpanDiagonalPlus;
      case 3: return sample.narrowSpanDiagonalMinus;
      default: return 0;
    }
  }

  static boolean narrowProgress(Sample previous, Sample current, int axis) {
    long ticks = current.tick - previous.tick;
    if (ticks <= 0 || ticks > 10 || previous.level != current.level
        || !previous.nativeTerrain || !current.nativeTerrain || previous.synthetic || current.synthetic
        || previous.blocked || current.blocked || previous.unitSize != current.unitSize) return false;
    int before = narrowSpan(previous, axis), after = narrowSpan(current, axis);
    if (before <= 0 || after <= 0) return false;
    float dx = current.position.x - previous.position.x;
    float dy = current.position.y - previous.position.y;
    float along, across;
    switch (axis) {
      case 0: along = Math.abs(dy); across = Math.abs(dx); break;
      case 1: along = Math.abs(dx); across = Math.abs(dy); break;
      case 2: along = Math.abs(dx - dy) / (float) Math.sqrt(2);
        across = Math.abs(dx + dy) / (float) Math.sqrt(2); break;
      case 3: along = Math.abs(dx + dy) / (float) Math.sqrt(2);
        across = Math.abs(dx - dy) / (float) Math.sqrt(2); break;
      default: return false;
    }
    float allowed = Math.max(previous.maximumMovementSpeed, current.maximumMovementSpeed) * ticks * .04f + .1f;
    return along >= 1f && across <= .75f && previous.position.dst(current.position) <= allowed;
  }

  private static boolean clear(Map map, Map.Zone zone, float x, float y) {
    return clear(map, zone, x, y, 1);
  }

  private static boolean clear(Map map, Map.Zone zone, float x, float y, int size) {
    int px = Math.round(x), py = Math.round(y);
    return zone.contains(px, py)
        && !map.isBlockedForPath(zone, px, py,
            com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags, size, false);
  }

  static Vector2 spawn(World world, Map map, int playerId, Map.Zone zone, Vector2 fallback) {
    if (zone == null) return null;
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    java.util.ArrayList<Vector2> anchors = new java.util.ArrayList<>();
    if (zone.isTown() && fallback != null) anchors.add(fallback);
    if (zone.level != null && zone.level.Id == 26 && zone.nativeTileGrid() != null) {
      // DrlgOutdoors.cpp DRLGOUTDOORS_SpawnAct1DirtPaths explicitly anchors
      // Tamoe's road at MonasteryGate +(27,13) tiles. The first RoomEx can
      // instead be the detached decorative wing of facade1.ds1. This is the
      // canonical segment entrance, not a teleport towards a failed exit.
      Vector2 entrance = monasteryRoadEntrance(zone);
      anchors.add(entrance);
    }
    for (Map.RoomEx room : zone.getRoomsEx()) {
      anchors.add(new Vector2(room.x + room.width / 2, room.y + room.height / 2));
    }
    if (fallback != null && !zone.isTown()) anchors.add(fallback);
    for (Vector2 anchor : anchors) {
      for (int radius = 0; radius <= 48; radius++) {
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = Math.round(anchor.x) + dx, y = Math.round(anchor.y) + dy;
          if (clear(map, zone, x, y, size) && dynamicallyFree(world, playerId, zone, x, y, size)
              ) {
            Vector2 candidate = new Vector2(x, y);
            Vector2 walk = localTarget(world, map, playerId, zone, candidate, size);
            // Avoid tiny decoration pockets on the outside of native borders.
            // This is segment-start placement, never recovery from a failed exit.
            if (walk == null || walk.dst2(candidate) < 16 * 16) continue;
            return new Vector2(x, y);
          }
        }
      }
    }
    return null;
  }

  static Vector2 monasteryRoadEntrance(Map.Zone zone) {
    return new Vector2(zone.x() + 27 * 5, zone.y() + 13 * 5);
  }

  private static Vector2 localTarget(World world, Map map, int playerId, Map.Zone zone, Vector2 origin, int size) {
    java.util.ArrayDeque<Vector2> queue = new java.util.ArrayDeque<>();
    java.util.HashSet<Long> seen = new java.util.HashSet<>();
    Vector2 start = new Vector2(Math.round(origin.x), Math.round(origin.y));
    Vector2 best = null;
    queue.add(start); seen.add(key((int) start.x, (int) start.y));
    while (!queue.isEmpty()) {
      Vector2 p = queue.remove();
      if (best == null || p.dst2(origin) > best.dst2(origin)) best = p;
      if (p.dst2(origin) >= 24 * 24) return p;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int x = (int) p.x + dx, y = (int) p.y + dy;
        if (Math.abs(x - start.x) > 24 || Math.abs(y - start.y) > 24) continue;
        // Match native A*: candidate footprint, not extra side footprints.
        if (seen.add(key(x, y)) && clear(map, zone, x, y, size)
            && dynamicallyFree(world, playerId, zone, x, y, size)) queue.add(new Vector2(x, y));
      }
    }
    return best != null && best.dst2(origin) >= 4 * 4 ? best : null;
  }

  private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

  private static java.util.BitSet reachable(World world, Map map, int playerId,
      Map.Zone zone, Vector2 origin, int size) {
    return reachable(world, map, playerId, zone, origin, size, true);
  }

  private static java.util.BitSet reachable(World world, Map map, int playerId,
      Map.Zone zone, Vector2 origin, int size, boolean dynamic) {
    int width = zone.width(), height = zone.height();
    long cells = (long) width * height;
    if (cells <= 0 || cells > 4_000_000L) throw new IllegalStateException("Tour reachability footprint: " + cells);
    java.util.BitSet seen = new java.util.BitSet((int) cells);
    int[] queue = new int[(int) cells]; int head = 0, tail = 0;
    int startX = Math.round(origin.x) - zone.x(), startY = Math.round(origin.y) - zone.y();
    if (startX < 0 || startY < 0 || startX >= width || startY >= height) return seen;
    int start = startY * width + startX;
    seen.set(start); queue[tail++] = start;
    while (head < tail) {
      int p = queue[head++], px = p % width, py = p / width;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int x = px + dx, y = py + dy;
        if (x < 0 || y < 0 || x >= width || y >= height) continue;
        int id = y * width + x;
        if (seen.get(id) || !clear(map, zone, x + zone.x(), y + zone.y(), size)
            || dynamic && !dynamicallyFree(world, playerId, x + zone.x(), y + zone.y(), size)) continue;
        // Native PATH_AStar_ExploreChildren validates the target pattern.
        seen.set(id); queue[tail++] = id;
      }
    }
    return seen;
  }

  private static boolean dynamicallyFree(World world, int playerId, int x, int y, int size) {
    return dynamicallyFree(world, playerId, world.getMapper(MapWrapper.class).get(playerId).zone, x, y, size);
  }

  private static boolean dynamicallyFree(World world, int playerId, Map.Zone zone, int x, int y, int size) {
    com.riiablo.engine.server.DynamicUnitCollisionSystem collision =
        world.getSystem(com.riiablo.engine.server.DynamicUnitCollisionSystem.class);
    return collision == null || collision.isFreeForPath(zone, playerId, Engine.INVALID_ENTITY, x, y, size);
  }

  static String diagnose(World world, Map map, int playerId) {
    Sample state = inspect(world, map, playerId, -1, true);
    Map.Zone zone = world.getMapper(MapWrapper.class).get(playerId).zone;
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    StringBuilder out = new StringBuilder("level=" + state.level + " position=" + state.position
        + " size=" + size + " pathTarget=" + state.pathTarget + " destination=" + state.destination
        + " stalled=" + state.stalledTime + " reason=" + state.movementBlockReason + "\n");
    out.append("vis=").append(java.util.Arrays.toString(zone.level.Vis))
        .append(" warp=").append(java.util.Arrays.toString(zone.level.Warp)).append('\n');
    for (int slot = 0; slot < 8; slot++) {
      int override = map.getWarpDestinationOverride(zone.level.Id, slot);
      if (override > 0) out.append("warpOverride=").append(slot).append("->").append(override).append('\n');
    }
    out.append("Legend: P player, # terrain/objects, D dynamic units, . free; world subtile grid\n");
    int cx = Math.round(state.position.x), cy = Math.round(state.position.y);
    out.append("origin=").append(cx - 40).append(',').append(cy - 20).append('\n');
    for (int y = cy - 20; y <= cy + 20; y++) {
      for (int x = cx - 40; x <= cx + 40; x++) {
        out.append(x == cx && y == cy ? 'P' : !clear(map, zone, x, y, size) ? '#'
            : !dynamicallyFree(world, playerId, x, y, size) ? 'D' : '.');
      }
      out.append('\n');
    }
    java.util.BitSet connected = reachable(world, map, playerId, zone, state.position, size);
    java.util.BitSet staticConnected = reachable(world, map, playerId, zone, state.position, size, false);
    out.append("reachableCells=").append(connected.cardinality()).append('\n');
    out.append("staticReachableCells=").append(staticConnected.cardinality()).append('\n');
    com.riiablo.drlg.TileGrid nativeGrid = zone.nativeTileGrid();
    if (nativeGrid != null && nativeGrid.nativeCollisionFlags != null) {
      int mismatches = 0, maxX = 0, maxY = 0;
      for (int y = 0; y < nativeGrid.height; y++) for (int x = 0; x < nativeGrid.width; x++) {
        if (nativeGrid.nativeCollisionCells[y][x]) {
          maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
          for (int sy = 0; sy < 5; sy++) for (int sx = 0; sx < 5; sx++) {
            int px = x * 5 + sx, py = y * 5 + sy;
            int selected = nativeGrid.nativeCollisionFlags[py * nativeGrid.width * 5 + px] & 9;
            int applied = zone.staticFlags(px, py) & 9;
            if (selected != applied) {
              if (mismatches < 12) out.append("nativeCollisionMismatch cell=").append(px).append(',').append(py)
                  .append(" selected=").append(selected).append(" applied=").append(applied).append('\n');
              mismatches++;
            }
          }
        }
      }
      out.append("nativeCollisionMismatches=").append(mismatches).append('\n');
      out.append("terrainTileFreeCounts crop=").append(Math.min(maxX, 79)).append(',').append(Math.min(maxY, 79)).append('\n');
      for (int y = 0; y <= Math.min(maxY, 79); y++) {
        out.append("terrainRow=").append(y).append(' ');
        for (int x = 0; x <= Math.min(maxX, 79); x++) {
          int free = 0;
          for (int sy = 0; sy < 5; sy++) for (int sx = 0; sx < 5; sx++) {
            if ((zone.staticFlags(x * 5 + sx, y * 5 + sy) & 9) == 0) free++;
          }
          out.append("0123456789ABCDEFGHIJKLMNOP".charAt(free));
        }
        out.append('\n');
      }
    }
    for (Map.RoomEx room : zone.getRoomsEx()) {
      out.append("room=").append(room.id).append(" rect=").append(room.x).append(',')
          .append(room.y).append(',').append(room.width).append(',').append(room.height)
          .append(" adjacent=").append(java.util.Arrays.toString(room.getAdjacentRoomIds())).append('\n');
    }
    com.artemis.utils.IntBag units = world.getAspectSubscriptionManager().get(Aspect.all(Position.class, MapWrapper.class)).getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (world.getMapper(MapWrapper.class).get(id).zone != zone) continue;
      Vector2 p = world.getMapper(Position.class).get(id).position;
      Warp warp = world.getMapper(Warp.class).get(id);
      com.riiablo.engine.server.component.Object object = world.getMapper(com.riiablo.engine.server.component.Object.class).get(id);
      if (warp != null || object != null || p.dst2(state.position) < 48 * 48) {
        out.append("entity=").append(id).append(" pos=").append(p);
        com.riiablo.engine.server.component.Monster monster = world.getMapper(
            com.riiablo.engine.server.component.Monster.class).get(id);
        if (monster != null) {
          com.riiablo.engine.server.component.AttributesWrapper attributes = world.getMapper(
              com.riiablo.engine.server.component.AttributesWrapper.class).get(id);
          com.riiablo.attributes.StatRef life = attributes == null || attributes.attrs == null ? null
              : attributes.attrs.get(com.riiablo.attributes.Stat.hitpoints,
                  com.riiablo.attributes.StatRef.obtain());
          com.riiablo.engine.server.Actioneer actioneer = world.getSystem(
              com.riiablo.engine.server.Actioneer.class);
          out.append(" monsterClass=").append(monster.monstats == null ? -1 : monster.monstats.hcIdx)
              .append(" life=").append(life == null ? Float.NaN : life.asFixed())
              .append(" hostile=").append(hostileBlocker(monster))
              .append(" meleeBarrier=").append(meleeBarrier(world, map, playerId, id))
              .append(" meleeReady=").append(actioneer != null && actioneer.isInMeleeRange(playerId, id, 0))
              .append(" deadCol=").append(monster.monstats2 != null && monster.monstats2.deadCol)
              .append(" velocity=").append(world.getMapper(com.riiablo.engine.server.component.Velocity.class).has(id))
              .append(" corpse=").append(world.getMapper(com.riiablo.engine.server.component.Corpse.class).has(id))
              .append(" groundCenterClear=").append(clear(map, zone, p.x, p.y, 1));
        }
        if (warp != null) {
          Interactable interaction = world.getMapper(Interactable.class).get(id);
          out.append(" warpDst=").append(warp.dstLevel == null ? -1 : warp.dstLevel.Id)
              .append(" warpIndex=").append(warp.index).append(" range=")
              .append(interaction == null ? 1f : interaction.range);
        }
        if (object != null) out.append(" object=").append(object.base == null ? "null" : object.base.Id)
            .append(" mode=").append(object.mode)
            .append(" isDoor=").append(object.base != null && object.base.IsDoor)
            .append(" operateFn=").append(object.base == null ? -1 : object.base.OperateFn)
            .append(" interactRange=").append(world.getMapper(Interactable.class).has(id)
                ? world.getMapper(Interactable.class).get(id).range : -1f);
        if (object != null && object.base != null && object.base.IsDoor) {
          float nearestApproach = Float.POSITIVE_INFINITY;
          for (int cell = staticConnected.nextSetBit(0); cell >= 0; cell = staticConnected.nextSetBit(cell + 1)) {
            nearestApproach = Math.min(nearestApproach, p.dst2(zone.x() + cell % zone.width(),
                zone.y() + cell / zone.width()));
          }
          out.append(" nearestStaticApproach=").append(Math.sqrt(nearestApproach));
        }
        out.append('\n');
        if (warp != null) {
          int wx = Math.round(p.x), wy = Math.round(p.y);
          Interactable interaction = world.getMapper(Interactable.class).get(id);
          float range = warpApproachRange(interaction == null ? 1f : interaction.range);
          int dynamicApproaches = 0, staticApproaches = 0;
          int radius = (int) Math.ceil(range);
          for (int y = wy - radius; y <= wy + radius; y++) {
            for (int x = wx - radius; x <= wx + radius; x++) {
              if (!zone.contains(x, y) || p.dst2(x, y) > range * range) continue;
              int cell = (y - zone.y()) * zone.width() + x - zone.x();
              if (connected.get(cell)) dynamicApproaches++;
              if (staticConnected.get(cell)) staticApproaches++;
            }
          }
          out.append("warpApproaches static=").append(staticApproaches)
              .append(" dynamic=").append(dynamicApproaches).append('\n');
          out.append("warpGridOrigin=").append(wx - 7).append(',').append(wy - 7).append('\n');
          for (int y = wy - 7; y <= wy + 7; y++) {
            for (int x = wx - 7; x <= wx + 7; x++) {
              out.append(x == wx && y == wy ? 'W' : !clear(map, zone, x, y, size) ? '#'
                  : !dynamicallyFree(world, playerId, x, y, size) ? 'D' : '.');
            }
            out.append('\n');
          }
        }
      }
    }
    return out.toString();
  }

  /** A closed ordinary door is an interaction, never a collision exemption. */
  static boolean doorOpen(World world, int entityId) {
    com.riiablo.engine.server.component.Object object = world.getMapper(
        com.riiablo.engine.server.component.Object.class).get(entityId);
    com.riiablo.engine.server.component.CofReference cof = world.getMapper(
        com.riiablo.engine.server.component.CofReference.class).get(entityId);
    // ObjectInteractor persists ON when it starts the OP animation. The
    // collision updater still obeys that active COF until the sequence ends.
    return object != null && object.base != null && object.base.IsDoor
        && object.mode == Engine.Object.MODE_ON && cof != null
        && cof.mode == Engine.Object.MODE_ON
        && !world.getMapper(com.riiablo.engine.server.component.Sequence.class).has(entityId);
  }

  /** Find a closed door reachable through the current terrain component. */
  static Sample door(World world, Map map, int playerId, Vector2 destination) {
    Map.Zone zone = world.getMapper(MapWrapper.class).get(playerId).zone;
    Vector2 origin = world.getMapper(Position.class).get(playerId).position;
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    com.artemis.utils.IntBag objects = world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.Object.class, Interactable.class, Position.class, MapWrapper.class)).getEntities();
    java.util.ArrayList<Integer> candidates = new java.util.ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      int id = objects.get(i);
      com.riiablo.engine.server.component.Object object = world.getMapper(
          com.riiablo.engine.server.component.Object.class).get(id);
      if (world.getMapper(MapWrapper.class).get(id).zone != zone || object.base == null
          || object.mode != Engine.Object.MODE_NU) continue;
      com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle kind =
          com.riiablo.engine.server.object.NativeObjectOperateTable.resolve(object.base, null);
      if (kind == com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.TOGGLE_DOOR
          || kind == com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ONE_WAY_DOOR) candidates.add(id);
    }
    // The door is a static planning frontier. A monster before it must not
    // hide that frontier; route() will stop at the occupied prefix and use
    // real combat. Every approach cell still obeys terrain/object collision.
    java.util.BitSet connected = reachable(world, map, playerId, zone, origin, size, false);
    if (destination != null && zone.contains(Math.round(destination.x), Math.round(destination.y))) {
      int goal = (Math.round(destination.y) - zone.y()) * zone.width()
          + Math.round(destination.x) - zone.x();
      // If terrain and current doors already connect the destination, an
      // empty live prefix means unit blockage. Opening unrelated side-room
      // doors is not progress on this route and can wake avoidable packs.
      if (connected.get(goal)) return null;
    }
    candidates.sort(java.util.Comparator.comparingDouble(id ->
        world.getMapper(Position.class).get(id).position.dst2(destination == null ? origin : destination)));
    for (int id : candidates) {
      Vector2 anchor = world.getMapper(Position.class).get(id).position;
      float range = doorApproachRange(world.getMapper(Interactable.class).get(id).range);
      int radius = (int) Math.ceil(range + size + 2);
      for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
        int x = Math.round(anchor.x) + dx, y = Math.round(anchor.y) + dy;
        if (anchor.dst2(x, y) > range * range || !clear(map, zone, x, y, size)
            || !dynamicallyFree(world, playerId, x, y, size)) continue;
        int localX = x - zone.x(), localY = y - zone.y();
        if (!connected.get(localY * zone.width() + localX)) continue;
        Vector2 approach = new Vector2(x, y);
        Sample result = new Sample();
        result.level = zone.level.Id; result.target = approach; result.warp = id;
        result.transition = "DOOR_OPEN";
        return result;
      }
    }
    return null;
  }

  /** Static long-range planning, live obstacles near the next click. Every
   * waypoint still has to execute through production ECS collision/pathing. */
  static java.util.List<Vector2> route(World world, Map map, int playerId, Vector2 target) {
    return route(world, map, playerId, target, true);
  }

  private static java.util.List<Vector2> route(World world, Map map, int playerId,
      Vector2 target, boolean liveOccupancy) {
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(playerId);
    Position position = world.getMapper(Position.class).get(playerId);
    if (wrapper == null || wrapper.zone == null || position == null) return java.util.Collections.emptyList();
    Map.Zone zone = wrapper.zone;
    int size = world.getMapper(com.riiablo.engine.server.component.Size.class).get(playerId).size;
    int width = zone.width(), height = zone.height();
    long cells = (long) width * height;
    if (cells <= 0 || cells > 4_000_000L) throw new IllegalStateException("Tour route footprint too large: " + cells);
    int sx = Math.round(position.position.x) - zone.x(), sy = Math.round(position.position.y) - zone.y();
    int tx = Math.round(target.x) - zone.x(), ty = Math.round(target.y) - zone.y();
    if (!zone.contains(Math.round(target.x), Math.round(target.y))) {
      Map.Zone destination = map.getZone(target);
      if (!map.isNativeSeamConnection(zone, destination) || position.position.dst2(target) > 8 * 8) {
        return java.util.Collections.emptyList();
      }
      com.badlogic.gdx.ai.utils.Collision<Vector2> hit =
          new com.badlogic.gdx.ai.utils.Collision<>(new Vector2(), new Vector2());
      if (map.castRay(zone, new com.badlogic.gdx.ai.utils.Ray<>(position.position, target),
          com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags, size, hit, false)) {
        return java.util.Collections.emptyList();
      }
      com.badlogic.gdx.ai.pfa.DefaultGraphPath<com.riiablo.map.pfa.Point2> proof =
          new com.badlogic.gdx.ai.pfa.DefaultGraphPath<>();
      int pathMask = com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags;
      boolean staticPath = map.findPath(zone, position.position, target, pathMask, size, proof,
          null, playerId, -1, false);
      boolean dynamicPath = map.findPath(zone, position.position, target, pathMask, size, proof,
          (mover, ignored, x, y, footprint) -> dynamicallyFree(world, playerId, x, y, footprint),
          playerId, -1, false);
      System.out.println("[MAP_SEAM_PATH] from=" + zone.levelId() + " to=" + destination.levelId()
          + " position=" + position.position + " target=" + target
          + " staticPath=" + staticPath + " dynamicPath=" + dynamicPath);
      com.riiablo.engine.server.DynamicUnitCollisionSystem units = world.getSystem(
          com.riiablo.engine.server.DynamicUnitCollisionSystem.class);
      if (!dynamicPath && units != null) {
        for (int i = 0; i <= 4; i++) {
          Vector2 point = new Vector2(position.position).lerp(target, i / 4f);
          com.badlogic.gdx.utils.IntArray owners = units.pathBlockers(playerId,
              Math.round(point.x), Math.round(point.y), size);
          for (int j = 0; j < owners.size; j++) {
            int id = owners.get(j);
            MapWrapper owner = world.getMapper(MapWrapper.class).get(id);
            Monster monster = world.getMapper(Monster.class).get(id);
            System.out.println("[MAP_SEAM_BLOCKER] cell=" + point + " entity=" + id
                + " level=" + (owner == null || owner.zone == null ? -1 : owner.zone.levelId())
                + " position=" + world.getMapper(Position.class).get(id).position
                + " monster=" + (monster == null || monster.monstats == null ? "none" : monster.monstats.Id)
                + " hostile=" + (monster != null && hostileBlocker(monster)));
          }
        }
      }
      return java.util.Collections.singletonList(new Vector2(target));
    }
    if (sx < 0 || sy < 0 || sx >= width || sy >= height || tx < 0 || ty < 0 || tx >= width || ty >= height) {
      return java.util.Collections.emptyList();
    }
    int[] parent = new int[(int) cells]; java.util.Arrays.fill(parent, -1);
    int[] queue = new int[(int) cells]; int head = 0, tail = 0;
    int start = sy * width + sx, goal = ty * width + tx;
    queue[tail++] = start; parent[start] = start;
    while (head < tail && parent[goal] < 0) {
      int p = queue[head++], px = p % width, py = p / width;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int x = px + dx, y = py + dy;
        if (x < 0 || y < 0 || x >= width || y >= height) continue;
        int id = y * width + x;
        if (parent[id] >= 0 || !clear(map, zone, x + zone.x(), y + zone.y(), size)
            || liveOccupancy && !routeDynamicallyFree(world, playerId, zone, position.position,
                x + zone.x(), y + zone.y(), size)) continue;
        // Keep the same candidate-only full-pattern rule as production A*.
        parent[id] = p; queue[tail++] = id;
      }
    }
    if (parent[goal] < 0) {
      // A blocker 5-24 cells away can sever the live snapshot while still
      // being outside melee range. Plan toward it using static terrain;
      // every click still executes production collision and must actually
      // pass (or stop for real TCP combat). This is not a movement bypass.
      if (liveOccupancy) {
        java.util.List<Vector2> staticRoute = route(world, map, playerId, target, false);
        if (!staticRoute.isEmpty()) System.out.println("[MAP_TOUR_ROUTE] planning=STATIC_BLOCKER_APPROACH"
            + " level=" + zone.level.Id + " target=" + target + " productionCollision=ENABLED");
        return staticRoute;
      }
      return java.util.Collections.emptyList();
    }
    java.util.ArrayList<Vector2> path = new java.util.ArrayList<>();
    for (int p = goal; p != start; p = parent[p]) {
      path.add(new Vector2(p % width + zone.x(), p / width + zone.y()));
    }
    java.util.Collections.reverse(path);
    if (!liveOccupancy) {
      // A location command aimed at an occupied endpoint can be rejected by
      // production pathfinding before moving at all. Walk the free prefix
      // toward the blocker, then replan/fight at close range.
      for (int i = 0; i < path.size(); i++) {
        Vector2 p = path.get(i);
        if (!routeDynamicallyFree(world, playerId, zone, position.position,
                Math.round(p.x), Math.round(p.y), size)) {
          path.subList(i, path.size()).clear();
          if (path.isEmpty()) return java.util.Collections.emptyList();
          target = path.get(path.size() - 1);
          break;
        }
      }
    }
    java.util.ArrayList<Vector2> steps = new java.util.ArrayList<>();
    for (int i = 11; i < path.size(); i += 12) steps.add(path.get(i));
    if (path.isEmpty() || steps.isEmpty() || !steps.get(steps.size() - 1).equals(target)) steps.add(new Vector2(target));
    return steps;
  }

  static boolean usesLiveRouteOccupancy(Vector2 origin, int x, int y) {
    return origin.dst2(x, y) <= 24 * 24;
  }

  private static boolean routeDynamicallyFree(World world, int playerId, Map.Zone zone,
      Vector2 origin, int x, int y, int size) {
    return !usesLiveRouteOccupancy(origin, x, y)
        || dynamicallyFree(world, playerId, zone, x, y, size);
  }
}
