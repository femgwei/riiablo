package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeFixedElementalMathTest {
  @Test
  void rateKeepsPvpBeforeMdrAndRateScalingInFractionalUnits() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 25600, true, true, 50, 0, 0, 10, 1024, 0, 0);
    assertEquals(896, out.damageFixed); // ((100hp * 17%) - 10hp) * 50%.
    NativeFixedElementalMath.rateDamage(out, 256, false, true, 0, 0, 0, 1, 41, 0, 0);
    assertEquals(246, out.damageFixed); // 256 - floor(256 * 41 / 1024).
    NativeFixedElementalMath.rateDamage(out, 256, false, true, 0, 0, 0, 1, 0, 0, 0);
    assertEquals(0, out.damageFixed); // Nonpositive rate retains unscaled MDR.
  }

  @Test
  void rateChecksImmunityBeforePierceAndCapsAbsorbAtForty() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 1000, false, false, 100, 0, 500, 0, 0, 0, 0);
    assertTrue(out.immune);
    NativeFixedElementalMath.rateDamage(out, 1000, true, false, 100, 0, 0, 0, 0, 90, 0);
    assertFalse(out.immune);
    assertEquals(150, out.damageFixed);
    assertEquals(100, out.absorbedLifeFixed);
    NativeFixedElementalMath.rateDamage(out, 1000, true, false, -500, 0, 0, 0, 0, 0, 100);
    assertEquals(0, out.damageFixed);
    assertEquals(2000, out.absorbedLifeFixed);
  }

  @Test
  void staticFieldCompensatesThenUsesNativePvpMdrResistanceAndCappedAbsorb() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.staticFieldDamage(out, 25600, true, true, -100, 0, false, 10, 25, 1);
    assertEquals(0, out.damageFixed); // compensated 50hp * 17% < 10hp MDR
    assertEquals(0, out.absorbedLifeFixed);
    NativeFixedElementalMath.staticFieldDamage(out, 25600, true, false, -100, 0, false, 10, 25, 1);
    assertEquals(15104, out.damageFixed); // (50-10)*2=80hp; absorb 20+1hp
    assertEquals(5376, out.absorbedLifeFixed);
    NativeFixedElementalMath.staticFieldDamage(out, 257, true, false, -50, 0, true, 100, 0, 0);
    assertEquals(256, out.damageFixed); // Negative-resistance compensation keeps both truncations.
    NativeFixedElementalMath.staticFieldDamage(out, 1000, false, false, 100, 0, false, 0, 0, 0);
    assertTrue(out.immune);
    NativeFixedElementalMath.staticFieldDamage(out, 1000, true, false, 0, 0, false, 0, 90, 0);
    assertEquals(600, out.damageFixed);
    assertEquals(400, out.absorbedLifeFixed); // Same 40% cap as every elemental packet.
  }

  @Test
  void lightningPreservesPacketAccumulationImmunityAndMonsterResistanceCap() {
    NativeDamagePacket out = new NativeDamagePacket();
    out.absorbedLife = 7;
    out.physicalDamage = 91;
    out.elementalDamage[NativeCombatConstants.DAMAGE_FIRE] = 23;
    NativeFixedElementalMath.lightningDamage(out, 25600, false, false, false, false, 50, 75, 0, 10, 25, 1);
    assertEquals(8384, out.elementalDamage[NativeCombatConstants.DAMAGE_LIGHTNING]);
    assertEquals(3143, out.absorbedLife);
    assertEquals(91, out.physicalDamage);
    assertEquals(23, out.elementalDamage[NativeCombatConstants.DAMAGE_FIRE]);
    NativeFixedElementalMath.lightningDamage(out, 25600, false, false, true, false, 100, 75, 200, 0, 0, 0);
    assertEquals(8384, out.elementalDamage[NativeCombatConstants.DAMAGE_LIGHTNING]);
    assertEquals(3143, out.absorbedLife); // Immune return does not clear a borrowed packet.
    NativeFixedElementalMath.lightningDamage(out, 1000, false, false, true, false, 99, 75, 0, 0, 0, 0);
    assertEquals(10, out.elementalDamage[NativeCombatConstants.DAMAGE_LIGHTNING]);
  }

  @Test
  void resultsResetAndSaturateWithoutLeakingPreviousImmunity() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 100, false, false, 100, 0, 0, 0, 0, 0, 0);
    NativeFixedElementalMath.rateDamage(out, Integer.MAX_VALUE, true, false, -100, 0, 0, 0, 0, 0, 0);
    assertFalse(out.immune);
    assertEquals(Integer.MAX_VALUE, out.damageFixed);
    assertEquals(0, out.absorbedLifeFixed);
    NativeFixedElementalMath.staticFieldDamage(out, 0, false, false, 100, 0, false, 0, 0, 0);
    assertFalse(out.immune);
    assertEquals(0, out.damageFixed);
    NativeDamagePacket packet = new NativeDamagePacket();
    packet.absorbedLife = Integer.MAX_VALUE - 1;
    NativeFixedElementalMath.lightningDamage(packet, Integer.MAX_VALUE, false, false, false, false,
        -100, 75, 0, 0, 40, Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, packet.absorbedLife);
  }
}
