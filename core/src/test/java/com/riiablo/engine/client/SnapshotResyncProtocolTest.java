package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.SnapshotBaseline;
import com.riiablo.net.packet.d2gs.SnapshotBaselinePhase;
import com.riiablo.net.packet.d2gs.SnapshotResyncRequest;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class SnapshotResyncProtocolTest {
  @Test
  void requestRoundTripsThroughEnvelope() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int reason = builder.createString("tick_gap");
    int payload = SnapshotResyncRequest.createSnapshotResyncRequest(builder, 7L, 42L, reason);
    int root = D2GS.createD2GS(builder, D2GSData.SnapshotResyncRequest, payload);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    D2GS packet = D2GS.getRootAsD2GS(frame);
    SnapshotResyncRequest request = (SnapshotResyncRequest) packet.data(
        new SnapshotResyncRequest());
    assertEquals(D2GSData.SnapshotResyncRequest, packet.dataType());
    assertEquals(7L, request.requestId());
    assertEquals(42L, request.lastAcceptedTick());
    assertEquals("tick_gap", request.reason());
  }

  @Test
  void baselineMarkersExposeBoundaryAndPhase() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int reason = builder.createString("");
    int masks = SnapshotBaseline.createWaypointMasksVector(builder, new int[] {1, 2, 4, 8, 16});
    int payload = SnapshotBaseline.createSnapshotBaseline(builder, 7L, 3L,
        99L, 1234L, SnapshotBaselinePhase.END, true, reason, 12L, masks, 0, 17L);
    int root = D2GS.createD2GS(builder, D2GSData.SnapshotBaseline, payload);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    SnapshotBaseline marker = (SnapshotBaseline) D2GS.getRootAsD2GS(frame)
        .data(new SnapshotBaseline());
    assertTrue(marker.success());
    assertEquals(3L, marker.baselineId());
    assertEquals(99L, marker.serverTick());
    assertEquals(SnapshotBaselinePhase.END, marker.phase());
    assertEquals(12L, marker.entityCount());
    assertEquals(5, marker.waypointMasksLength());
    assertEquals(4, marker.waypointMasks(2));
    assertEquals(17L, marker.inventoryRevision());
  }

  @Test
  void entitySyncCarriesAuthoritativeLevelContext() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int componentTypes = EntitySync.createComponentTypeVector(builder, new byte[0]);
    int components = EntitySync.createComponentVector(builder, new int[0]);
    int payload = EntitySync.createEntitySync(builder, 123, 1, 0,
        componentTypes, components, 55L, 9000L, 0L, 12L, 0L, 8);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, payload);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    EntitySync sync = (EntitySync) D2GS.getRootAsD2GS(frame)
        .data(new EntitySync());
    assertEquals(D2GSData.EntitySync, D2GS.getRootAsD2GS(frame).dataType());
    assertEquals(123, sync.entityId());
    assertEquals(55L, sync.tick());
    assertEquals(8, sync.levelId());
  }
}
