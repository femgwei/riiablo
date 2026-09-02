package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;

class NativeObjectDropAdapterTest extends RiiabloTest {
  @Test
  void selectsNativeChestTiersAtOriginalThresholds() {
    assertEquals(0, NativeObjectDropAdapter.selectChestTier(1, 1, 12));
    assertEquals(0, NativeObjectDropAdapter.selectChestTier(4, 1, 12));
    assertEquals(1, NativeObjectDropAdapter.selectChestTier(5, 1, 12));
    assertEquals(1, NativeObjectDropAdapter.selectChestTier(8, 1, 12));
    assertEquals(2, NativeObjectDropAdapter.selectChestTier(9, 1, 12));
  }

  @Test
  void parsesNativeGoldFixedPointMultiplier() {
    assertEquals(256, NativeObjectDropAdapter.multiplier("gld"));
    assertEquals(2048, NativeObjectDropAdapter.multiplier("\"gld,mul=2048\""));
    assertEquals(256, NativeObjectDropAdapter.multiplier("gld,mul=invalid"));
  }

  @Test
  void resolvesActualActOneChestLeavesToConstructibleItems() {
    Levels.Entry bloodMoor = Riiablo.files.Levels.get(2);
    assertNotNull(bloodMoor);
    NativeObjectDropAdapter adapter = new NativeObjectDropAdapter(Riiablo.files);

    List<NativeObjectDropAdapter.Drop> drops = adapter.rollChest(
        bloodMoor, Riiablo.NORMAL, bound -> bound - 1);

    assertFalse(drops.isEmpty());
    for (NativeObjectDropAdapter.Drop drop : drops) {
      assertNotNull(drop.code);
      assertFalse(drop.code.isEmpty());
    }
  }

  @Test
  void matchesNativeContainerDropGates() {
    assertFalse(NativeObjectDropSystem.shouldDropContainer(4, false, false, 24));
    assertTrue(NativeObjectDropSystem.shouldDropContainer(4, false, false, 25));
    assertTrue(NativeObjectDropSystem.shouldDropContainer(4, true, false, 0));
    assertTrue(NativeObjectDropSystem.shouldDropContainer(4, false, true, 0));
    assertFalse(NativeObjectDropSystem.shouldDropContainer(3, false, false, 21));
    assertTrue(NativeObjectDropSystem.shouldDropContainer(3, false, false, 20));
  }

  @Test
  void matchesNativeSparkChestQualityDistributionBoundaries() {
    assertEquals(com.riiablo.item.Quality.RARE,
        NativeObjectDropSystem.sparkChestQuality(4));
    assertEquals(com.riiablo.item.Quality.MAGIC,
        NativeObjectDropSystem.sparkChestQuality(5));
  }
}
