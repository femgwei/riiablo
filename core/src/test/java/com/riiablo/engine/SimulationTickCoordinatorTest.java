package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTickCoordinatorTest {
  @Test void commandsAndEventsRunInFixedPhaseOrder() {
    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    List<String> trace = new ArrayList<>();
    coordinator.commands().submit(SimulationCommandEnvelope.builder("ui", "move", "east").tick(1));
    coordinator.events().publish(1, "accepted", "move");
    coordinator.structuralChanges().enqueue(() -> trace.add("structure"));
    coordinator.advance(command -> trace.add("command:" + command.kind),
        tick -> { trace.add("simulation:" + tick); assertEquals(SimulationTickCoordinator.Phase.SIMULATION, coordinator.phase()); },
        event -> trace.add("event:" + event.kind));
    assertEquals(Arrays.asList("command:move", "simulation:1", "event:accepted", "structure"), trace);
    assertEquals(1L, coordinator.tick());
    assertEquals(SimulationTickCoordinator.Phase.IDLE, coordinator.phase());
  }

  @Test void commandsSubmittedDuringSimulationTargetTheFollowingTick() {
    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    List<Long> executed = new ArrayList<>();
    coordinator.advance(command -> {}, tick -> {
      assertEquals(SimulationTickCoordinator.Phase.SIMULATION, coordinator.phase());
      assertEquals(2L, coordinator.nextCommandTick());
      coordinator.commands().submit(SimulationCommandEnvelope
          .builder("input", "move", "east").tick(coordinator.nextCommandTick()));
    }, event -> {});
    assertEquals(1, coordinator.commands().pendingCount());
    coordinator.advance(command -> executed.add(command.tick), tick -> {}, event -> {});
    assertEquals(Collections.singletonList(2L), executed);
  }

  @Test void nextCommandTickNeverFallsBehindQueueWatermark() {
    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    coordinator.commands().drain(4, ignored -> {});
    assertEquals(5L, coordinator.nextCommandTick());
    assertNotNull(coordinator.commands().submit(SimulationCommandEnvelope
        .builder("input", "move", "east").tick(coordinator.nextCommandTick())));
  }

  @Test void commandsSubmittedWhileIdleTargetTheNextTick() {
    SimulationTickCoordinator coordinator = new SimulationTickCoordinator();
    assertEquals(1L, coordinator.nextCommandTick());
    coordinator.commands().submit(SimulationCommandEnvelope
        .builder("input", "move", "east").tick(coordinator.nextCommandTick()));
    coordinator.advance(command -> {}, tick -> {}, event -> {});
    assertEquals(1L, coordinator.tick());
  }

  @Test void structuralChangesEnqueuedDuringBarrierWaitForNextTick() {
    StructuralChangeQueue queue = new StructuralChangeQueue();
    List<String> trace = new ArrayList<>();
    queue.enqueue(() -> { trace.add("first"); queue.enqueue(() -> trace.add("second")); });
    assertEquals(1, queue.flush());
    assertEquals(Arrays.asList("first"), trace);
    assertEquals(1, queue.pendingCount());
    assertEquals(1, queue.flush());
    assertEquals(Arrays.asList("first", "second"), trace);
  }

  @Test void staleCommandsAreRejectedAfterTheirTickIsDrained() {
    SimulationCommandQueue queue = new SimulationCommandQueue();
    queue.submit(SimulationCommandEnvelope.builder("input", "cast", 1).tick(1));
    assertEquals(1, queue.drain(1, ignored -> {}));
    assertNull(queue.submit(SimulationCommandEnvelope.builder("input", "late", 2).tick(1)));
    assertEquals(SimulationCommandAdmission.Rejection.STALE_TICK, queue.lastRejection());
    assertEquals(0, queue.pendingCount());
  }

  @Test void commandsSkippedByFixedStepCatchupAreStillExecuted() {
    SimulationCommandQueue queue = new SimulationCommandQueue();
    List<String> drained = new ArrayList<>();
    queue.submit(SimulationCommandEnvelope.builder("input", "move", "east").tick(2));
    assertEquals(1, queue.drain(3, command -> drained.add(command.kind)));
    assertEquals(Collections.singletonList("move"), drained);
    assertEquals(0, queue.pendingCount());
  }

  @Test void networkCommandsRequireIdentityAndAuthorizedTargets() {
    SimulationCommandQueue queue = new SimulationCommandQueue(
        SimulationCommandAdmission.network((command, target) -> command.actorId == 7 && target == 42));
    assertNull(queue.submit(SimulationCommandEnvelope.builder("net", "cast", "x").tick(1)
        .request(1).authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT)));
    assertEquals(SimulationCommandAdmission.Rejection.MISSING_ACTOR, queue.lastRejection());

    SimulationCommandEnvelope accepted = queue.submit(SimulationCommandEnvelope.builder("net", "cast", "x")
        .tick(1).actor(7).request(1).target(42)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT));
    assertNotNull(accepted);
    assertNull(queue.submit(SimulationCommandEnvelope.builder("net", "cast", "x")
        .tick(1).actor(7).request(1).target(42)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT)));
    assertEquals(SimulationCommandAdmission.Rejection.DUPLICATE_REQUEST, queue.lastRejection());
    assertNull(queue.submit(SimulationCommandEnvelope.builder("net", "cast", "x")
        .tick(1).actor(7).request(2).target(99)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT)));
    assertEquals(SimulationCommandAdmission.Rejection.UNAUTHORIZED_TARGET, queue.lastRejection());
  }

  @Test void networkAuthorizationFailureRejectsWithoutEscapingTheAdmissionBoundary() {
    SimulationCommandQueue queue = new SimulationCommandQueue(
        SimulationCommandAdmission.network((command, target) -> {
          throw new IllegalStateException("policy unavailable");
        }));
    assertNull(queue.submit(SimulationCommandEnvelope.builder("net", "cast", "x")
        .tick(1).actor(7).request(1).target(42)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT)));
    assertEquals(SimulationCommandAdmission.Rejection.UNAUTHORIZED_TARGET, queue.lastRejection());
    assertEquals(0, queue.pendingCount());
  }

  @Test void phaseContractExposesReadWriteSetsAndStructuralBarrier() {
    SimulationPhaseContract contract = new SimulationPhaseContract();
    contract.markStructuralResource("entities");
    contract.register("movement", SimulationTickCoordinator.Phase.SIMULATION,
        Collections.singleton("world"), Collections.singleton("position"));
    contract.register("commit", SimulationTickCoordinator.Phase.STRUCTURAL_COMMIT,
        Collections.singleton("world"), Collections.singleton("entities"),
        Collections.singleton("entities"));

    assertTrue(contract.mayRead("movement", SimulationTickCoordinator.Phase.SIMULATION, "world"));
    assertTrue(contract.mayWrite("movement", SimulationTickCoordinator.Phase.SIMULATION, "position"));
    assertFalse(contract.mayWrite("movement", SimulationTickCoordinator.Phase.SIMULATION, "entities"));
    assertTrue(contract.mayWrite("commit", SimulationTickCoordinator.Phase.STRUCTURAL_COMMIT, "entities"));
    assertThrows(IllegalStateException.class, () -> contract.register("movement",
        SimulationTickCoordinator.Phase.EVENTS, Collections.emptySet(), Collections.emptySet()));
  }
}
