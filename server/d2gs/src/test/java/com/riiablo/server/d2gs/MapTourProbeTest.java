package com.riiablo.server.d2gs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapTourProbeTest {
  @Test public void narrowCrossSectionRequiresBothWallsAndAClearFootprint() {
    assertEquals(3, MapTourProbe.narrowCenterSpan(offset -> Math.abs(offset) < 2));
    assertEquals(5, MapTourProbe.narrowCenterSpan(offset -> Math.abs(offset) < 3));
    assertEquals(1, MapTourProbe.narrowCenterSpan(offset -> offset == 0));
    assertEquals(0, MapTourProbe.narrowCenterSpan(offset -> true));
    assertEquals(0, MapTourProbe.narrowCenterSpan(offset -> offset > -2));
    assertEquals(0, MapTourProbe.narrowCenterSpan(offset -> offset < 2));
    assertEquals(0, MapTourProbe.narrowCenterSpan(offset -> offset != 0));
  }

  @Test public void narrowMovementRejectsStallsJumpsStaleTicksAndUnboundedSpace() {
    MapTourProbe.Sample a = new MapTourProbe.Sample(), b = new MapTourProbe.Sample();
    a.level = b.level = 29; a.nativeTerrain = b.nativeTerrain = true;
    a.unitSize = b.unitSize = 2; a.maximumMovementSpeed = b.maximumMovementSpeed = 10;
    a.narrowSpanX = b.narrowSpanX = 3;
    a.position = new com.badlogic.gdx.math.Vector2(10, 10);
    b.position = new com.badlogic.gdx.math.Vector2(10, 12);
    a.tick = 10; b.tick = 15;
    assertTrue(MapTourProbe.narrowProgress(a, b, true));
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, false));
    b.position.set(10, 10);
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.position.set(10, 20);
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.position.set(10, 12); b.tick = 10;
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.tick = 21;
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.tick = 15; b.narrowSpanX = 0;
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.narrowSpanX = 3; b.blocked = true;
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
    b.blocked = false; b.level = 30;
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, true));
  }

  @Test public void diagonalNarrowProgressUsesPerpendicularProjection() {
    MapTourProbe.Sample a = new MapTourProbe.Sample(), b = new MapTourProbe.Sample();
    a.level = b.level = 29; a.nativeTerrain = b.nativeTerrain = true;
    a.unitSize = b.unitSize = 2; a.maximumMovementSpeed = b.maximumMovementSpeed = 20;
    a.narrowSpanDiagonalMinus = b.narrowSpanDiagonalMinus = 3;
    a.narrowSpanDiagonalPlus = b.narrowSpanDiagonalPlus = 3;
    a.position = new com.badlogic.gdx.math.Vector2(10, 10);
    b.position = new com.badlogic.gdx.math.Vector2(12, 12);
    a.tick = 10; b.tick = 15;
    assertTrue(MapTourProbe.narrowProgress(a, b, 3));
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, 2));
    b.position.set(12, 8);
    assertTrue(MapTourProbe.narrowProgress(a, b, 2));
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, 3));
    b.position.set(12, 10);
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, 2));
    org.junit.Assert.assertFalse(MapTourProbe.narrowProgress(a, b, 3));
  }

  @Test public void seamDoorMustFinishOpeningBeforeItsNextFrontierIsPlanned() {
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder().build());
    try {
      int doorId = world.create();
      com.riiablo.engine.server.component.Object door = world.getMapper(
          com.riiablo.engine.server.component.Object.class).create(doorId);
      door.base = new com.riiablo.codec.excel.Objects.Entry();
      door.base.Id = 23; door.base.IsDoor = true; door.base.OperateFn = 8;
      door.mode = com.riiablo.engine.Engine.Object.MODE_ON;
      com.riiablo.engine.server.component.CofReference cof = world.getMapper(
          com.riiablo.engine.server.component.CofReference.class).create(doorId);
      cof.mode = com.riiablo.engine.Engine.Object.MODE_OP;
      world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(doorId)
          .sequence(com.riiablo.engine.Engine.Object.MODE_OP, com.riiablo.engine.Engine.Object.MODE_ON);
      world.process();
      org.junit.Assert.assertFalse("27->26: accepted interaction persists ON while OP still blocks the doorway",
          MapTourProbe.doorOpen(world, doorId));
      cof.mode = com.riiablo.engine.Engine.Object.MODE_ON;
      org.junit.Assert.assertFalse("an active sequence must finish before the planner continues",
          MapTourProbe.doorOpen(world, doorId));
      world.getMapper(com.riiablo.engine.server.component.Sequence.class).remove(doorId);
      world.process();
      assertTrue(MapTourProbe.doorOpen(world, doorId));
      door.mode = com.riiablo.engine.Engine.Object.MODE_NU;
      org.junit.Assert.assertFalse("a stale ON COF cannot prove a reopened door", MapTourProbe.doorOpen(world, doorId));
      world.delete(doorId); world.process();
      org.junit.Assert.assertFalse(MapTourProbe.doorOpen(world, doorId));
    } finally { world.dispose(); }
  }
  @Test public void meleeProbeUsesNativeBarrierAndSelectedTargetInsteadOfGroundWalkRay() throws Exception {
    com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    com.riiablo.Files previousFiles = com.riiablo.Riiablo.files;
    com.badlogic.gdx.files.FileHandle previousHome = com.riiablo.Riiablo.home;
    com.riiablo.mpq.MPQFileHandleResolver previousMpqs = com.riiablo.Riiablo.mpqs;
    com.badlogic.gdx.backends.headless.HeadlessApplication app = new com.badlogic.gdx.backends.headless.HeadlessApplication(
        new com.badlogic.gdx.ApplicationAdapter() {});
    final boolean[] barrier = {false};
    final boolean[] nativeSeam = {false};
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0) {
      @Override public boolean isNativeSeamConnection(com.riiablo.map.Map.Zone source, com.riiablo.map.Map.Zone destination) {
        return nativeSeam[0] && source.level.Id == 23 && destination.level.Id == 24;
      }
      @Override public int playerFlyingFlags(int x, int y) {
        return barrier[0] && x == 5 ? com.riiablo.map.DT1.Tile.FLAG_BLOCK_JUMP : 0;
      }
      @Override public int flags(int x, int y) {
        return com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK;
      }
    };
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder().build());
    try {
      com.riiablo.Riiablo.home = com.riiablo.util.InstallationFinder.getInstance().defaultHomeDir();
      com.riiablo.Riiablo.mpqs = new com.riiablo.mpq.MPQFileHandleResolver();
      com.riiablo.Riiablo.files = new com.riiablo.Files();
      com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone();
      zone.level = new com.riiablo.codec.excel.Levels.Entry();
      zone.level.Id = 23;
      int player = world.create(), nearest = world.create(), selected = world.create();
      for (int id : new int[] {player, nearest, selected}) {
        world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).position
            .set(id == player ? 2 : id == nearest ? 4 : 7, 2);
        world.getMapper(com.riiablo.engine.server.component.Size.class).create(id).size = 1;
        world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(id).set(map, zone);
        if (id == player) continue;
        com.riiablo.engine.server.component.Monster monster = world.getMapper(
            com.riiablo.engine.server.component.Monster.class).create(id);
        monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
        monster.monstats.killable = true;
        com.riiablo.attributes.Attributes attrs = com.riiablo.attributes.Attributes.obtainStandard();
        attrs.base().put(com.riiablo.attributes.Stat.hitpoints, 10f);
        attrs.reset();
        world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class).create(id).attrs = attrs;
      }
      world.process();
      assertEquals(nearest, MapTourProbe.blocker(world, map, player, null).warp);
      com.riiablo.map.Map.Zone neighbor = new com.riiablo.map.Map.Zone();
      neighbor.level = new com.riiablo.codec.excel.Levels.Entry(); neighbor.level.Id = 24;
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(selected).set(map, neighbor);
      org.junit.Assert.assertNull("a nearby unit in an unrelated map is not a seam blocker",
          MapTourProbe.blocker(world, map, player, null, selected));
      nativeSeam[0] = true;
      assertEquals("27->26: the near-side player must be able to select a hostile native-seam blocker",
          selected, MapTourProbe.blocker(world, map, player, null, selected).warp);
      nativeSeam[0] = false;
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(selected).set(map, zone);
      assertEquals("recheck the selected target even when another monster is closer", selected,
          MapTourProbe.blocker(world, map, player, null, selected).warp);
      barrier[0] = true;
      org.junit.Assert.assertNull("missile barrier still rejects an otherwise reachable target",
          MapTourProbe.blocker(world, map, player, null, selected));
      assertEquals(nearest, MapTourProbe.blocker(world, map, player, null).warp);
    } finally {
      world.dispose();
      app.exit();
      com.badlogic.gdx.Gdx.app = previousApp;
      com.riiablo.Riiablo.files = previousFiles;
      com.riiablo.Riiablo.home = previousHome;
      com.riiablo.Riiablo.mpqs = previousMpqs;
    }
  }

  @Test public void blockedDoorApproachCannotRecursivelySelectTheSameDoor() {
    assertTrue(MapTourProbe.mayPlanDoor("WARP_WALK", 0));
    org.junit.Assert.assertFalse(MapTourProbe.mayPlanDoor("DOOR_APPROACH", 0));
    org.junit.Assert.assertFalse(MapTourProbe.mayPlanDoor("WARP_WALK", 32));
  }
  @Test public void monasterySegmentUsesTheOriginalTamoeRoadEntranceNotTheFirstDecorativeRoom() {
    com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone();
    zone.setPosition(9480, -880);
    assertEquals(new com.badlogic.gdx.math.Vector2(9615, -815), MapTourProbe.monasteryRoadEntrance(zone));
  }
  @Test public void blockerApproachPlanningDoesNotDisableProductionUnitOrTerrainCollision() throws Exception {
    com.riiablo.engine.server.DynamicUnitCollisionSystem collision = new com.riiablo.engine.server.DynamicUnitCollisionSystem();
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder().with(collision).build());
    try {
      com.riiablo.map.Map map = new com.riiablo.map.Map(12345, 0);
      java.lang.reflect.Field policy = com.riiablo.map.Map.class.getDeclaredField("generationPolicy");
      policy.setAccessible(true);
      policy.set(map, com.riiablo.map.MapGenerationPolicy.fromNativeToggle(false));
      com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone();
      byte[] flags = new byte[40 * 5];
      java.util.Arrays.fill(flags, (byte) 1);
      for (int x = 1; x < 39; x++) flags[2 * 40 + x] = 0;
      for (String field : new String[] {"width", "height", "tilesX", "tilesY", "flags"}) {
        Class<?> owner = com.riiablo.map.Map.Zone.class;
        java.lang.reflect.Field f = null;
        while (owner != null && f == null) {
          try { f = owner.getDeclaredField(field); }
          catch (NoSuchFieldException e) { owner = owner.getSuperclass(); }
        }
        if (f == null) throw new NoSuchFieldException(field);
        f.setAccessible(true);
        f.set(zone, "flags".equals(field) ? flags : "width".equals(field) ? 40
            : "height".equals(field) ? 5 : "tilesX".equals(field) ? 8 : 1);
      }
      zone.level = new com.riiablo.codec.excel.Levels.Entry();
      zone.level.Id = 55;
      java.lang.reflect.Field zonesField = com.riiablo.map.Map.class.getDeclaredField("zones");
      zonesField.setAccessible(true);
      ((com.badlogic.gdx.utils.Array<com.riiablo.map.Map.Zone>) zonesField.get(map)).add(zone);
      int player = world.create(), blocker = world.create();
      for (int id : new int[] {player, blocker}) {
        world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).position.set(id == player ? 2 : 14, 2);
        world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
        world.getMapper(com.riiablo.engine.server.component.Size.class).create(id).size = 1;
        world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type = com.riiablo.engine.server.component.Class.Type.PLR;
        world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(id).set(map, zone);
      }
      world.process();
      collision.refreshNow();
      org.junit.Assert.assertFalse(collision.isFreeForPath(zone, player, -1, 14, 2, 1));
      com.badlogic.gdx.math.Vector2 meleeApproach = MapTourProbe.meleeApproach(world, map, player, blocker, 0);
      org.junit.Assert.assertNotNull("an out-of-range blocker needs a real walking approach", meleeApproach);
      assertTrue(meleeApproach.x > 2 && meleeApproach.x < 14);
      assertTrue(collision.isFreeForPath(zone, player, -1, (int) meleeApproach.x, (int) meleeApproach.y, 1));
      java.util.List<com.badlogic.gdx.math.Vector2> approach = MapTourProbe.route(world, map, player,
          new com.badlogic.gdx.math.Vector2(35, 2));
      assertTrue(!approach.isEmpty());
      assertEquals("stop before the combined unit footprints, not the occupied goal", 12f,
          approach.get(approach.size() - 1).x, 0f);
      org.junit.Assert.assertFalse("planning must not release the blocker", collision.isFreeForPath(zone, player, -1, 14, 2, 1));
      int doorId = world.create();
      com.riiablo.engine.server.component.Object door = world.getMapper(
          com.riiablo.engine.server.component.Object.class).create(doorId);
      door.base = new com.riiablo.codec.excel.Objects.Entry();
      door.base.OperateFn = 8; door.base.IsDoor = true;
      world.getMapper(com.riiablo.engine.server.component.Interactable.class).create(doorId).range = 6;
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(doorId).position.set(35, 2);
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(doorId).set(map, zone);
      world.process();
      org.junit.Assert.assertNull("a dynamic route blockage must not select an unrelated closed door",
          MapTourProbe.door(world, map, player, new com.badlogic.gdx.math.Vector2(38, 2)));
      flags[2 * 40 + 35] = 1; // materialized closed door severs the static corridor
      MapTourProbe.Sample doorApproach = MapTourProbe.door(world, map, player, new com.badlogic.gdx.math.Vector2(38, 2));
      org.junit.Assert.assertNotNull("a distant monster may not hide the static door frontier", doorApproach);
      assertEquals(doorId, doorApproach.warp);
      assertTrue(doorApproach.target.x >= 28);
      org.junit.Assert.assertFalse(collision.isFreeForPath(zone, player, -1, 14, 2, 1));
      flags[2 * 40 + 25] = 1;
      assertTrue("static fallback may never cross a terrain wall", MapTourProbe.route(world, map, player,
          new com.badlogic.gdx.math.Vector2(35, 2)).isEmpty());
      flags[2 * 40 + 8] = 1;
      org.junit.Assert.assertNull("a combat approach cannot jump a terrain wall",
          MapTourProbe.meleeApproach(world, map, player, blocker, 0));
    } finally { world.dispose(); }
  }
  @Test public void combatClearingRejectsFriendlyNpcAndConvertedUnits() {
    com.riiablo.engine.server.component.Monster monster = new com.riiablo.engine.server.component.Monster();
    org.junit.Assert.assertFalse(MapTourProbe.hostileBlocker(monster));
    monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
    monster.monstats.killable = true;
    assertTrue(MapTourProbe.hostileBlocker(monster));
    monster.monstats.npc = true;
    org.junit.Assert.assertFalse(MapTourProbe.hostileBlocker(monster));
    monster.monstats.npc = false;
    monster.monstats.Align = 1;
    org.junit.Assert.assertFalse(MapTourProbe.hostileBlocker(monster));
    monster.monstats.Align = 0;
    monster.converted = true;
    org.junit.Assert.assertFalse(MapTourProbe.hostileBlocker(monster));
    monster.converted = false;
    monster.playerRevive = true;
    org.junit.Assert.assertFalse(MapTourProbe.hostileBlocker(monster));
  }
  @Test public void longRouteDoesNotTreatDistantMonsterSnapshotAsPermanentWall() {
    com.badlogic.gdx.math.Vector2 origin = new com.badlogic.gdx.math.Vector2(100, 100);
    assertTrue(MapTourProbe.usesLiveRouteOccupancy(origin, 124, 100));
    org.junit.Assert.assertFalse(MapTourProbe.usesLiveRouteOccupancy(origin, 125, 100));
    assertTrue(MapTourProbe.usesLiveRouteOccupancy(origin, 112, 112));
  }
  @Test public void warpArrivalAllowsProductionOneTileStopClick() {
    assertEquals(1f, MapTourProbe.arrivalToleranceSquared("WARP_WALK"), 0f);
    assertEquals(1f, MapTourProbe.arrivalToleranceSquared("DOOR_APPROACH"), 0f);
  }

  @Test public void ordinaryDoorApproachReservesTheProductionStopAllowance() {
    assertEquals(7f, MapTourProbe.doorApproachRange(6f), 0f);
  }

  @Test public void warpApproachRemainsInsideValidationRangeAfterArrivalError() {
    for (float interactionRange : new float[] {0f, 1f, 2f, 5f}) {
      float validationRange = Math.max(1f, interactionRange) + 2f;
      double arrivalError = Math.sqrt(MapTourProbe.arrivalToleranceSquared("WARP_WALK"));
      assertTrue(MapTourProbe.warpApproachRange(interactionRange) + arrivalError
          <= validationRange - 0.5f);
    }
  }
}
