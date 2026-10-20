package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeDirectDamageMathTest {
  @Test
  void reflectionUsesTheSameNativeReductionOrderCapsAndZeroAsDirect() {
    assertEquals(45, NativeDirectDamageMath.nativePhysicalDamage(100, true, 50, 10));
    assertEquals(45, NativeDirectDamageMath.reflectedPhysicalDamage(100, true, false, 50, 10));
    assertEquals(0, NativeDirectDamageMath.nativePhysicalDamage(100, false, 0, 100));
    assertEquals(0, NativeDirectDamageMath.reflectedPhysicalDamage(100, false, false, 0, 100));
    assertEquals(50, NativeDirectDamageMath.nativePhysicalDamage(100, true, 100, 0));
    assertEquals(50, NativeDirectDamageMath.reflectedPhysicalDamage(100, true, false, 100, 0));
    assertEquals(135, NativeDirectDamageMath.nativePhysicalDamage(100, false, -50, 10));
    assertEquals(3, NativeDirectDamageMath.reflectedPhysicalDamage(100, true, true, 50, 10)); // (17 - 10) / 2
    assertEquals(Integer.MAX_VALUE, NativeDirectDamageMath.nativePhysicalDamage(Integer.MAX_VALUE, false, -100, 0));
  }

  @Test
  void directElementalPoliciesKeepMdrAndAbsorbDifferences() {
    NativeDamagePacket out = new NativeDamagePacket();
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, false, false, 50, 75, 10, 0, 0, true);
    assertEquals(45, out.totalDamage);
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, false, false, 50, 75, 10, 0, 0, false);
    assertEquals(40, out.totalDamage);
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, false, false, 0, 75, 0, 90, 0, true);
    assertEquals(60, out.totalDamage);
    assertEquals(40, out.absorbedLife);
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, false, false, 0, 75, 0, 90, 0, false);
    assertEquals(10, out.totalDamage);
    assertEquals(90, out.absorbedLife);
  }

  @Test
  void immunityPrecedesPierceWhilePoisonSkipsMdr() {
    NativeDamagePacket out = new NativeDamagePacket();
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 200, false, false, 100, 75, 0, 0, 0, true);
    assertTrue(out.hit);
    assertEquals(100, out.hitChance);
    assertEquals(0, out.totalDamage);
    NativeDirectDamageMath.elementalDamage(out, 4, 100, 0, true, true, 50, 75, 100, 0, 0, true);
    assertEquals(8, out.totalDamage); // PvP 17, then poison resistance 50%; no MDR.
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, false, false, 99, 75, 0, 0, 0, true);
    assertEquals(1, out.totalDamage);
  }

  @Test
  void freshDirectOutputClearsOldChannelsAndRejectsInvalidInput() {
    NativeDamagePacket out = new NativeDamagePacket();
    out.physicalDamage = 12;
    out.elementalDamage[2] = 23;
    out.absorbedLife = 19;
    NativeDirectDamageMath.elementalDamage(out, 1, 100, 0, true, false, 0, 75, 0, 0, 0, false);
    assertEquals(0, out.physicalDamage);
    assertEquals(0, out.elementalDamage[2]);
    assertEquals(0, out.absorbedLife);
    NativeDirectDamageMath.elementalDamage(out, 6, 100, 0, true, false, 0, 75, 0, 0, 0, false);
    assertFalse(out.hit);
    assertEquals(0, out.totalDamage);
  }
}
