package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import org.junit.jupiter.api.Test;

/** Pure D2MOO parameter, integer-life and seed-consumption boundaries. */
class NativeAiDecisionTest {
  @Test
  void selectsDifficultyColumnsAndHandlesMissingSyntheticData() {
    int[] columns = {11, 22, 33};
    assertEquals(11, AI.difficultyValue(columns, 0));
    assertEquals(22, AI.difficultyValue(columns, 1));
    assertEquals(33, AI.difficultyValue(columns, 2));
    assertEquals(11, AI.difficultyValue(columns, -1));
    assertEquals(33, AI.difficultyValue(columns, 3));
    assertEquals(7, AI.difficultyValue(new int[] {7}, 2));
    assertEquals(0, AI.difficultyValue(null, 2));
    assertEquals(0, AI.difficultyValue(new int[0], 2));
  }

  @Test
  void percentageUsesD2SeedAndConsumesCertainAndImpossibleRolls() {
    int oldSeed = Riiablo.gameSeed;
    try {
      Riiablo.gameSeed = 0xDEADBEEF;
      int id = 0x42;
      AI ai = new AI(id) {};
      AI twin = new AI(id) {};
      D2Seed expected = new D2Seed(Riiablo.gameSeed ^ (id * 0x45D9F3B), 666);
      int[] chances = {0, 100, -10, 250, 1, 25, 50, 80, 99};
      for (int i = 0; i < 500; i++) {
        int chance = chances[i % chances.length];
        boolean result = Seed.rollPercentage(expected) < chance;
        assertEquals(result, ai.rollAiChance(chance), "roll " + i);
        assertEquals(result, twin.rollAiChance(chance), "independent unit stream " + i);
      }
    } finally {
      Riiablo.gameSeed = oldSeed;
    }
  }

  @Test
  void smithUsesIntegerLifeBeforePercentageAndClampsSpeedBonus() {
    assertEquals(50, Smith.lifePercentage(10, 20));
    assertEquals(0, Smith.lifePercentage(0, 100));
    assertEquals(0, Smith.lifePercentage(100, 0));
    assertEquals(100, Smith.lifePercentage(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertEquals(0, Smith.velocityBonus(101));
    assertEquals(0, Smith.velocityBonus(100));
    assertEquals(0, Smith.velocityBonus(99));
    assertEquals(25, Smith.velocityBonus(50));
    assertEquals(50, Smith.velocityBonus(0));
    assertEquals(50, Smith.velocityBonus(-5));
  }

  @Test
  void andarielParamSlotsMatchNativeEnum() {
    assertEquals(0, Andariel.PARAM_SPRAY_IN_MELEE_CHANCE);
    assertEquals(1, Andariel.PARAM_STALL_CHANCE);
    assertEquals(2, Andariel.PARAM_FIRE_OR_ENGAGE_CHANCE);
    assertEquals(3, Andariel.PARAM_SPRAY_FROM_DISTANCE_CHANCE);
  }
}
