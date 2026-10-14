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
  void rawItemRollAndPercentageAdvanceOneSharedNativeStream() {
    int previous = Riiablo.gameSeed;
    try {
      Riiablo.gameSeed = 0xFEDCBA98;
      AI ai = new AI(0x42) {};
      D2Seed expected = new D2Seed(Riiablo.gameSeed ^ (0x42 * 0x45D9F3B), 666);
      for (int i = 0; i < 100; i++) {
        assertEquals(Seed.rollRandomNumber(expected) & 0xFFFFFFFFL, ai.rollAiRandomNumber());
        assertEquals(Seed.rollPercentage(expected), ai.rollAiPercentage());
      }
    } finally {
      Riiablo.gameSeed = previous;
    }
  }

  @Test
  void mephistoSkillBucketsRespectDifficultyAndStrictDistanceBoundaries() {
    assertEquals(0, Mephisto.PARAM_CAST_OR_MELEE_CHANCE);
    assertEquals(1, Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE);
    assertEquals(2, Mephisto.PARAM_SECOND_TARGET_CHANCE);
    for (int roll = 0; roll < 100; roll++) {
      int ordinary = roll < 32 ? 3 : roll < 48 ? 1 : 0;
      int close = roll < 16 ? 4 : ordinary;
      assertEquals(ordinary, Mephisto.selectSkillSlot(roll, 6, 10f, 0, true));
      for (int difficulty = 1; difficulty <= 2; difficulty++) {
        assertEquals(close, Mephisto.selectSkillSlot(roll, 6, 14.99f, difficulty, false));
        assertEquals(ordinary, Mephisto.selectSkillSlot(roll, 6, 15f, difficulty, false));
        assertEquals(ordinary, Mephisto.selectSkillSlot(roll, 6, 30f, difficulty, false));
        assertEquals(5, Mephisto.selectSkillSlot(roll, 6, 30.01f, difficulty, false));
        assertEquals(5, Mephisto.selectSkillSlot(roll, 6, 10f, difficulty, true));
      }
    }
  }

  @Test
  void tentacleParamSlotsMatchD2Moo() {
    assertEquals(0, TentacleAI.PARAM_ATTACK_CHANCE);
    assertEquals(1, TentacleAI.PARAM_SUBMERGE_CHANCE);
    assertEquals(2, TentacleAI.PARAM_SUBMERGE_SECONDS);
    assertEquals(3, TentacleAI.PARAM_EMERGE_SECONDS);
    assertEquals(4, TentacleAI.PARAM_STALL_DURATION);
    assertEquals(5, TentacleAI.PARAM_ACTIVE_DISTANCE);
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

  @Test
  void remainingAct1ParamSlotsMatchNativeEnums() {
    assertEquals(0, Arach.PARAM_ATTACK_CHANCE);
    assertEquals(1, Arach.PARAM_CIRCLE_IN_MELEE_CHANCE);
    assertEquals(2, Arach.PARAM_ENGAGE_CHANCE);
    assertEquals(3, Arach.PARAM_RUN_DISTANCE);
    assertEquals(4, Arach.PARAM_HURT_PCT);
    assertEquals(0, GargoyleTrap.PARAM_SHOOT_DISTANCE);
    assertEquals(1, GargoyleTrap.PARAM_SHOOT_CHANCE);
    assertEquals(2, GargoyleTrap.PARAM_FIRE_RECOVERY);
    assertEquals(3, GargoyleTrap.PARAM_STALL_DURATION);
  }
}
