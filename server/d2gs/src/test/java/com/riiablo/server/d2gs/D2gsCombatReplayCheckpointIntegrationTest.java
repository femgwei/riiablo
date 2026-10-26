package com.riiablo.server.d2gs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.SimulationCommandEnvelope;
import com.riiablo.engine.SimulationTickCoordinator;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.replay.SimulationReplayLog;
import com.riiablo.engine.replay.SimulationReplayRunner;
import com.riiablo.engine.replay.TransactionRuntimeCheckpoint;
import com.riiablo.engine.server.combat.CombatCheckpoint;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Composition gate for the portable combat summary, replay manifest and D2GS
 * checkpoint envelope.  This deliberately uses an empty ECS payload: the
 * D2GS snapshot tests own serializer/lifecycle coverage, while this test
 * proves that all three authoritative witnesses survive the same hand-off.
 */
public class D2gsCombatReplayCheckpointIntegrationTest {
  @Test public void combatContinuationSurvivesD2gsAndReplayRoundTrip() {
    CombatCheckpoint archivedCombat = CombatCheckpoint.of(
        40L, 7, 11, 36, 256000, 227354, 2);
    CombatCheckpoint resumedCombat = CombatCheckpoint.of(
        41L, 7, 11, 36, 227354, 198000, 1);
    assertTrue(archivedCombat.verifyContinuation(resumedCombat).accepted());

    SimulationCheckpoint.RngState archivedRng =
        new SimulationCheckpoint.RngState("combat.hit", 11L, 0x1234, 8L);
    SimulationCheckpoint.NativeUnitRngState archivedNativeRng =
        new SimulationCheckpoint.NativeUnitRngState("unit", 11L, 4, 9);
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(3, "hero", 7, 40L).accepted);
    AuthoritativeSessionRegistry.Session session = sessions.activeConnection(3);
    TransactionRuntimeCheckpoint.Entry transaction = TransactionRuntimeCheckpoint.Entry.of(
        3, session.generation, 7, 9L, "skill", 11, 40L, 41L,
        "skill=36|target=11", new byte[] {1, 2, 3});
    TransactionRuntimeCheckpoint runtime = TransactionRuntimeCheckpoint.capture(
        10L, Collections.singletonList(transaction));

    D2gsCheckpointSnapshot archivedD2gs = D2gsCheckpointSnapshot.fromPayloads(
        40L, Collections.emptyMap(), Collections.singletonList(archivedRng),
        Collections.singletonList(archivedNativeRng), null, sessions.snapshot())
        .withTransactionRuntime(runtime);
    D2gsCheckpointSnapshot restoredD2gs =
        D2gsCheckpointSnapshot.fromBytes(archivedD2gs.toBytes());
    assertEquals(archivedD2gs.summary().canonical(), restoredD2gs.summary().canonical());
    assertEquals(runtime.canonical(), restoredD2gs.transactionRuntime().canonical());
    assertEquals(10L, restoredD2gs.transactionRuntime().nextIngressRequestId());

    SimulationCommandEnvelope command = SimulationCommandEnvelope.builder(
        "network", "skill", "skill=36|target=11").tick(41L).actor(7).request(9L)
        .target(11).authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT).build(1L);
    SimulationCheckpoint archivedReplay = replayCheckpoint(40L, archivedD2gs,
        archivedCombat, archivedRng, archivedNativeRng);
    SimulationCheckpoint resumedReplay = replayCheckpoint(41L, restoredD2gs,
        resumedCombat, new SimulationCheckpoint.RngState("combat.hit", 11L, 0x1235, 9L),
        new SimulationCheckpoint.NativeUnitRngState("unit", 11L, 4, 9));
    SimulationReplayLog original = SimulationReplayLog.builder(0x110F)
        .command(command, "skill=36|target=11")
        .checkpoint(archivedReplay)
        .checkpoint(resumedReplay)
        .build();
    SimulationReplayLog decoded = SimulationReplayLog.fromBytes(original.toBytes());
    assertEquals(original.canonical(), decoded.canonical());
    assertEquals(original.digest(), decoded.digest());

    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    coordinator.restore(restoredD2gs.tick());
    SimulationReplayRunner.Result result = SimulationReplayRunner.play(decoded, coordinator,
        ignored -> {}, tick -> {}, event -> {}, tick ->
            tick == 40L ? archivedReplay : resumedReplay);
    assertTrue(result.success());
    assertEquals(40L, result.startTick);
    assertEquals(41L, result.endTick);
    assertEquals(1, result.submittedCommands);
    assertEquals(2, result.verifiedCheckpoints);
  }

  @Test public void combatAndTransactionWitnessesChangeReplayDigest() {
    CombatCheckpoint combat = CombatCheckpoint.of(40L, 7, 11, 36, 256000, 227354, 2);
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(3, "hero", 7, 40L).accepted);
    AuthoritativeSessionRegistry.Session session = sessions.activeConnection(3);
    TransactionRuntimeCheckpoint runtime = TransactionRuntimeCheckpoint.capture(10L,
        Collections.singletonList(TransactionRuntimeCheckpoint.Entry.of(
            3, session.generation, 7, 9L, "skill", 11, 40L, 40L,
            "skill=36", new byte[] {1})));
    D2gsCheckpointSnapshot d2gs = D2gsCheckpointSnapshot.fromPayloads(
        40L, Collections.emptyMap(), null, null, null, sessions.snapshot())
        .withTransactionRuntime(runtime);
    SimulationReplayLog first = SimulationReplayLog.builder(0x110F)
        .checkpoint(replayCheckpoint(40L, d2gs, combat,
            new SimulationCheckpoint.RngState("combat.hit", 11L, 1, 1), null)).build();
    SimulationReplayLog changedCombat = SimulationReplayLog.builder(0x110F)
        .checkpoint(replayCheckpoint(40L, d2gs,
            CombatCheckpoint.of(40L, 7, 11, 47, 256000, 227354, 2),
            new SimulationCheckpoint.RngState("combat.hit", 11L, 1, 1), null)).build();
    assertTrue(!first.digest().equals(changedCombat.digest()));

    D2gsCheckpointSnapshot changedD2gs = d2gs.withTransactionRuntime(
        TransactionRuntimeCheckpoint.capture(10L,
            Collections.singletonList(TransactionRuntimeCheckpoint.Entry.of(
                3, session.generation, 7, 9L, "skill", 11, 40L, 40L,
                "skill=36", new byte[] {2}))));
    SimulationReplayLog changedTransaction = SimulationReplayLog.builder(0x110F)
        .checkpoint(replayCheckpoint(40L, changedD2gs, combat,
            new SimulationCheckpoint.RngState("combat.hit", 11L, 1, 1), null)).build();
    assertTrue(!first.digest().equals(changedTransaction.digest()));
  }

  private static SimulationCheckpoint replayCheckpoint(long tick, D2gsCheckpointSnapshot d2gs,
      CombatCheckpoint combat, SimulationCheckpoint.RngState rng,
      SimulationCheckpoint.NativeUnitRngState nativeRng) {
    Map<Integer, String> entities = new HashMap<>();
    entities.put(combat.targetId(), "combat=" + combat.digest());
    String world = "d2gs=" + d2gs.summary().digest() + "|combat=" + combat.digest();
    return SimulationCheckpoint.of(tick, world, entities,
        Collections.singletonList(rng),
        nativeRng == null ? null : Collections.singletonList(nativeRng));
  }
}
