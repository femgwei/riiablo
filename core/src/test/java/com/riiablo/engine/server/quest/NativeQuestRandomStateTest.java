package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.d2moo.common.seed.Seed;
import org.junit.jupiter.api.Test;

class NativeQuestRandomStateTest {
  @Test void restoreBeforeWorldInitializationPreservesBothWordsAndStream() {
    NativeQuestRandomState original = new NativeQuestRandomState();
    original.restore(0x89abcdef, 0xfedcba98);
    Seed.rollRandomNumber(original.seed());
    NativeQuestRandomState restored = new NativeQuestRandomState();
    restored.restore(original.lowSeed(), original.highSeed());
    World world = new World(new WorldConfigurationBuilder().with(restored).build());
    try {
      assertEquals(original.lowSeed(), restored.lowSeed());
      assertEquals(original.highSeed(), restored.highSeed());
      for (int i = 0; i < 100; i++) {
        assertEquals(Seed.rollRandomNumber(original.seed()), Seed.rollRandomNumber(restored.seed()));
      }
    } finally {
      world.dispose();
    }
  }
}
