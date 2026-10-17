package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import com.riiablo.map.pfa.AStarPathFinder;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.RaycastCollisionDetector;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/** Carved terrain, real A*, swept movement and dynamic occupancy; not generated-map coverage. */
class PathfinderTerrainPursuitTest {
  @Test void ordinaryLargeMonsterUsesTheSameThreeCellDoorFootprintAsDoorOpeners() {
    try (Fixture f = new Fixture((x, y) -> x == 20 && (y < 9 || y > 11), false)) {
      assertTrue(f.start());
      f.advance(240);
      assertTrue(f.position(f.actor).x > 23, "ordinary size-three movement must fit a three-cell door");
    }
  }

  @Test void largeMonsterPassesWideDoorWithoutCuttingItsJambs() {
    try (Fixture f = new Fixture((x, y) -> x == 20 && (y < 9 || y > 11))) {
      assertTrue(f.start());
      f.advance(240);
      assertTrue(f.position(f.actor).x > 23, "must actually traverse the doorway");
      assertTrue(f.position(f.actor).dst(f.position(f.target)) < 7);
    }
  }

  @Test void largeMonsterCannotSqueezeThroughOneCellDoor() {
    try (Fixture f = new Fixture((x, y) -> x == 20 && y != 10)) {
      assertFalse(f.start());
      f.advance(60);
      assertEquals(new Vector2(10, 10), f.position(f.actor));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  @Test void pathAndSweptMovementGoAroundWallEndInsteadOfCuttingTheCorner() {
    try (Fixture f = new Fixture((x, y) -> x == 20 && y <= 14)) {
      assertTrue(f.start());
      f.advance(300);
      assertTrue(f.maxY >= 16, "size-three footprint must clear the end of the wall");
      assertTrue(f.position(f.actor).x > 23);
      assertTrue(f.position(f.actor).dst(f.position(f.target)) < 7);
    }
  }

  @Test void movingTargetKeepsIdentityAndRefreshesItsApproachDuringRealMovement() {
    try (Fixture f = new Fixture((x, y) -> false)) {
      assertTrue(f.start());
      f.advance(12);
      f.position(f.target).set(32, 18);
      f.collision.refreshNow();
      f.advance(220);
      assertTrue(f.position(f.actor).y > 14, "must follow the new position, not the old endpoint");
      assertTrue(f.position(f.actor).dst(f.position(f.target)) < 7);
    }
  }

  @Test void thirdUnitInsertedIntoDoorStopsMovementThenRemovalAllowsFreshPursuit() {
    try (Fixture f = new Fixture((x, y) -> x == 20 && (y < 9 || y > 11))) {
      assertTrue(f.start());
      int blocker = f.unit(20, 10, 3, false);
      f.world.process();
      f.advance(120);
      assertTrue(f.position(f.actor).x < 20, "must not pass through the blocker");
      f.world.delete(blocker);
      f.world.process();
      assertTrue(f.start(), "a later AI request can resume after the obstruction disappears");
      f.advance(240);
      assertTrue(f.position(f.actor).x > 23);
    }
  }

  static final class Fixture implements AutoCloseable {
    // Collision sampling makes many calls; retain no Mockito invocation history.
    final Map map = mock(Map.class, withSettings().stubOnly());
    final Map.Zone zone = new Map.Zone();
    final Pathfinder paths = new Pathfinder();
    final DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    final World world;
    final int actor, target;
    final NativePathIdentity.Token identity;
    final BiPredicate<Integer, Integer> wall;
    final boolean opensDoors;
    float maxY;

    Fixture(BiPredicate<Integer, Integer> wall) {
      this(wall, true);
    }

    Fixture(BiPredicate<Integer, Integer> wall, boolean opensDoors) {
      this.wall = wall;
      this.opensDoors = opensDoors;
      when(map.isBlockedForPath(anyInt(), anyInt(), anyInt(), anyBoolean()))
          .thenAnswer(call -> blocked(call.getArgument(0), call.getArgument(1)));
      when(map.isBlockedForPath(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
          .thenCallRealMethod();
      when(map.flags(anyInt(), anyInt())).thenAnswer(call ->
          blocked(call.getArgument(0), call.getArgument(1)) ? 1 : 0);
      when(map.flags(any(Vector2.class))).thenCallRealMethod();
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      MapGraph graph = new MapGraph(map);
      AStarPathFinder search = new AStarPathFinder(graph);
      RaycastCollisionDetector rays = new RaycastCollisionDetector(map, graph);
      when(map.findPath(nullable(Map.Zone.class), any(Vector2.class), any(Vector2.class),
          anyInt(), anyInt(), any(GraphPath.class), nullable(MapGraph.Obstacle.class),
          anyInt(), anyInt(), anyBoolean())).thenAnswer(call ->
              graph.searchNodePath(search, call.getArgument(1), call.getArgument(2),
                  call.getArgument(3), call.getArgument(4), call.getArgument(5),
                  call.getArgument(6), call.getArgument(7), call.getArgument(8), call.getArgument(9)));
      when(map.castRay(nullable(Map.Zone.class), any(Ray.class), anyInt(), anyInt(),
          any(Collision.class), anyBoolean())).thenAnswer(call ->
              rays.findCollision(call.getArgument(1), call.getArgument(2), call.getArgument(3),
                  call.getArgument(4), call.getArgument(5)));
      world = new World(new WorldConfigurationBuilder().with(collision, paths, new VelocityAdder())
          .build().register("map", map));
      paths.actioneer = new Actioneer() {
        @Override public boolean isInMeleeRange(int a, int b, int bonus) { return false; }
        @Override public int getMeleeRange(int a) { return 1; }
      };
      actor = unit(10, 10, 3, true);
      target = unit(30, 10, 2, false);
      identity = world.getMapper(NativePathIdentity.class).create(target).allocated(world, target).token();
      world.setDelta(.04f);
      world.process();
    }

    boolean blocked(int x, int y) { return x < 2 || x > 40 || y < 2 || y > 24 || wall.test(x, y); }

    int unit(int x, int y, int size, boolean monster) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Velocity.class).create(id).setMonster(6);
      world.getMapper(Size.class).create(id).size = size;
      world.getMapper(Angle.class).create(id).set(Vector2.X);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type = monster
          ? com.riiablo.engine.server.component.Class.Type.MON
          : com.riiablo.engine.server.component.Class.Type.PLR;
      if (monster) {
        MonStats.Entry stats = new MonStats.Entry();
        stats.Id = "terrain-pursuit";
        stats.opendoors = opensDoors;
        world.getMapper(Monster.class).create(id).monstats = stats;
      }
      return id;
    }

    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    boolean start() { return paths.findPath(actor, position(target), false, target); }

    void advance(int ticks) {
      for (int tick = 0; tick < ticks; tick++) {
        world.process();
        Vector2 p = position(actor);
        maxY = Math.max(maxY, p.y);
        assertFalse(map.isBlockedForPath(Map.round(p.x), Map.round(p.y), 1, 3, true),
            "authoritative movement entered terrain at " + p);
        assertTrue(collision.isFreeForPath(actor, Engine.INVALID_ENTITY, Map.round(p.x), Map.round(p.y), 3),
            "authoritative movement overlapped another unit at " + p);
        Pathfind path = world.getMapper(Pathfind.class).get(actor);
        if (path != null) {
          assertEquals(target, path.targetEntityId);
          assertSame(identity, path.targetIdentity);
        }
      }
    }

    @Override public void close() { world.dispose(); }
  }
}
