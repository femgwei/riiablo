package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgTileInfoStrc;
import com.riiablo.engine.server.portal.TownPortalRegistry;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class NativeTownPortalAnchorTest {
  @Test
  void lutGholeinUsesPortalMarkerRatherThanZoneCenterOrFirstSpawn() {
    assertRetailAnchor(40, 35, 10, 11, 178, 53);
  }

  @Test
  void kurastDocksUsesPortalMarker() {
    assertRetailAnchor(75, 31, 13, 11, 158, 68);
  }

  @Test
  void pandemoniumFortressUsesPortalMarker() {
    assertRetailAnchor(103, 9, 8, 11, 48, 43);
  }

  @Test
  void harrogathWithoutPortalMarkerUsesNativeFirstRecordFallback() {
    assertRetailAnchor(109, 19, 4, 0, 98, 23);
  }

  @Test
  void noMatchUsesFirstRecordEvenWithMultipleOtherSpawnClasses() {
    D2DrlgLevel level = level(109);
    level.setPTileInfo(new D2DrlgTileInfoStrc[] {
        new D2DrlgTileInfoStrc(119, 204, 0),
        new D2DrlgTileInfoStrc(125, 210, 12)});
    level.setNTileInfo(2);
    Map.Zone zone = town();

    assertTrue(NativeTownPortalAnchor.capture(level, zone));
    assertEquals(new Vector2(-402, 723), zone.townPortalSpawn());
  }

  @Test
  void unscannedDefaultEntriesDoNotCreateAnAnchor() {
    Map.Zone zone = town();
    assertFalse(NativeTownPortalAnchor.capture(level(40), zone));
    assertNull(zone.townPortalSpawn());
  }

  @Test
  void nonTownDoesNotReceiveAnAnchor() {
    D2DrlgLevel level = level(41);
    level.setPTileInfo(new D2DrlgTileInfoStrc[] {new D2DrlgTileInfoStrc(135, 210, 11)});
    level.setNTileInfo(1);
    Map.Zone zone = new Map.Zone();
    assertFalse(NativeTownPortalAnchor.capture(level, zone));
    assertNull(zone.townPortalSpawn());
  }

  private static void assertRetailAnchor(int levelId, int tileX, int tileY, int index,
      int subtileX, int subtileY) {
    D2DrlgLevel level = level(levelId);
    D2DrlgTileInfoStrc marker = new D2DrlgTileInfoStrc(100 + tileX, 200 + tileY, index);
    if (index == 11) {
      // Ignore a non-portal spawn before the requested index, and entries
      // outside nTileInfo (the native array normally has spare capacity).
      level.setPTileInfo(new D2DrlgTileInfoStrc[] {
          new D2DrlgTileInfoStrc(101, 201, 0), marker,
          new D2DrlgTileInfoStrc(199, 299, 11)});
      level.setNTileInfo(2);
    } else {
      level.setPTileInfo(new D2DrlgTileInfoStrc[] {
          marker, new D2DrlgTileInfoStrc(199, 299, 11)});
      level.setNTileInfo(1);
    }
    Map.Zone zone = town();
    assertTrue(NativeTownPortalAnchor.capture(level, zone));
    assertEquals(new Vector2(-500 + subtileX, 700 + subtileY), zone.townPortalSpawn());
    assertEquals(zone.townPortalSpawn(), TownPortalRegistry.preferredTownPosition(zone));
  }

  private static D2DrlgLevel level(int id) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(id);
    level.getLevelCoords().setNPosX(100);
    level.getLevelCoords().setNPosY(200);
    return level;
  }

  private static Map.Zone town() {
    Map.Zone zone = new Map.Zone() {
      @Override public boolean isTown() { return true; }
    };
    zone.setPosition(-500, 700);
    return zone;
  }
}
