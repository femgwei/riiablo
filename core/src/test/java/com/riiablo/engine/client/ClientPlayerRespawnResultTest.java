package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.net.Socket;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Position;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ClientPlayerRespawnResultTest extends RiiabloTest {
  @Test void oneOutstandingRequestAcceptsOnlyItsOwnResultOnce() throws Exception {
    ClientNetworkSynchronizer network = new ClientNetworkSynchronizer();
    network.socket = mock(Socket.class);
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    when(network.socket.getOutputStream()).thenReturn(sent);
    long first = network.requestPlayerRespawn();
    int size = sent.size();
    assertEquals(first, network.requestPlayerRespawn());
    assertEquals(size, sent.size(), "repeated ESC does not duplicate an in-flight intent");
    assertFalse(network.acceptPlayerLifecycleResult(0));
    assertFalse(network.acceptPlayerLifecycleResult(first + 1));
    assertTrue(network.acceptPlayerLifecycleResult(first));
    assertFalse(network.acceptPlayerLifecycleResult(first));
    long second = network.requestPlayerRespawn();
    assertTrue(second > first);
    assertFalse(network.acceptPlayerLifecycleResult(first));
    assertTrue(network.acceptPlayerLifecycleResult(second));
  }

  @Test void evenMatchingAckCannotTeleportOrReviveAnEntity() throws Exception {
    ClientNetworkSynchronizer network = new ClientNetworkSynchronizer();
    network.socket = mock(Socket.class);
    when(network.socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int player = world.create();
      Position current = world.getMapper(Position.class).create(player);
      current.initializeNativeCell(99, 101);
      world.process();
      ClientNetworkReceiver receiver = new ClientNetworkReceiver();
      receiver.networkSynchronizer = network;
      // No local death system/map/player is available to calculate a result.
      // A successful acknowledgement can only ask for timestamped snapshots.
      long request = network.requestPlayerRespawn();
      receive(receiver, result(request, true, 1f, 2f));
      assertEquals(99f, current.position.x);
      assertEquals(101f, current.position.y);
      assertTrue(current.hasNativePrecision());
      receive(receiver, result(request, true, -100f, -200f));
      assertEquals(99f, current.position.x);
      assertTrue(current.hasNativePrecision());
    } finally { world.dispose(); }
  }

  @Test void rejectionCompletesOnlyMatchingRequestAndAllowsRetry() throws Exception {
    ClientNetworkSynchronizer network = new ClientNetworkSynchronizer();
    network.socket = mock(Socket.class);
    when(network.socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    ClientNetworkReceiver receiver = new ClientNetworkReceiver();
    receiver.networkSynchronizer = network;
    long request = network.requestPlayerRespawn();
    receive(receiver, result(request, false, 1f, 2f));
    assertTrue(network.requestPlayerRespawn() > request);
  }

  static void receive(ClientNetworkReceiver receiver, D2GS packet) throws Exception {
    Method process = ClientNetworkReceiver.class.getDeclaredMethod("process", D2GS.class);
    process.setAccessible(true);
    process.invoke(receiver, packet);
  }

  private static D2GS result(long request, boolean success, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reason = builder.createString(success ? "OK" : "PLAYER_NOT_DEAD");
    int result = PlayerLifecycleResult.createPlayerLifecycleResult(builder, request, success,
        reason, PlayerLifecycleOperation.RESPAWN, 100, x, y);
    builder.finish(D2GS.createD2GS(builder, D2GSData.PlayerLifecycleResult, result));
    return D2GS.getRootAsD2GS(builder.dataBuffer());
  }
}
