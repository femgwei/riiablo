package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.replay.SimulationReplayCapture;
import com.riiablo.engine.replay.SimulationReplayLog;

class SimulationReplayLogTest {
  @Test
  void commandsAndCheckpointsHaveStableCrossProcessDigest() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "network", "move", "ignored-object").tick(4).actor(7).request(12)
        .target(42).authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT)
        .build(3);
    Map<Integer, String> entities = new HashMap<>();
    entities.put(42, "hp=90|x=12");
    SimulationCheckpoint checkpoint = SimulationCheckpoint.of(4, "world-4", entities,
        Collections.singletonList(new SimulationCheckpoint.RngState("combat.hit", 42L, 17, 3)));

    SimulationReplayLog first = SimulationReplayLog.builder(0x110F)
        .command(command, "move:12,8")
        .checkpoint(checkpoint)
        .build();
    SimulationReplayLog replay = SimulationReplayLog.builder(0x110F)
        .command(command, "move:12,8")
        .checkpoint(checkpoint)
        .build();
    assertEquals(first.canonical(), replay.canonical());
    assertEquals(first.digest(), replay.digest());
    assertEquals(checkpoint, first.checkpointAt(4));
  }

  @Test
  void checkpointDiffIdentifiesTickEntityAndRngField() {
    Map<Integer, String> expectedEntities = new HashMap<>();
    expectedEntities.put(42, "hp=90");
    Map<Integer, String> actualEntities = new HashMap<>();
    actualEntities.put(42, "hp=89");
    SimulationCheckpoint expected = SimulationCheckpoint.of(4, "world-4", expectedEntities,
        Collections.singletonList(new SimulationCheckpoint.RngState("combat.hit", 42L, 17, 3)));
    SimulationCheckpoint actual = SimulationCheckpoint.of(5, "world-5", actualEntities,
        Collections.singletonList(new SimulationCheckpoint.RngState("combat.hit", 42L, 18, 4)));

    List<SimulationCheckpoint.Mismatch> mismatches = expected.diff(actual);
    assertTrue(mismatches.stream().anyMatch(item -> item.entityId == 42
        && "digest".equals(item.field)));
    assertTrue(mismatches.stream().anyMatch(item -> "tick".equals(item.field)));
    assertTrue(mismatches.stream().anyMatch(item -> item.field.startsWith("rng:")));
  }

  @Test
  void changingCanonicalPayloadChangesReplayDigest() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder("ui", "cast", 1)
        .tick(1).build(1);
    String first = SimulationReplayLog.builder(1).command(command, "skill=36").build().digest();
    String changed = SimulationReplayLog.builder(1).command(command, "skill=37").build().digest();
    assertNotEquals(first, changed);
  }

  @Test
  void duplicateCheckpointTicksAreRejected() {
    SimulationCheckpoint checkpoint = SimulationCheckpoint.of(4, "world", null, null);
    SimulationReplayLog.Builder builder = SimulationReplayLog.builder(1)
        .checkpoint(checkpoint).checkpoint(checkpoint);
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void admittedCommandsReplayToIdenticalCheckpoints() {
    SimulationReplayCapture capture = new SimulationReplayCapture(0x110F, String::valueOf);
    SimulationTickCoordinator recorded = new SimulationTickCoordinator();
    recorded.setReplayRecorder(capture);
    recorded.commands().submit(SimulationCommandEnvelope.builder("ui", "delta", 2).tick(1));
    recorded.commands().submit(SimulationCommandEnvelope.builder("ui", "delta", 3).tick(2));
    assertEquals(0, capture.snapshot().commands().size());
    int[] recordedTotal = {0};
    advance(recorded, recordedTotal);
    assertEquals(1, capture.snapshot().commands().size());
    recorded.publishReplayCheckpoint(checkpoint(recorded, recordedTotal[0]));
    advance(recorded, recordedTotal);
    recorded.publishReplayCheckpoint(checkpoint(recorded, recordedTotal[0]));

    SimulationReplayLog log = capture.snapshot();
    assertEquals(2, log.commands().size());
    assertEquals(2, log.checkpoints().size());

    SimulationTickCoordinator replayed = new SimulationTickCoordinator();
    for (SimulationReplayLog.Command command : log.commands()) {
      assertTrue(replayed.commands().submit(command.toBuilder()) != null);
    }
    int[] replayedTotal = {0};
    advance(replayed, replayedTotal);
    assertTrue(log.checkpointAt(1).diff(checkpoint(replayed, replayedTotal[0])).isEmpty());
    advance(replayed, replayedTotal);
    assertTrue(log.checkpointAt(2).diff(checkpoint(replayed, replayedTotal[0])).isEmpty());
    assertEquals(recordedTotal[0], replayedTotal[0]);
  }

  private static void advance(SimulationTickCoordinator coordinator, int[] total) {
    coordinator.advance(command -> total[0] += Integer.parseInt(String.valueOf(command.payload)),
        tick -> {}, event -> {});
  }

  private static SimulationCheckpoint checkpoint(SimulationTickCoordinator coordinator,
      int total) {
    Map<Integer, String> entities = new HashMap<>();
    entities.put(7, "total=" + total);
    return SimulationCheckpoint.of(coordinator.tick(), "world-total=" + total, entities,
        Collections.singletonList(new SimulationCheckpoint.RngState("test", 0L, total, total)));
  }
}
