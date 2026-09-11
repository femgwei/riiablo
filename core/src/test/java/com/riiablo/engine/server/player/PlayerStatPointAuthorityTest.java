package com.riiablo.engine.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Regression coverage for level-up attribute point allocation. */
class PlayerStatPointAuthorityTest extends RiiabloTest {
  @Test
  void eachAttributePointIsConsumedAndMirroredToAggregateStats() {
    CharData data = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "StatHero", (byte) CharacterClass.AMAZON.id);
    data.level = 2;
    data.getStats().base().put(Stat.level, 2);
    data.getStats().aggregate().put(Stat.level, 2);
    data.getStats().base().put(Stat.statpts, 2);
    data.getStats().aggregate().put(Stat.statpts, 2);

    assertEquals(PlayerStatsManager.RESULT_SUCCESS,
        PlayerStatsManager.INSTANCE.spendStatPoint(
            data, PlayerStatsManager.STAT_TYPE_STRENGTH));
    assertEquals(1, data.getStats().base().get(Stat.strength).asInt());
    assertEquals(1, data.getStats().aggregate().get(Stat.strength).asInt());
    assertEquals(1, PlayerStatsManager.INSTANCE.getAvailableStatPoints(data));

    assertEquals(PlayerStatsManager.RESULT_SUCCESS,
        PlayerStatsManager.INSTANCE.spendStatPoint(
            data, PlayerStatsManager.STAT_TYPE_VITALITY));
    assertEquals(1, data.getStats().base().get(Stat.vitality).asInt());
    assertEquals(0, PlayerStatsManager.INSTANCE.getAvailableStatPoints(data));
    assertEquals(PlayerStatsManager.RESULT_NO_POINTS,
        PlayerStatsManager.INSTANCE.spendStatPoint(
            data, PlayerStatsManager.STAT_TYPE_ENERGY));
    System.out.println("[STAT_POINT_AUTH] strength=1 vitality=1 points=0 status=PASS");
  }
}
