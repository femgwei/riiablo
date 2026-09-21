package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DrlgOutPlaceRoomFlagsTest {
  @Test
  void extractsOnlyNativeWaypointBits() {
    int runtimeFlags = D2DrlgRoomFlags.NO_LOS_DRAW
        | D2DrlgRoomFlags.HAS_ROOM
        | D2DrlgRoomFlags.TILELIB_LOADED
        | D2DrlgRoomFlags.PRESET_UNITS_ADDED;

    assertEquals(0, DrlgOutPlace.waypointSubThemeFromFlags(runtimeFlags));
    assertEquals(1, DrlgOutPlace.waypointSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.HAS_WAYPOINT));
    assertEquals(2, DrlgOutPlace.waypointSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.HAS_WAYPOINT_SMALL));
    assertEquals(3, DrlgOutPlace.waypointSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.HAS_WAYPOINT
            | D2DrlgRoomFlags.HAS_WAYPOINT_SMALL));
  }

  @Test
  void extractsOnlyNativeShrineRowBits() {
    int runtimeFlags = D2DrlgRoomFlags.NO_LOS_DRAW
        | D2DrlgRoomFlags.HAS_ROOM
        | D2DrlgRoomFlags.TILELIB_LOADED
        | D2DrlgRoomFlags.PRESET_UNITS_ADDED
        | D2DrlgRoomFlags.PRESET_UNITS_SPAWNED;

    assertEquals(0, DrlgOutPlace.shrineSubThemeFromFlags(runtimeFlags));
    assertEquals(1, DrlgOutPlace.shrineSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.SUBSHRINE_ROW1));
    assertEquals(2, DrlgOutPlace.shrineSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.SUBSHRINE_ROW2));
    assertEquals(4, DrlgOutPlace.shrineSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.SUBSHRINE_ROW3));
    assertEquals(8, DrlgOutPlace.shrineSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.SUBSHRINE_ROW4));
    assertEquals(15, DrlgOutPlace.shrineSubThemeFromFlags(
        runtimeFlags | D2DrlgRoomFlags.SUBSHRINE_ROWS_MASK));
  }
}
