package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal D2MOO-derived audit; these are not original executable captures. */
@Tag("monster-parity-audit")
class MonsterElementalOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"1,2560,2816,0,0,0,0,2815,0", "2,2816,2560,0,0,0,0,2815,0",
      "3,2560,2560,0,0,0,0,2560,0", "1,0,2816,0,0,0,0,0,0",
      "1,2560,2816,10,50,0,0,127,0", "5,2560,2816,10,50,0,0,127,0",
      "1,2560,2816,0,0,100,1,1433,1382",
      "1,1280256,1280256,0,50,0,0,640100,0"})
  void nativeInstantaneousMissileSamples(int type, int min, int max, int mdr, int resistance,
      int absorb, int flatAbsorb, int expected, int healed) {
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.magicDamageReduced = mdr;
    d.resistances[type] = resistance;
    d.absorbPercent[type] = absorb;
    d.absorbFlat[type] = flatAbsorb;
    NativeDamagePacket p = NativeMissileElementalDamageTest.resolve(
        NativeMissileElementalDamageTest.attacker(type, min, max), d, new ArrayList<>());
    assertEquals(expected, p.elementalDamage[type]);
    assertEquals(healed, p.absorbedLife);
    assertEquals(256, p.elementalScale);
  }
}
