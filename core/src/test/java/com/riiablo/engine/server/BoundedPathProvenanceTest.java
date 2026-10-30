package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;

class BoundedPathProvenanceTest {
  @Test void towardRetainsOnlyDiagnosticTargetAndCoordinateReplacementClearsIt() {
    try (PathfinderTerrainPursuitTest.Fixture f = new PathfinderTerrainPursuitTest.Fixture((x,y) -> false)) {
      assertTrue(f.paths.findNativePath(f.actor, f.target, 4, 0));
      Pathfind path = f.world.getMapper(Pathfind.class).get(f.actor);
      assertNull(path.nativeRuntime);
      assertEquals(Engine.INVALID_ENTITY, path.targetEntityId);
      assertNull(path.targetIdentity);
      assertEquals(f.target, path.requestedTargetEntityId);
      assertSame(f.identity, path.requestedTargetIdentity);
      GraphPath replacement = new GraphPath();
      replacement.add(new Point2(10, 10)); replacement.add(new Point2(11, 10));
      path.set(replacement);
      assertEquals(Engine.INVALID_ENTITY, path.requestedTargetEntityId);
      assertNull(path.requestedTargetIdentity);
      path.requestedTargetEntityId = f.target; path.requestedTargetIdentity = f.identity;
      path.reset();
      assertEquals(Engine.INVALID_ENTITY, path.requestedTargetEntityId);
      assertNull(path.requestedTargetIdentity);
    }
  }
  @Test void orbitDoesNotClaimTowardRequestProvenance() {
    try (PathfinderTerrainPursuitTest.Fixture f = new PathfinderTerrainPursuitTest.Fixture((x,y) -> false)) {
      assertTrue(f.paths.findNativePath(f.actor, f.target, 4, 2));
      Pathfind path = f.world.getMapper(Pathfind.class).get(f.actor);
      assertEquals(Engine.INVALID_ENTITY, path.requestedTargetEntityId);
      assertNull(path.requestedTargetIdentity);
    }
  }
}
