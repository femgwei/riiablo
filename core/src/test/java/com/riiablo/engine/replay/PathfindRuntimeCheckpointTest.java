package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;

class PathfindRuntimeCheckpointTest {
  @Test void graphPathCursorAndMovementTimersRoundTrip() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      int sourceId = source.world.create();
      int targetId = target.world.create();
      GraphPath graph = new GraphPath();
      graph.add(new Point2(4, 5)); graph.add(new Point2(7, 9)); graph.add(new Point2(11, 13));
      Pathfind path = source.world.getMapper(Pathfind.class).create(sourceId).set(graph, true);
      path.advanceTarget();
      path.target.set(7.5f, 9.25f);
      path.destination.set(11, 13);
      path.nativeBounded = true; path.nativePathType = 9;
      path.targetEntityId = -1;
      path.lastTargetPosition.set(6.5f, 8.5f);
      path.repathTimer = 0.375f; path.pendingDirection = 3; path.pendingDirectionFrames = 2;
      path.stalledTime = 0.12f; path.blockedByDynamic = true; path.movementBlockReason = "door";
      target.world.getMapper(Pathfind.class).create(targetId);

      PathfindRuntimeCheckpoint image = PathfindRuntimeCheckpoint.capture(source.world, sourceId);
      image.validateHost(target.world, targetId);
      PathfindRuntimeCheckpoint.restore(target.world, targetId, image);
      assertArrayEquals(image.bytes(), PathfindRuntimeCheckpoint.capture(target.world, targetId).bytes());
      assertEquals(1, target.world.getMapper(Pathfind.class).get(targetId).checkpointTargetIndex());
      assertEquals("door", target.world.getMapper(Pathfind.class).get(targetId).movementBlockReason);
    }
  }
  private static final class Fixture implements AutoCloseable {
    final World world = new World();
    @Override public void close() { world.dispose(); }
  }
}
