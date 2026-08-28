package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterRank;
import org.junit.jupiter.api.Test;

class MonsterStatsCalculatorNativeTest extends RiiabloTest {
  @Test
  void calculatesRealTableStatsWithNativeMonLvlRatios() {
    MonStats.Entry monster = Riiablo.files.monstats.get("quillrat1");
    assertNotNull(monster);
    int level = monster.Level[0];
    MonLvl.Entry monLvl = Riiablo.files.MonLvl.get(level);
    assertNotNull(monLvl);

    MonsterStatsCalculator.MonsterStatsInit result =
        new MonsterStatsCalculator.MonsterStatsInit();
    assertTrue(MonsterStatsCalculator.calculateMonsterStatsByLevel(
        monster.hcIdx, 1, 0, level, (short) 15, result));

    assertEquals(MonsterStatsCalculator.applyRatio(
        monLvl.LHP[0], monster.minHP[0], 100), result.minHP);
    assertEquals(MonsterStatsCalculator.applyRatio(
        monLvl.LHP[0], monster.maxHP[0], 100), result.maxHP);
    assertEquals(MonsterStatsCalculator.applyRatio(
        monLvl.LAC[0], monster.AC[0], 100), result.AC);
    assertEquals(MonsterStatsCalculator.applyRatio(
        monLvl.LXP[0], monster.Exp[0], 100), result.Exp);
    assertEquals(MonsterStatsCalculator.applyRatio(
        monLvl.LDM[0], monster.A1MaxD[0], 100), result.A1MaxD);
  }

  @Test
  void selectsExpansionNightmareAreaLevelForRatioMonsters() {
    MonStats.Entry monster = Riiablo.files.monstats.get("fallen1");
    Levels.Entry bloodMoor = Riiablo.files.Levels.get(2);
    assertNotNull(monster);
    assertNotNull(bloodMoor);

    assertEquals(bloodMoor.MonLvlEx[1],
        MonsterStatsCalculator.resolveMonsterLevel(monster, bloodMoor, 1, true));

    MonStats.Entry noRatio = new MonStats.Entry();
    noRatio.Level = monster.Level.clone();
    noRatio.noRatio = true;
    assertEquals(noRatio.Level[1],
        MonsterStatsCalculator.resolveMonsterLevel(noRatio, bloodMoor, 1, true));
  }

  @Test
  void mirrorsPlayerCountAndUniqueLevelExperienceModifiers() {
    MonStats.Entry evil = new MonStats.Entry();
    evil.Align = 0;
    MonStats.Entry allied = new MonStats.Entry();
    allied.Align = 1;

    assertEquals(4, MonsterStatsCalculator.nativePlayerCount(evil, 4));
    assertEquals(1, MonsterStatsCalculator.nativePlayerCount(allied, 4));
    assertEquals(150, MonsterStatsCalculator.nativeHpBonus(4));
    assertEquals(150, MonsterStatsCalculator.nativeExperienceBonus(4));
    assertEquals(2, MonsterStatsCalculator.nativeRankLevelBonus(MonsterRank.CHAMPION));
    assertEquals(3, MonsterStatsCalculator.nativeRankLevelBonus(MonsterRank.UNIQUE));
    assertEquals(300, MonsterStatsCalculator.nativeRankExperience(100, MonsterRank.CHAMPION));
    assertEquals(500, MonsterStatsCalculator.nativeRankExperience(100, MonsterRank.UNIQUE));
  }

  @Test
  void applyRatioMatchesNativeZeroAndOverflowAvoidanceBranches() {
    assertEquals(0, MonsterStatsCalculator.applyRatio(100, 50, 0));
    assertEquals(560, MonsterStatsCalculator.applyRatio(123, 456, 100));
    assertEquals(25_000_000,
        MonsterStatsCalculator.applyRatio(5_000_000, 500, 100));
  }
}
