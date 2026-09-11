package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act2ArcaneSanctuaryLayoutTest {
  @Test
  void nativeDirectionMappingKeepsRotationAndPresetPair() {
    assertEquals(3, Act2ArcaneSanctuaryLayout.Direction.NORTH.nativeRotation());
    assertEquals(0, Act2ArcaneSanctuaryLayout.Direction.EAST.nativeRotation());
    assertEquals(1, Act2ArcaneSanctuaryLayout.Direction.SOUTH.nativeRotation());
    assertEquals(2, Act2ArcaneSanctuaryLayout.Direction.WEST.nativeRotation());
    for (Act2ArcaneSanctuaryLayout.Direction direction
        : Act2ArcaneSanctuaryLayout.Direction.values()) {
      assertTrue(Act2ArcaneSanctuaryLayout.isSummonerPreset(direction.summonerPresetDef()));
    }
  }

  @Test
  void levelSeedSelectionIsDeterministicAndCoversNativeBranches() {
    assertEquals(Act2ArcaneSanctuaryLayout.fromLevelSeed(12345),
        Act2ArcaneSanctuaryLayout.fromLevelSeed(12345));
    boolean[] seen = new boolean[Act2ArcaneSanctuaryLayout.Direction.values().length];
    for (int seed = 0; seed < 256; seed++) {
      seen[Act2ArcaneSanctuaryLayout.fromLevelSeed(seed).ordinal()] = true;
    }
    for (boolean branch : seen) assertTrue(branch);
  }
}
