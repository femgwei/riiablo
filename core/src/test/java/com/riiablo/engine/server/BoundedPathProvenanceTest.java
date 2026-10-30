package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.NativeTowardRequestTrace;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;

class BoundedPathProvenanceTest {
  @Test void rejectedTowardRetainsDiagnosticIdentityAndAdvancesRevision() {
    try (PathfinderTerrainPursuitTest.Fixture f = new PathfinderTerrainPursuitTest.Fixture((x,y) -> false)) {
      f.world.getMapper(Velocity.class).remove(f.actor);
      assertFalse(f.paths.findNativePath(f.actor, f.target, 4, 0));
      NativeTowardRequestTrace trace = f.world.getMapper(NativeTowardRequestTrace.class).get(f.actor);
      assertNotNull(trace);
      assertFalse(trace.accepted);
      assertEquals(f.target, trace.targetId);
      assertSame(f.identity, trace.targetIdentity);
      long revision = trace.revision;
      assertFalse(f.paths.findNativePath(f.actor, f.target, 4, 0));
      assertEquals(revision + 1, trace.revision);
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }
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
