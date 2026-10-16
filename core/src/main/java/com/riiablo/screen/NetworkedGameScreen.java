package com.riiablo.screen;

import com.artemis.WorldConfigurationBuilder;

import com.badlogic.gdx.net.Socket;

import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.client.AuthoritativeInterpolationSystem;
import com.riiablo.engine.client.NetworkProfiler;
import com.riiablo.engine.client.Pinger;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import com.riiablo.util.Disposal;

public class NetworkedGameScreen extends GameScreen {
  private static final String TAG = "NetworkedGameScreen";
  private static final boolean DEBUG = true;

  private final Runnable stopHostedServer;
  private boolean networkDisposed;

  public NetworkedGameScreen(CharData charData, Socket socket) {
    this(charData, socket, false);
  }

  public NetworkedGameScreen(CharData charData, Socket socket, boolean localHost) {
    super(charData, socket);
    // Capture this host's process, not whichever process happens to be current
    // when an old screen is eventually disposed.
    stopHostedServer = localHost ? HostedD2GS.stopAction() : null;
  }

  @Override
  protected WorldConfigurationBuilder getWorldConfigurationBuilder() {
    WorldConfigurationBuilder builder = super.getWorldConfigurationBuilder();
    builder.with(WorldConfigurationBuilder.Priority.HIGH,
        new AuthoritativeInterpolationSystem());
    builder.with(WorldConfigurationBuilder.Priority.HIGH, new ClientNetworkReceiver());
    builder.with(new ClientNetworkSynchronizer());
    builder.with(new Pinger());
    builder.with(new NetworkProfiler());
    return builder;
  }

  @Override
  public void dispose() {
    if (networkDisposed) return;
    networkDisposed = true;
    try (Disposal cleanup = new Disposal()) {
      // Retire World/network consumers before closing their borrowed socket.
      cleanup.run(super::dispose);
      Socket ownedSocket = socket;
      socket = null;
      cleanup.dispose(ownedSocket);
      if (stopHostedServer != null) cleanup.run(stopHostedServer);
    }
  }

  @Override
  protected int resolveMapSeed(CharData charData) {
    // HostedD2GS and the default dedicated server currently use gameSeed=0.
    // Render the authoritative layout rather than the save-file seed, or
    // server monsters and remote players occupy another DRLG coordinate space.
    int seed = Riiablo.gameSeed;
    com.badlogic.gdx.Gdx.app.log(TAG, String.format(
        "[NETWORK_MAP] authoritativeSeed=0x%08X saveSeed=0x%08X difficulty=%d",
        seed, charData.mapSeed, charData.diff));
    return seed;
  }
}
