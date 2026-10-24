package com.riiablo.engine.server;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.*;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import com.riiablo.map.NativePathRoomGraph;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Production non-missile request/continuation/tick bridge for resolved native monsters.
 * Unknown precision, topology or skill-owned motion stays with its existing owner. */
public final class NativePathRuntime implements D2PathTick.Effects {
  private final World world;
  private final int id;
  private final Map map;
  private final DynamicUnitCollisionSystem collision;
  public final D2PathResult.State result;
  public final D2PathTick.State tick;
  private final NativePathUnitMetadata metadata;
  private final int moveMask;
  private int targetId = Engine.INVALID_ENTITY;
  private NativePathIdentity.Token targetToken;
  private D2PathUnits.Unit target;
  private NativePathRoomGraph rooms;
  private D2PathRequest.Room room;
  private int searchRevision, projectedRevision = -1, projectedIndex = -1;
  private final IdentityHashMap<Map.Zone,Integer> generations = new IdentityHashMap<>();

  private NativePathRuntime(World world, int id, Map map, DynamicUnitCollisionSystem collision,
      NativePathUnitMetadata metadata, int mask) {
    this.world = world; this.id = id; this.map = map; this.collision = collision;
    this.metadata = metadata; moveMask = mask;
    D2PathContinuation.State path = new D2PathContinuation.State(metadata.type);
    Position position = world.getMapper(Position.class).get(id);
    path.currentPoint = new D2Path.Point(position.nativePrecisionX() >>> 16, position.nativePrecisionY() >>> 16);
    result = new D2PathResult.State(path);
    tick = new D2PathTick.State(result);
    capturePosition();
    rebuildRooms();
  }

  /** null means unsupported metadata, false means a genuine native request rejection.
   * A native rejection must never silently retry through host A*. */
  static Boolean request(World world, int id, Map map, DynamicUnitCollisionSystem collision,
      Vector2 destination, int targetId, int type, int budget, int directionOffset) {
    Position position = world.getMapper(Position.class).get(id);
    Velocity velocity = world.getMapper(Velocity.class).get(id);
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(id);
    Monster monster = world.getMapper(Monster.class).get(id);
    if (map == null || collision == null || monster == null || position == null || !position.hasNativePrecision()
        || velocity == null || velocity.nativeMonsterBaseVelocity < 0 || velocity.stateSpeedMultiplier != 1f
        || wrapper == null || wrapper.zone == null || !map.usesNativeCollision(wrapper.zone)
        || !wrapper.zone.hasNativeRoomTopology() || world.getMapper(Casting.class).has(id)
        || world.getMapper(Leap.class).has(id) || world.getMapper(WhirlwindRuntime.class).has(id)) return null;
    if (targetId < 0 && (destination.x != (int) destination.x || destination.y != (int) destination.y
        || destination.x < 0 || destination.x > 65535 || destination.y < 0 || destination.y > 65535)) return null;
    NativePathUnitMetadata metadata;
    NativePathAllocationCollision allocation;
    try {
      metadata = NativePathUnitMetadata.read(world,id);
      allocation = NativePathAllocationCollision.read(world,id);
      if (targetId >= 0) {
        NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(targetId);
        Position targetPosition = world.getMapper(Position.class).get(targetId);
        if (identity == null || identity.token() == null || !identity.token().isCurrent(world,targetId)
            || targetPosition == null || !targetPosition.hasNativePrecision()) return null;
        int targetType = NativePathUnitMetadata.read(world,targetId).type;
        if (targetType != 0 && targetType != 1) return null;
      }
    } catch (IllegalArgumentException unknown) { return null; }
    int mask = monster.vultureAirborne ? 0 : allocation.moveMask;
    Pathfind existing = world.getMapper(Pathfind.class).get(id);
    NativePathRuntime runtime = existing == null ? null : existing.nativeRuntime;
    if (runtime == null || runtime.map != map || runtime.moveMask != mask) {
      runtime = new NativePathRuntime(world,id,map,collision,metadata,mask);
    } else {
      runtime.capturePosition();
      if (runtime.gridChanged()) runtime.rebuildRooms();
      runtime.recache(runtime.result);
    }
    runtime.targetId = targetId;
    NativePathIdentity identity = targetId < 0 ? null : world.getMapper(NativePathIdentity.class).get(targetId);
    runtime.targetToken = identity == null ? null : identity.token();
    D2PathTypes.setType(runtime.result,type);
    runtime.result.path.directionOffset = directionOffset;
    runtime.result.path.setNewDistance(budget);
    // MonsterMode walk mode installs a 20-point continuation budget.
    runtime.result.path.setDistance(20);
    runtime.result.path.targetPoint = new D2Path.Point((int)destination.x,(int)destination.y);
    runtime.result.velocity = velocity.nativeMonsterVelocity();
    int count = runtime.search(runtime.result.path);
    if (count == 0 && (type == 2 || type == 7 || type == 9 || type == 13)) {
      // MonsterMode.cpp sub_6FC65680: initial mode search has its own type15 fallback.
      runtime.refresh(runtime.result.path);
      runtime.result.path.ownerFlags |= D2PathContinuation.UNIT_DOUPDATE;
      D2PathTypes.setType(runtime.result,15);
      count = runtime.search(runtime.result.path);
    }
    if (count == 0) {
      world.getMapper(Pathfind.class).remove(id);
      velocity.velocity.setZero();
      runtime.publishCache();
      return false;
    }
    world.getMapper(Running.class).remove(id);
    Pathfind path = world.getMapper(Pathfind.class).create(id);
    runtime.project(path);
    return true;
  }

  /** Prevent host timers, floating arrival tests and A* recovery from consuming native state. */
  public void prepare() {
    Velocity velocity = world.getMapper(Velocity.class).get(id);
    UnitLifecycle lifecycle = world.getMapper(UnitLifecycle.class).get(id);
    if (!currentTarget() || lifecycle != null && lifecycle.isDead()) { stop(); return; }
    if (velocity.stateMovementLocked) { velocity.velocity.setZero(); return; }
    D2Path.Point point = result.path.hasRemainingPoints() ? result.points[result.path.currentPointIndex] : null;
    Position position = world.getMapper(Position.class).get(id);
    if (point == null) velocity.velocity.setZero();
    else velocity.velocity.set(point.x - position.position.x,point.y - position.position.y)
        .setLength(velocity.speed(false));
  }

  /** Called exactly once by the authoritative 25 Hz integrator. */
  public void advance() {
    Velocity velocity = world.getMapper(Velocity.class).get(id);
    velocity.nativeMovementOwned = true;
    if (velocity.stateMovementLocked || world.delta == 0) return;
    if (Math.abs(world.delta - SimulationClock.STEP_SECONDS) > 0.000001f
        || !world.getMapper(Position.class).get(id).hasNativePrecision() || !currentTarget()) {
      stop(); return;
    }
    Position observed = world.getMapper(Position.class).get(id);
    if (observed.nativePrecisionX() != result.precisionX || observed.nativePrecisionY() != result.precisionY) {
      stop(); return; // A teleport/correction invalidates the old movement cursor.
    }
    capturePosition();
    if (gridChanged()) rebuildRooms();
    if (room == null) { stop(); return; }
    collision.refreshNow();
    result.velocity = velocity.nativeMonsterVelocity();
    observeTarget();
    Position position = world.getMapper(Position.class).get(id);
    float oldX = position.position.x, oldY = position.position.y;
    boolean active = D2PathTick.advance(tick,1024,this);
    position.setNativePrecision(result.precisionX,result.precisionY);
    collision.synchronizeNativeCollision(id);
    velocity.velocity.set(position.position.x-oldX,position.position.y-oldY).scl(1f/world.delta);
    Angle angle = world.getMapper(Angle.class).get(id);
    if (angle != null && !velocity.velocity.isZero()) angle.target.set(velocity.velocity).nor();
    publishCache();
    Pathfind path = world.getMapper(Pathfind.class).get(id);
    if (!active) {
      world.getMapper(Pathfind.class).remove(id);
      velocity.velocity.setZero();
    } else project(path);
  }

  private void stop() {
    world.getMapper(Pathfind.class).remove(id);
    world.getMapper(Velocity.class).get(id).velocity.setZero();
    result.path.pathFlags &= ~D2PathResult.ACTIVE;
    result.path.pathPointCount = result.path.currentPointIndex = 0;
    publishCache();
  }

  private boolean currentTarget() {
    return targetId < 0 || targetToken != null && targetToken.isCurrent(world,targetId)
        && !com.artemis.PendingEntityDeletion.isDeleted(world,targetId)
        && world.getMapper(Position.class).has(targetId)
        && world.getMapper(Position.class).get(targetId).hasNativePrecision();
  }

  private void capturePosition() {
    Position position = world.getMapper(Position.class).get(id);
    result.precisionX = position.nativePrecisionX(); result.precisionY = position.nativePrecisionY();
    result.path.currentPoint = new D2Path.Point(result.precisionX >>> 16,result.precisionY >>> 16);
  }

  private D2PathUnits.Unit unit(int unitId, NativePathUnitMetadata data) {
    Position position = world.getMapper(Position.class).get(unitId);
    if (!position.hasNativePrecision()) throw new IllegalStateException("target precision became unknown");
    // Lead speed is zero for this route: no host animation angle is presented as raw facing.
    return data.dynamicUnit(new D2PathUnits.DynamicPath(position.nativePrecisionX(),position.nativePrecisionY(),0));
  }

  private void observeTarget() {
    target = targetId < 0 ? null : unit(targetId,NativePathUnitMetadata.read(world,targetId));
    result.path.targetUnit = target == null ? null : D2PathUnits.observation(unit(id,metadata),target,0,
        () -> { if (!currentTarget()) throw new IllegalStateException("stale path target allocation"); });
  }

  private boolean gridChanged() {
    if (generations.size() != map.getZones().size) return true;
    for (Map.Zone zone : map.getZones()) {
      Integer generation = generations.get(zone);
      if (generation == null || generation != zone.nativeCollisionGeneration()) return true;
    }
    return false;
  }

  private void rebuildRooms() {
    ArrayList<Map.Zone> resolved = new ArrayList<>();
    generations.clear();
    for (Map.Zone zone : map.getZones()) {
      generations.put(zone,zone.nativeCollisionGeneration());
      if (map.usesNativeCollision(zone) && zone.hasNativeRoomTopology()) resolved.add(zone);
    }
    rooms = new NativePathRoomGraph(resolved);
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(id);
    Map.RoomEx source = wrapper.zone.findRoomEx(result.path.currentPoint.x,result.path.currentPoint.y);
    room = source == null ? null : rooms.room(source);
    result.room = room == null ? null : room.bounds;
  }

  @Override public int search(D2PathContinuation.State path) {
    // Continuation may call again after a failed type13 search; retain its mutations.
    observeTarget();
    if (room == null) return D2PathResult.reject(result);
    D2PathRequest.State request = new D2PathRequest.State(result,target,room,
        metadata.ownerCanBeInTown(),0,metadata.sizeX,collision.nativePathPattern(id),moveMask,0);
    int count = collision.computeNativePath(request,id,targetId);
    searchRevision++;
    publishCache();
    return count;
  }

  @Override public void refresh(D2PathContinuation.State path) { recache(result); }

  @Override public boolean tryMove(int x,int y,int nx,int ny,boolean unchecked) {
    // Step.cpp turns the door-opening SEARCH mask back into the ordinary movement mask.
    int mask = moveMask == com.d2moo.common.collision.D2Collision.COLLIDE_MASK_MONSTER_THAT_CAN_OPEN_DOORS
        ? com.d2moo.common.collision.D2Collision.COLLIDE_MASK_MONSTER_PATH : moveMask;
    return collision.moveNativePath(id,nx,ny,mask,unchecked);
  }

  @Override public void recache(D2PathResult.State state) {
    D2PathRequest.Room next = D2PathRequest.findRoom(room,state.path.currentPoint.x,state.path.currentPoint.y);
    if (next == null) return; // Native recache retains the previous room on a miss.
    if (next != room) state.path.pathFlags |= 1;
    room = next; state.room = room.bounds;
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(id);
    Map.Zone zone = wrapper.zone.nativeCollisionZone(state.path.currentPoint.x,state.path.currentPoint.y);
    if (zone != null) {
      wrapper.zone = zone;
      Map.RoomEx source = zone.findRoomEx(state.path.currentPoint.x,state.path.currentPoint.y);
      if (!wrapper.roomIdPinned) wrapper.roomId = source == null ? -1 : source.id;
    }
  }

  private void publishCache() {
    NativeAiPathCache cache = world.getMapper(NativeAiPathCache.class).get(id);
    if (cache != null) {
      cache.targetCoordinates(result.path.targetPoint.x,result.path.targetPoint.y);
      cache.pathPoints(result.path.pathPointCount > 0);
    }
  }

  private void project(Pathfind path) {
    if (path.nativeRuntime == this && projectedRevision == searchRevision
        && projectedIndex == result.path.currentPointIndex) return;
    GraphPath graph = new GraphPath();
    graph.add(new Point2(result.path.currentPoint.x,result.path.currentPoint.y));
    for (int i=result.path.currentPointIndex;i<result.path.pathPointCount;i++)
      graph.add(new Point2(result.points[i].x,result.points[i].y));
    path.set(graph);
    path.nativeRuntime = this;
    path.nativePathType = result.path.pathType;
    path.targetEntityId = targetId;
    path.targetIdentity = targetToken;
    projectedRevision = searchRevision;
    projectedIndex = result.path.currentPointIndex;
  }
}
