package com.riiablo.net.packet.d2gs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.flatbuffers.FlatBufferBuilder;
import org.junit.jupiter.api.Test;

class NpcServiceFlatBufferTest {
  @Test void requestRoundTripsOnlyIntentAndRevision() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = NpcServiceRequest.createNpcServiceRequest(builder, 17, 42,
        NpcServiceType.TRADE, NpcServiceOperation.BUY, 99, -1, 7);
    int root = D2GS.createD2GS(builder, D2GSData.NpcServiceRequest, request);
    D2GS.finishD2GSBuffer(builder, root);

    D2GS packet = D2GS.getRootAsD2GS(builder.dataBuffer());
    NpcServiceRequest decoded = (NpcServiceRequest) packet.data(new NpcServiceRequest());
    assertEquals(17, decoded.requestId());
    assertEquals(42, decoded.npcEntityId());
    assertEquals(NpcServiceType.TRADE, decoded.serviceType());
    assertEquals(NpcServiceOperation.BUY, decoded.operation());
    assertEquals(99, decoded.itemId());
    assertEquals(7, decoded.stockRevision());
  }
}
