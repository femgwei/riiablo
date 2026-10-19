package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal independent source-derived samples, NOT original EXE captures.
 * D2MOO 0c0bbd1: Missile.cpp AddStatsToDamage(owner,8), CalculateFinalDamage;
 * MissMode.cpp FillDamageParams poison-count division BEFORE resistances.
 */
@Tag("monster-parity-audit")
class MonsterMissileSourceOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"64,0,133,266,190,1,190", "128,0,266,532,190,3,63",
      "192,0,399,798,190,4,47", "64,30,133,266,70,0,70",
      "192,30,399,798,70,0,70"})
  void rawPoisonRateAndUnscaledSummedLength(int scale, int override, int expectedMin,
      int expectedMax, int length, int count, int averaged) {
    int[] min = {0, 2, 0, 0, 10, 0}, max = {0, 3, 0, 0, 20, 0};
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_POISON, 256, 512, 40, scale, min, max, 20, 150, 3, override);
    assertEquals(expectedMin, p.minimum(DAMAGE_POISON));
    assertEquals(expectedMax, p.maximum(DAMAGE_POISON));
    assertEquals(length, p.poisonLength);
    assertEquals(count, p.poisonCount);
    assertEquals(averaged, p.basePoisonDuration());
  }

  @ParameterizedTest
  @CsvSource({"64,384,640,30", "128,768,1280,60", "192,1152,1920,90"})
  void tableAndSourceColdAreAddedThenWholePacketScales(int scale, int min, int max, int length) {
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_COLD, 256, 512, 40, scale,
        new int[] {0, 0, 0, 2, 0, 0}, new int[] {0, 0, 0, 3, 0, 0}, 20, 0, 0, 0);
    assertEquals(min, p.minimum(DAMAGE_COLD));
    assertEquals(max, p.maximum(DAMAGE_COLD));
    assertEquals(length, p.coldLength);
  }
}
