package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
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
    assertEquals(0, queue.pendingCount());
  }
}
