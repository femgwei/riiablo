package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class ScarabAttackChoiceTest extends RiiabloTest {
  @Test void attackChoiceEndpointsUseA1OnSuccess() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int chance : new int[] {0, 100}) {
        verifyChoice(difficulty, chance, 12);
      }
    }
  }

  @Test void intermediateChancePreservesNativeRandomSequence() {
    for (int seed : new int[] {0, 1, 2, 123456}) verifyChoice(0, 37, seed);
  }

  private void verifyChoice(int difficulty, int chance, int seedValue) {
    try (Fixture f = new Fixture("scarab1", difficulty, 1)) {
      Scarab ai = (Scarab) f.ai;
      ai.params[0] = 100;
      ai.params[3] = 0;
      ai.params[1] = chance;
      Riiablo.gameSeed = seedValue;
      D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
      Seed.rollPercentage(seed); // Attack chance.
      Seed.rollPercentage(seed); // Configured Jab exists, but its chance is zero.
      byte expected = Seed.rollPercentage(seed) < chance
          ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
      ai.update(0.04f);
      assertEquals("ATTACK", ai.getState());
      assertEquals(ai.SLEEP, ai.time);
      assertEquals(expected, f.sequence().mode1);
      assertEquals(Engine.Monster.MODE_NU, f.sequence().mode2);
      assertEquals(com.riiablo.skill.SkillCodes.attack, f.casting().skillId);
      assertEquals(f.target, f.casting().targetId);
      assertEquals(f.targetPosition(), f.casting().targetVec);
      assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
    }
  }
}
