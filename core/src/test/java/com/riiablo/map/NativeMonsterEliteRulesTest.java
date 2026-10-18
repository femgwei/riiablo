package com.riiablo.map;

import com.riiablo.codec.excel.LosslessTxtTable;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.monster.MonsterAffix;
import com.riiablo.engine.server.monster.MonsterRank;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterEliteRulesTest {
  @Test void minimumPressureUsesVisitedRoomFractionAndMaximumRollIsInclusive() {
    assertTrue(NativeMonsterEliteRules.selectElite(0, 1, 2, 1, 4, rolls(24)));
    assertFalse(NativeMonsterEliteRules.selectElite(0, 1, 2, 1, 4, rolls(25, 6, 0)));
    assertTrue(NativeMonsterEliteRules.selectElite(1, 1, 2, 1, 4, rolls(5)));
    assertFalse(NativeMonsterEliteRules.selectElite(1, 1, 2, 1, 4, rolls(6, 0)));
    assertFalse(NativeMonsterEliteRules.selectElite(2, 1, 2, 4, 4, rolls(0)));
    assertFalse(NativeMonsterEliteRules.selectElite(0, 1, 2, 0, 0, rolls(6, 0)));
  }

  @Test void championBoundaryAndVariantAreDataDriven() throws Exception {
    NativeMonsterEliteRules rules = rules();
    NativeMonsterEliteRules.Roll champion = rules.roll(monster(), modes(), 0, rolls(19, 0));
    assertTrue(champion.champion);
    assertEquals(MonsterRank.CHAMP_GHOSTLY, champion.championType);
    assertArrayEquals(new int[] {36}, champion.mods);
    assertFalse(rules.roll(monster(), modes(), 0, rolls(20, 0)).champion);
  }

  @Test void difficultyChoosesOneTwoThreeDistinctModifiersAndTransfersOnlyXfer() throws Exception {
    for (int diff = 0; diff < 3; diff++) {
      NativeMonsterEliteRules.Roll roll = rules().roll(monster(), modes(), diff, rolls(99, 0, 0, 0));
      assertEquals(diff + 1, roll.mods.length);
      assertEquals(diff + 1, java.util.Arrays.stream(roll.mods).distinct().count());
      assertEquals(MonsterAffix.EXTRA_STRONG | (diff == 2 ? MonsterAffix.TELEPORTATION : 0L), roll.minionAffixes);
      if (diff > 0) assertTrue(MonsterAffix.hasAffix(roll.affixes, MonsterAffix.AURA_ENCHANTED));
      assertFalse(MonsterAffix.hasAffix(roll.minionAffixes, MonsterAffix.AURA_ENCHANTED));
      if (diff == 2) assertEquals(26, roll.mods[2]);
    }
  }

  @Test void typeAncestryAndModeFiltersExcludeInvalidModifiers() throws Exception {
    MonStats.Entry monster = monster();
    monster.MonType = "child";
    monster.isMelee = true;
    MonStats2.Entry modes = modes();
    modes.mMode[2] = modes.mMode[4] = false;
    NativeMonsterEliteRules.Roll roll = rules().roll(monster, modes, 2, rolls(99, 0, 0, 0));
    assertEquals(MonsterAffix.EXTRA_STRONG | MonsterAffix.AURA_ENCHANTED, roll.affixes);
    assertEquals(0, roll.mods[2], "all other candidates disabled or ineligible");
  }

  @Test void difficultyWeightsDoNotLeakNormalOnlyMods() throws Exception {
    NativeMonsterEliteRules rules = rules();
    assertEquals(25, rules.roll(monster(), modes(), 0, rolls(99, 2)).mods[0]);
    assertNotEquals(25, rules.roll(monster(), modes(), 1, rolls(99, 2, 0)).mods[0]);
  }

  private static NativeMonsterEliteRules rules() throws Exception {
    String header = "id\tenabled\tchampion\tconstants\tcpick\tcpick (N)\tcpick (H)\tupick\tupick (N)\tupick (H)\txfer\tfPick\texclude1\texclude2\n";
    return new NativeMonsterEliteRules(table(header
        + "0\t0\t0\t20\n"
        + "5\t1\t0\t0\t0\t0\t0\t1\t1\t1\t1\t0\n"
        + "30\t1\t0\t0\t0\t0\t0\t1\t1\t1\t0\t0\n"
        + "25\t1\t0\t0\t0\t0\t0\t1\t0\t0\t1\t0\tparent\n"
        + "26\t1\t0\t0\t0\t0\t0\t0\t1\t1\t1\t1\n"
        + "6\t1\t0\t0\t0\t0\t0\t0\t1\t1\t1\t3\n"
        + "29\t1\t0\t0\t0\t0\t0\t0\t1\t1\t1\t2\n"
        + "9\t0\t0\t0\t0\t0\t0\t99\t99\t99\t1\n"
        + "36\t1\t1\t0\t1\t1\t1\t0\t0\t0\t0\n"),
        table("type\tequiv1\tequiv2\tequiv3\nchild\tparent\nparent\tchild\n"));
  }

  private static LosslessTxtTable table(String text) throws Exception {
    return LosslessTxtTable.parse(text.getBytes(StandardCharsets.ISO_8859_1));
  }
  private static MonStats.Entry monster() { return new MonStats.Entry(); }
  private static MonStats2.Entry modes() {
    MonStats2.Entry entry = new MonStats2.Entry();
    entry.mMode = new boolean[16];
    entry.mMode[2] = entry.mMode[4] = true;
    return entry;
  }
  private static Random rolls(int... values) {
    return new Random() {
      int index;
      @Override public int nextInt(int bound) {
        assertTrue(index < values.length, "unexpected RNG call");
        assertTrue(values[index] >= 0 && values[index] < bound, "out of range scripted roll");
        return values[index++];
      }
    };
  }
}
