package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.DifficultyLevels;
import org.junit.jupiter.api.Test;

/** Runs on the domain classpath, without core, MPQs, an ECS world or a client. */
class NativeCombatDomainTest {
  @Test
  void domainClasspathDoesNotContainTheCoreHost() {
    for (String host : new String[] {"com.riiablo.Riiablo", "com.riiablo.Files",
        "com.riiablo.engine.server.combat.CombatSystem",
        "com.riiablo.engine.server.combat.DefenseCalculator"}) {
      assertThrows(ClassNotFoundException.class, () -> Class.forName(host, false,
          NativeCombatDomainTest.class.getClassLoader()));
    }
  }

  @Test
  void resistancePolicyKeepsClassicAndExpansionSeparate() {
    DifficultyLevels.Entry row = new DifficultyLevels.Entry();
    row.ResistPenalty = -37;
    for (int d = 0; d < 3; d++) {
      assertEquals(new int[] {0, -20, -50}[d], NativeDifficultyPolicy
          .playerResistancePenalty(NativeCombatContext.forGameplay(d, row, false)));
      assertEquals(-37, NativeDifficultyPolicy
          .playerResistancePenalty(NativeCombatContext.forGameplay(d, row, true)));
      assertEquals(new int[] {0, -40, -100}[d], NativeDifficultyPolicy
          .playerResistancePenalty(NativeCombatContext.ofDifficulty(d)));
    }
    row.ResistPenalty = 12;
    assertEquals(12, NativeDifficultyPolicy.playerResistancePenalty(
        NativeCombatContext.forGameplay(2, row, true)));
  }

  @Test
  void derivedContextPreservesWorldInputsWithoutChangingOriginal() {
    DifficultyLevels.Entry row = new DifficultyLevels.Entry();
    NativeCombatContext original = NativeCombatContext.forGameplay(99, row, false);
    NativeCombatContext derived = original.withCombatants(true, true)
        .withTargetType(true, true).withPlayerCount(99);
    assertSame(row, derived.difficultyRow());
    assertEquals(2, derived.difficulty());
    assertFalse(derived.expansion());
    assertTrue(derived.stateDurationsDeferred());
    assertTrue(derived.isPvp());
    assertTrue(derived.defenderDemon());
    assertTrue(derived.defenderUndead());
    assertEquals(8, derived.playerCount());
    assertFalse(original.isPvp());
    assertEquals(1, original.playerCount());
    assertFalse(NativeCombatContext.ofDifficulty(-1).stateDurationsDeferred());
    assertTrue(NativeCombatContext.ofDifficulty(-1).isNormal());
  }

  @Test
  void tableRulesKeepDistinctDivisorsAndNativeIntegerExplosionMath() {
    DifficultyLevels.Entry row = new DifficultyLevels.Entry();
    row.MonsterColdDivisor = 3;
    row.MonsterFreezeDivisor = 5;
    row.AiCurseDivisor = 7;
    row.MonsterCEDamagePercent = 33;
    row.StaticFieldMin = 37;
    NativeCombatContext context = NativeCombatContext.forGameplay(2, row, true);
    assertEquals(3, NativeDifficultyPolicy.tableMonsterColdDivisor(context));
    assertEquals(5, NativeDifficultyPolicy.tableMonsterFreezeDivisor(context));
    assertEquals(7, NativeDifficultyPolicy.aiCurseDivisor(context));
    assertEquals(329, NativeDifficultyPolicy.monsterCorpseExplosionMaximum(999, context));
    assertEquals(Integer.MAX_VALUE, NativeDifficultyPolicy
        .monsterCorpseExplosionMaximumForPercent(Integer.MAX_VALUE, 200));
    assertEquals(0, NativeDifficultyPolicy.monsterCorpseExplosionMaximumForPercent(999, -1));
    assertEquals(37, NativeDifficultyPolicy.staticFieldLifeFloorPercent(context, true));
    assertEquals(0, NativeDifficultyPolicy.staticFieldLifeFloorPercent(context, false));
  }

  @Test
  void absentAndInvalidTableValuesRetainCompatibilityFallbacks() {
    NativeCombatContext missing = NativeCombatContext.normal();
    assertEquals(1, NativeDifficultyPolicy.tableMonsterColdDivisor(missing));
    assertEquals(1, NativeDifficultyPolicy.tableMonsterFreezeDivisor(missing));
    assertEquals(1, NativeDifficultyPolicy.aiCurseDivisor(missing));
    assertEquals(0, NativeDifficultyPolicy.staticFieldLifeFloorPercent(missing, true));
    assertThrows(NullPointerException.class, () -> NativeDifficultyPolicy.tableResistancePenalty(missing));
    assertThrows(NullPointerException.class, () -> NativeDifficultyPolicy.monsterCorpseExplosionMaximum(1, missing));
    DifficultyLevels.Entry row = new DifficultyLevels.Entry();
    row.MonsterFreezeDivisor = -1;
    row.AiCurseDivisor = -1;
    NativeCombatContext context = NativeCombatContext.ofDifficulty(1, row);
    assertEquals(1, NativeDifficultyPolicy.tableMonsterColdDivisor(context));
    assertEquals(1, NativeDifficultyPolicy.tableMonsterFreezeDivisor(context));
    assertEquals(1, NativeDifficultyPolicy.aiCurseDivisor(context));
    for (int d = 0; d < 3; d++) {
      assertEquals(80 / (1 << d), NativeDifficultyPolicy.scaleMonsterColdDuration(80, d));
    }
  }

  @Test
  void packetKeepsFixedPointAndPoisonStateSemantics() {
    NativeDamagePacket packet = new NativeDamagePacket();
    packet.physicalDamage = 10;
    packet.elementalScale = 256;
    packet.elementalDamage[NativeCombatConstants.DAMAGE_FIRE] = 384;
    packet.elementalDamage[NativeCombatConstants.DAMAGE_POISON] = 256;
    packet.poisonDuration = 25;
    packet.absorbedLife = 128;
    assertEquals(11.5f, packet.totalDamageHp());
    assertEquals(0.5f, packet.absorbedLifeHp());
    packet.poisonDuration = 0;
    assertEquals(12.5f, packet.totalDamageHp());
    packet.elementalScale = 1;
    packet.totalDamage = 17;
    assertEquals(17f, packet.totalDamageHp());
  }

  @Test
  void packetResetRestoresAllPublicPayloadDefaults() throws IllegalAccessException {
    NativeDamagePacket packet = new NativeDamagePacket();
    for (java.lang.reflect.Field field : NativeDamagePacket.class.getFields()) {
      if (field.getType() == int.class) field.setInt(packet, 91);
      else if (field.getType() == float.class) field.setFloat(packet, 3.5f);
      else if (field.getType() == boolean.class) field.setBoolean(packet, true);
      else if (field.getType() == int[].class) java.util.Arrays.fill((int[]) field.get(packet), 91);
      else fail("Add reset coverage for " + field);
    }
    packet.reset();
    NativeDamagePacket defaults = new NativeDamagePacket();
    for (java.lang.reflect.Field field : NativeDamagePacket.class.getFields()) {
      if (field.getType() == int[].class) assertArrayEquals((int[]) field.get(defaults), (int[]) field.get(packet));
      else assertEquals(field.get(defaults), field.get(packet), field.getName());
    }
    for (int defense = 0; defense <= 5; defense++) {
      packet.defenseType = defense;
      assertEquals(defense >= 2 && defense <= 4, packet.isAmazonPassiveDefense());
    }
  }
}
