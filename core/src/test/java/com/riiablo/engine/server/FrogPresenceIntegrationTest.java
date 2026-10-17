package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real factory/collision/map implementation; only entity construction is minimal. */
class FrogPresenceIntegrationTest extends RiiabloTest {
  @Test void genericSubmergeIsNotNoPresenceAndSmallSpawnDoesNotEmergeAgain() {
    try (Fixture f = new Fixture()) {
      assertFalse(f.factory.restoreFrogPresence(f.actor));
      assertTrue(f.factory.submergeMonster(f.actor));
      f.collisions.refreshNow();
      assertTrue(f.collisions.hasPresence(f.actor), "SrvSt51 alone does not clear presence");
      assertTrue(f.factory.enterFrogHidden(f.actor));
      assertFalse(f.collisions.hasPresence(f.actor));
      assertTrue(f.collisions.isFreeForFrogEmergence(-1, 30, 30, 2));
      Position precision = f.world.getMapper(Position.class).get(f.actor);
      precision.initializeNativeCell(30, 30);
      assertTrue(f.factory.restoreFrogPresence(f.actor));
      assertEquals(new Vector2(30, 30), precision.position);
      assertFalse(precision.hasNativePrecision(), "same-position float landing must invalidate raw");
      assertTrue(f.collisions.hasPresence(f.actor));
      assertFalse(f.collisions.isFreeForFrogEmergence(-1, 30, 30, 2));
      assertFalse(f.factory.restoreFrogPresence(f.actor));
    }
  }

  @Test void ordinaryNoPathBlocksLandingButPetPresenceIsNotInMask1c09() {
    for (boolean pet : new boolean[] {false, true}) {
      try (Fixture f = new Fixture()) {
        f.factory.enterFrogHidden(f.actor);
        int blocker = f.unit(45, 45);
        if (pet) f.world.getMapper(NativeUnitFlags.class).get(blocker).set(NativeUnitFlags.IS_REVIVE);
        f.world.process();
        f.world.getMapper(Position.class).get(blocker).position.set(30, 30);
        f.collisions.refreshNow();
        assertEquals(pet, f.collisions.isFreeForFrogEmergence(f.actor, 30, 30, 2));
        Position precision = f.world.getMapper(Position.class).get(f.actor);
        precision.initializeNativeCell(30, 30);
        assertTrue(f.factory.restoreFrogPresence(f.actor));
        Vector2 pos = f.world.getMapper(Position.class).get(f.actor).position;
        assertEquals(pet, pos.equals(new Vector2(30, 30)), "ordinary units require a free relocation");
        assertTrue(f.collisions.hasPresence(f.actor));
        // Restore without querying raw first: invalidation must survive a matching projection.
        pos.set(30, 30);
        assertFalse(precision.hasNativePrecision());
      }
    }
  }

  @Test void blockedSourceRoomCannotEscapeIntoAnUnlinkedRoomAndFailureStillSetsFlags() {
    try (Fixture f = new Fixture()) {
      Map.RoomEx room = f.zone.addRoomEx(20, 20, 21, 21);
      Map.RoomEx neighbor = f.zone.addRoomEx(41, 20, 40, 21);
      room.setAdjacentRoomIds(new int[0]);
      neighbor.setAdjacentRoomIds(new int[0]);
      f.factory.enterFrogHidden(f.actor);
      for (int y = 20; y < 41; y++) for (int x = 20; x < 41; x++)
        f.zone.or(x, y, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
      Position precision = f.world.getMapper(Position.class).get(f.actor);
      precision.initializeNativeCell(30, 30);
      assertFalse(f.factory.restoreFrogPresence(f.actor));
      assertTrue(precision.hasNativePrecision(), "failed search does not commit a float landing");
      assertTrue(f.world.getMapper(Monster.class).get(f.actor).frogNoPresence);
      assertEquals(NativeUnitFlags.MONSTER_TARGET,
          f.world.getMapper(NativeUnitFlags.class).get(f.actor).flags() & NativeUnitFlags.MONSTER_TARGET);
      assertEquals(new Vector2(30, 30), f.world.getMapper(Position.class).get(f.actor).position);
      assertFalse(f.collisions.hasPresence(f.actor));
    }
  }

  @Test void successfulSearchCanUseNativeNeighborDespiteFalseFallbackArgument() {
    try (Fixture f = new Fixture()) {
      Map.RoomEx room = f.zone.addRoomEx(20, 20, 21, 21);
      Map.RoomEx neighbor = f.zone.addRoomEx(41, 20, 40, 21);
      room.setAdjacentRoomIds(new int[] {neighbor.id});
      neighbor.setAdjacentRoomIds(new int[] {room.id});
      f.factory.enterFrogHidden(f.actor);
      for (int y = 20; y < 41; y++) for (int x = 20; x < 41; x++)
        f.zone.or(x, y, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
      assertTrue(f.factory.restoreFrogPresence(f.actor));
      assertTrue(neighbor.contains(f.world.getMapper(Position.class).get(f.actor).position.x,
          f.world.getMapper(Position.class).get(f.actor).position.y));
    }
  }

  @Test void nativeSizeOneChecksCenterNotTheAdjacentPathPattern() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 31, 30, 1);
    assertTrue(grid.isFreeWithSize(0, 30, 30, 1, null));
    assertFalse(grid.isFreeWithSize(0, 30, 30, 2, null));
    grid.put(1, 31, 31, 1);
    assertTrue(grid.isFreeWithSize(0, 30, 30, 2, null));
    assertFalse(grid.isFreeWithSize(0, 30, 30, 3, null));
  }

  private static class Fixture implements AutoCloseable {
    final Map map = new Map(1, 0);
    final Map.Zone zone = map.createSyntheticArena(2, 2, 2);
    final ServerEntityFactory factory = new ServerEntityFactory();
    final DynamicUnitCollisionSystem collisions = new DynamicUnitCollisionSystem();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), factory, collisions)
        .build().register("factory", factory).register("map", map)
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    final int actor;
    Fixture() { actor = unit(30, 30); world.process(); }
    int unit(int x, int y) {
      int id = world.create();
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("frogdemon1");
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Class.class).create(id).type = Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Size.class).create(id).size = 2;
      world.getMapper(Velocity.class).create(id);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      world.getMapper(NativeUnitFlags.class).create(id).reset().set(NativeUnitFlags.MONSTER_TARGET);
      return id;
    }
    @Override public void close() { world.dispose(); map.dispose(); }
  }
}
