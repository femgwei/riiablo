package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal source-derived oracle: SUNITDMG_FillDamageValues / RollDamageValueInRange,
 * sgDamageStatTable and MONSTERUNIQUE_CalculatePercentage. Not process captures. */
@Tag("monster-parity-audit")
class MonsterMeleeElementOriginalParityTest {
  @ParameterizedTest @CsvSource({
      "1,0,2,0,0,0,0,511,0", "1,2,2,0,0,0,0,512,0",
      "1,3,2,0,0,0,0,768,0", "1,1,2,50,0,0,0,767,0",
      "1,10,10,0,1,50,0,1152,0", "3,2,2,0,99,50,0,0,0",
      "4,511,511,0,999,50,100,0,255", "5,3,3,0,0,-100,0,1536,0"})
  void originalSourceStatSamples(int type, int min, int max, int mastery, int mdr,
      int resistance, int length, int instant, int rate) {
    NativeAttackData a = new NativeAttackData(); a.alwaysHit = true;
    a.nativeMonsterMeleeElements = true; a.level = 40;
    a.elementalMinDamage[type] = min; a.elementalMaxDamage[type] = max;
    a.elementalMastery[type] = mastery; a.poisonLength = length;
    NativeDefenseData d = new NativeDefenseData(); d.isPlayer = true; d.level = 40;
    d.maxResistances[type] = 75; d.resistances[type] = resistance; d.magicDamageReduced = mdr;
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> n - 1)).resolve(a, d);
    assertEquals(instant, p.elementalDamage[type]);
    assertEquals(rate / 256f, p.poisonDamagePerFrame);
  }
}
