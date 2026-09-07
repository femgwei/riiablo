package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
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
    int payload = SnapshotBaseline.createSnapshotBaseline(builder, 7L, 3L,
        99L, 1234L, SnapshotBaselinePhase.END, true, reason, 12L);
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
  }
}
