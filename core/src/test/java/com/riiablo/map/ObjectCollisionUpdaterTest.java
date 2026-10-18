package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

class ObjectCollisionUpdaterTest {

  @Test
  void nativeObjectOverlapClearsSharedBitAndDoesNotReappearNextFrame() {
    Fixture fixture = new Fixture();
    try {
      int first = fixture.create(object(1, 1, true, false, false), 5, 5, Engine.Object.MODE_NU);
      fixture.create(object(1, 1, true, false, false), 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      assertEquals(0x400, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      fixture.mode(first, Engine.Object.MODE_ON);
      fixture.world.process();
      assertEquals(0, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      fixture.world.process();
      assertEquals(0, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      assertEquals(1, fixture.zone.objectCollisionReferences(5, 5));
      // Diagnostic references must not restore cleared native bits for walking.
      fixture.assertBlocked(5, 5, false);
      assertTrue(fixture.world.getSystem(com.riiablo.engine.server.DynamicUnitCollisionSystem.class)
          .isFreeForMonsterSpawnMask(-1, fixture.zone, 5, 5, 1, 0x400));
    } finally { fixture.dispose(); }
  }

  @Test
  void nativeDoorUsesUnitsMaskAndDeletesWithoutRestoringClearedTerrainBits() {
    Fixture fixture = new Fixture();
    try {
      fixture.zone.or(5, 5, 2);
      Objects.Entry door = object(1, 1, true, false, false);
      door.IsDoor = true;
      door.BlocksVis = true;
      int id = fixture.create(door, 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      assertEquals(0x806, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      assertFalse(fixture.world.getSystem(com.riiablo.engine.server.DynamicUnitCollisionSystem.class)
          .isFreeForMonsterSpawnMask(-1, fixture.zone, 5, 5, 1, 0x800));
      fixture.world.delete(id);
      fixture.world.process();
      assertEquals(0, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      assertEquals(2, fixture.zone.staticFlagsWorld(5, 5));
    } finally { fixture.dispose(); }
  }

  @Test
  void nativeGridPreservesUnsignedBitsAndTracksLateTerrainAndZoneReanchoring() {
    Fixture fixture = new Fixture();
    try {
      fixture.zone.setNativeCollisionMask(4, 4, 0x18000);
      fixture.zone.or(4, 4, 1);
      assertEquals(0x8001, fixture.zone.nativeCollisionFlagsWorld(4, 4));
      fixture.zone.setPosition(100, 200);
      assertEquals(0x8001, fixture.zone.nativeCollisionFlagsWorld(104, 204));
      assertEquals(0x27, fixture.zone.nativeCollisionFlagsWorld(4, 4));
      fixture.zone.resetNativeCollisionMask(104, 204, 0x8000);
      assertEquals(1, fixture.zone.nativeCollisionFlagsWorld(104, 204));
    } finally { fixture.dispose(); }
  }

  @Test
  void nativeObjectMaskIncludesMissileBarrierAndPortalCorpseBit() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry solid = object(1, 1, true, false, false);
      solid.BlockMissile = true;
      fixture.create(solid, 3, 3, Engine.Object.MODE_NU);
      Objects.Entry portal = object(1, 1, true, false, false);
      portal.SubClass = 4;
      fixture.create(portal, 7, 7, Engine.Object.MODE_NU);
      fixture.world.process();
      assertEquals(0x404, fixture.zone.nativeCollisionFlagsWorld(3, 3));
      assertEquals(0x8000, fixture.zone.nativeCollisionFlagsWorld(7, 7));
    } finally { fixture.dispose(); }
  }

  @Test
  void nativeObjectMoveTransfersFootprintAndMaskChangesAreObserved() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry door = object(1, 1, true, false, false);
      door.IsDoor = true;
      door.BlockMissile = true;
      int id = fixture.create(door, 3, 3, Engine.Object.MODE_NU);
      fixture.world.process();
      assertEquals(0x804, fixture.zone.nativeCollisionFlagsWorld(3, 3));
      fixture.world.getMapper(Position.class).get(id).position.set(7, 7);
      fixture.world.process();
      assertEquals(0, fixture.zone.nativeCollisionFlagsWorld(3, 3));
      assertEquals(0x804, fixture.zone.nativeCollisionFlagsWorld(7, 7));
      door.BlockMissile = false;
      fixture.world.process();
      assertEquals(0x400, fixture.zone.nativeCollisionFlagsWorld(7, 7));
    } finally { fixture.dispose(); }
  }

  @Test
  void nativeObjectZoneTransferClearsOldGridAndWritesDestination() {
    Fixture fixture = new Fixture();
    try {
      Map.Zone destination = new Map.Zone();
      destination.map = fixture.map;
      destination.x = 12;
      destination.width = destination.height = 12;
      destination.flags = new byte[144];
      fixture.map.zones.add(destination);
      int id = fixture.create(object(1, 1, true, false, false), 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.world.getMapper(Position.class).get(id).position.set(17, 5);
      fixture.world.getMapper(MapWrapper.class).get(id).set(fixture.map, destination);
      fixture.world.process();
      assertEquals(0, fixture.zone.nativeCollisionFlagsWorld(5, 5));
      assertEquals(0x400, destination.nativeCollisionFlagsWorld(17, 5));
      fixture.world.delete(id);
      fixture.world.process();
      assertEquals(0, destination.nativeCollisionFlagsWorld(17, 5));
    } finally { fixture.dispose(); }
  }

  @Test
  void headlessDoorFollowsNativeModeCollisionAndRestoresAfterRecreation() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry door = object(3, 3, true, false, false);
      int entity = fixture.create(door, 5, 5, Engine.Object.MODE_NU);

      fixture.world.process();
      fixture.assertBlocked(5, 5, true);

      fixture.mode(entity, Engine.Object.MODE_OP);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.mode(entity, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.world.delete(entity);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.create(door, 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(5, 5, true);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void overlappingObjectRemovalClearsSharedWalkingBitDespiteRemainingReference() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry solid = object(3, 3, true, false, false);
      int first = fixture.create(solid, 5, 5, Engine.Object.MODE_NU);
      int second = fixture.create(solid, 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(5, 5, true);

      fixture.mode(first, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);
      assertEquals(1, fixture.zone.objectCollisionReferences(5, 5));

      fixture.world.delete(second);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void dynamicObjectsNeverClearOrBecomeStaticTerrain() {
    Fixture fixture = new Fixture();
    try {
      fixture.zone.or(2, 2, DT1.Tile.FLAG_BLOCK_WALK);
      int entity = fixture.create(
          object(1, 1, true, false, false), 2, 2, Engine.Object.MODE_NU);
      fixture.world.process();

      assertEquals(DT1.Tile.FLAG_BLOCK_WALK,
          fixture.map.staticFlags(2, 2) & DT1.Tile.FLAG_BLOCK_WALK);
      fixture.mode(entity, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(2, 2, true);

      int dynamicOnly = fixture.create(
          object(1, 1, true, false, false), 8, 8, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(8, 8, true);
      assertEquals(0,
          fixture.map.staticFlags(8, 8) & DT1.Tile.FLAG_BLOCK_WALK);
      fixture.world.delete(dynamicOnly);
      fixture.world.process();
      fixture.assertBlocked(8, 8, false);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void playerFlyingMaskIncludesClosedDoorsButNotOrdinaryObjects() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry door = object(1, 1, true, false, false);
      door.IsDoor = true;
      door.BlockMissile = true;
      int doorEntity = fixture.create(door, 3, 3, Engine.Object.MODE_NU);
      fixture.create(object(1, 1, true, false, false), 7, 7, Engine.Object.MODE_NU);
      fixture.world.process();

      assertEquals(DT1.Tile.FLAG_BLOCK_JUMP,
          fixture.map.playerFlyingFlags(3, 3) & DT1.Tile.FLAG_BLOCK_JUMP);
      assertEquals(0,
          fixture.map.playerFlyingFlags(7, 7) & DT1.Tile.FLAG_BLOCK_JUMP);

      fixture.mode(doorEntity, Engine.Object.MODE_ON);
      fixture.world.process();
      assertEquals(0,
          fixture.map.playerFlyingFlags(3, 3) & DT1.Tile.FLAG_BLOCK_JUMP);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void openingMonsterIgnoresDoorButNotOrdinaryDynamicObject() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry door = object(1, 1, true, false, false);
      door.IsDoor = true;
      door.BlockMissile = true;
      fixture.create(door, 3, 3, Engine.Object.MODE_NU);
      fixture.create(object(1, 1, true, false, false), 7, 7, Engine.Object.MODE_NU);
      fixture.world.process();

      assertTrue(fixture.map.isBlockedForPath(3, 3,
          DT1.Tile.FLAG_BLOCK_WALK, false));
      assertFalse(fixture.map.isBlockedForPath(3, 3,
          DT1.Tile.FLAG_BLOCK_WALK, true));
      assertTrue(fixture.map.isBlockedForPath(7, 7,
          DT1.Tile.FLAG_BLOCK_WALK, true));
    } finally {
      fixture.dispose();
    }
  }

  private static Objects.Entry object(int width, int height,
      boolean nu, boolean op, boolean on) {
    Objects.Entry row = new Objects.Entry();
    row.SizeX = width;
    row.SizeY = height;
    row.HasCollision = new boolean[8];
    row.HasCollision[Engine.Object.MODE_NU] = nu;
    row.HasCollision[Engine.Object.MODE_OP] = op;
    row.HasCollision[Engine.Object.MODE_ON] = on;
    return row;
  }

  private static final class Fixture {
    final Map map = new Map(0, 0);
    final Map.Zone zone = new Map.Zone();
    final World world;

    Fixture() {
      zone.map = map;
      zone.x = 0;
      zone.y = 0;
      zone.width = 12;
      zone.height = 12;
      zone.tilesX = 3;
      zone.tilesY = 3;
      zone.flags = new byte[zone.width * zone.height];
      map.zones.add(zone);
      world = new World(new WorldConfigurationBuilder()
          .with(new ObjectCollisionUpdater(), new com.riiablo.engine.server.DynamicUnitCollisionSystem())
          .build().register("map", map));
    }

    int create(Objects.Entry base, float x, float y, byte mode) {
      int entity = world.create();
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entity).base = base;
      world.getMapper(com.riiablo.engine.server.component.Object.class).get(entity).mode = mode;
      world.getMapper(Position.class).create(entity).position.set(x, y);
      world.getMapper(CofReference.class).create(entity).set("DR", mode);
      world.getMapper(MapWrapper.class).create(entity).set(map, zone);
      return entity;
    }

    void mode(int entity, byte mode) {
      world.getMapper(CofReference.class).get(entity).mode = mode;
      world.getMapper(com.riiablo.engine.server.component.Object.class).get(entity).mode = mode;
    }

    void assertBlocked(int x, int y, boolean expected) {
      int actual = map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK;
      assertEquals(expected ? DT1.Tile.FLAG_BLOCK_WALK : 0, actual);
    }

    void dispose() {
      world.dispose();
      // The test Zone is not pool-owned and has deliberately minimal fields,
      // so Map.dispose() is not appropriate here.
      map.zones.clear();
    }
  }
}
