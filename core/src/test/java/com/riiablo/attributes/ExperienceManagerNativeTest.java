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
    assertEquals(3, table.levelForExperience(0, 1, 100_000L, 3));
    assertEquals(2, table.levelForExperience(0, 1, 2_400L, 2),
        "an accepted award may advance a hireling to its owner's level");
    assertEquals(31L, table.maximumAward(0, 1));
    assertEquals(2_400L, table.nextThreshold(0, 1));
  }

  @Test
  void mirrorsNativeHirelingStatGrowth() {
    com.riiablo.engine.server.NativeHirelingExperienceTable table =
        new com.riiablo.engine.server.NativeHirelingExperienceTable().add(
            new com.riiablo.engine.server.NativeHirelingExperienceTable.Row(
                0, 5, 200, 50, 6, 10, 3, 40, 8, 52, 16,
                30, 8, 2, 6, 8, 10, 4,
                new int[]{6}, new int[]{1}, new int[]{1}, new int[]{32}));
    com.riiablo.engine.server.NativeHirelingExperienceTable.Stats stats =
        table.stats(0, 7);
    assertEquals(7, stats.level);
    assertEquals(42, stats.strength);
    assertEquals(56, stats.dexterity);
    assertEquals(62, stats.hitpoints);
    assertEquals(16, stats.defense);
    assertEquals(4, stats.damageMin);
    assertEquals(8, stats.damageMax);
    assertEquals(46, stats.attackRate);
    assertEquals(12, stats.resist);
    assertEquals(7, stats.hpRegenEncoded);
    assertEquals(3, stats.skillLevels[0]);

  }

  private static long gain(int attackerLevel, int defenderLevel,
      int defenderExperience, int ratio, int itemBonus) {
    return ExperienceManager.computeNativeExperienceGain(
        99, attackerLevel, defenderLevel, defenderExperience,
        ratio, 10, itemBonus);
  }
}
