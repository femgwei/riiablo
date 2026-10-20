package com.riiablo.engine.server.combat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent hand-calculated SUnitDmg.cpp / MonsterUnique.cpp / SkillSor.cpp
 * expectations at D2MOO 0c0bbd1e. No legacy resolver is used as an oracle. */
@Tag("monster-parity-audit")
class NativeMitigationAuditTest {
  @Test void pvpPrecedesMdrResistanceAndBothAbsorbChannels() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 25600, true, true, 50, 0, 0, 10, 1024, 90, 1);
    // 25600*17%=4352; -2560=1792; /2=896; absorb floor(896*.4)+256=614.
    assertEquals(282, out.damageFixed);
    assertEquals(614, out.absorbedLifeFixed);
  }

  @Test void rateScalesFlatReductionNotResistancePierce() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 10000, false, false, -50, 0, 0, 10, 41, 90, 1);
    assertEquals(8653, out.damageFixed);
    assertEquals(6194, out.absorbedLifeFixed);
    NativeFixedElementalMath.rateDamage(out, 2000000, false, false, 0, 0, 0, 5001, 41, 0, 0);
    // Native generic percentage divides 1280256 by 1024 BEFORE multiplying by 41.
    assertEquals(1948750, out.damageFixed);
  }

  @Test void monsterNinetyNineResistanceIsNotCappedAtPlayerSeventyFive() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    NativeFixedElementalMath.rateDamage(out, 25600, false, true, 99, 0, 0, 0, 0, 0, 0);
    assertEquals(256, out.damageFixed);
    NativeFixedElementalMath.staticFieldDamage(out, 25600, false, true, 99, 0, false, 0, 0, 0);
    assertEquals(256, out.damageFixed);
  }

  @Test void nativeLargeValueDivisionIsPreservedAtEachStage() {
    assertEquals(178279, NativeMitigationMath.pvp(1048799, true, 256));
    assertEquals(524350, NativeMitigationMath.reduce(1048799, 256, 50, 0, 0));
    assertEquals(104870, NativeLeechMath.percentage(1048799, 2000000, 10, 1, 100));
    assertEquals(52434, NativeLeechMath.percentage(1048799, 2000000, 10, 1, 50));
  }

  @Test void magicHasNoPierceAndPoisonHasNeitherMdrNorAbsorb() {
    NativeDamagePacket out = new NativeDamagePacket();
    NativeDirectDamageMath.elementalDamage(out, DAMAGE_MAGIC, 100, 100, false, false,
        50, 75, 10, 0, 0, true);
    assertEquals(45, out.totalDamage);
    NativeDirectDamageMath.elementalDamage(out, DAMAGE_POISON, 100, 0, true, true,
        50, 75, 100, 100, 100, true);
    assertEquals(8, out.totalDamage);
    assertEquals(0, out.absorbedLife);
  }

  @Test void immunityCannotBePiercedButPlayerOvercapResistanceCan() {
    NativeDamagePacket out = new NativeDamagePacket();
    NativeDirectDamageMath.elementalDamage(out, DAMAGE_FIRE, 100, 200, false, false,
        100, 75, 0, 0, 0, true);
    assertEquals(0, out.totalDamage);
    NativeDirectDamageMath.elementalDamage(out, DAMAGE_FIRE, 100, 200, true, false,
        100, 75, 0, 0, 0, true);
    assertEquals(200, out.totalDamage);
  }

  @Test void reflectedPhysicalUsesPreMitigationPvpAndAllowsFullReduction() {
    assertEquals(3, NativeDirectDamageMath.reflectedPhysicalDamage(100, true, true, 50, 10));
    assertEquals(0, NativeDirectDamageMath.reflectedPhysicalDamage(100, false, false, 0, 100));
    assertEquals(135, NativeDirectDamageMath.reflectedPhysicalDamage(100, false, false, -50, 10));
  }

  @Test void staticCompensationDoesNotCancelALaterDifficultyPenalty() {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    // SkillSor reads LIGHTRESIST=0; SUnitDmg later adds the Hell -100 penalty.
    NativeFixedElementalMath.staticFieldDamage(out, 6400, true, false,
        -100, 0, false, 0, 0, 0, 0, 0);
    assertEquals(12800, out.damageFixed);
    NativeFixedElementalMath.staticFieldDamage(out, 6400, true, false,
        -50, 0, false, 0, 0, 0, 25, -50);
    assertEquals(7465, out.damageFixed); // compensation floor=4266; final -75 resistance.
  }

  @Test void coldLengthDoesNotPayMdrOrPvpAndHalfDurationPrecedesResistance() {
    NativeAttackData a = new NativeAttackData();
    a.alwaysHit = a.isPlayer = true;
    a.coldLength = 7;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = d.halfFreezeDuration = true;
    d.resistances[DAMAGE_COLD] = -50;
    d.magicDamageReduced = 100;
    NativeDamagePacket hit = new NativeAttackResolver(new NativeCombatRolls(bound -> 0)).resolve(a, d);
    assertEquals(4, hit.coldDuration); // floor(floor(7/2)*1.5), not floor(floor(7*1.5)/2).
  }
}
