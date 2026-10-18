package com.riiablo.map;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act2SewerWarpPairingTest extends RiiabloTest {
  @Test void real110fTownAndSewerEntrancesHaveDistinctReciprocalEndpoints() {
    Map map = new Map(7, 0);
    Map.Zone town = zone(Riiablo.files.Levels.get(40));
    Map.Zone sewer = zone(Riiablo.files.Levels.get(47));
    map.zones.add(town);
    map.zones.add(sewer);
    addMarkers(town, 47);
    addMarkers(sewer, 40);
    assertEquals(2, town.specials.size);
    assertEquals(2, sewer.specials.size);
    new Act2MapBuilderD2MOD().linkNativeWarpSpecials(map);
    java.util.Set<Integer> endpoints = new java.util.HashSet<>();
    for (DS1.Cell entrance : town.specials.values()) {
      int reverse = town.getWarp(entrance.id);
      assertTrue(reverse >= 0);
      assertTrue(endpoints.add(reverse), "two entrances must not share a landing");
      assertEquals(entrance.id, sewer.getWarp(reverse), "return must reach the original entrance");
    }
  }

  @Test void slotOrderControlsPairingRegardlessOfSpecialHashIteration() {
    Map map = new Map(7, 0);
    Map.Zone town = zone(level(40, new int[] {47, 50, 47}));
    Map.Zone sewer = zone(level(47, new int[] {48, 40, 40}));
    DS1.Cell first = marker(1), second = marker(2);
    sewer.specials.put(50, second);
    sewer.specials.put(2, first);
    assertSame(first, Act2MapBuilderD2MOD.findReverseWarp(map, town, marker(0), sewer));
    assertSame(second, Act2MapBuilderD2MOD.findReverseWarp(map, town, marker(2), sewer));
    sewer.specials.remove(50);
    assertNull(Act2MapBuilderD2MOD.findReverseWarp(map, town, marker(2), sewer),
        "a missing second endpoint must not silently reuse the first");
  }

  @Test void runtimeDestinationOverridesParticipateInRepeatedLinkOrder() {
    Map map = new Map(7, 0);
    Map.Zone town = zone(level(40, new int[] {47, 0, 0}));
    Map.Zone sewer = zone(level(47, new int[] {40, 0, 0}));
    map.addWarpDestinationOverride(40, 2, 47);
    map.addWarpDestinationOverride(47, 1, 40);
    DS1.Cell second = marker(1);
    sewer.specials.put(10, marker(0));
    sewer.specials.put(20, second);
    assertSame(second, Act2MapBuilderD2MOD.findReverseWarp(map, town, marker(2), sewer));
  }

  private static Map.Zone zone(Levels.Entry level) {
    Map.Zone zone = new Map.Zone();
    zone.level = level;
    zone.specials = new IntMap<>();
    return zone;
  }

  private static void addMarkers(Map.Zone zone, int destination) {
    for (int slot = 0; slot < zone.level.Vis.length; slot++) {
      if (zone.level.Vis[slot] == destination && zone.level.Warp[slot] >= 0) {
        zone.specials.put(slot * 19, marker(slot));
      }
    }
  }

  private static DS1.Cell marker(int slot) {
    DS1.Cell cell = new DS1.Cell();
    cell.mainIndex = (short) slot;
    cell.id = DT1.Tile.Index.create(Orientation.SPECIAL_10, slot, 0);
    return cell;
  }

  private static Levels.Entry level(int id, int[] vis) {
    Levels.Entry level = new Levels.Entry();
    level.Id = id;
    level.Vis = vis;
    level.Warp = new int[vis.length];
    return level;
  }
}
