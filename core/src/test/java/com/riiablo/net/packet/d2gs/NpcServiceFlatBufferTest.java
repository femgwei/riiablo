package com.riiablo.net.packet.d2gs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.flatbuffers.FlatBufferBuilder;
import org.junit.jupiter.api.Test;

class NpcServiceFlatBufferTest {
  @Test void hireRosterAndPersistentIdentityRoundTripSeparatelyFromItemStock() {
    FlatBufferBuilder builder = new FlatBufferBuilder(512);
    int description = builder.createString("comb");
    int offer = NpcHirelingOffer.createNpcHirelingOffer(builder, 3, 123, 1, 9,
        5, 33, 440, 250, 8500, description);
    int offers = NpcServiceResult.createHirelingsVector(builder, new int[] {offer});
    int mercenary = NpcMercenarySnapshot.createNpcMercenarySnapshot(builder, 456, 5, 9,
        0xFFFFFFFFL, 0x10000);
    NpcServiceResult.startNpcServiceResult(builder);
    NpcServiceResult.addRequestId(builder, 17);
    NpcServiceResult.addServiceType(builder, NpcServiceType.HIRE);
    NpcServiceResult.addHirelings(builder, offers);
    NpcServiceResult.addMercenary(builder, mercenary);
    int result = NpcServiceResult.endNpcServiceResult(builder);
    int root = D2GS.createD2GS(builder, D2GSData.NpcServiceResult, result);
    D2GS.finishD2GSBuffer(builder, root);
    NpcServiceResult decoded = (NpcServiceResult) D2GS.getRootAsD2GS(builder.dataBuffer())
        .data(new NpcServiceResult());
    assertEquals(1, decoded.hirelingsLength());
    assertEquals(0, decoded.stockLength());
    assertEquals("comb", decoded.hirelings(0).description());
    assertEquals(9, decoded.hirelings(0).hirelingId());
    assertEquals(3, decoded.hirelings(0).index());
    assertEquals(123, decoded.hirelings(0).seed());
    assertEquals(8500, decoded.hirelings(0).cost());
    assertEquals(456, decoded.mercenary().seed());
    assertEquals(0xFFFFFFFFL, decoded.mercenary().experience());
    assertEquals(0x10000, decoded.mercenary().flags());
  }
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
