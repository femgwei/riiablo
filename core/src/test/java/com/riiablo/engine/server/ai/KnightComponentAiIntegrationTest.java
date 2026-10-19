package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

class KnightComponentAiIntegrationTest extends RiiabloTest {
  private void variant(Fixture f, int value) {
    Monster monster = f.world.getMapper(Monster.class).get(f.actor);
    monster.componentVariants[10] = value;
    // Production has rendering data; an absent CofComponents hid the bug.
    int code = Riiablo.files.compcode.index(new String[] {"pos", "fir", "col", "unh"}[value % 4]);
    assertTrue(code >= 4);
    f.world.getMapper(CofComponents.class).create(f.actor).component[10] = code;
  }

  @Test void abyssFiresAllFourVariantsWithRealRenderingCodesInEveryDifficulty() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int v = 0; v < 4; v++) {
        try (Fixture f = new Fixture("doomknight2", difficulty, 15)) {
          variant(f, v);
          f.ai.update(Animation.FRAME_DURATION);
          f.assertSkill(0);
        }
      }
    }
  }

  @Test void oblivionAndDkmagFireAllFourVariantsWithoutBoneSpiritOrCurse() {
    for (String row : new String[] {"doomknight3", "dkmag1"}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        for (int v = 0; v < 4; v++) {
          try (Fixture f = new Fixture(row, difficulty, 15)) {
            variant(f, v);
            f.ai.params[0] = 5;
            f.ai.params[1] = 30;
            f.ai.params[3] = 0;
            f.ai.params[4] = 100;
            f.ai.params[5] = 0;
            f.ai.update(Animation.FRAME_DURATION);
            f.assertSkill(0);
          }
        }
      }
    }
  }

  @Test void abyssKeepsMeleeAndCloseRangeRecoveryInsteadOfShootingEverywhere() {
    try (Fixture f = new Fixture("doomknight2", 0, 1)) {
      variant(f, 3);
      f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
    try (Fixture f = new Fixture("doomknight2", 0, 8)) {
      variant(f, 3);
      f.ai.params[4] = 10;
      f.ai.params[5] = 5;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting(), "non-melee but too close: native recovery gate");
    }
  }

  @Test void nativeOrdinalFourIsStillRejectedEvenWithValidRenderingCode() {
    try (Fixture f = new Fixture("doomknight2", 0, 15)) {
      variant(f, 4);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
    }
  }
}
