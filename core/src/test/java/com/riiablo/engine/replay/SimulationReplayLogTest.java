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
import com.riiablo.engine.replay.SimulationReplayRunner;

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
  void replayRejectsCommandsThatBypassAdmissionIdentityChecks() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "ui", "delta", 1).tick(1).build(0);
    assertThrows(IllegalArgumentException.class,
        () -> SimulationReplayLog.builder(1).command(command, "1").build());
  }

  @Test
  void replayRejectsDuplicateCommandSequences() {
    SimulationCommandEnvelope first = SimulationCommandEnvelope.builder(
        "ui", "delta", 1).tick(1).build(7);
    SimulationCommandEnvelope second = SimulationCommandEnvelope.builder(
        "ui", "delta", 2).tick(2).build(7);
    SimulationReplayLog.Builder builder = SimulationReplayLog.builder(1)
        .command(first, "1").command(second, "2");
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void replayRejectsDuplicateActorRequestIdentity() {
    SimulationCommandEnvelope first = SimulationCommandEnvelope.builder(
        "net", "move", 1).tick(1).actor(4).request(9).build(1);
    SimulationCommandEnvelope second = SimulationCommandEnvelope.builder(
        "net", "move", 2).tick(2).actor(4).request(9).build(2);
    SimulationReplayLog.Builder builder = SimulationReplayLog.builder(1)
        .command(first, "1").command(second, "2");
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void replayRejectsRequestWithoutActorIdentity() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "net", "move", 1).tick(1).request(9).build(1);
    assertThrows(IllegalArgumentException.class,
        () -> SimulationReplayLog.builder(1).command(command, "1").build());
  }

  @Test
  void replayRejectsNetworkTargetWithoutActorIdentity() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "net", "hit", 1).tick(1).target(42)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT).build(1);
    assertThrows(IllegalArgumentException.class,
        () -> SimulationReplayLog.builder(1).command(command, "1").build());
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

  @Test
  void replayRunnerVerifiesRecordedTicks() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "ui", "delta", 3).tick(1).build(1);
    SimulationCheckpoint expected = SimulationCheckpoint.of(1, "world-total=3",
        Collections.singletonMap(7, "total=3"),
        Collections.singletonList(new SimulationCheckpoint.RngState("test", 0L, 3, 3)));
    SimulationReplayLog log = SimulationReplayLog.builder(1)
        .command(command, "3").checkpoint(expected).build();
    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    int[] total = {0};
    SimulationReplayRunner.Result result = SimulationReplayRunner.play(log, coordinator,
        item -> total[0] += Integer.parseInt(String.valueOf(item.payload)),
        tick -> {}, event -> {}, tick -> checkpoint(coordinator, total[0]));
    assertTrue(result.success());
    assertEquals(1, result.submittedCommands);
    assertEquals(1, result.verifiedCheckpoints);
    assertEquals(3, total[0]);
  }

  @Test
  void replayRunnerRejectsNetworkCommandWithoutRequestIdentity() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "net", "cast", "skill=36").tick(1).actor(7)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT).build(1);
    SimulationReplayLog log = SimulationReplayLog.builder(1)
        .command(command, "skill=36").build();
    assertThrows(IllegalArgumentException.class, () -> SimulationReplayRunner.play(log,
        new SimulationTickCoordinator(), ignored -> {}, tick -> {}, event -> {}, tick -> null));
  }

  @Test
  void replayRunnerContinuesAfterAResumeTickAndReportsMismatch() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "ui", "delta", 2).tick(2).build(2);
    SimulationCheckpoint baseline = SimulationCheckpoint.of(1, "world-total=1",
        Collections.singletonMap(7, "total=1"), null);
    SimulationCheckpoint expected = SimulationCheckpoint.of(2, "world-total=3",
        Collections.singletonMap(7, "total=3"), null);
    SimulationReplayLog log = SimulationReplayLog.builder(1)
        .command(command, "2").checkpoint(baseline).checkpoint(expected).build();

    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    int[] total = {1};
    coordinator.advance(ignored -> {}, tick -> {}, event -> {});
    SimulationReplayRunner.Result result = SimulationReplayRunner.play(log, coordinator,
        item -> total[0] += Integer.parseInt(String.valueOf(item.payload)),
        tick -> {}, event -> {}, tick -> SimulationCheckpoint.of(tick,
            "world-total=" + total[0], Collections.singletonMap(7, "total=" + total[0]), null));
    assertTrue(result.success());
    assertEquals(1L, result.startTick);
    assertEquals(2L, result.endTick);
    assertEquals(1, result.submittedCommands);
    assertEquals(2, result.verifiedCheckpoints);
    assertEquals(3, total[0]);

    SimulationReplayRunner.Result mismatch = SimulationReplayRunner.play(log,
        new SimulationTickCoordinator(), item -> {}, tick -> {}, event -> {},
        tick -> SimulationCheckpoint.of(tick, "wrong", null, null));
    assertTrue(!mismatch.success());
    assertTrue(mismatch.mismatches().stream().anyMatch(item -> "worldDigest".equals(item.field)));
  }

  @Test
  void binaryRoundTripPreservesCanonicalReplayAcrossJvmBoundary() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "net", "cast", "skill=36").tick(2).actor(7).request(19)
        .target(11).authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT).build(4);
    SimulationCheckpoint checkpoint = SimulationCheckpoint.of(2, "world-2",
        Collections.singletonMap(11, "hp=888"),
        Collections.singletonList(new SimulationCheckpoint.RngState("combat.hit", 11, 17, 3)),
        Collections.singletonList(new SimulationCheckpoint.NativeUnitRngState("unit", 11, 4, 9)));
    SimulationReplayLog original = SimulationReplayLog.builder(0x110F)
        .command(command, "skill=36").checkpoint(checkpoint).build();

    SimulationReplayLog decoded = SimulationReplayLog.fromBytes(original.toBytes());
    assertEquals(original.canonical(), decoded.canonical());
    assertEquals(original.digest(), decoded.digest());
  }

  @Test
  void binaryDecoderRejectsUnknownVersionAndOversizedCount() {
    byte[] version = originalHeader(99, 0);
    assertThrows(IllegalArgumentException.class, () -> SimulationReplayLog.fromBytes(version));
    byte[] count = originalHeader(1, 1_000_001);
    assertThrows(IllegalArgumentException.class, () -> SimulationReplayLog.fromBytes(count));
  }

  @Test
  void binaryDecoderRejectsTrailingDataAfterReplay() {
    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "ui", "delta", 1).tick(1).build(1);
    byte[] encoded = SimulationReplayLog.builder(1).command(command, "1").build().toBytes();
    byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
    trailing[trailing.length - 1] = 0x5A;
    assertThrows(IllegalArgumentException.class, () -> SimulationReplayLog.fromBytes(trailing));
  }

  private static byte[] originalHeader(int version, int commandCount) {
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(16);
    bytes.putInt(0x52504C59).putInt(version).putInt(1).putInt(commandCount);
    return bytes.array();
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
