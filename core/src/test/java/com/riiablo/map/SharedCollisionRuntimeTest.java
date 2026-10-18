package com.riiablo.map;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production ECS boundary and actual zone bits, without MPQ or GL. */
class SharedCollisionRuntimeTest {
  private static final class Fixture implements AutoCloseable {
    final Map map = new Map(1, 0);
    final Map.Zone zone = zone(10, 10, 30, 30, 109);
    final DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new ObjectCollisionUpdater(), collision).build().register("map", map));

    final com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    Fixture() {
      if (com.badlogic.gdx.Gdx.app == null) {
        com.badlogic.gdx.Gdx.app = org.mockito.Mockito.mock(com.badlogic.gdx.Application.class);
      }
      zone.map = map; map.zones.add(zone);
    }
    int unit(boolean player, int x, int y, int size) {
      int id = world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type = player
          ? com.riiablo.engine.server.component.Class.Type.PLR : com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Size.class).create(id).size = size;
      world.getMapper(Velocity.class).create(id);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      if (player) world.getMapper(Player.class).create(id);
      else world.getMapper(Monster.class).create(id).set(new MonStats.Entry(), new MonStats2.Entry());
      if (player) collision.reservePlayerSpawn(id);
      else collision.reserveMonsterSpawn(id, x, y, size);
      return id;
    }
    int flags(int x, int y) { return zone.nativeCollisionFlagsWorld(x, y); }
    void position(int id, int x, int y) { world.getMapper(Position.class).get(id).position.set(x, y); }
    boolean free(int x, int y, int mask) {
      return collision.isFreeForMonsterSpawnMask(-1, zone, x, y, 1, mask);
    }
    @Override public void close() { world.dispose(); map.zones.clear(); com.badlogic.gdx.Gdx.app = previousApp; }
  }

  private static Map.Zone zone(int x, int y, int width, int height, int level) {
    Map.Zone zone = new Map.Zone();
    zone.x = x; zone.y = y; zone.width = width; zone.height = height;
    zone.flags = new byte[width * height];
    zone.level = new Levels.Entry(); zone.level.Id = level;
    return zone;
  }

  @Test void overlappingUnitDeletionClearsBitsAndEveryRefreshLeavesThemCleared() {
    try (Fixture f = new Fixture()) {
      int first = f.unit(false, 20, 20, 3);
      f.unit(false, 20, 20, 3);
      f.world.process();
      assertEquals(0x1100, f.flags(20, 20));
      assertEquals(0x100, f.flags(21, 21));
      f.world.delete(first);
      assertTrue(f.free(20, 20, 0x1100), "pending deletion must publish before ECS flush");
      f.world.process();
      f.collision.refreshNow();
      f.collision.rebuildNow();
      assertEquals(0, f.flags(20, 20));
      assertEquals(0, f.flags(21, 21));
    }
  }

  @Test void successfulMoveClearsOverlappingSourceAndSameCellNeverReassertsIt() {
    try (Fixture f = new Fixture()) {
      int first = f.unit(false, 20, 20, 2);
      int second = f.unit(false, 20, 20, 2);
      assertTrue(f.collision.tryMove(first, 25, 20, 2));
      f.position(first, 25, 20);
      assertEquals(0, f.flags(20, 20));
      assertEquals(0x1100, f.flags(25, 20));
      assertTrue(f.collision.tryMove(second, 20, 20, 2));
      f.world.process();
      assertEquals(0, f.flags(20, 20));
      assertTrue(f.collision.isFreeForPath(first, -1, 20, 20, 2));
      assertEquals(0, f.collision.pathBlockers(first, 20, 20, 2).size);
    }
  }

  @Test void rejectedMoveRestoresSourceAndKeepsDestinationWithoutMovingPosition() {
    try (Fixture f = new Fixture()) {
      int mover = f.unit(false, 20, 20, 2);
      f.unit(false, 25, 20, 3);
      assertFalse(f.collision.tryMove(mover, 25, 20, 2));
      assertEquals(0x1100, f.flags(20, 20));
      assertEquals(0x1100, f.flags(25, 20));
      assertEquals(20f, f.world.getMapper(Position.class).get(mover).position.x);
      assertFalse(f.collision.isFreeForPath(mover, -1, 25, 20, 2));
    }
  }

  @Test void deadMonsterCorpseRevivalAndRemovalUpdateSameSharedGrid() {
    try (Fixture f = new Fixture()) {
      int id = f.unit(false, 20, 20, 3);
      f.collision.markMonsterDeadCollision(id);
      f.world.getMapper(Velocity.class).remove(id);
      assertEquals(0x8000, f.flags(20, 20));
      assertEquals(0x8000, f.flags(21, 21));
      f.world.getMapper(Monster.class).get(id).nativeCorpseCollision = false;
      f.world.getMapper(Monster.class).get(id).nativeRestoredCollisionPattern = true;
      f.world.getMapper(Velocity.class).create(id);
      f.collision.reserveMonsterSpawn(id, 20, 20, 3);
      assertEquals(0x1100, f.flags(20, 20));
      assertEquals(0x100, f.flags(21, 21));
      f.world.process();
      f.world.delete(id); f.world.process();
      assertEquals(0, f.flags(20, 20));
      assertEquals(0, f.flags(21, 21));
    }
  }

  @Test void playerCorpseStaysInDeathZoneAfterOwnerReturnsElsewhere() {
    try (Fixture f = new Fixture()) {
      int player = f.unit(true, 20, 20, 2);
      f.world.getMapper(PlayerCorpse.class).create(player);
      int corpse = f.world.create();
      f.world.getMapper(Position.class).create(corpse).position.set(20, 20);
      f.world.getMapper(MapWrapper.class).create(corpse).set(f.map, f.zone);
      f.world.getMapper(PlayerCorpse.class).create(corpse);
      f.world.getMapper(Corpse.class).create(corpse);
      f.collision.reservePlayerCorpse(player, corpse);
      assertEquals(0x8000, f.flags(20, 20));
      assertEquals(0x8000, f.flags(21, 21));
      f.position(player, 30, 30);
      f.collision.refreshNow();
      assertEquals(0x8000, f.flags(20, 20));
      assertEquals(0, f.flags(30, 30));
      f.world.process();
      f.world.getMapper(Corpse.class).remove(corpse);
      assertTrue(f.free(20, 20, 0x8000));
    }
  }

  @Test void largeFrogHidesByCenterClearAndFieldChangeWithNativeResiduals() {
    try (Fixture f = new Fixture()) {
      int id = f.unit(false, 20, 20, 3);
      Monster monster = f.world.getMapper(Monster.class).get(id);
      monster.frogNoPresence = true; monster.frogSmallPresence = true;
      f.collision.refreshNow();
      assertEquals(0, f.flags(20, 20));
      assertEquals(0x1100, f.flags(21, 20));
      assertEquals(0x100, f.flags(21, 21));
      assertTrue(f.collision.tryMove(id, 20, 20, 1));
      assertEquals(0, f.flags(20, 20));
      assertTrue(f.collision.tryMove(id, 25, 20, 1));
      f.position(id, 25, 20);
      assertEquals(0x1000, f.flags(21, 20), "old cross MONSTER cleared, NO_PATH retained");
      assertEquals(0x100, f.flags(21, 21), "old BIG corners survive SMALL pattern removal");
      assertEquals(0x100, f.flags(25, 20));
      monster.frogNoPresence = false;
      f.collision.reserveMonsterSpawn(id, 25, 20, 1);
      assertEquals(0x1100, f.flags(25, 20));
      assertEquals(0x1000, f.flags(21, 20));
    }
  }

  @Test void petAndOrdinaryPatternsShareMarkersButUseDifferentMovementMasks() {
    try (Fixture f = new Fixture()) {
      int player = f.unit(true, 15, 20, 2);
      int ordinary = f.unit(false, 15, 25, 2);
      int pet = f.unit(false, 25, 25, 3);
      f.world.getMapper(Monster.class).get(pet).monstats.inTown = true;
      f.collision.reserveMonsterSpawn(pet, 25, 25, 3);
      assertEquals(0x2100, f.flags(25, 25));
      assertEquals(0x100, f.flags(26, 26));
      assertTrue(f.collision.isFreeForPath(player, -1, 25, 25, 2));
      assertFalse(f.collision.isFreeForPath(ordinary, -1, 25, 25, 2));
    }
  }

  @Test void missileAndItemBitsMoveAndClearOnPendingDeletionOrComponentRemoval() {
    try (Fixture f = new Fixture()) {
      int missile = f.world.create();
      f.world.getMapper(Position.class).create(missile).position.set(20, 20);
      f.world.getMapper(MapWrapper.class).create(missile).set(f.map, f.zone);
      Missile component = f.world.getMapper(Missile.class).create(missile);
      component.authoritative = true;
      component.missile = new com.riiablo.codec.excel.Missiles.Entry();
      component.missile.Collision = true; component.missile.Size = 3;
      f.collision.reserveMissileSpawn(missile);
      assertEquals(0x40, f.flags(21, 21));
      f.position(missile, 25, 25);
      assertTrue(f.free(21, 21, 0x40));
      assertEquals(0x40, f.flags(26, 26));
      f.world.delete(missile);
      assertTrue(f.free(26, 26, 0x40));
      int item = f.world.create();
      f.world.getMapper(Position.class).create(item).position.set(20, 20);
      f.world.getMapper(MapWrapper.class).create(item).set(f.map, f.zone);
      f.world.getMapper(Item.class).create(item);
      f.collision.reserveNativeCollision(item);
      assertFalse(f.free(20, 20, 0x200));
      f.world.getMapper(Item.class).remove(item);
      assertTrue(f.free(20, 20, 0x200));
    }
  }

  @Test void seamFootprintWritesAndClearsNeighborGridButNeverDetachedOverlap() {
    try (Fixture f = new Fixture()) {
      Map.Zone neighbor = zone(40, 10, 30, 30, 110);
      neighbor.map = f.map; f.map.zones.add(neighbor);
      f.map.addWarpDestinationOverride(109, 0, 110);
      f.zone.setNativeSeamAnchor(0, 39, 20);
      Map.Zone detached = zone(40, 10, 30, 30, 1);
      detached.map = f.map; f.map.zones.add(detached);
      int id = f.unit(false, 39, 20, 3);
      assertEquals(0x1100, neighbor.nativeCollisionFlagsWorld(40, 20));
      assertEquals(0, detached.nativeCollisionFlagsWorld(40, 20));
      assertTrue(f.collision.tryMove(id, 41, 20, 3)); f.position(id, 41, 20);
      f.world.getMapper(MapWrapper.class).get(id).set(f.map, neighbor);
      f.collision.refreshNow();
      assertEquals(0, f.flags(39, 20));
      assertEquals(0x1100, neighbor.nativeCollisionFlagsWorld(41, 20));
      f.world.process(); f.world.delete(id); f.world.process();
      assertEquals(0, neighbor.nativeCollisionFlagsWorld(40, 20));
    }
  }

  @Test void teleportBetweenDetachedZonesUpdatesOldAndDestinationBitsImmediately() {
    try (Fixture f = new Fixture()) {
      int id = f.unit(true, 20, 20, 2);
      Map.Zone destination = zone(100, 100, 30, 30, 1);
      destination.map = f.map; f.map.zones.add(destination);
      f.world.getMapper(MapWrapper.class).get(id).set(f.map, destination);
      f.position(id, 110, 110);
      f.collision.reserveNativeCollision(id);
      assertEquals(0, f.flags(20, 20));
      assertEquals(0x1080, destination.nativeCollisionFlagsWorld(110, 110));
      f.collision.refreshNow();
      assertEquals(0x1080, destination.nativeCollisionFlagsWorld(110, 110));
    }
  }

  @Test void deadColRetainsLivePatternInsteadOfAddingServerCorpseBox() {
    try (Fixture f = new Fixture()) {
      int id = f.unit(false, 20, 20, 3);
      f.world.getMapper(Monster.class).get(id).monstats2.deadCol = true;
      f.world.getMapper(UnitLifecycle.class).create(id).transition(UnitLifecycle.Phase.DEATH);
      f.world.getMapper(Corpse.class).create(id);
      f.collision.refreshNow();
      assertEquals(0x1100, f.flags(20, 20));
      assertEquals(0x100, f.flags(21, 21));
      assertTrue(f.free(20, 20, 0x8000));
    }
  }

  @Test void roomGridReplacementDoesNotClearNewTerrainThroughOldFootprint() {
    try (Fixture f = new Fixture()) {
      int id = f.unit(true, 20, 20, 2);
      int generation = f.zone.nativeCollisionGeneration();
      f.zone.setNativeTileGrid(new com.riiablo.drlg.TileGrid(8, 8), 0);
      assertNotEquals(generation, f.zone.nativeCollisionGeneration());
      f.zone.or(10, 10, 0x80); // New terrain shares the former PLAYER bit at world (20,20).
      f.position(id, 25, 25);
      f.collision.refreshNow();
      assertEquals(0x80, f.flags(20, 20));
      assertEquals(0x1080, f.flags(25, 25));
    }
  }

  @Test void objectDeletionDoesNotClearReplacementNeighborGrid() {
    try (Fixture f = new Fixture()) {
      Map.Zone neighbor = zone(40, 10, 30, 30, 110);
      neighbor.map = f.map; f.map.zones.add(neighbor);
      f.map.addWarpDestinationOverride(109, 0, 110);
      f.zone.setNativeSeamAnchor(0, 39, 20);
      int id = f.world.create();
      com.riiablo.codec.excel.Objects.Entry door = new com.riiablo.codec.excel.Objects.Entry();
      door.SizeX = door.SizeY = 3; door.IsDoor = true; door.BlockMissile = true;
      door.HasCollision = new boolean[8]; door.HasCollision[0] = true;
      f.world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = door;
      f.world.getMapper(Position.class).create(id).position.set(39, 20);
      f.world.getMapper(MapWrapper.class).create(id).set(f.map, f.zone);
      f.world.process();
      assertEquals(0x804, neighbor.nativeCollisionFlagsWorld(40, 20));
      neighbor.setNativeTileGrid(new com.riiablo.drlg.TileGrid(8, 8), 0);
      neighbor.setNativeCollisionMask(40, 20, 0x804);
      assertEquals(0x804, neighbor.nativeCollisionFlagsWorld(40, 20));
      f.world.delete(id);
      f.free(39, 20, 0x804);
      assertEquals(0, f.flags(39, 20));
      assertEquals(0x804, neighbor.nativeCollisionFlagsWorld(40, 20),
          "old object footprint cannot clear the replacement neighbor terrain");
    }
  }
  @Test void objectFootprintAcrossSeamClearsBothGridsOnSameTickDeletion() {
    try (Fixture f = new Fixture()) {
      Map.Zone neighbor = zone(40, 10, 30, 30, 110);
      neighbor.map = f.map; f.map.zones.add(neighbor);
      f.map.addWarpDestinationOverride(109, 0, 110);
      f.zone.setNativeSeamAnchor(0, 39, 20);
      int id = f.world.create();
      com.riiablo.codec.excel.Objects.Entry door = new com.riiablo.codec.excel.Objects.Entry();
      door.SizeX = door.SizeY = 3; door.IsDoor = true; door.BlockMissile = true;
      door.HasCollision = new boolean[8]; door.HasCollision[0] = true;
      f.world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = door;
      f.world.getMapper(Position.class).create(id).position.set(39, 20);
      f.world.getMapper(MapWrapper.class).create(id).set(f.map, f.zone);
      f.world.process();
      assertEquals(0x804, f.flags(39, 20));
      assertEquals(0x804, neighbor.nativeCollisionFlagsWorld(40, 20));
      f.world.delete(id);
      assertTrue(f.free(40, 20, 0x804));
      assertEquals(0, f.flags(39, 20));
      assertEquals(0, neighbor.nativeCollisionFlagsWorld(40, 20));
    }
  }
}
