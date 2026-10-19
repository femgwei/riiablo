package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** D2MOO 0c0bbd1 source-derived literals, not captured EXE/DLL values or a frozen host oracle. */
@Tag("monster-parity-audit")
class MonsterStatsOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"0,1,101,40,10,20", "0,8,454,40,10,20", "1,2,151,42,10,21",
      "1,8,454,57,14,28", "2,8,454,57,14,28", "2,10,505,60,15,30"})
  void lodHpAndModeProfilesFollowDifferentPlayerCountRules(int difficulty, int players,
      int hp, int ar, int min, int max) {
    // MonStats HP/AR/DM ratios are 100/100/100..200; MonLvl bases 101/40/10.
    // Monster.cpp HP: H + floor(H*50*(players-1)/100) through 8 players.
    // MonsterMode.cpp AR/DM: V + floor(V*8*(players-1)/128), only NM/Hell;
    // for players>=9 the multiplier is 8*players-16.
    MonStats.Entry row = new MonStats.Entry();
    row.minHP = row.maxHP = row.A1TH = row.A1MinD = new int[] {100,100,100};
    row.A1MaxD = new int[] {200,200,200};
    MonLvl.Entry level = new MonLvl.Entry();
    level.LHP = new int[] {101,101,101}; level.LTH = new int[] {40,40,40};
    level.LDM = new int[] {10,10,10};
    MonsterStatsSnapshot actual = NativeMonsterStatsCalculator.spawnSnapshot(
        NativeCombatContext.ofDifficulty(difficulty), row, level, 40, 0, players);
    assertEquals(hp, actual.minHp); assertEquals(hp, actual.maxHp);
    assertEquals(ar, actual.attack1.attackRating);
    assertEquals(min, actual.attack1.minDamage); assertEquals(max, actual.attack1.maxDamage);
  }

  @ParameterizedTest
  @CsvSource({"123,456,100,560", "5000001,500,100,25000000",
      "101,65537,100,66155", "99,1000000,100,990000", "100,50,0,0"})
  void ratioUsesOriginalDivisionBranches(int value, int multiplier, int divisor, int expected) {
    // DATATBLS_ApplyRatio: large first argument divides before multiplying.
    assertEquals(expected, NativeMonsterStatsCalculator.applyRatio(value, multiplier, divisor));
  }
}
