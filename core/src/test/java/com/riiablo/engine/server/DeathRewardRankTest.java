package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterRank;
import org.junit.jupiter.api.Test;

class DeathRewardRankTest {
  @Test
  void selectsNativeTreasureClassColumnByRank() {
    MonStats.Entry stats = stats();

    assertSame(stats.TreasureClass1,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.NORMAL, false));
    assertSame(stats.TreasureClass1,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.MINION, false));
    assertSame(stats.TreasureClass2,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.CHAMPION, false));
    assertSame(stats.TreasureClass3,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.UNIQUE, false));
    assertSame(stats.TreasureClass3,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.BOSS, false));
    assertSame(stats.TreasureClass3,
        DeathRewardSystem.treasureClassColumn(stats, MonsterRank.NORMAL, true));
  }

  @Test
  void appliesNativeEliteMonsterLevelOffsets() {
    MonStats.Entry stats = stats();
    stats.Level = new int[] {5, 35, 70};

    assertEquals(5, DeathRewardSystem.monsterLevel(stats, 0, MonsterRank.NORMAL));
    assertEquals(7, DeathRewardSystem.monsterLevel(stats, 0, MonsterRank.CHAMPION));
    assertEquals(8, DeathRewardSystem.monsterLevel(stats, 0, MonsterRank.UNIQUE));
    assertEquals(8, DeathRewardSystem.monsterLevel(stats, 0, MonsterRank.SUPER_UNIQUE));
    assertEquals(5, DeathRewardSystem.monsterLevel(stats, 0, MonsterRank.BOSS));
    assertTrue(DeathRewardSystem.isEliteRank(MonsterRank.CHAMPION));
    assertTrue(DeathRewardSystem.isEliteRank(MonsterRank.UNIQUE));
  }

  private static MonStats.Entry stats() {
    MonStats.Entry stats = new MonStats.Entry();
    stats.TreasureClass1 = new String[] {"normal", "normal-n", "normal-h"};
    stats.TreasureClass2 = new String[] {"champion", "champion-n", "champion-h"};
    stats.TreasureClass3 = new String[] {"unique", "unique-n", "unique-h"};
    return stats;
  }
}
