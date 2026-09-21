package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.trade.TradeState;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.TradeItemSnapshot;
import com.riiablo.net.packet.d2gs.TradeOperation;
import com.riiablo.net.packet.d2gs.TradeResult;

class ClientTradeStateTest {
  @Test
  void appliesAuthoritativeSnapshotAndClosesOnCompletion() {
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int item = TradeItemSnapshot.createTradeItemSnapshot(builder, 77, 1, 2, 2, 1, 3, true);
    int items = TradeResult.createSourceItemsVector(builder, new int[] {item});
    int reason = builder.createString("OK");
    int result = TradeResult.createTradeResult(builder, 41, true, reason,
        TradeOperation.CONFIRM, 9, 10, 20, TradeState.COMPLETED,
        12, 4, items, 0);
    int root = D2GS.createD2GS(builder, D2GSData.TradeResult, result);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);

    D2GS packet = D2GS.getRootAsD2GS(frame);
    ClientTradeState state = new ClientTradeState();
    state.apply((TradeResult) packet.data(new TradeResult()));

    assertEquals(41, state.lastRequestId());
    assertTrue(state.lastSuccess());
    assertEquals(9, state.sessionId());
    assertEquals(12, state.sourceGold());
    assertEquals(1, state.sourceItems().size);
    assertEquals(77, state.sourceItems().first().itemId);
    assertTrue(!state.active());
  }
}
