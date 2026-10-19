package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Files;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.combat.MonsterStatsSnapshot;
import java.lang.reflect.Field;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Frozen 776c385cd migration equivalence; independent native expectations live in domain. */
class MonsterStatsMigrationTest {
  @Test void twoThousandFlagAndTableInputsMatchTheFrozenHost() throws Exception {
    Fixture f = new Fixture(); Random random = new Random(0x57A75);
    for (int i = 0; i < 2000; i++) {
      randomArrays(f.monster, random); randomArrays(f.level, random);
      f.monster.noRatio = random.nextBoolean();
      int gameType = random.nextInt(2), difficulty = random.nextInt(8) - 3;
      int level = random.nextInt(130) - 2;
      short flags = (short) random.nextInt(512);
      MonsterStatsCalculator.MonsterStatsInit actual = new MonsterStatsCalculator.MonsterStatsInit();
      PreviousMonsterStatsCalculator.MonsterStatsInit before = new PreviousMonsterStatsCalculator.MonsterStatsInit();
      for (Field field : PreviousMonsterStatsCalculator.MonsterStatsInit.class.getFields()) {
        int sentinel = random.nextInt();
        field.setInt(before, sentinel);
        actual.getClass().getField(field.getName()).setInt(actual, sentinel);
      }
      assertEquals(PreviousMonsterStatsCalculator.calculateMonsterStatsByLevel(
          f.files, 77, gameType, difficulty, level, flags, before),
          MonsterStatsCalculator.calculateMonsterStatsByLevel(
              f.files, 77, gameType, difficulty, level, flags, actual), "case " + i);
      for (Field field : PreviousMonsterStatsCalculator.MonsterStatsInit.class.getFields()) {
        assertEquals(field.getInt(before), actual.getClass().getField(field.getName()).getInt(actual),
            "case " + i + " field " + field.getName());
      }
    }
  }

  @Test void spawnSnapshotMatchesOldFactoryCompositionForRanksCountsAndEveryBaseRoll() throws Exception {
    Fixture f = new Fixture(); Random random = new Random(0x5FA7);
    for (int i = 0; i < 500; i++) {
      randomArrays(f.monster, random); randomArrays(f.level, random);
      f.monster.noRatio = random.nextBoolean(); f.monster.Align = random.nextInt(3);
      int difficulty = random.nextInt(3), count = random.nextInt(13) - 1;
      int rank = random.nextInt(6), baseLevel = random.nextInt(120), gameType = random.nextInt(2);
      PreviousMonsterStatsCalculator.MonsterStatsInit base = new PreviousMonsterStatsCalculator.MonsterStatsInit();
      assertTrue(PreviousMonsterStatsCalculator.calculateMonsterStatsByLevel(
          f.files, 77, gameType, difficulty, baseLevel, (short) 7, base));
      MonsterStatsSnapshot actual = MonsterStatsCalculator.spawnSnapshot(f.files, f.monster,
          gameType, difficulty, baseLevel, rank, count);
      int players = PreviousMonsterStatsCalculator.nativePlayerCount(f.monster, count);
      assertEquals(players, actual.playerCount);
      assertEquals(baseLevel + PreviousMonsterStatsCalculator.nativeRankLevelBonus(rank), actual.level);
      assertEquals(base.AC, actual.defense);
      int experience = base.Exp + (int) ((long) base.Exp
          * PreviousMonsterStatsCalculator.nativeExperienceBonus(players) / 100);
      assertEquals(PreviousMonsterStatsCalculator.nativeRankExperience(experience, rank), actual.experience);
      int min = Math.min(base.minHP, base.maxHP), max = Math.max(base.minHP, base.maxHP);
      assertEquals(min, actual.baseMinHp); assertEquals(max, actual.baseMaxHp);
      for (int roll : new int[] {min, min + (max - min) / 2, max}) {
        int hp = Math.min(roll + (int) ((long) roll
            * PreviousMonsterStatsCalculator.nativeHpBonus(players) / 100), (1 << 23) - 1);
        hp = PreviousMonsterStatsCalculator.nativeRankHitpoints(hp, rank, difficulty);
        assertEquals(hp, actual.hitpointsForBaseRoll(roll), "HP case " + i);
      }
      for (int attack = 0; attack < 3; attack++) {
        short flag = (short) (8 << attack);
        PreviousMonsterStatsCalculator.MonsterStatsInit old = new PreviousMonsterStatsCalculator.MonsterStatsInit();
        PreviousMonsterStatsCalculator.calculateMonsterStatsByLevel(
            f.files, 77, gameType, difficulty, baseLevel, flag, old);
        PreviousMonsterStatsCalculator.applyNativeCombatMultiplier(old,
            PreviousMonsterStatsCalculator.nativeCombatMultiplier(f.monster, difficulty, players, gameType != 0));
        com.riiablo.engine.server.combat.NativeMonsterStatsCalculator.Attack profile =
            attack == 0 ? actual.attack1 : attack == 1 ? actual.attack2 : actual.skill1;
        assertEquals(old.TH, profile.attackRating);
        assertEquals(attack == 0 ? old.A1MinD : attack == 1 ? old.A2MinD : old.S1MinD, profile.minDamage);
        assertEquals(attack == 0 ? old.A1MaxD : attack == 1 ? old.A2MaxD : old.S1MaxD, profile.maxDamage);
      }
    }
  }

  @Test void missingTablesAndRowsKeepFailureSemantics() throws Exception {
    Fixture f = new Fixture();
    assertNull(MonsterStatsCalculator.spawnSnapshot(null, f.monster, 1, 0, 5, 0, 1));
    assertNull(MonsterStatsCalculator.spawnSnapshot(f.files, null, 1, 0, 5, 0, 1));
    assertNull(MonsterStatsCalculator.spawnSnapshot(f.files, f.monster, 1, 0, -1, 0, 1));
    when(f.monsters.get(77)).thenReturn(null);
    assertFalse(MonsterStatsCalculator.calculateMonsterStatsByLevel(
        f.files, 77, 1, 0, 5, (short) 7, new MonsterStatsCalculator.MonsterStatsInit()));
    when(f.levels.get(anyInt())).thenReturn(null);
    assertNull(MonsterStatsCalculator.spawnSnapshot(f.files, f.monster, 1, 0, 5, 0, 1));
  }

  private static void randomArrays(Object row, Random random) throws Exception {
    for (Field field : row.getClass().getFields()) {
      if (field.getType() == int[].class) {
        int length = random.nextInt(5);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = random.nextInt(1000);
        field.set(row, values);
      }
    }
  }

  private static final class Fixture {
    final Files files = mock(Files.class);
    final MonStats monsters = mock(MonStats.class);
    final MonLvl levels = mock(MonLvl.class);
    final MonStats.Entry monster = new MonStats.Entry();
    final MonLvl.Entry level = new MonLvl.Entry();
    Fixture() throws Exception {
      for (String name : new String[] {"monstats", "MonLvl"}) {
        Field field = Files.class.getField(name); field.setAccessible(true);
        field.set(files, name.equals("monstats") ? monsters : levels);
      }
      when(monsters.get(77)).thenReturn(monster);
      when(levels.size()).thenReturn(100); when(levels.get(anyInt())).thenReturn(level);
    }
  }
}
