package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class VultureAttackCadenceTest extends RiiabloTest {
  @Test void attackCompletionWaitsFifteenFramesBeforeNextAttack() {
    try (Fixture f = new Fixture("vulture1", 0, 2)) {
      Vulture ai = (Vulture) f.ai;
      ai.SLEEP = 15 * .04f;
      ai.params[0] = 100;
      ai.update(.04f);
      assertNotNull(f.casting());
      f.finishAction();
      ai.onActionReturnedToNeutral();
      for (int i = 1; i < 15; i++) {
        ai.update(.04f);
        assertNull(f.casting(), "attack must wait through frame " + i);
      }
      ai.update(.04f);
      assertNotNull(f.casting(), "attack becomes eligible exactly at AIdel");
    }
  }

  @Test void neutralKeepsFutureIdleEventAndUsesNativeFallbackDelay() {
    try (Fixture f = new Fixture("vulture1", 0, 2)) {
      Vulture ai = (Vulture) f.ai;
      ai.SLEEP = 0;
      ai.idle(4);
      ai.onActionReturnedToNeutral();
      assertTrue(ai.getDecisionDiagnostics().contains("due=4 "));
      ai.attack(f.target);
      f.finishAction();
      ai.onActionReturnedToNeutral();
      assertTrue(ai.getDecisionDiagnostics().contains("due=" + (ai.nativeFrame() + 15) + " "));
    }
  }
}
