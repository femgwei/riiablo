package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.datatbls.D2LvlSubTxt;

class DrlgTileSubPresetUnitTest {
  @Test
  void copiesLvlSubObjectInNativeSubtileCoordinatesAndPreservesDs1Origin() {
    D2DrlgRoom room = new D2DrlgRoom();
    D2DrlgCoord group = group(2, 3, 6, 6);
    D2PresetUnit waypoint = unit(15, 20);

    D2PresetUnit copy = DrlgTileSub.copySubstitutionPresetUnit(
        room, null, waypoint, 7, 8, group, true);

    assertSame(copy, room.getPresetUnits());
    assertEquals(D2UnitTypes.UNIT_OBJECT, copy.getNUnitType());
    assertEquals(12, copy.getNIndex());
    assertEquals(40, copy.getNXpos());
    assertEquals(45, copy.getNYpos());
    assertTrue(copy.isDs1Raw());
    assertTrue(copy.isExternalEntity());
  }

  @Test
  void followsNativeStrictBoundsForLvlSubPresetUnits() {
    D2DrlgRoom room = new D2DrlgRoom();
    D2DrlgCoord group = group(2, 3, 6, 6);

    assertNull(DrlgTileSub.copySubstitutionPresetUnit(
        room, null, unit(10, 20), 7, 8, group, true));
    assertNull(DrlgTileSub.copySubstitutionPresetUnit(
        room, null, unit(40, 20), 7, 8, group, true));
    assertNull(room.getPresetUnits());
  }

  @Test
  void onlyWaypointLvlSubObjectsAreExposedToTheExternalEntityBridge() {
    D2LvlSubTxt waypoint = new D2LvlSubTxt();
    waypoint.setSzFile("Act1/Outdoors/Waypoint.ds1");
    D2LvlSubTxt smallWaypoint = new D2LvlSubTxt();
    smallWaypoint.setSzFile("Act1/Outdoors/WaySmall.ds1");
    D2LvlSubTxt border = new D2LvlSubTxt();
    border.setSzFile("Act1/Outdoors/BorderMiddle.ds1");

    assertTrue(DrlgTileSub.isWaypointSubstitution(waypoint));
    assertTrue(DrlgTileSub.isWaypointSubstitution(smallWaypoint));
    org.junit.jupiter.api.Assertions.assertFalse(DrlgTileSub.isWaypointSubstitution(border));

    D2PresetUnit copy = DrlgTileSub.copySubstitutionPresetUnit(
        new D2DrlgRoom(), null, unit(15, 20), 7, 8,
        group(2, 3, 6, 6), false);
    assertTrue(copy.isDs1Raw());
    org.junit.jupiter.api.Assertions.assertFalse(copy.isExternalEntity());
  }

  private static D2PresetUnit unit(int x, int y) {
    D2PresetUnit unit = new D2PresetUnit();
    unit.setNUnitType(D2UnitTypes.UNIT_OBJECT);
    unit.setNIndex(12);
    unit.setNMode(0);
    unit.setNXpos(x);
    unit.setNYpos(y);
    unit.setDs1Raw(true);
    return unit;
  }

  private static D2DrlgCoord group(int x, int y, int width, int height) {
    D2DrlgCoord group = new D2DrlgCoord();
    group.setNPosX(x);
    group.setNPosY(y);
    group.setNWidth(width);
    group.setNHeight(height);
    return group;
  }
}
