package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.NativeAiState;
import org.junit.jupiter.api.Test;

class BaboonRegenerationDecisionTest extends RiiabloTest {
  private Attributes regen(Fixture f, int amount) {
    Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(f.actor).attrs;
    attrs.base().put(Stat.hpregen, amount);
    attrs.aggregate().put(Stat.hpregen, amount);
    return attrs;
  }

  @Test void nativeHitMemoryStartsScaledRegenerationAndEscape() {
    for (int mode : new int[] {3, 19}) {
      try (Fixture f = new Fixture("baboon1", 0, 1)) {
        Baboon ai = (Baboon) f.ai;
        f.health(25, 100);
        Attributes attrs = regen(f, 13);
        ai.params[0] = 50;
        ai.params[4] = 7;
        f.world.getMapper(NativeAiState.class).create(f.actor).previousMode = mode;
        f.seedFirstRoll(50, true);
        ai.update(0.04f);
        assertEquals("ESCAPE", ai.getState());
        assertTrue(ai.aiParam0 >= 2 && ai.aiParam0 <= 6);
        assertEquals(11, ai.aiParam2);
        assertEquals(24, attrs.getValue(Stat.hpregen, 0));
        assertEquals(24, attrs.base().getValue(Stat.hpregen, 0));
        org.mockito.Mockito.verify(f.paths).findPath(org.mockito.ArgumentMatchers.eq(f.actor),
            org.mockito.ArgumentMatchers.eq(new com.badlogic.gdx.math.Vector2(-5, 10)),
            org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(com.riiablo.engine.Engine.INVALID_ENTITY));
      }
    }
  }

  @Test void expirationSubtractsSavedBonusAndKeepsOtherRegeneration() {
    try (Fixture f = new Fixture("baboon1", 0, 10)) {
      Baboon ai = (Baboon) f.ai;
      f.health(50, 100);
      Attributes attrs = regen(f, 31);
      ai.aiParam0 = 1;
      ai.aiParam2 = 11;
      ai.update(0.04f);
      assertEquals(20, attrs.getValue(Stat.hpregen, 0));
      assertEquals(20, attrs.base().getValue(Stat.hpregen, 0));
    }
  }

  @Test void healthyNoncombatExitRemovesBonusAndStopsCountdown() {
    try (Fixture f = new Fixture("baboon1", 0, 10)) {
      Baboon ai = (Baboon) f.ai;
      f.health(76, 100);
      Attributes attrs = regen(f, 24);
      ai.aiParam0 = 4;
      ai.aiParam2 = 11;
      ai.update(0.04f);
      assertEquals(0, ai.aiParam0);
      assertEquals(13, attrs.getValue(Stat.hpregen, 0));
      assertEquals("APPROACH", ai.getState());
    }
  }
  @Test void fractionalLifeBelow76PercentDoesNotTriggerHealthyExit() {
    try (Fixture f = new Fixture("baboon1", 0, 10)) {
      Baboon ai = (Baboon) f.ai;
      f.health(75.9f, 100);
      Attributes attrs = regen(f, 24);
      ai.aiParam0 = 2;
      ai.aiParam2 = 11;
      ai.update(0.04f);
      assertEquals(1, ai.aiParam0);
      assertEquals(24, attrs.getValue(Stat.hpregen, 0));
    }
  }

  @Test void zeroRegenerationDoesNotCreateFlatBonus() {
    try (Fixture f = new Fixture("baboon1", 0, 1)) {
      Baboon ai = (Baboon) f.ai;
      f.health(25, 100);
      Attributes attrs = regen(f, 0);
      ai.params[0] = 50;
      ai.params[4] = 7;
      f.world.getMapper(NativeAiState.class).create(f.actor).previousMode = 3;
      f.seedFirstRoll(50, true);
      ai.update(0.04f);
      assertEquals("ESCAPE", ai.getState());
      assertEquals(0, ai.aiParam2);
      assertEquals(0, attrs.getValue(Stat.hpregen, 0));
    }
  }
}