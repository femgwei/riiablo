package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import com.riiablo.map.pfa.AStarPathFinder;
import com.riiablo.map.pfa.GraphPath;
import org.junit.jupiter.api.Test;

/** Real A* and dynamic occupancy on an empty mocked terrain layer. */
class PathfinderLargeTargetApproachTest {
  @Test void sizeThreePursuitFindsAFreeApproachWithoutIgnoringThePlayerFootprint() {
    Map map = mock(Map.class);
    MapGraph graph = new MapGraph(map);
    AStarPathFinder search = new AStarPathFinder(graph);
    when(map.findPath(nullable(Map.Zone.class), any(Vector2.class), any(Vector2.class),
        anyInt(), anyInt(), any(GraphPath.class), nullable(MapGraph.Obstacle.class),
        anyInt(), anyInt(), anyBoolean())).thenAnswer(call ->
            graph.searchNodePath(search, call.getArgument(1), call.getArgument(2),
                call.getArgument(3), call.getArgument(4), call.getArgument(5),
                call.getArgument(6), call.getArgument(7), call.getArgument(8), false));
    Pathfinder paths = new Pathfinder();
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    World world = new World(new WorldConfigurationBuilder().with(collision, paths)
        .build().register("map", map));
    try {
      int actor = world.create(), target = world.create();
      for (int id : new int[] {actor, target}) {
        world.getMapper(Position.class).create(id).position.set(id == actor ? 146 : 128, 128);
        world.getMapper(Velocity.class).create(id).setMonster(6);
        world.getMapper(Size.class).create(id).size = id == actor ? 3 : 2;
        world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type = id == actor
            ? com.riiablo.engine.server.component.Class.Type.MON
            : com.riiablo.engine.server.component.Class.Type.PLR;
        world.getMapper(Angle.class).create(id).set(Vector2.X);
      }
      MonStats.Entry stats = new MonStats.Entry();
      stats.Id = "large-pursuit";
      world.getMapper(Monster.class).create(actor).monstats = stats;
      NativePathIdentity.Token token = world.getMapper(NativePathIdentity.class)
          .create(target).allocated(world, target).token();
      world.process();
      Vector2 goal = world.getMapper(Position.class).get(target).position;
      Vector2 source = world.getMapper(Position.class).get(actor).position;
      MapGraph.Obstacle obstacle = (mover, ignored, x, y, size) -> collision.isFreeForPath(
          actor, x == 128 && y == 128 ? target : Engine.INVALID_ENTITY, x, y, size);
      assertFalse(graph.searchNodePath(search, source, goal, 1, 3,
          new GraphPath(), obstacle, actor, target), "an ignored goal still has no free neighbors");
      assertTrue(paths.findPath(actor, goal, false, target));
      Pathfind path = world.getMapper(Pathfind.class).get(actor);
      assertNotEquals(goal, path.destination);
      assertTrue(collision.isFreeForPath(actor, Engine.INVALID_ENTITY,
          Map.round(path.destination.x), Map.round(path.destination.y), 3));
      assertEquals(target, path.targetEntityId);
      assertSame(token, path.targetIdentity);
      assertEquals(goal, path.lastTargetPosition);
    } finally { world.dispose(); }
  }
}
