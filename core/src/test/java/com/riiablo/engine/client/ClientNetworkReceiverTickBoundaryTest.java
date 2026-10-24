package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.SimulationTickCoordinator;
import com.riiablo.engine.client.event.ClientObjectStateEvent;
import com.riiablo.engine.server.event.SoundEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ClientNetworkReceiverTickBoundaryTest {
  @Test void networkEventWaitsForEventsPhase() {
    ClientNetworkReceiver receiver = new ClientNetworkReceiver();
    receiver.simulationTickCoordinator = new SimulationTickCoordinator();
    receiver.events = mock(EventSystem.class);
    SoundEvent event = SoundEvent.obtain(7, "door", true);

    receiver.publishEvent("network.sound", event);
    verifyNoInteractions(receiver.events);

    receiver.simulationTickCoordinator.advance(ignored -> {}, ignored -> {},
        queued -> ((Runnable) queued.payload).run());
    verify(receiver.events).dispatch(event);
  }

  @Test void replicaPresentationEventWaitsForEventsPhase() {
    ClientNetworkReceiver receiver = new ClientNetworkReceiver();
    receiver.simulationTickCoordinator = new SimulationTickCoordinator();
    receiver.events = mock(EventSystem.class);
    ClientObjectStateEvent event = ClientObjectStateEvent.obtain(11, 2, 4);

    receiver.publishEvent("network.object_state", event);
    verifyNoInteractions(receiver.events);

    receiver.simulationTickCoordinator.advance(ignored -> {}, ignored -> {},
        queued -> ((Runnable) queued.payload).run());
    verify(receiver.events).dispatch(event);
  }

  @Test void networkDeletionWaitsForStructuralCommitPhase() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int entity = world.create();
      TestReceiver receiver = new TestReceiver();
      receiver.attach(world);
      receiver.simulationTickCoordinator = new SimulationTickCoordinator();

      receiver.deleteEntity(entity);
      assertTrue(world.getEntityManager().isActive(entity));

      receiver.simulationTickCoordinator.advance(ignored -> {}, ignored -> {}, ignored -> {});
      world.process();
      assertFalse(world.getEntityManager().isActive(entity));
    } finally {
      world.dispose();
    }
  }

  @com.artemis.annotations.All
  private static final class TestReceiver extends ClientNetworkReceiver {
    void attach(World world) { attachWorld(world); }
    private void attachWorld(World world) { this.world = world; }
  }
}
