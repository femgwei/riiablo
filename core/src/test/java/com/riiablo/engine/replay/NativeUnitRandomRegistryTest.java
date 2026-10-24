package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.component.Monster;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeUnitRandomRegistryTest {
  @Test
  void restoresBothSeedWordsAndPreservesTheNextNativeDraw() {
    Monster unit = new Monster();
    unit.nativeSeed = new D2Seed(0x13572468, 0x24681357);
    unit.rngState = unit.nativeSeed.getNLowSeed();
    NativeUnitRandom rng = NativeUnitRandom.monster(unit, 1, 42);
    rng.nextUnsigned();
    List<SimulationCheckpoint.NativeUnitRngState> checkpoint =
        register("combat.hit", 42L, rng).snapshot();
    long expected = rng.nextUnsigned();

    rng.nextUnsigned();
    NativeUnitRandomRegistry registry = register("combat.hit", 42L, rng);
    registry.restore(checkpoint);

    assertEquals(checkpoint, registry.snapshot());
    assertEquals(expected, rng.nextUnsigned());
  }

  @Test
  void recreatesMissingUnitThroughExplicitFactory() {
    Monster source = new Monster();
    source.nativeSeed = new D2Seed(3, 4);
    source.rngState = source.nativeSeed.getNLowSeed();
    NativeUnitRandom original = NativeUnitRandom.monster(source, 0, 7);
    original.nextUnsigned();
    List<SimulationCheckpoint.NativeUnitRngState> checkpoint =
        register("ai.roll", 7L, original).snapshot();

    final Monster[] restoredUnit = new Monster[1];
    NativeUnitRandomRegistry restored = new NativeUnitRandomRegistry(state -> {
      Monster unit = new Monster();
      unit.nativeSeed = new D2Seed(state.lowSeed, state.highSeed);
      unit.rngState = state.lowSeed;
      restoredUnit[0] = unit;
      return NativeUnitRandom.monster(unit, 0, (int) state.contextId);
    });
    restored.restore(checkpoint);

    assertEquals(checkpoint, restored.snapshot());
    assertEquals(checkpoint.get(0).highSeed, restoredUnit[0].nativeSeed.getNHighSeed());
  }

  @Test
  void missingFactoryIsRejectedBeforeRegistryMutation() {
    NativeUnitRandomRegistry registry = new NativeUnitRandomRegistry();
    SimulationCheckpoint.NativeUnitRngState state =
        new SimulationCheckpoint.NativeUnitRngState("unit", 1L, 2, 3);

    assertThrows(IllegalStateException.class,
        () -> registry.restore(Collections.singletonList(state)));
    assertEquals(Collections.emptyList(), registry.snapshot());
  }

  private static NativeUnitRandomRegistry register(String stream, long context,
      NativeUnitRandom rng) {
    NativeUnitRandomRegistry registry = new NativeUnitRandomRegistry();
    registry.register(stream, context, rng);
    return registry;
  }
}
