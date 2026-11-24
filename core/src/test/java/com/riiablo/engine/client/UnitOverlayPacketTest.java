package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.SimulationTickCoordinator;
import com.riiablo.engine.server.event.UnitOverlayEvent;
import com.riiablo.net.packet.d2gs.UnitOverlayP;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UnitOverlayPacketTest {
  @Test void remoteTargetIsMappedAndEffectWaitsForPresentationPhase() {
    ClientNetworkReceiver receiver = new ClientNetworkReceiver();
    receiver.syncIds = new NetworkIdManager();
    receiver.syncIds.serverToEntityId.put(700, 12);
    receiver.events = mock(EventSystem.class);
    receiver.simulationTickCoordinator = new SimulationTickCoordinator();
    receiver.receiveUnitOverlay(packet(700, "fire_hit"));
    verifyNoInteractions(receiver.events);
    receiver.simulationTickCoordinator.advance(ignored -> {}, ignored -> {},
        queued -> ((Runnable) queued.payload).run());
    ArgumentCaptor<UnitOverlayEvent> event = ArgumentCaptor.forClass(UnitOverlayEvent.class);
    verify(receiver.events).dispatch(event.capture());
    assertEquals(12, event.getValue().entityId);
    assertEquals("fire_hit", event.getValue().overlay);
  }

  @Test void unknownTargetAndEmptyOverlayDoNotCreatePhantomUnits() {
    ClientNetworkReceiver receiver = new ClientNetworkReceiver();
    receiver.syncIds = new NetworkIdManager();
    receiver.syncIds.serverToEntityId.put(700, 12);
    receiver.events = mock(EventSystem.class);
    receiver.receiveUnitOverlay(packet(701, "fire_hit"));
    receiver.receiveUnitOverlay(packet(700, ""));
    verifyNoInteractions(receiver.events);
  }

  private static UnitOverlayP packet(int target, String overlay) {
    FlatBufferBuilder b = new FlatBufferBuilder(64);
    int name = b.createString(overlay);
    b.finish(UnitOverlayP.createUnitOverlayP(b, target, name));
    return UnitOverlayP.getRootAsUnitOverlayP(b.dataBuffer());
  }
}
