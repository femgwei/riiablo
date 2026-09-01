package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.net.packet.d2gs.PartyMemberSnapshot;
import com.riiablo.net.packet.d2gs.PartyRequest;
import com.riiablo.net.packet.d2gs.PartyResult;
import com.riiablo.engine.client.ClientPartyState;
import org.junit.jupiter.api.Test;

class PartyProtocolFlatBufferTest {
  @Test
  void requestRoundTripsThroughD2gsUnion() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = PartyRequest.createPartyRequest(builder, 42, PartyOperation.INVITE, 77);
    int root = D2GS.createD2GS(builder, D2GSData.PartyRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    D2GS packet = D2GS.getRootAsD2GS(frame);
    assertEquals(D2GSData.PartyRequest, packet.dataType());
    PartyRequest decoded = (PartyRequest) packet.data(new PartyRequest());
    assertEquals(42, decoded.requestId());
    assertEquals(PartyOperation.INVITE, decoded.operation());
    assertEquals(77, decoded.targetEntityId());
    assertTrue(D2GSData.name(packet.dataType()).contains("Party"));
  }

  @Test
  void resultDetachesIntoClientState() {
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int name = builder.createString("Amazon");
    int member = PartyMemberSnapshot.createPartyMemberSnapshot(builder,
        77, name, 0, 9, 30, 40, 12, 20, 2, 10, 11,
        true, true, false, -1, PartyRelation.INVITED);
    int members = PartyResult.createMembersVector(builder, new int[] {member});
    int reason = builder.createString("");
    int result = PartyResult.createPartyResult(builder, 4, true, reason,
        PartyOperation.INVITE, 77, 88, -1, members, 12_345L);
    int root = D2GS.createD2GS(builder, D2GSData.PartyResult, result);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    D2GS packet = D2GS.getRootAsD2GS(frame);
    PartyResult decoded = (PartyResult) packet.data(new PartyResult());

    ClientPartyState state = new ClientPartyState();
    state.apply(decoded);
    assertEquals(77, state.incomingInviterId());
    assertEquals("Amazon", state.get(77).name);
    assertEquals(30, state.get(77).hp);
    assertEquals(12_345L, state.lastRetryAfterMillis());
    assertEquals(1, state.revision());

    FlatBufferBuilder refreshBuilder = new FlatBufferBuilder(128);
    int refreshReason = refreshBuilder.createString("");
    int refreshResult = PartyResult.createPartyResult(refreshBuilder, 0, true,
        refreshReason, PartyOperation.SNAPSHOT, 88, -1, -1, 0, 0L);
    int refreshRoot = D2GS.createD2GS(
        refreshBuilder, D2GSData.PartyResult, refreshResult);
    D2GS.finishSizePrefixedD2GSBuffer(refreshBuilder, refreshRoot);
    ByteBuffer refreshFrame = refreshBuilder.dataBuffer();
    refreshFrame.position(refreshFrame.position() + Integer.BYTES);
    PartyResult refresh = (PartyResult) D2GS.getRootAsD2GS(refreshFrame)
        .data(new PartyResult());
    state.apply(refresh);
    assertEquals(4, state.lastRequestId());
    assertEquals(PartyOperation.INVITE, state.lastOperation());
    assertEquals(12_345L, state.lastRetryAfterMillis());
    assertEquals(2, state.revision());
  }
}
