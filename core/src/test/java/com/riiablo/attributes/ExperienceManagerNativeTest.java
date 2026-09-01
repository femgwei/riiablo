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

  private static long gain(int attackerLevel, int defenderLevel,
      int defenderExperience, int ratio, int itemBonus) {
    return ExperienceManager.computeNativeExperienceGain(
        99, attackerLevel, defenderLevel, defenderExperience,
        ratio, 10, itemBonus);
  }
}
