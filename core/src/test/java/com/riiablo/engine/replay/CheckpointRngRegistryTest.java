package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.riiablo.engine.server.NativeRng;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckpointRngRegistryTest {
  @Test
  void capturesAndRestoresTheNextDrawByStableIdentity() {
    CheckpointRngRegistry registry = new CheckpointRngRegistry();
    NativeRng source = new NativeRng(1234);
    NativeRng named = com.riiablo.engine.server.NamedRngStreams.openLegacy(
        source.state(), "combat.hit", 7L);
    named.nextInt();
    registry.register(named);
    List<SimulationCheckpoint.RngState> checkpoint = registry.snapshot();
    int expected = NativeRng.fromSnapshot(new NativeRng.Snapshot(
        "combat.hit", 7L, checkpoint.get(0).state, checkpoint.get(0).drawCount)).nextInt();

    named.nextInt();
    registry.restore(checkpoint);

    assertEquals(checkpoint, registry.snapshot().subList(0, 1));
    assertEquals(expected, named.nextInt());
  }

  @Test
  void recreatesMissingStreamsForAnotherProcess() {
    CheckpointRngRegistry source = new CheckpointRngRegistry();
    NativeRng stream = com.riiablo.engine.server.NamedRngStreams.openLegacy(
        99, "loot.drop", 42L);
    stream.nextInt();
    source.register(stream);
    List<SimulationCheckpoint.RngState> checkpoint = source.snapshot();

    CheckpointRngRegistry restored = new CheckpointRngRegistry();
    restored.restore(checkpoint);

    assertEquals(checkpoint, restored.snapshot());
    assertEquals(checkpoint.get(0).state, restored.get("loot.drop", 42L).state());
  }

  @Test
  void rejectsDuplicateIdentityBeforeChangingRegistry() {
    CheckpointRngRegistry registry = new CheckpointRngRegistry();
    NativeRng stream = com.riiablo.engine.server.NamedRngStreams.openLegacy(
        11, "combat.hit", 1L);
    registry.register(stream);
    List<SimulationCheckpoint.RngState> before = registry.snapshot();
    SimulationCheckpoint.RngState duplicate = new SimulationCheckpoint.RngState(
        "combat.hit", 1L, 99, 4);

    assertThrows(IllegalArgumentException.class,
        () -> registry.restore(Arrays.asList(before.get(0), duplicate)));
    assertEquals(before, registry.snapshot());
  }

  @Test
  void emptyCheckpointClearsAuthoritativeRegistry() {
    CheckpointRngRegistry registry = new CheckpointRngRegistry();
    registry.register(com.riiablo.engine.server.NamedRngStreams.openLegacy(
        17, "combat.hit", 1L));

    registry.restore(Collections.<SimulationCheckpoint.RngState>emptyList());

    assertEquals(Collections.emptyList(), registry.snapshot());
  }
}
