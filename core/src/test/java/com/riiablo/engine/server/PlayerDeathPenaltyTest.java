package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class PlayerDeathPenaltyTest extends RiiabloTest {
  @Test
  void levelTenLosesTenPercentOfCombinedWallet() {
    CharData player = character(10, 20, 980);

    PlayerDeathPenalty.Result result = PlayerDeathPenalty.apply(player);

    assertEquals(10, result.penaltyPercent);
    assertEquals(100, result.penaltyGold);
    assertEquals(100, result.droppedGold);
    assertEquals(0, value(player, Stat.gold));
    assertEquals(900, value(player, Stat.goldbank));
    assertEquals(100, value(player, Stat.goldlost));
  }

  @Test
  void penaltyCapsAtTwentyPercentAndAllCarriedGoldStillDrops() {
    CharData player = character(30, 300, 700);

    PlayerDeathPenalty.Result result = PlayerDeathPenalty.apply(player);

    assertEquals(20, result.penaltyPercent);
    assertEquals(200, result.penaltyGold);
    assertEquals(300, result.droppedGold);
    assertEquals(0, value(player, Stat.gold));
    assertEquals(700, value(player, Stat.goldbank));
  }

  @Test
  void stashCoversPenaltyWhenCarriedGoldIsInsufficient() {
    CharData player = character(10, 0, 1_000);

    PlayerDeathPenalty.Result result = PlayerDeathPenalty.apply(player);

    assertEquals(100, result.droppedGold);
    assertEquals(900, result.bankAfter);
  }

  private static CharData character(int level, int carried, int bank) {
    CharData player = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "DeathPenalty", Riiablo.AMAZON);
    player.level = (byte) level;
    player.getStats().base().put(Stat.level, level);
    player.getStats().aggregate().put(Stat.level, level);
    player.getStats().base().put(Stat.gold, carried);
    player.getStats().aggregate().put(Stat.gold, carried);
    player.getStats().base().put(Stat.goldbank, bank);
    player.getStats().aggregate().put(Stat.goldbank, bank);
    return player;
  }

  private static int value(CharData player, short stat) {
    return player.getStats().get(stat).asInt();
  }
}
