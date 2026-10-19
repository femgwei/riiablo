package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D2MOO-derived oracle over actual installation data, not output frozen from the resolver. */
class MonsterMeleeDataReferenceTest extends RiiabloTest {
  @ParameterizedTest(name = "{0}: classic/LoD, three difficulties, A1/A2/S1, players 1/2/8")
  @ValueSource(strings = {"fallen1", "skeleton1", "zombie1", "corruptrogue1", "goatman1", "quillrat1"})
  void attackProfilesMatchIndependentTableArithmetic(String id) {
    MonStats.Entry monster = Riiablo.files.monstats.get(id);
    assertNotNull(monster, id);
    for (boolean expansion : new boolean[] {false, true}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        // Explicit level input: separate from the area's spawn-level selection rules.
        int level = new int[] {5, 40, 85}[difficulty];
        MonLvl.Entry base = Riiablo.files.MonLvl.get(level);
        assertNotNull(base);
        int[] dm = expansion ? base.LDM : base.DM;
        int[] th = expansion ? base.LTH : base.TH;
        for (int attack = 0; attack < 3; attack++) {
          int[] rawMin = attack == 0 ? monster.A1MinD : attack == 1 ? monster.A2MinD : monster.S1MinD;
          int[] rawMax = attack == 0 ? monster.A1MaxD : attack == 1 ? monster.A2MaxD : monster.S1MaxD;
          int[] rawTh = attack == 0 ? monster.A1TH : attack == 1 ? monster.A2TH : monster.S1TH;
          int mode = new int[] {Engine.Monster.MODE_A1, Engine.Monster.MODE_A2, Engine.Monster.MODE_S1}[attack];
          for (int players : new int[] {1, 2, 8}) {
            String context = id + " expansion=" + expansion + " difficulty=" + difficulty
                + " level=" + level + " attack=" + attack + " players=" + players;
            // MonsterTbls.cpp DATATBLS_CalculateMonsterStatsByLevel and
            // MonsterMode.cpp sub_6FC627B0. No production math helper is used here.
            int numerator = expansion && difficulty > 0 && monster.Align == 0
                ? 8 * (players - 1) : 0;
            int expectedMin = reference(rawMin[difficulty], dm[difficulty], monster.noRatio, numerator);
            int expectedMax = Math.max(expectedMin,
                reference(rawMax[difficulty], dm[difficulty], monster.noRatio, numerator));
            int expectedTh = reference(rawTh[difficulty], th[difficulty], monster.noRatio, numerator);
            MonsterModeDamageResolver.Profile actual = MonsterModeDamageResolver.resolve(
                Riiablo.files, bound -> 99, monster, level, difficulty, mode, players, expansion);
            assertEquals(expectedMin, actual.minDamage, context + " min");
            assertEquals(expectedMax, actual.maxDamage, context + " max");
            assertEquals(expectedTh, actual.attackRating, context + " AR");
          }
        }
      }
    }
  }

  private static int reference(int ratio, int base, boolean noRatio, int numerator) {
    // These real combat table values are below DATATBLS_ApplyRatio's overflow branches.
    assertTrue(base >= 0 && base <= 0x100000 && ratio >= 0 && ratio <= 0x10000);
    int value = noRatio ? ratio : (int) ((long) base * ratio / 100);
    return value + (int) ((long) numerator * value / 128);
  }
}
