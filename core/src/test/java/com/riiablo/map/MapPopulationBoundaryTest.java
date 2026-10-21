package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.riiablo.Files;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlTypes;
import com.riiablo.codec.excel.MonStats;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MapPopulationBoundaryTest {
  @Test void layoutAndQuestPlacementsNeedNoWorldFactoryOrSocket() throws Exception {
    Application previous = Gdx.app;
    Gdx.app = mock(Application.class);
    Files files = mock(Files.class);
    LvlTypes types = mock(LvlTypes.class);
    MonStats monsters = mock(MonStats.class);
    setTable(files, "LvlTypes", types);
    setTable(files, "monstats", monsters);
    MonStats.Entry raven = new MonStats.Entry();
    raven.hcIdx = 267;
    raven.Id = "bloodraven";
    when(monsters.get("bloodraven")).thenReturn(raven);
    Map map = new Map(7, 2, new MapResources(files, null, null));
    try {
      Levels.Entry level = new Levels.Entry();
      level.Id = 17;
      level.LevelName = "Burial Grounds";
      level.SizeX = new int[] {8};
      level.SizeY = new int[] {16};
      BaseMapBuilderD2MOD builder = new BaseMapBuilderD2MOD(new MapResources(files, null, null));
      Map.Zone zone = builder.createZoneWithGenerator(map, level, 2, -40, 80);
      assertEquals(40, zone.width());
      assertEquals(80, zone.height());
      assertEquals(-40, zone.x());
      assertEquals(80, zone.y());
      assertEquals(0, zone.entities.size);

      Field burial = Act1MapBuilderD2MOD.class.getDeclaredField("burialGroundsId");
      burial.setAccessible(true);
      burial.setInt(map.act1Builder(), level.Id);
      Method spawn = Act1MapBuilderD2MOD.class.getDeclaredMethod("spawnBloodRaven", Map.Zone.class);
      spawn.setAccessible(true);
      spawn.invoke(map.act1Builder(), zone);
      spawn.invoke(map.act1Builder(), zone);
      assertEquals(1, map.populationPlan().monsters().size(), "quest placement is idempotent");
      MapPopulationPlan.Monster placement = map.populationPlan().monsters().get(0);
      assertSame(zone, placement.zone);
      assertEquals(267, placement.monsterId);
      assertEquals(-20, placement.x);
      assertEquals(120, placement.y);
      assertEquals(0, zone.entities.size, "generation must not allocate ECS entities");
    } finally { map.dispose(); Gdx.app = previous; }
  }

  @Test void assemblyPreservesOrderCoordinatesAndDoesNotReplayPlacements() {
    Map map = new Map(7, 0);
    Map.Zone zone = zone(map);
    MapPopulationPort factory = mock(MapPopulationPort.class);
    MapManager assembly = assembly(map, factory);
    zone.queueMonster(10, -2.25f, 9.5f);
    zone.queueMonster(11, 3.75f, -4f);
    verifyNoInteractions(factory);
    assembly.createPlannedMonsters();
    assembly.createPlannedMonsters();
    org.mockito.InOrder order = inOrder(factory);
    order.verify(factory).createMonster(10, -2.25f, 9.5f);
    order.verify(factory).createMonster(11, 3.75f, -4f);
    verifyNoMoreInteractions(factory);

    zone.queueMonster(12, 1, 2);
    assembly.createPlannedMonsters();
    verify(factory).createMonster(12, 1, 2);
    MapPopulationPlan old = map.populationPlan();
    map.dispose();
    assertNotSame(old, map.populationPlan());
    zone(map).queueMonster(13, 4, 5);
    assembly.createPlannedMonsters();
    verify(factory).createMonster(13, 4, 5);
  }

  @Test void failedAssemblyResumesWithoutDuplicatingEarlierAllocations() {
    Map map = new Map(7, 0);
    Map.Zone zone = zone(map);
    zone.queueMonster(10, 1, 2);
    zone.queueMonster(11, 3, 4);
    MapPopulationPort factory = mock(MapPopulationPort.class);
    when(factory.createMonster(11, 3, 4)).thenThrow(new IllegalStateException("allocation failed"))
        .thenReturn(20);
    MapManager assembly = assembly(map, factory);
    assertThrows(IllegalStateException.class, assembly::createPlannedMonsters);
    assembly.createPlannedMonsters();
    verify(factory, times(1)).createMonster(10, 1, 2);
    verify(factory, times(2)).createMonster(11, 3, 4);
  }

  @Test void remoteClientDoesNotProduceLocalMonsterPlacements() {
    Map map = new Map(7, 0);
    map.configureGeneration(true, true);
    zone(map).queueMonster(10, 1, 2);
    assertTrue(map.populationPlan().monsters().isEmpty());
  }

  @Test void warpLookupUsesBoundMetadataAndIgnoresRemovedEntities() {
    Map map = new Map(7, 0);
    Map.Zone zone = zone(map);
    zone.addWarp(12);
    assertEquals(-1, zone.findWarp(5));
    map.bindWarpIndex(id -> id == 12 ? 5 : Integer.MIN_VALUE);
    assertEquals(12, zone.findWarp(5));
    zone.removeWarp(12);
    assertEquals(-1, zone.findWarp(5));
    assertFalse(zone.getEntities().contains(12));
  }

  @Test void dataBoundsRetainNormalFallbackAndNativeGroupMinimums() {
    assertEquals(99, MapDataPort.value(null, 2, 99));
    assertEquals(99, MapDataPort.value(new int[0], 2, 99));
    assertEquals(10, MapDataPort.value(new int[] {10, 20, 30}, -10, 99));
    assertEquals(30, MapDataPort.value(new int[] {10, 20, 30}, 10, 99));
    assertEquals(10, MapDataPort.value(new int[] {10, 20}, 2, 99));
    assertEquals(1, MapDataPort.levelSizeX(null, 2, -1));
    MonStats.Entry monster = new MonStats.Entry();
    monster.MinGrp = 4;
    monster.MaxGrp = 2;
    assertEquals(4, MapDataPort.maxGroup(monster));
    assertEquals(1, MapDataPort.minGroup(null));
  }

  private static Map.Zone zone(Map map) {
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    return zone;
  }

  private static MapManager assembly(Map map, MapPopulationPort factory) {
    MapManager manager = new MapManager();
    manager.map = map;
    manager.factory = factory;
    return manager;
  }

  private static void setTable(Files files, String name, Object value) throws Exception {
    Field field = Files.class.getField(name);
    field.setAccessible(true);
    field.set(files, value);
  }
}
