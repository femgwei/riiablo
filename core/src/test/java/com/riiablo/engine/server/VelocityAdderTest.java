package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VelocityAdderTest {
  @Test void legacyMovementInvalidatesRawPrecisionButIdleTicksPreserveIt() {
    World world = new World(new WorldConfigurationBuilder().with(
        new DynamicUnitCollisionSystem(false), new VelocityAdder()).build());
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id).initializeNativeCell(10, 20);
      Velocity velocity = world.getMapper(Velocity.class).create(id);
      world.setDelta(0.04f);
      world.process();
      assertEquals(0x000A8000, position.nativePrecisionX());
      velocity.velocity.set(6, 0);
      world.process();
      assertFalse(position.hasNativePrecision());
      position.position.set(10, 20);
      assertFalse(position.hasNativePrecision());
    } finally { world.dispose(); }
  }

  static final class BlockedMap extends Map {
    private final Zone zone = new Zone();
    BlockedMap() { super(1, 0); }
    @Override public Zone getZone(int x, int y) { return zone; }
    @Override public boolean castRay(Ray<Vector2> ray, int flags, int size,
        Collision<Vector2> collision, boolean canOpenDoors) { return true; }
  }

  @Test void headlessTerrainStallsAreRecordedAndResetAfterMovement() {
    VelocityAdder movement = new VelocityAdder();
    World world = new World(new WorldConfigurationBuilder().with(
        new DynamicUnitCollisionSystem(false), movement).build()
        .register("map", new BlockedMap()));
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id);
      Velocity velocity = world.getMapper(Velocity.class).create(id);
      Pathfind path = world.getMapper(Pathfind.class).create(id);
      world.setDelta(0.04f);
      velocity.velocity.set(6, 0);
      world.process();
      assertEquals(0.04f, path.stalledTime, 0.00001f);
      assertEquals("terrain", path.movementBlockReason);
      assertTrue(position.position.isZero());
      assertFalse(path.blockedByDynamic);
      movement.map = null;
      velocity.velocity.set(6, 0);
      world.process();
      assertEquals(0.24f, position.position.x, 0.00001f);
      assertEquals(0f, path.stalledTime);
      assertEquals("none", path.movementBlockReason);
    } finally { world.dispose(); }
  }
}
