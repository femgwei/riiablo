package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;
import com.badlogic.gdx.Gdx;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.engine.Engine;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.net.packet.d2gs.ItemMoveRequest;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter96;
import com.riiablo.skill.SkillCodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Windowless protocol client for an authoritative D2GS combat smoke test.
 *
 * <p>The client sends the same FlatBuffers connection and cast packets as the
 * game client. It can start an embedded headless D2GS, waits for a monster
 * snapshot, moves the test player next to that monster through the existing
 * client sync path, attacks it, and requires an authoritative life decrease.
 * This deliberately exercises the TCP boundary instead of calling combat ECS
 * systems directly.</p>
 */
public final class D2GSHeadlessClient {
  private static final String TAG = "[HEADLESS_COMBAT]";
  private static final int DEFAULT_PORT = 6114;
  private static final int MAX_PACKET_SIZE = 1 << 20;

  private final Config config;
  private final Map<Integer, Snapshot> monsters = new HashMap<>();
  private final Set<Integer> playerMissiles = new HashSet<>();
  private int playerId = Engine.INVALID_ENTITY;
  private boolean sawAttackMode;

  private D2GSHeadlessClient(Config config) {
    this.config = config;
  }

  public static void main(String[] args) throws Exception {
    Config config = Config.parse(args);
    boolean startedServer = config.home != null;
    if (startedServer) {
      log("server_start", "home=" + config.home + " seed=" + config.seed);
      D2GS.main(new String[] {
          "-home", config.home.getAbsolutePath(),
          "-seed", Integer.toString(config.seed),
          "-diff", Integer.toString(config.difficulty)
      });
    }

    try {
      new D2GSHeadlessClient(config).run();
    } finally {
      if (startedServer && Gdx.app != null) Gdx.app.exit();
    }
  }

  private void run() throws Exception {
    waitForServer();
    byte[] d2s = config.generatedAmazon
        ? createGeneratedAmazonSave()
        : Files.readAllBytes(config.save.toPath());
    CharacterHeader character = CharacterHeader.read(d2s);
    log("connect", "server=" + config.host + ':' + config.port
        + " character=" + character.name + " class=" + character.charClass);

    if (config.requireFallenScenario) {
      runFallenDual(d2s, character);
      return;
    }

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(config.host, config.port), config.connectTimeoutMillis);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(500);
      try (DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
           OutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
        send(output, connectionPacket(character, d2s));
        awaitConnection(input, System.currentTimeMillis() + config.testTimeoutMillis);
        if (config.requirePeer) verifyPeerVisibility(character, d2s, input, output);

        Snapshot target = awaitTarget(input, System.currentTimeMillis() + config.testTimeoutMillis);
        if (config.requireMonsterMovement) {
          Snapshot movingTarget = awaitMeleeTarget(
              input, System.currentTimeMillis() + config.testTimeoutMillis);
          verifyMonsterMovement(input, output, movingTarget);
        }
        float initialLife = target.life;
        log("target", String.format(
            "player=%d monster=%d monsterClass=%d position=(%.2f,%.2f) life=%.2f",
            playerId, target.entityId, target.monsterClass, target.x, target.y, initialLife));

        boolean damaged = attackUntilDamaged(input, output, target, initialLife);
        if (!damaged && !config.requirePeer) {
          throw new IllegalStateException("authoritative target life did not decrease after "
              + config.attempts + " cast attempts");
        }
        if (!sawAttackMode) {
          throw new IllegalStateException(
              "authoritative player attack mode was not synchronized to the client");
        }

        Snapshot result = monsters.get(target.entityId);
        log("pass", String.format(
            "player=%d target=%d skill=%d life=%.2f->%.2f damaged=%s attackMode=%s missiles=%d",
            playerId, target.entityId, config.skillId, initialLife,
            result == null ? 0f : result.life, damaged, sawAttackMode, playerMissiles.size()));
      }
    }
  }

  /**
   * Real two-socket regression: client A kills a Fallen, both clients observe
   * the death and subsequent Shaman resurrection, then client B picks up one
   * of the resulting ground drops after the native owner window expires.
   */
  private void runFallenDual(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient a = new D2GSHeadlessClient(config);
    D2GSHeadlessClient b = new D2GSHeadlessClient(config);
    try (Socket socketA = openSocket(); Socket socketB = openSocket()) {
      DataInputStream inA = input(socketA);
      DataInputStream inB = input(socketB);
      OutputStream outA = output(socketA);
      OutputStream outB = output(socketB);
      send(outA, connectionPacket(character, d2s));
      send(outB, connectionPacket(character, d2s));
      a.awaitConnection(inA, deadline());
      b.awaitConnection(inB, deadline());
      Snapshot fallenA = a.awaitNamedMonster(inA, "fallen1", false, deadline());
      Snapshot fallenB = b.awaitEntity(inB, fallenA.entityId, deadline());
      log("dual_target", "fallen=" + fallenA.entityId + " class=" + fallenA.monsterClass
          + " clients=" + a.playerId + "," + b.playerId);

      a.attackUntilDead(inA, outA, fallenA, deadline());
      b.awaitDead(inB, fallenA.entityId, deadline());
      Snapshot shamanA = a.awaitNamedMonster(inA, "fallenshaman1", true, deadline());
      Snapshot shamanB = b.awaitEntity(inB, shamanA.entityId, deadline());
      a.awaitRevived(inA, fallenA.entityId, deadline());
      b.awaitRevived(inB, fallenA.entityId, deadline());
      log("dual_revive_pass", "fallen=" + fallenA.entityId + " shaman="
          + shamanA.entityId + " clientA=true clientB=true");

      Snapshot drop = b.awaitGroundItem(inB, deadline());
      if (drop == null) throw new IOException("no ground drop observed after Fallen death");
      // Native D2 protects the killer's drop for a short owner window.  Verify
      // the peer path after that window rather than bypassing ownership rules.
      Thread.sleep(10_200L);
      send(outB, positionPacket(b.playerId, drop.x, drop.y));
      send(outB, itemMovePacket(1L, 0L, drop.entityId));
      ItemMoveResult result = b.awaitItemMoveResult(inB, deadline());
      if (result == null || !result.success()) {
        throw new IllegalStateException("peer pickup failed: result="
            + (result == null ? "timeout" : result.failure()));
      }
      log("dual_pickup_pass", "peer=" + b.playerId + " ground=" + drop.entityId
          + " revision=" + result.revision() + " inventorySnapshot="
          + result.snapshotLength());
    }
  }

  private Socket openSocket() throws IOException {
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(config.host, config.port), config.connectTimeoutMillis);
    socket.setTcpNoDelay(true);
    socket.setSoTimeout(500);
    return socket;
  }

  private static DataInputStream input(Socket socket) throws IOException {
    return new DataInputStream(new BufferedInputStream(socket.getInputStream()));
  }

  private static OutputStream output(Socket socket) {
    return new BufferedOutputStream(socketOutput(socket));
  }

  private static OutputStream socketOutput(Socket socket) {
    try {
      return socket.getOutputStream();
    } catch (IOException e) {
      throw new IllegalStateException("cannot open D2GS output", e);
    }
  }

  private long deadline() {
    return System.currentTimeMillis() + config.testTimeoutMillis;
  }

  private Snapshot awaitNamedMonster(DataInputStream input, String name, boolean allowDead,
                                     long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) {
        if (!snapshot.hasPosition || !snapshot.hasVitals) continue;
        if (!allowDead && snapshot.life <= 0f) continue;
        if (snapshot.monsterClass < 0 || Riiablo.files == null || Riiablo.files.monstats == null) continue;
        com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(snapshot.monsterClass);
        if (row != null && name.equalsIgnoreCase(row.Id)) return snapshot;
      }
    }
    throw new IOException("timed out waiting for monster " + name);
  }

  private Snapshot awaitEntity(DataInputStream input, int entityId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot != null && snapshot.hasPosition && snapshot.hasVitals) return snapshot;
    }
    throw new IOException("timed out waiting for entity " + entityId);
  }

  private void attackUntilDead(DataInputStream input, OutputStream output, Snapshot target,
                               long deadline) throws Exception {
    for (int attempt = 1; attempt <= Math.max(config.attempts, 40)
        && System.currentTimeMillis() < deadline; attempt++) {
      Snapshot current = monsters.get(target.entityId);
      if (current != null && (current.dead || current.life <= 0f)) return;
      float x = current == null ? target.x : current.x;
      float y = current == null ? target.y : current.y;
      send(output, positionPacket(playerId, x - 1f, y));
      send(output, castPacket(config.skillId, target.entityId, x, y));
      long attemptDeadline = Math.min(deadline, System.currentTimeMillis() + 1800L);
      while (System.currentTimeMillis() < attemptDeadline) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
        if (packet == null) continue;
        consume(packet);
        Snapshot after = monsters.get(target.entityId);
        if (after != null && (after.dead || after.life <= 0f)) {
          log("dual_death_pass", "killer=" + playerId + " fallen=" + target.entityId
              + " attempts=" + attempt);
          return;
        }
      }
    }
    throw new IOException("Fallen did not die after automatic attacks");
  }

  private void awaitDead(DataInputStream input, int entityId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot != null && (snapshot.dead || snapshot.life <= 0f)) return;
    }
    throw new IOException("peer did not observe Fallen death " + entityId);
  }

  private void awaitRevived(DataInputStream input, int entityId, long deadline) throws Exception {
    boolean deadSeen = false;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot == null) continue;
      deadSeen |= snapshot.dead || snapshot.life <= 0f;
      if (deadSeen && snapshot.life > 0f && !snapshot.dead) return;
    }
    throw new IOException("client did not observe Fallen resurrection " + entityId);
  }

  private Snapshot awaitGroundItem(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) if (snapshot.groundItem) return snapshot;
    }
    return null;
  }

  private ItemMoveResult awaitItemMoveResult(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      if (packet.dataType() == D2GSData.ItemMoveResult) {
        return (ItemMoveResult) packet.data(new ItemMoveResult());
      }
      consume(packet);
    }
    return null;
  }

  private void waitForServer() throws Exception {
    long deadline = System.currentTimeMillis() + config.serverTimeoutMillis;
    Throwable last = null;
    while (System.currentTimeMillis() < deadline) {
      try (Socket probe = new Socket()) {
        probe.connect(new InetSocketAddress(config.host, config.port), 250);
        log("server_ready", "server=" + config.host + ':' + config.port);
        return;
      } catch (Throwable t) {
        last = t;
        Thread.sleep(100);
      }
    }
    throw new IOException("D2GS did not open " + config.host + ':' + config.port, last);
  }

  private void awaitConnection(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      if (packet.dataType() == D2GSData.Connection) {
        Connection connection = (Connection) packet.data(new Connection());
        if (connection.charName() == null) {
          playerId = connection.entityId();
          log("connected", "player=" + playerId);
          return;
        }
      }
      consume(packet);
    }
    throw new IOException("timed out waiting for the D2GS connection acknowledgement");
  }

  private void verifyPeerVisibility(
      CharacterHeader character, byte[] d2s, DataInputStream firstInput,
      OutputStream firstOutput) throws Exception {
    try (Socket peer = new Socket()) {
      peer.connect(new InetSocketAddress(config.host, config.port), config.connectTimeoutMillis);
      peer.setTcpNoDelay(true);
      peer.setSoTimeout(500);
      try (DataInputStream peerInput =
               new DataInputStream(new BufferedInputStream(peer.getInputStream()));
           OutputStream peerOutput = new BufferedOutputStream(peer.getOutputStream())) {
        send(peerOutput, connectionPacket(character, d2s));
        int peerId = awaitConnectionId(
            peerInput, System.currentTimeMillis() + config.testTimeoutMillis);
        boolean firstSawPeer = false;
        boolean peerSawFirst = false;
        boolean peerReceivedFirstEntity = false;
        float firstX = Float.NaN;
        float firstY = Float.NaN;
        int peerEntitySyncs = 0;
        long deadline = System.currentTimeMillis() + config.testTimeoutMillis;
        while (System.currentTimeMillis() < deadline && (!firstSawPeer || !peerSawFirst)) {
          com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
          if (packet != null) {
            if (packet.dataType() == D2GSData.Connection) {
              Connection connection = (Connection) packet.data(new Connection());
              firstSawPeer |= connection.charName() != null
                  && connection.entityId() == peerId;
            }
            consume(packet);
          }

          packet = readPacket(peerInput);
          if (packet != null && packet.dataType() == D2GSData.EntitySync) {
            EntitySync sync = (EntitySync) packet.data(new EntitySync());
            peerEntitySyncs++;
            if (sync.entityId() == playerId) {
              peerReceivedFirstEntity = true;
              int positionIndex = findComponent(sync, ComponentP.PositionP);
              if (positionIndex >= 0) {
                PositionP position = (PositionP) sync.component(new PositionP(), positionIndex);
                firstX = position.x();
                firstY = position.y();
              }
              log("peer_snapshot", "entity=" + sync.entityId() + " type=" + sync.type()
                  + " components=" + componentTypes(sync));
            }
            peerSawFirst |= sync.entityId() == playerId
                && findComponent(sync, ComponentP.PlayerP) >= 0;
          }
        }
        if (!firstSawPeer || !peerSawFirst) {
          throw new IllegalStateException("multiplayer visibility failed: firstSawPeer="
              + firstSawPeer + " peerSawFirst=" + peerSawFirst
              + " peerReceivedFirstEntity=" + peerReceivedFirstEntity
              + " peerEntitySyncs=" + peerEntitySyncs);
        }
        log("peer_pass", "first=" + playerId + " peer=" + peerId
            + " firstSawPeer=true peerSawFirst=true");

        if (!Float.isFinite(firstX) || !Float.isFinite(firstY)) {
          throw new IllegalStateException("peer baseline omitted first player position");
        }

        // Regression: joining the second client must not freeze the first
        // player's input stream. Send a real movement snapshot after the peer
        // is fully visible and require D2GS to echo both movement and RN mode.
        send(firstOutput, movementPacket(playerId, firstX, firstY, 1f, 0f));
        boolean positionAdvanced = false;
        boolean movementMode = false;
        deadline = System.currentTimeMillis() + config.testTimeoutMillis;
        while (System.currentTimeMillis() < deadline && (!positionAdvanced || !movementMode)) {
          com.riiablo.net.packet.d2gs.D2GS packet = readPacket(peerInput);
          if (packet == null || packet.dataType() != D2GSData.EntitySync) continue;
          EntitySync sync = (EntitySync) packet.data(new EntitySync());
          if (sync.entityId() != playerId) continue;
          int positionIndex = findComponent(sync, ComponentP.PositionP);
          if (positionIndex >= 0) {
            PositionP position = (PositionP) sync.component(new PositionP(), positionIndex);
            positionAdvanced |= position.x() > firstX + 0.001f;
          }
          int cofIndex = findComponent(sync, ComponentP.CofReferenceP);
          if (cofIndex >= 0) {
            CofReferenceP cof = (CofReferenceP) sync.component(new CofReferenceP(), cofIndex);
            movementMode |= cof.mode() == Engine.Player.MODE_RN
                || cof.mode() == Engine.Player.MODE_TW
                || cof.mode() == Engine.Player.MODE_WL;
          }
        }
        if (!positionAdvanced || !movementMode) {
          throw new IllegalStateException("first player movement failed after peer joined: "
              + "positionAdvanced=" + positionAdvanced + " movementMode=" + movementMode);
        }
        log("peer_movement_pass", "first=" + playerId + " peer=" + peerId
            + " positionAdvanced=true movementMode=true");
        // Leave the first client in the same stopped state as a real client
        // after releasing movement input. Otherwise this synthetic velocity
        // remains authoritative and drags the later combat target forever.
        send(firstOutput, movementPacket(playerId, firstX, firstY, 0f, 0f));
      }
    }
  }

  private static int awaitConnectionId(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null || packet.dataType() != D2GSData.Connection) continue;
      Connection connection = (Connection) packet.data(new Connection());
      if (connection.charName() == null) return connection.entityId();
    }
    throw new IOException("timed out waiting for peer connection acknowledgement");
  }

  private Snapshot awaitTarget(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) {
        if (snapshot.hasPosition && snapshot.hasVitals && snapshot.life > 0f
            && isHostileMonster(snapshot, false)) return snapshot;
      }
    }
    throw new IOException("timed out waiting for a live monster snapshot");
  }

  private Snapshot awaitMeleeTarget(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      for (Snapshot snapshot : monsters.values()) {
        if (snapshot.hasPosition && snapshot.hasVitals && snapshot.life > 0f
            && isHostileMonster(snapshot, true)) return snapshot;
      }
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet != null) consume(packet);
    }
    throw new IOException("timed out waiting for a live melee monster snapshot");
  }

  private boolean isHostileMonster(Snapshot snapshot, boolean requireMelee) {
    if (snapshot.monsterClass < 0 || Riiablo.files == null
        || Riiablo.files.monstats == null) return true;
    com.riiablo.codec.excel.MonStats.Entry monster =
        Riiablo.files.monstats.get(snapshot.monsterClass);
    return monster != null && !monster.npc && monster.killable
        && (!requireMelee || monster.isMelee);
  }

  private void verifyMonsterMovement(
      DataInputStream input, OutputStream output, Snapshot selected) throws Exception {
    float startX = selected.x;
    float startY = selected.y;
    send(output, positionPacket(playerId, startX - 8f, startY));
    long deadline = System.currentTimeMillis() + Math.min(config.testTimeoutMillis, 8000L);
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot current = monsters.get(selected.entityId);
      if (current == null || !current.hasPosition) continue;
      float dx = current.x - startX;
      float dy = current.y - startY;
      if (dx * dx + dy * dy >= 0.01f) {
        log("monster_move_pass", String.format(
            "monster=%d class=%d from=(%.2f,%.2f) to=(%.2f,%.2f)",
            current.entityId, current.monsterClass, startX, startY, current.x, current.y));
        return;
      }
    }
    throw new IllegalStateException(String.format(
        "hostile monster did not move after player approached: monster=%d class=%d position=(%.2f,%.2f)",
        selected.entityId, selected.monsterClass, startX, startY));
  }

  private boolean attackUntilDamaged(
      DataInputStream input, OutputStream output, Snapshot selected, float initialLife)
      throws Exception {
    long deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    for (int attempt = 1; attempt <= config.attempts && System.currentTimeMillis() < deadline;
         attempt++) {
      Snapshot target = monsters.get(selected.entityId);
      if (target == null || !target.hasPosition) break;
      float playerX = target.x - (config.requireMissile ? 6f : 1f);
      float playerY = target.y;
      send(output, positionPacket(playerId, playerX, playerY));
      send(output, castPacket(config.skillId, target.entityId, target.x, target.y));
      log("cast", String.format(
          "attempt=%d skill=%d target=%d targetPos=(%.2f,%.2f)",
          attempt, config.skillId, target.entityId, target.x, target.y));

      long attemptDeadline = Math.min(deadline, System.currentTimeMillis() + 1800L);
      while (System.currentTimeMillis() < attemptDeadline) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
        if (packet == null) continue;
        consume(packet);
        // The multiplayer task specifically verifies presentation-state
        // synchronization. Damage has its own headlessCombat task and can be
        // nondeterministic here when the selected AI is already fleeing.
        if (config.requirePeer && sawAttackMode) return false;
        Snapshot current = monsters.get(selected.entityId);
        boolean damaged = current != null && (current.life < initialLife || current.dead);
        if (damaged && (!config.requireMissile || !playerMissiles.isEmpty())) return true;
      }
    }
    return false;
  }

  private void consume(com.riiablo.net.packet.d2gs.D2GS packet) {
    if (packet.dataType() != D2GSData.EntitySync) return;
    EntitySync sync = (EntitySync) packet.data(new EntitySync());
    if (sync.entityId() == playerId) {
      int cofIndex = findComponent(sync, ComponentP.CofReferenceP);
      if (cofIndex >= 0) {
        CofReferenceP cof = (CofReferenceP) sync.component(new CofReferenceP(), cofIndex);
        if (cof.mode() >= Engine.Player.MODE_A1 && cof.mode() <= Engine.Player.MODE_S4) {
          if (!sawAttackMode) {
            log("attack_mode", "player=" + playerId + " mode=" + cof.mode());
          }
          sawAttackMode = true;
        }
      }
    }
    if (sync.type() == 3) {
      Snapshot snapshot = monsters.get(sync.entityId());
      if (snapshot == null) {
        snapshot = new Snapshot(sync.entityId());
        monsters.put(sync.entityId(), snapshot);
      }
      snapshot.groundItem = findComponent(sync, ComponentP.ItemP) >= 0;
      int positionIndex = findComponent(sync, ComponentP.PositionP);
      if (positionIndex >= 0) {
        PositionP position = (PositionP) sync.component(new PositionP(), positionIndex);
        snapshot.x = position.x();
        snapshot.y = position.y();
        snapshot.hasPosition = true;
      }
      return;
    }
    if (sync.type() == 5) {
      int missileIndex = findComponent(sync, ComponentP.MissileP);
      if (missileIndex >= 0) {
        MissileP missile = (MissileP) sync.component(new MissileP(), missileIndex);
        if (missile.ownerId() == playerId && playerMissiles.add(sync.entityId())) {
          log("missile", "entity=" + sync.entityId() + " owner=" + missile.ownerId()
              + " missile=" + missile.missileId());
        }
      }
      return;
    }
    if (sync.type() != 1) return;

    Snapshot snapshot = monsters.get(sync.entityId());
    if (snapshot == null) {
      snapshot = new Snapshot(sync.entityId());
      monsters.put(sync.entityId(), snapshot);
    }
    if ((sync.flags() & EntityFlags.deleted) != 0) {
      snapshot.life = 0f;
      snapshot.dead = true;
      snapshot.hasVitals = true;
      return;
    }
    int index = findComponent(sync, ComponentP.MonsterP);
    if (index >= 0) {
      MonsterP monster = (MonsterP) sync.component(new MonsterP(), index);
      snapshot.monsterClass = monster.monsterId();
    }
    index = findComponent(sync, ComponentP.PositionP);
    if (index >= 0) {
      PositionP position = (PositionP) sync.component(new PositionP(), index);
      snapshot.x = position.x();
      snapshot.y = position.y();
      snapshot.hasPosition = true;
    }
    index = findComponent(sync, ComponentP.VitalsP);
    if (index >= 0) {
      VitalsP vitals = (VitalsP) sync.component(new VitalsP(), index);
      snapshot.life = vitals.hitpoints();
      snapshot.dead = vitals.dead();
      snapshot.hasVitals = true;
    }
  }

  private static int findComponent(EntitySync sync, byte type) {
    for (int i = 0; i < sync.componentLength(); i++) {
      if (sync.componentType(i) == type) return i;
    }
    return -1;
  }

  private static String componentTypes(EntitySync sync) {
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < sync.componentLength(); i++) {
      if (i > 0) builder.append(',');
      builder.append(ComponentP.name(sync.componentType(i)));
    }
    return builder.append(']').toString();
  }

  private static com.riiablo.net.packet.d2gs.D2GS readPacket(DataInputStream input)
      throws IOException {
    try {
      int size = readLittleEndianInt(input);
      if (size <= 0 || size > MAX_PACKET_SIZE) {
        throw new IOException("invalid D2GS packet size " + size);
      }
      byte[] payload = new byte[size];
      input.readFully(payload);
      return com.riiablo.net.packet.d2gs.D2GS.getRootAsD2GS(
          ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN));
    } catch (SocketTimeoutException ignored) {
      return null;
    }
  }

  private static int readLittleEndianInt(DataInputStream input) throws IOException {
    int b0 = input.read();
    if (b0 < 0) throw new EOFException("D2GS closed the connection");
    int b1 = input.read();
    int b2 = input.read();
    int b3 = input.read();
    if ((b1 | b2 | b3) < 0) throw new EOFException("incomplete D2GS size prefix");
    return b0 | b1 << 8 | b2 << 16 | b3 << 24;
  }

  private static ByteBuffer connectionPacket(CharacterHeader character, byte[] d2s) {
    FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(1024, d2s.length + 256));
    int name = builder.createString(character.name);
    byte[] components = new byte[16];
    byte[] transforms = new byte[16];
    byte[] alphas = new byte[16];
    for (int i = 0; i < alphas.length; i++) alphas[i] = (byte) 0xFF;
    int componentsOffset = Connection.createCofComponentsVector(builder, components);
    int transformsOffset = Connection.createCofTransformsVector(builder, transforms);
    int alphasOffset = Connection.createCofAlphasVector(builder, alphas);
    int d2sOffset = Connection.createD2sVector(builder, d2s);
    Connection.startConnection(builder);
    Connection.addCharClass(builder, character.charClass);
    Connection.addCharName(builder, name);
    Connection.addCofComponents(builder, componentsOffset);
    Connection.addCofTransforms(builder, transformsOffset);
    Connection.addCofAlphas(builder, alphasOffset);
    Connection.addD2s(builder, d2sOffset);
    int connection = Connection.endConnection(builder);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.Connection, connection);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer positionPacket(int playerId, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int position = PositionP.createPositionP(builder, x, y);
    int types = EntitySync.createComponentTypeVector(builder,
        new byte[] {ComponentP.PositionP});
    int components = EntitySync.createComponentVector(builder, new int[] {position});
    int sync = EntitySync.createEntitySync(builder, playerId, 2, 0, types, components);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer movementPacket(
      int playerId, float x, float y, float velocityX, float velocityY) {
    FlatBufferBuilder builder = new FlatBufferBuilder(192);
    int position = PositionP.createPositionP(builder, x, y);
    int velocity = VelocityP.createVelocityP(builder, velocityX, velocityY);
    int angle = AngleP.createAngleP(builder, velocityX, velocityY);
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {
        ComponentP.PositionP, ComponentP.VelocityP, ComponentP.AngleP});
    int components = EntitySync.createComponentVector(
        builder, new int[] {position, velocity, angle});
    int sync = EntitySync.createEntitySync(builder, playerId, 2, 0, types, components);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer castPacket(int skillId, int targetId, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = CastSkillRequest.createCastSkillRequest(
        builder, skillId, targetId, x, y);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.CastSkillRequest, request);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer itemMovePacket(long requestId, long revision, int groundEntityId) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = ItemMoveRequest.createItemMoveRequest(builder, requestId, revision,
        ItemMoveOperation.GROUND_TO_CURSOR, -1, groundEntityId, -1, -1, -1, -1, false);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.ItemMoveRequest, request);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  /** Creates a normal level-one Amazon whose native starting javelin owns Throw. */
  private static byte[] createGeneratedAmazonSave() {
    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "HeadlessAma", Riiablo.AMAZON);
    com.riiablo.codec.excel.CharStats.Entry stats = CharacterClass.AMAZON.entry();
    StatListRef base = character.getStats().base();
    base.put(Stat.strength, stats.str);
    base.put(Stat.energy, stats._int);
    base.put(Stat.dexterity, stats.dex);
    base.put(Stat.vitality, stats.vit);
    base.put(Stat.statpts, 0);
    base.put(Stat.newskills, 0);
    int maxHp = stats.vit + stats.hpadd;
    base.put(Stat.hitpoints, maxHp);
    base.put(Stat.maxhp, maxHp);
    base.put(Stat.mana, stats._int);
    base.put(Stat.maxmana, stats._int);
    base.put(Stat.stamina, stats.stamina);
    base.put(Stat.maxstamina, stats.stamina);
    base.put(Stat.level, 1);
    base.put(Stat.experience, 0);
    base.put(Stat.gold, 0);
    base.put(Stat.goldbank, 0);
    character.getStats().reset();
    character.activateWaypoint(Riiablo.NORMAL, Riiablo.ACT1, 0);
    character.mapSeed = 0x48434D41; // "HCMA", stable for reproducible item ids.
    character.initializeStartItems(stats);
    byte[] data = new D2SWriter96().writeD2S(D2SWriter96.createD2S(character));
    log("character_generated", "name=HeadlessAma class=amazon skill=throw bytes=" + data.length);
    return data;
  }

  private static void send(OutputStream output, ByteBuffer buffer) throws IOException {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    output.write(bytes);
    output.flush();
  }

  private static void log(String phase, String details) {
    System.out.println(TAG + " phase=" + phase + ' ' + details);
  }

  private static final class Snapshot {
    final int entityId;
    int monsterClass = -1;
    float x;
    float y;
    float life;
    boolean dead;
    boolean groundItem;
    boolean hasPosition;
    boolean hasVitals;

    Snapshot(int entityId) {
      this.entityId = entityId;
    }
  }

  private static final class CharacterHeader {
    final String name;
    final int charClass;

    CharacterHeader(String name, int charClass) {
      this.name = name;
      this.charClass = charClass;
    }

    static CharacterHeader read(byte[] bytes) {
      if (bytes.length < 0x29
          || (bytes[0] & 0xFF) != 0x55 || (bytes[1] & 0xFF) != 0xAA
          || (bytes[2] & 0xFF) != 0x55 || (bytes[3] & 0xFF) != 0xAA) {
        throw new IllegalArgumentException("save is not a valid D2S file");
      }
      int end = 0x14;
      while (end < 0x24 && bytes[end] != 0) end++;
      String name = new String(bytes, 0x14, end - 0x14, StandardCharsets.US_ASCII);
      if (name.isEmpty()) throw new IllegalArgumentException("D2S character name is empty");
      return new CharacterHeader(name, bytes[0x28] & 0xFF);
    }
  }

  private static final class Config {
    String host = "127.0.0.1";
    int port = DEFAULT_PORT;
    File home;
    File save;
    int seed = 12345;
    int difficulty;
    int skillId = SkillCodes.attack;
    boolean generatedAmazon;
    boolean requireMissile;
    boolean requirePeer;
    boolean requireMonsterMovement;
    boolean requireFallenScenario;
    int attempts = 20;
    int connectTimeoutMillis = 2000;
    int serverTimeoutMillis = 180000;
    int testTimeoutMillis = 60000;

    static Config parse(String[] args) throws IOException {
      Config config = new Config();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--host".equals(arg)) config.host = value(args, ++i, arg);
        else if ("--port".equals(arg)) config.port = integer(args, ++i, arg);
        else if ("--home".equals(arg)) config.home = new File(value(args, ++i, arg));
        else if ("--save".equals(arg)) config.save = new File(value(args, ++i, arg));
        else if ("--seed".equals(arg)) config.seed = integer(args, ++i, arg);
        else if ("--difficulty".equals(arg)) config.difficulty = integer(args, ++i, arg);
        else if ("--skill".equals(arg)) config.skillId = integer(args, ++i, arg);
        else if ("--generated-amazon".equals(arg)) config.generatedAmazon = true;
        else if ("--require-missile".equals(arg)) config.requireMissile = true;
        else if ("--require-peer".equals(arg)) config.requirePeer = true;
        else if ("--require-monster-movement".equals(arg)) config.requireMonsterMovement = true;
        else if ("--require-fallen-scenario".equals(arg)) config.requireFallenScenario = true;
        else if ("--attempts".equals(arg)) config.attempts = integer(args, ++i, arg);
        else if ("--server-timeout".equals(arg)) {
          config.serverTimeoutMillis = integer(args, ++i, arg) * 1000;
        } else if ("--test-timeout".equals(arg)) {
          config.testTimeoutMillis = integer(args, ++i, arg) * 1000;
        } else if ("--help".equals(arg)) {
          usage();
          System.exit(0);
        } else {
          throw new IllegalArgumentException("unknown argument " + arg);
        }
      }

      if (config.home == null) {
        String env = System.getenv("D2_HOME");
        if (env != null && !env.isEmpty()) config.home = new File(env);
      }
      if (config.home != null && !new File(config.home, "d2data.mpq").isFile()) {
        throw new IOException("--home is not a valid D2 installation: " + config.home);
      }
      if (config.home != null && config.port != DEFAULT_PORT) {
        throw new IllegalArgumentException("embedded D2GS currently listens on port "
            + DEFAULT_PORT + "; omit --home when testing an external server port");
      }
      if (!config.generatedAmazon && config.save == null && config.home != null) {
        config.save = firstSave(new File(config.home, "Save"));
      }
      if (!config.generatedAmazon && (config.save == null || !config.save.isFile())) {
        throw new IOException("provide --save <character.d2s>, or put a save in <home>/Save");
      }
      return config;
    }

    private static File firstSave(File directory) {
      File[] saves = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".d2s"));
      return saves == null || saves.length == 0 ? null : saves[0];
    }

    private static String value(String[] args, int index, String option) {
      if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
      return args[index];
    }

    private static int integer(String[] args, int index, String option) {
      return Integer.parseInt(value(args, index, option));
    }

    private static void usage() {
      System.out.println("Usage: D2GSHeadlessClient [--home <D2 dir>] [--save <file.d2s>]"
          + " [--generated-amazon] [--host 127.0.0.1] [--port 6114]"
          + " [--skill 0] [--require-missile] [--require-fallen-scenario] [--attempts 20]");
    }
  }
}
