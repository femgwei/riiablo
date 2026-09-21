package com.riiablo.engine.server.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.TradeOperation;
import com.riiablo.net.packet.d2gs.TradeRequest;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class TradeProtocolFlatBufferTest {
  @Test
  void requestRoundTripsThroughD2gsUnion() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = TradeRequest.createTradeRequest(builder, 41,
        TradeOperation.ADD_ITEM, 77, 12, 9001, 2, 1, 0);
    int root = D2GS.createD2GS(builder, D2GSData.TradeRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    D2GS packet = D2GS.getRootAsD2GS(frame);
    TradeRequest decoded = (TradeRequest) packet.data(new TradeRequest());
    assertEquals(D2GSData.TradeRequest, packet.dataType());
    assertEquals(41, decoded.requestId());
    assertEquals(TradeOperation.ADD_ITEM, decoded.operation());
    assertEquals(77, decoded.targetEntityId());
    assertEquals(9001, decoded.itemId());
    assertEquals(2, decoded.x());
    assertEquals(1, decoded.y());
    assertTrue(D2GSData.name(packet.dataType()).contains("Trade"));
  }
}
