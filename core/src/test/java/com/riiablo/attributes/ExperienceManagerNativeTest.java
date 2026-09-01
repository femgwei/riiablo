package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperienceManagerNativeTest {
  @Test
  void mirrorsNativeLevelDifferenceFactors() {
    assertEquals(1000, gain(10, 10, 1000, 1024, 0));
    assertEquals(808, gain(10, 4, 1000, 1024, 0));
    assertEquals(878, gain(10, 16, 1000, 1024, 0));
    assertEquals(500, gain(25, 50, 1000, 1024, 0));
  }

  @Test
  void mirrorsNativeExpRatioAndItemBonusOrdering() {
    assertEquals(953, gain(70, 70, 1000, 976, 0));
    assertEquals(1048, gain(70, 70, 1000, 976, 10));
  }

  @Test
  void mirrorsNativeZeroExperienceAndMaxLevelBoundaries() {
    assertEquals(1, ExperienceManager.computeNativeExperienceGain(
        99, 99, 1, 0, 1024, 10, 0));
    assertEquals(0, ExperienceManager.computeNativeExperienceGain(
        99, 99, 99, 1000, 1024, 10, 0));
  }

  @Test
  void mirrorsNativePartyBonusAndLevelWeightedShare() {
    assertEquals(673, ExperienceManager.computeNativePartyShare(1000, 10, 2, 20));
    assertEquals(269, ExperienceManager.computeNativePartyShare(1000, 4, 2, 20));
    assertEquals(1000, ExperienceManager.computeNativePartyShare(1000, 10, 1, 10));
  }

  @Test
  void mirrorsNativeHirelingKillerAndOwnerShares() {
    assertEquals(1000, ExperienceManager.computeNativeHirelingAward(1000, true));
    assertEquals(335, ExperienceManager.computeNativeHirelingAward(1000, false));
    assertEquals(0, ExperienceManager.computeNativeHirelingAward(0, false));
  }

  @Test
  void mirrorsNativeHirelingThresholdAndOwnerLevelCap() {
    com.riiablo.engine.server.NativeHirelingExperienceTable table =
        new com.riiablo.engine.server.NativeHirelingExperienceTable()
            .add(0, 1, 200).add(0, 2, 200).add(0, 3, 200);
    // D2Common MONSTERS_GetHirelingExpForNextLevel computes
    // expPerLevel * level * level * (level + 1).
    assertEquals(2_400L,
        com.riiablo.engine.server.NativeHirelingExperienceTable.threshold(2, 200));
    assertEquals(2, table.levelForExperience(0, 1, 2_400L, 5));
    assertEquals(2, table.levelForExperience(0, 1, 100_000L, 3));
    assertEquals(31L, table.maximumAward(0, 1));
    assertEquals(2_400L, table.nextThreshold(0, 1));
  }

  private static long gain(int attackerLevel, int defenderLevel,
      int defenderExperience, int ratio, int itemBonus) {
    return ExperienceManager.computeNativeExperienceGain(
        99, attackerLevel, defenderLevel, defenderExperience,
        ratio, 10, itemBonus);
  }
}
