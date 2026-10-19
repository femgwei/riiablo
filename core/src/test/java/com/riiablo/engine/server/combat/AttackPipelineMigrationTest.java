package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.CombatSystem.*;

import com.riiablo.engine.server.NativeRng;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AttackPipelineMigrationTest {
  @Test
  void packetsMatchHostAndDomainWithFrozenCompatibilityAndNativeMitigationSnapshots() throws Exception {
    Random inputs = new Random(0x110F5399L);
    int misses = 0, shields = 0, passives = 0, landed = 0;
    for (int i = 0; i < 4000; i++) {
      AttackerData a = attacker(inputs);
      DefenderData d = defender(inputs);
      // Half retain a frozen full-packet oracle in the unchanged no-mitigation,
      // single nonnegative bonus domain. The other half exercise corrected
      // bonuses/mitigation, compared host-vs-domain below; independent native
      // expectations live in NativeMonsterHitMitigationTest/NativePhysicalBonusTest.
      // Never rewrite the old resolver to pass.
      boolean frozenCompatible = i % 2 == 0;
      if (frozenCompatible) {
        d.damageReduced = d.damageReducedPercent = 0;
        d.immunePhysical = false;
        a.strength = 0;
        a.enhancedDamagePercent = Math.max(0, a.enhancedDamagePercent);
        a.demonDamagePercent = Math.max(0, a.demonDamagePercent);
        a.undeadDamagePercent = Math.max(0, a.undeadDamagePercent);
      }
      // Exercise complete damage packets as well as each early-return family.
      if (i % 4 == 0) {
        a.alwaysHit = true;
        d.canBlock = false;
        d.passiveDodge = d.passiveAvoid = d.passiveEvade = d.passiveWeaponBlock = 0;
      }
      int initialFrame = d.lastBlockFrame;
      int seed = inputs.nextInt();
      NativeRng before = new NativeRng(seed), hostRng = new NativeRng(seed), domainRng = new NativeRng(seed);
      List<Integer> beforeBounds = new ArrayList<>(), domainBounds = new ArrayList<>();
      PreviousAttackResolver previous = new PreviousAttackResolver(bound -> {
        beforeBounds.add(bound);
        return before.nextInt(bound);
      });
      CombatResult expected = previous.calculateAttack(a, d);
      int expectedFrame = d.lastBlockFrame;
      d.lastBlockFrame = initialFrame;
      CombatResult actual = new CombatSystem(hostRng).calculateAttack(a, d);
      if (frozenCompatible) assertPacket(expected, actual, "frozen host snapshot " + i);
      assertEquals(expectedFrame, d.lastBlockFrame, "host block frame " + i);
      assertEquals(before.state(), hostRng.state(), "host RNG " + i);
      d.lastBlockFrame = initialFrame;
      NativeDamagePacket standalone = new NativeAttackResolver(new NativeCombatRolls(bound -> {
        domainBounds.add(bound);
        return domainRng.nextInt(bound);
      })).resolve(a, d);
      assertPacket(actual, standalone, "host/domain snapshot " + i);
      assertEquals(expectedFrame, d.lastBlockFrame, "domain block frame " + i);
      assertEquals(beforeBounds, domainBounds, "domain draw bounds " + i);
      assertEquals(before.state(), domainRng.state(), "domain RNG " + i);
      if (!expected.hit) misses++;
      else if (!expected.blocked) landed++;
      else if (expected.defenseType == DefenseCalculator.DEFENSE_BLOCK) shields++;
      else passives++;
    }
    assertTrue(misses > 0 && shields > 0 && passives > 0 && landed >= 1000,
        "The snapshot suite must reach every return family");
  }

  @Test
  void hostVirtualHitChanceAndShieldSeamsRemainInTheProductionPath() {
    int[] calls = new int[4];
    CombatSystem host = new CombatSystem(new NativeRng(17)) {
      @Override public int calculateHitChance(AttackerData a, DefenderData d) {
        calls[0]++;
        return 100;
      }
      @Override public HitResult calculateHit(AttackerData a, DefenderData d, int roll) {
        calls[1]++;
        return super.calculateHit(a, d, roll);
      }
      @Override public int calculateBlockChance(DefenderData d) {
        calls[2]++;
        return 42;
      }
      @Override protected boolean rollShieldBlock(int chance) {
        calls[3]++;
        assertEquals(42, chance);
        return true;
      }
    };
    AttackerData a = new AttackerData();
    DefenderData d = new DefenderData();
    d.canBlock = true;
    d.currentFrame = 16;
    CombatResult result = host.calculateAttack(a, d);
    assertTrue(result.hit && result.blocked && result.blockAnimationAllowed);
    assertEquals(16, d.lastBlockFrame);
    assertArrayEquals(new int[] {1, 1, 1, 1}, calls);
  }

  private static void assertPacket(NativeDamagePacket expected, NativeDamagePacket actual,
      String label) throws Exception {
    for (Field field : NativeDamagePacket.class.getFields()) {
      if (field.getType() == int[].class) {
        assertArrayEquals((int[]) field.get(expected), (int[]) field.get(actual), label + " " + field.getName());
      } else {
        assertEquals(field.get(expected), field.get(actual), label + " " + field.getName());
      }
    }
    assertEquals(expected.totalDamageHp(), actual.totalDamageHp(), label + " HP view");
  }

  @Test
  void hitCapturesRatingAndDefenseBeforeVirtualChanceEvaluation() {
    CombatSystem host = new CombatSystem(new NativeRng(17)) {
      @Override public int calculateHitChance(AttackerData a, DefenderData d) {
        a.attackRating = 999;
        d.defense = 999;
        return 42;
      }
    };
    AttackerData a = new AttackerData();
    a.attackRating = 17;
    DefenderData d = new DefenderData();
    d.defense = 23;
    HitResult hit = host.calculateHit(a, d, 41);
    assertEquals(17, hit.attackRating);
    assertEquals(23, hit.targetDefense);
    assertEquals(42, hit.chance);
    assertTrue(hit.hit);
  }

  private static AttackerData attacker(Random r) {
    AttackerData a = new AttackerData();
    a.isPlayer = r.nextBoolean();
    a.level = r.nextInt(101);
    a.strength = r.nextInt(401) - 20;
    a.dexterity = r.nextInt(200);
    a.attackRating = r.nextInt(3000);
    a.attackRatingPercent = r.nextInt(301) - 50;
    a.minDamage = r.nextInt(300) - 20;
    a.maxDamage = r.nextInt(500) - 20;
    a.enhancedDamagePercent = r.nextInt(400) - 50;
    a.physicalConversionPercent = r.nextInt(151) - 10;
    a.physicalConversionType = r.nextInt(DAMAGE_TYPE_COUNT + 2) - 1;
    a.magicTargetBonusPercent = r.nextInt(201) - 20;
    a.demonToHit = r.nextInt(100);
    a.undeadToHit = r.nextInt(100);
    a.demonDamagePercent = r.nextInt(200) - 10;
    a.undeadDamagePercent = r.nextInt(200) - 10;
    a.bypassUndeadPhysicalResistance = r.nextBoolean();
    a.criticalStrike = r.nextInt(141) - 20;
    a.deadlyStrike = r.nextInt(141) - 20;
    a.crushingBlow = r.nextInt(141) - 20;
    a.ignoreTargetDefense = r.nextBoolean();
    a.isMissile = r.nextBoolean();
    a.fixedPointElements = r.nextBoolean();
    a.alwaysHit = r.nextBoolean();
    a.lifeLeech = r.nextInt(51) - 10;
    a.manaLeech = r.nextInt(51) - 10;
    a.coldLength = r.nextInt(501) - 10;
    a.poisonLength = r.nextBoolean() ? 0 : r.nextInt(501) - 10;
    a.difficulty = r.nextInt(5) - 1;
    a.stateDurationsDeferred = r.nextBoolean();
    for (int type = 1; type < DAMAGE_TYPE_COUNT; type++) {
      a.elementalMinDamage[type] = r.nextInt(100) - 20;
      a.elementalMaxDamage[type] = r.nextInt(200) - 20;
      a.elementalPierce[type] = r.nextInt(151) - 20;
    }
    return a;
  }

  private static DefenderData defender(Random r) {
    DefenderData d = new DefenderData();
    d.isPlayer = r.nextBoolean();
    d.isMonster = !d.isPlayer;
    d.ignoreTargetDefenseAllowed = r.nextBoolean();
    d.level = r.nextInt(101);
    d.dexterity = r.nextInt(401);
    d.defense = r.nextInt(3000);
    d.defenseVsMelee = r.nextInt(201) - 100;
    d.defenseVsMissile = r.nextInt(201) - 100;
    d.currentLife = r.nextInt(10000);
    d.blockChance = r.nextInt(151);
    d.canBlock = r.nextBoolean();
    d.hasEquippedShield = r.nextBoolean();
    d.blockFactor = r.nextInt(51);
    d.isRunning = r.nextBoolean();
    d.fasterBlockRate = r.nextInt(241) - 20;
    d.currentFrame = r.nextInt(200);
    d.lastBlockFrame = r.nextInt(200) - 50;
    d.attackType = r.nextInt(3);
    d.isMoving = d.isRunning || r.nextBoolean();
    d.passiveDodge = r.nextInt(141) - 20;
    d.passiveAvoid = r.nextInt(141) - 20;
    d.passiveEvade = r.nextInt(141) - 20;
    d.passiveWeaponBlock = r.nextInt(141) - 20;
    d.damageReduced = r.nextInt(50);
    d.damageReducedPercent = r.nextInt(201) - 100;
    d.magicDamageReduced = r.nextInt(50);
    d.isDemon = r.nextBoolean();
    d.isUndead = r.nextBoolean();
    d.immunePhysical = r.nextBoolean();
    d.cannotBeFrozen = r.nextBoolean();
    d.halfFreezeDuration = r.nextBoolean();
    for (int type = 1; type < DAMAGE_TYPE_COUNT; type++) {
      d.resistances[type] = r.nextInt(251) - 100;
      d.maxResistances[type] = r.nextInt(121);
      d.immuneElemental[type] = r.nextInt(5) == 0;
      d.absorbPercent[type] = r.nextInt(121) - 10;
      d.absorbFlat[type] = r.nextInt(51) - 10;
    }
    return d;
  }
}
