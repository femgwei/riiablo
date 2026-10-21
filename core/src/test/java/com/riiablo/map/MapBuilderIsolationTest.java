package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;

class MapBuilderIsolationTest {
  @Test void generationCapabilitiesNeverRebindTheOtherMapsData() {
    Files previous = Riiablo.files;
    Files a = mock(Files.class), b = mock(Files.class);
    Map first = new Map(1, 0, new MapResources(a, null, null));
    Map second = new Map(2, 0, new MapResources(b, null, null));
    try {
      first.configureGeneration(false, false);
      second.configureGeneration(true, true);
      Riiablo.files = null;
      assertSame(a, first.act1Builder().files());
      assertSame(b, second.act1Builder().files());
      assertFalse(first.isRemoteClient());
      assertFalse(first.hasClientPresentation());
      assertTrue(second.isRemoteClient());
      assertTrue(second.hasClientPresentation());
      assertNotSame(first.act1Builder(), second.act1Builder());
      assertNotSame(first.act2Builder(), second.act2Builder());
      assertNotSame(first.act3Builder(), second.act3Builder());
      assertNotSame(first.act4Builder(), second.act4Builder());
      assertNotSame(first.act5Builder(), second.act5Builder());
    } finally { Riiablo.files = previous; first.dispose(); second.dispose(); }
  }

  @Test void runtimeWarpConstructionUsesEachMapsOwnLevelTable() throws Exception {
    Files previous = Riiablo.files;
    com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    com.badlogic.gdx.Gdx.app = mock(com.badlogic.gdx.Application.class);
    Files a = mock(Files.class), b = mock(Files.class);
    Levels firstLevels = mock(Levels.class), secondLevels = mock(Levels.class);
    java.lang.reflect.Field field = Files.class.getField("Levels");
    field.setAccessible(true);
    field.set(a, firstLevels);
    field.set(b, secondLevels);
    when(firstLevels.get(anyInt())).thenAnswer(call -> level(call.getArgument(0), 7));
    when(secondLevels.get(anyInt())).thenAnswer(call -> level(call.getArgument(0), 9));
    Map first = new Map(1, 0, new MapResources(a, null, null));
    Map second = new Map(2, 0, new MapResources(b, null, null));
    try {
      Riiablo.files = null;
      first.act2Builder().configureAct2OutdoorWarps(first);
      second.act2Builder().configureAct2OutdoorWarps(second);
      verify(firstLevels, atLeastOnce()).get(40);
      verify(secondLevels, atLeastOnce()).get(40);
      // The first occupied slot belongs to the session table; runtime links append after it.
      assertEquals(41, first.getWarpDestinationOverride(40, 1));
      assertEquals(41, second.getWarpDestinationOverride(40, 1));
      assertEquals(-1, first.getWarpDestinationOverride(40, 0));
    } finally {
      Riiablo.files = previous; com.badlogic.gdx.Gdx.app = previousApp;
      first.dispose(); second.dispose();
    }
  }

  private static Levels.Entry level(int id, int existing) {
    Levels.Entry row = new Levels.Entry();
    row.Id = id;
    row.Vis = new int[] {existing, 0, 0, 0, 0, 0, 0, 0};
    row.Warp = new int[] {0, -1, -1, -1, -1, -1, -1, -1};
    return row;
  }
}
