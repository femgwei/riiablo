package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NativeAiPathCacheTest {
  @Test
  void unknownAndAllocatedEmptyAreDifferentStates() {
    NativeAiPathCache cache = new NativeAiPathCache();
    assertFalse(cache.coordinatesKnown);
    assertFalse(cache.pointsKnown);
    cache.allocated();
    assertTrue(cache.coordinatesKnown);
    assertTrue(cache.pointsKnown);
    assertFalse(cache.hasPoints);
    cache.targetCoordinates(25, 30);
    cache.pathPoints(true);
    cache.invalidate();
    assertFalse(cache.coordinatesKnown);
    assertFalse(cache.pointsKnown);
    cache.allocated();
    assertEquals(0, cache.targetX);
    assertEquals(0, cache.targetY);
    assertFalse(cache.hasPoints);
  }

  @Test
  void removedComponentCannotLeakKnownStateToPooledEntityReuse() {
    World world = new World();
    try {
      int first = world.create();
      NativeAiPathCache old = world.getMapper(NativeAiPathCache.class).create(first).allocated();
      old.targetCoordinates(25, 30);
      old.pathPoints(true);
      world.process();
      world.delete(first);
      world.process();
      int next = world.create();
      NativeAiPathCache cache = world.getMapper(NativeAiPathCache.class).create(next);
      assertFalse(cache.coordinatesKnown);
      assertFalse(cache.pointsKnown);
      assertFalse(cache.hasPoints);
      assertEquals(0, cache.targetX);
      assertEquals(0, cache.targetY);
    } finally {
      world.dispose();
    }
  }

  @Test
  void actualPathfinderInvalidatesEvenRejectedRequestButNotEcsStop() {
    Pathfinder paths = new Pathfinder();
    World world = new World(new WorldConfigurationBuilder().with(paths)
        .build().register("map", mock(Map.class)));
    try {
      int actor = world.create();
      NativeAiPathCache cache = world.getMapper(NativeAiPathCache.class).create(actor).allocated();
      assertFalse(paths.findPath(actor, null));
      assertTrue(cache.coordinatesKnown);
      assertFalse(paths.findPath(actor, new Vector2(20, 20)), "missing velocity rejects the request");
      assertFalse(cache.coordinatesKnown);
      assertFalse(cache.pointsKnown);
    } finally {
      world.dispose();
    }
  }
}
