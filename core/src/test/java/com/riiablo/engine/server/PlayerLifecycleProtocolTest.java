package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
import org.junit.jupiter.api.Test;

class PlayerLifecycleProtocolTest {
  @Test
  void respawnRequestRoundTripsThroughD2gsUnion() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int request = PlayerLifecycleRequest.createPlayerLifecycleRequest(
        builder, 42, PlayerLifecycleOperation.RESPAWN);
    int root = D2GS.createD2GS(builder, D2GSData.PlayerLifecycleRequest, request);
    D2GS.finishD2GSBuffer(builder, root);

    D2GS packet = D2GS.getRootAsD2GS(builder.dataBuffer());
    assertEquals(D2GSData.PlayerLifecycleRequest, packet.dataType());
    PlayerLifecycleRequest decoded = (PlayerLifecycleRequest) packet.data(
        new PlayerLifecycleRequest());
    assertEquals(42, decoded.requestId());
    assertEquals(PlayerLifecycleOperation.RESPAWN, decoded.operation());
  }

  @Test
  void authoritativeResultCarriesPositionAndIdentity() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reason = builder.createString("OK");
    int result = PlayerLifecycleResult.createPlayerLifecycleResult(builder,
        9, true, reason, PlayerLifecycleOperation.RESPAWN, 17, 12.5f, 30.25f);
    int root = D2GS.createD2GS(builder, D2GSData.PlayerLifecycleResult, result);
    D2GS.finishD2GSBuffer(builder, root);

    D2GS packet = D2GS.getRootAsD2GS(builder.dataBuffer());
    PlayerLifecycleResult decoded = (PlayerLifecycleResult) packet.data(
        new PlayerLifecycleResult());
    assertTrue(decoded.success());
    assertEquals(17, decoded.playerEntityId());
    assertEquals(12.5f, decoded.x());
    assertEquals(30.25f, decoded.y());
  }
}
