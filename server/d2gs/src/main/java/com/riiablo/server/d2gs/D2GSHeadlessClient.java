package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
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
import com.riiablo.net.packet.d2gs.Disconnect;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.net.packet.d2gs.ItemMoveRequest;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.RunToLocation;
import com.riiablo.net.packet.d2gs.QuestOperation;
import com.riiablo.net.packet.d2gs.QuestRequest;
import com.riiablo.net.packet.d2gs.QuestResult;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.net.packet.d2gs.SnapshotBaseline;
import com.riiablo.net.packet.d2gs.SnapshotBaselinePhase;
import com.riiablo.net.packet.d2gs.SnapshotResyncRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
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
  private final Map<Integer, Visibility> visibility = new HashMap<>();
  private final Set<Integer> playerMissiles = new HashSet<>();
  private final Set<Long> snapshotTicks = new HashSet<>();
  private int playerId = Engine.INVALID_ENTITY;
  private boolean sawAttackMode;
  private long lastSnapshotTick = -1L;
  private long lastSnapshotServerTime = -1L;
  private long lastMovementAcknowledgement;
  private long lastRejectedMovementSequence;
  private long combatSequence;
  private int currentLevelId = -1;
  private int wrongLevelDrops;
  private float playerX = Float.NaN;
  private float playerY = Float.NaN;
  private long playerQuestRevision = -1L;
  private long baselineInventoryRevision = -1L;
  private long baselineQuestRevision = -1L;

  private D2GSHeadlessClient(Config config) {
    this.config = config;
  }

  public static void main(String[] args) throws Exception {
    Config config = Config.parse(args);
    boolean startedServer = config.home != null;
    if (startedServer) {
      // A few older combat fixtures deliberately place actors next to a
      // deterministic target. Production D2GS leaves this bridge disabled.
      if (!config.requireSnapshotOrder && !config.requireSnapshotResync
          && !config.requireSimulationTick) {
        System.setProperty("riiablo.d2gs.allowLegacyEntitySync", "true");
      }
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
        ? createGeneratedAmazonSave(
            config.requireMercenarySkill || config.requireMercenaryProgression
                || config.requireMercenaryRestore
                || config.requireMercenaryTravel ? 8 : 1,
            config.requireMercenaryLifecycle || config.requireMercenaryRestore ? 10_000 : 0,
            config.requireMercenaryRestore || config.requireMercenaryTravel,
            config.requireMercenaryRestore)
        : Files.readAllBytes(config.save.toPath());
    CharacterHeader character = CharacterHeader.read(d2s);
    log("connect", "server=" + config.host + ':' + config.port
        + " character=" + character.name + " class=" + character.charClass);

    if (config.requireFallenScenario) {
      runFallenDual(d2s, character);
      return;
    }
    if (config.requireSnapshotOrder) {
      runSnapshotOrder(d2s, character);
      return;
    }
    if (config.requireSnapshotResync) {
      runSnapshotResync(d2s, character);
      return;
    }
    if (config.requireMercenaryRestore) {
      runMercenaryRestore(d2s, character);
      return;
    }
    if (config.requireReconnectVisibility) {
      runReconnectVisibility(d2s, character);
      return;
    }
    if (config.requireMercenaryTravel) {
      runMercenaryTravel(d2s, character);
      return;
    }
    if (config.requireMercenarySkill || config.requireMercenaryProgression) {
      runMercenarySkill(d2s, character);
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
        if (config.requireSimulationTick) {
          verifySimulationTick();
          return;
        }
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

  /** Two-client protocol gate for authoritative snapshot ordering. */
  private void runSnapshotOrder(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient a = new D2GSHeadlessClient(config);
    D2GSHeadlessClient b = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket socketA = a.openSocket(); Socket socketB = b.openSocket()) {
      DataInputStream inA = input(socketA);
      DataInputStream inB = input(socketB);
      OutputStream outA = output(socketA);
      OutputStream outB = output(socketB);
      send(outA, connectionPacket(character, d2s));
      send(outB, connectionPacket(peerCharacter, peerD2s));
      a.awaitConnection(inA, deadline());
      b.awaitConnection(inB, deadline());

      long baselineDeadline = System.currentTimeMillis() + 4_000L;
      while (System.currentTimeMillis() < baselineDeadline
          && (!Float.isFinite(a.playerX) || !Float.isFinite(b.playerX))) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) a.consume(packet);
        packet = readPacket(inB);
        if (packet != null) b.consume(packet);
      }
      if (!Float.isFinite(a.playerX) || !Float.isFinite(b.playerX)) {
        throw new IOException("snapshot baseline omitted one player position");
      }
      long observedA = a.lastSnapshotTick;
      long observedB = b.lastSnapshotTick;
      send(outA, movementIntentPacket(a.playerX + 3f, a.playerY,
          1L, observedA, observedA + 2L));
      // Starting at sequence 3 models two lost client samples before the
      // first packet reaches D2GS.
      send(outB, movementIntentPacket(b.playerX + 3f, b.playerY,
          3L, observedB, observedB + 2L));
      // A new but impossible displacement must be processed as a rejected
      // input so the real client can hard-correct and discard prediction.
      send(outA, movementIntentPacket(a.playerX + 100f, a.playerY,
          2L, observedA, observedA + 2L));
      // Duplicate input must be ignored before its deliberately invalid
      // position can overwrite the authoritative entity.
      send(outA, movementIntentPacket(a.playerX + 100f, a.playerY,
          1L, observedA, observedA + 2L));
      // Production D2GS must reject the old absolute-coordinate upload even
      // if it carries a newer sequence.
      send(outB, movementPacket(b.playerId, b.playerX + 100f, b.playerY,
          1f, 0f, 4L));

      long observationDeadline = System.currentTimeMillis() + 2_000L;
      while (System.currentTimeMillis() < observationDeadline) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) a.consume(packet);
        packet = readPacket(inB);
        if (packet != null) b.consume(packet);
      }
      Set<Long> commonTicks = new HashSet<>(a.snapshotTicks);
      commonTicks.retainAll(b.snapshotTicks);
      if (a.snapshotTicks.size() < 2 || b.snapshotTicks.size() < 2
          || commonTicks.isEmpty()) {
        throw new IllegalStateException("snapshot ordering sample too small: clientA="
            + a.snapshotTicks + " clientB=" + b.snapshotTicks);
      }
      if (a.lastMovementAcknowledgement != 2L || b.lastMovementAcknowledgement != 4L
          || a.lastRejectedMovementSequence != 2L
          || b.lastRejectedMovementSequence != 4L) {
        throw new IllegalStateException("movement acknowledgement failed: clientA="
            + a.lastMovementAcknowledgement + "/" + a.lastRejectedMovementSequence
            + " clientB=" + b.lastMovementAcknowledgement + "/"
            + b.lastRejectedMovementSequence);
      }
      log("snapshot_order_pass", "clientA=" + a.playerId + " clientB=" + b.playerId
          + " commonTicks=" + commonTicks.size() + " tickA=" + a.lastSnapshotTick
          + " tickB=" + b.lastSnapshotTick + " serverTimeA=" + a.lastSnapshotServerTime
          + " serverTimeB=" + b.lastSnapshotServerTime + " ackA="
          + a.lastMovementAcknowledgement + " ackB=" + b.lastMovementAcknowledgement);
    }
  }

  /** Two-client fault-injection gate: only the requesting client receives a baseline. */
  private void runSnapshotResync(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient a = new D2GSHeadlessClient(config);
    D2GSHeadlessClient b = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket socketA = a.openSocket(); Socket socketB = b.openSocket()) {
      DataInputStream inA = input(socketA), inB = input(socketB);
      OutputStream outA = output(socketA), outB = output(socketB);
      send(outA, connectionPacket(character, d2s));
      send(outB, connectionPacket(peerCharacter, peerD2s));
      a.awaitConnection(inA, deadline());
      b.awaitConnection(inB, deadline());
      long baselineDeadline = System.currentTimeMillis() + 5_000L;
      while (System.currentTimeMillis() < baselineDeadline
          && (!Float.isFinite(a.playerX) || !Float.isFinite(b.playerX))) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) a.consume(packet);
        packet = readPacket(inB);
        if (packet != null) b.consume(packet);
      }
      if (!Float.isFinite(a.playerX) || !Float.isFinite(b.playerX)) {
        throw new IOException("resync fixture did not receive initial baselines");
      }
      float preWarpX = a.playerX, preWarpY = a.playerY;
      if (!D2GS.headlessWarpPlayer(a.playerId)) {
        throw new IOException("native warp trigger unavailable for resync fixture");
      }
      boolean warped = false;
      long warpDeadline = System.currentTimeMillis() + 5_000L;
      while (System.currentTimeMillis() < warpDeadline && !warped) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) {
          a.consume(packet);
          warped = Float.isFinite(a.playerX) && Float.isFinite(a.playerY)
              && Math.abs(a.playerX - preWarpX) + Math.abs(a.playerY - preWarpY) > 0.5f;
        }
      }
      if (!warped) throw new IllegalStateException("native warp produced no authoritative position change");
      if (!D2GS.headlessKillPlayer(a.playerId)) {
        throw new IOException("authoritative player death trigger unavailable");
      }
      // Pause client A's receive loop while client B keeps consuming traffic.
      // The explicit request models a packet-loss detector firing after resume.
      long pausedUntil = System.currentTimeMillis() + 2_500L;
      while (System.currentTimeMillis() < pausedUntil) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inB);
        if (packet != null) b.consume(packet);
      }
      send(outA, ByteBuffer.wrap(snapshotResyncPacket(77L, a.lastSnapshotTick,
          "headless_fault_injection")));
      boolean begin = false, end = false, peerMarker = false;
      long baselineId = -1L;
      int waypointCount = -1;
      long inventoryRevision = -1L;
      long questRevision = -1L;
      int entityFrames = 0;
      long declaredEntityCount = -1L;
      long deadline = System.currentTimeMillis() + config.testTimeoutMillis;
      while (System.currentTimeMillis() < deadline && !end) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) {
          if (packet.dataType() == D2GSData.SnapshotBaseline) {
            SnapshotBaseline marker = (SnapshotBaseline) packet.data(new SnapshotBaseline());
            if (marker.phase() == SnapshotBaselinePhase.BEGIN) {
              begin = true;
              baselineId = marker.baselineId();
              declaredEntityCount = marker.entityCount();
              waypointCount = marker.waypointMasksLength();
              inventoryRevision = marker.inventoryRevision();
              questRevision = marker.questRevision();
            }
            if (marker.phase() == SnapshotBaselinePhase.END) end = marker.success();
          } else if (begin && packet.dataType() == D2GSData.EntitySync) {
            entityFrames++;
          }
          a.consume(packet);
        }
        packet = readPacket(inB);
        if (packet != null && packet.dataType() == D2GSData.SnapshotBaseline) peerMarker = true;
      }
      if (!begin || !end || entityFrames == 0 || peerMarker) {
        throw new IllegalStateException("snapshot resync failed: begin=" + begin
            + " end=" + end + " entities=" + entityFrames + " peerMarker=" + peerMarker);
      }
      if (baselineId <= 0L || declaredEntityCount != entityFrames
          || waypointCount != Riiablo.NUM_ACTS || inventoryRevision < 0L
          || questRevision < 0L) {
        throw new IllegalStateException("snapshot state baseline missing: baselineId="
            + baselineId + " declaredEntities=" + declaredEntityCount
            + " receivedEntities=" + entityFrames + " waypoints=" + waypointCount
            + " inventoryRevision=" + inventoryRevision
            + " questRevision=" + questRevision);
      }

      // Replay the exact request ID. D2GS must remain idempotent and reuse the
      // same baseline identity rather than creating a second logical result.
      send(outA, ByteBuffer.wrap(snapshotResyncPacket(77L, a.lastSnapshotTick,
          "headless_duplicate")));
      long duplicateBaseline = -1L;
      boolean duplicateBegin = false, duplicateEnd = false;
      deadline = System.currentTimeMillis() + config.testTimeoutMillis;
      while (System.currentTimeMillis() < deadline && !duplicateEnd) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet == null) continue;
        if (packet.dataType() == D2GSData.SnapshotBaseline) {
          SnapshotBaseline marker = (SnapshotBaseline) packet.data(new SnapshotBaseline());
          duplicateBaseline = marker.baselineId();
          duplicateBegin |= marker.phase() == SnapshotBaselinePhase.BEGIN;
          duplicateEnd |= marker.phase() == SnapshotBaselinePhase.END && marker.success();
        }
      }
      if (!duplicateBegin || !duplicateEnd || duplicateBaseline != baselineId) {
        throw new IllegalStateException("snapshot duplicate is not idempotent: first="
            + baselineId + " duplicate=" + duplicateBaseline + " begin=" + duplicateBegin
            + " end=" + duplicateEnd);
      }
      float[] deadState = D2GS.headlessPlayerLifecycleState(a.playerId);
      if (deadState.length < 6 || deadState[0] != 1f || deadState[1] != 1f
          || deadState[2] > 0f || deadState[5] < 1f) {
        throw new IllegalStateException("player did not reach respawnable death state");
      }
      send(outA, playerLifecyclePacket(88L, PlayerLifecycleOperation.RESPAWN));
      PlayerLifecycleResult respawn = awaitPlayerLifecycleResult(inA, 88L,
          System.currentTimeMillis() + config.testTimeoutMillis);
      float[] liveState = D2GS.headlessPlayerLifecycleState(a.playerId);
      if (!respawn.success() || liveState.length < 6 || liveState[0] != 0f
          || liveState[2] <= 0f || liveState[5] < 1f
          || Math.abs(liveState[3] - respawn.x()) > 0.01f
          || Math.abs(liveState[4] - respawn.y()) > 0.01f) {
        throw new IllegalStateException("authoritative respawn failed: success="
            + respawn.success() + " reason=" + respawn.reason());
      }
      float townX = liveState[3], townY = liveState[4];
      if (!D2GS.headlessMovePlayerToLevel(a.playerId, 2)) { // Blood Moor staging
        throw new IOException("outdoor warp staging unavailable");
      }
      int caveWarpId = D2GS.headlessPrepareWarpToLevel(a.playerId, 8); // Den of Evil
      if (caveWarpId == Engine.INVALID_ENTITY) {
        throw new IOException("native Blood Moor cave warp unavailable");
      }
      send(outA, questRequestPacket(
          900L, QuestOperation.WARP_INTERACTION, caveWarpId, -1));
      QuestResult warpResult = a.awaitQuestResult(
          inA, 900L, System.currentTimeMillis() + config.testTimeoutMillis);
      if (!warpResult.success()) {
        throw new IOException("native cave warp rejected: " + warpResult.reason());
      }
      int[] warpState = D2GS.headlessWarpState(a.playerId);
      if (warpState.length < 4 || warpState[0] != 8 || warpState[1] < 0
          || warpState[2] != 1 || warpState[3] != 0) {
        throw new IOException("native cave warp was not atomic: "
            + java.util.Arrays.toString(warpState));
      }
      // Exact request replay must return the cached success without executing
      // the now wrong-level source Warp a second time.
      send(outA, questRequestPacket(
          900L, QuestOperation.WARP_INTERACTION, caveWarpId, -1));
      QuestResult replayedWarp = a.awaitQuestResult(
          inA, 900L, System.currentTimeMillis() + config.testTimeoutMillis);
      if (!replayedWarp.success()) {
        throw new IOException("native cave warp replay was not idempotent: "
            + replayedWarp.reason());
      }
      send(outA, ByteBuffer.wrap(snapshotResyncPacket(99L, a.lastSnapshotTick,
          "native_cave_warp")));
      boolean crossMapBegin = false, crossMapEnd = false;
      long crossMapDeadline = System.currentTimeMillis() + config.testTimeoutMillis;
      while (System.currentTimeMillis() < crossMapDeadline && !crossMapEnd) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet == null) continue;
        if (packet.dataType() == D2GSData.SnapshotBaseline) {
          SnapshotBaseline marker = (SnapshotBaseline) packet.data(new SnapshotBaseline());
          crossMapBegin |= marker.phase() == SnapshotBaselinePhase.BEGIN;
          crossMapEnd |= marker.phase() == SnapshotBaselinePhase.END && marker.success();
        }
        a.consume(packet);
      }
      if (!crossMapBegin || !crossMapEnd || Math.abs(a.playerX - townX)
          + Math.abs(a.playerY - townY) < 0.5f) {
        throw new IllegalStateException("cross-map baseline failed: begin=" + crossMapBegin
            + " end=" + crossMapEnd + " town=(" + townX + ',' + townY + ") current=("
            + a.playerX + ',' + a.playerY + ")");
      }
      float crossMapX = a.playerX, crossMapY = a.playerY;
      long delayedTick = Math.max(a.lastSnapshotTick + 100L, 100L);
      a.consume(staleLevelEntityPacket(0x7FFF0001, delayedTick, 1));
      if (a.currentLevelId != 8 || a.wrongLevelDrops <= 0
          || a.playerX != crossMapX || a.playerY != crossMapY) {
        throw new IllegalStateException("old-level snapshot was not discarded: level="
            + a.currentLevelId + " drops=" + a.wrongLevelDrops);
      }
      // Exercise the reverse entrance and the continuous underground chain.
      warpAndAssert(a, inA, outA, 910L, 2); // Den of Evil -> Blood Moor
      if (!D2GS.headlessMovePlayerToLevel(a.playerId, 4)) { // Stony Field staging
        throw new IOException("Stony Field staging unavailable");
      }
      warpAndAssert(a, inA, outA, 911L, 10); // Underground Passage level 1
      warpAndAssert(a, inA, outA, 912L, 14); // Underground Passage level 2
      // Level 2 is a dead-end branch in the native Act 1 topology. Return to
      // level 1 before using its second outdoor exit to Dark Wood.
      warpAndAssert(a, inA, outA, 913L, 10); // Underground Passage level 1
      verifyUndergroundVisibilityLifecycle(a, b, inA, inB);
      warpAndAssert(a, inA, outA, 914L, 5); // Dark Wood
      log("snapshot_resync_pass", "request=77 warp=true death=true respawn=true crossMap=true corpse=true baseline=" + baselineId
          + " entities=" + entityFrames + " waypoints=" + waypointCount
          + " inventoryRevision=" + inventoryRevision + " questRevision=" + questRevision
          + " duplicateBaseline=" + duplicateBaseline
          + " peerUnaffected=true pausedMillis=2500 oldLevelDrops=" + a.wrongLevelDrops);
    }
  }

  private void verifyUndergroundVisibilityLifecycle(D2GSHeadlessClient first,
      D2GSHeadlessClient peer, DataInputStream firstInput, DataInputStream peerInput)
      throws Exception {
    int[] rooms = D2GS.headlessNonAdjacentRoomPair(10);
    if (rooms.length < 2) {
      throw new IOException("Underground Passage has no non-adjacent walkable RoomEx pair");
    }
    if (!D2GS.headlessMovePlayerToRoom(first.playerId, 10, rooms[0])
        || !D2GS.headlessMovePlayerToRoom(peer.playerId, 10, rooms[0])) {
      throw new IOException("failed to stage clients in Underground Passage room " + rooms[0]);
    }

    long deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      Visibility firstOnPeer = peer.visibility.get(first.playerId);
      Visibility peerOnFirst = first.visibility.get(peer.playerId);
      if (first.currentLevelId == 10 && peer.currentLevelId == 10
          && firstOnPeer != null && !firstOnPeer.deleted
          && peerOnFirst != null && !peerOnFirst.deleted) break;
    }
    Visibility firstOnPeer = peer.visibility.get(first.playerId);
    Visibility peerOnFirst = first.visibility.get(peer.playerId);
    if (first.currentLevelId != 10 || peer.currentLevelId != 10
        || firstOnPeer == null || firstOnPeer.deleted
        || peerOnFirst == null || peerOnFirst.deleted) {
      throw new IOException("same-room clients did not become mutually visible");
    }

    Set<Integer> dynamicCandidates = new HashSet<>();
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (dynamicCandidates.isEmpty() && System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      int[] roomEntities = D2GS.headlessRoomDynamicEntities(10, rooms[0]);
      for (int entityId : roomEntities) {
        Visibility peerState = peer.visibility.get(entityId);
        Visibility firstState = first.visibility.get(entityId);
        if (peerState != null && peerState.levelId == 10 && !peerState.deleted
            && firstState != null && firstState.levelId == 10 && !firstState.deleted) {
          dynamicCandidates.add(entityId);
        }
      }
    }
    if (dynamicCandidates.isEmpty()) {
      throw new IOException("same-room underground activation exposed no dynamic entities");
    }

    if (!D2GS.headlessMovePlayerToRoom(peer.playerId, 10, rooms[1])) {
      throw new IOException("failed to move peer to non-adjacent room " + rooms[1]);
    }
    int deletedDynamic = Engine.INVALID_ENTITY;
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      firstOnPeer = peer.visibility.get(first.playerId);
      for (int entityId : dynamicCandidates) {
        Visibility state = peer.visibility.get(entityId);
        if (state != null && state.deleted) {
          deletedDynamic = entityId;
          break;
        }
      }
      if (firstOnPeer != null && firstOnPeer.deleted
          && deletedDynamic != Engine.INVALID_ENTITY) break;
    }
    if (firstOnPeer == null || !firstOnPeer.deleted
        || deletedDynamic == Engine.INVALID_ENTITY) {
      throw new IOException("non-adjacent room did not receive scoped deletions: player="
          + (firstOnPeer != null && firstOnPeer.deleted)
          + " dynamic=" + deletedDynamic + " candidates=" + dynamicCandidates.size());
    }
    Visibility retainedDynamic = first.visibility.get(deletedDynamic);
    Visibility peerOnSelf = peer.visibility.get(peer.playerId);
    if (retainedDynamic == null || retainedDynamic.deleted
        || peerOnSelf == null || peerOnSelf.deleted) {
      throw new IOException("scoped deletion leaked to an active recipient or local player");
    }

    if (!D2GS.headlessMovePlayerToRoom(peer.playerId, 10, rooms[0])) {
      throw new IOException("failed to return peer to room " + rooms[0]);
    }
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      firstOnPeer = peer.visibility.get(first.playerId);
      Visibility dynamic = peer.visibility.get(deletedDynamic);
      if (firstOnPeer != null && !firstOnPeer.deleted
          && dynamic != null && !dynamic.deleted) break;
    }
    Visibility dynamic = peer.visibility.get(deletedDynamic);
    if (firstOnPeer == null || firstOnPeer.deleted || dynamic == null || dynamic.deleted) {
      throw new IOException("returning to room did not restore full entity visibility: "
          + "player=" + (firstOnPeer == null ? "null" : firstOnPeer.deleted)
          + " dynamic=" + deletedDynamic + "/"
          + (dynamic == null ? "null" : dynamic.deleted)
          + " candidates=" + dynamicCandidates);
    }

    int[] fixtures = D2GS.headlessCreateRoomLifecycleFixtures(
        first.playerId, 10, rooms[0]);
    if (fixtures.length < 3 || fixtures[0] == Engine.INVALID_ENTITY
        || fixtures[1] == Engine.INVALID_ENTITY || fixtures[2] == Engine.INVALID_ENTITY) {
      throw new IOException("failed to create underground lifecycle fixtures: "
          + java.util.Arrays.toString(fixtures));
    }
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      Snapshot deadMonster = first.monsters.get(fixtures[0]);
      Visibility firstItem = first.visibility.get(fixtures[1]);
      Visibility peerItem = peer.visibility.get(fixtures[1]);
      Visibility firstObject = first.visibility.get(fixtures[2]);
      Visibility peerObject = peer.visibility.get(fixtures[2]);
      if (deadMonster != null && deadMonster.dead && !deadMonster.deleted
          && firstItem != null && !firstItem.deleted && firstItem.type == 3
          && peerItem != null && !peerItem.deleted && peerItem.type == 3
          && firstObject != null && !firstObject.deleted
          && firstObject.cofMode == Engine.Object.MODE_ON
          && peerObject != null && !peerObject.deleted
          && peerObject.cofMode == Engine.Object.MODE_ON) break;
    }
    Snapshot deadMonster = first.monsters.get(fixtures[0]);
    Visibility firstItem = first.visibility.get(fixtures[1]);
    Visibility peerItem = peer.visibility.get(fixtures[1]);
    Visibility firstObject = first.visibility.get(fixtures[2]);
    Visibility peerObject = peer.visibility.get(fixtures[2]);
    if (deadMonster == null || !deadMonster.dead || deadMonster.deleted
        || firstItem == null || firstItem.deleted || firstItem.type != 3
        || peerItem == null || peerItem.deleted || peerItem.type != 3
        || firstObject == null || firstObject.deleted
        || firstObject.cofMode != Engine.Object.MODE_ON
        || peerObject == null || peerObject.deleted
        || peerObject.cofMode != Engine.Object.MODE_ON) {
      throw new IOException("lifecycle fixtures were not synchronized to both clients");
    }

    Set<Integer> persistentEntities = entitySet(
        D2GS.headlessRoomDynamicEntities(10, rooms[0]));
    int[] activeState = D2GS.headlessRoomLifecycleState(10, rooms[0]);
    if (activeState[0] != com.riiablo.map.Map.RoomEx.CLIENT_IN_ROOM
        || activeState[1] != 2 || activeState[5] != 1 || activeState[6] != 1
        || !persistentEntities.contains(fixtures[0])
        || !persistentEntities.contains(fixtures[1])
        || !persistentEntities.contains(fixtures[2])) {
      throw new IOException("invalid active RoomEx state: "
          + java.util.Arrays.toString(activeState));
    }

    // A reconnect/baseline for one client must not advance the global cache
    // past an update still pending for its peer. Mutate the authoritative
    // object, immediately replay a baseline to the peer, then require both
    // clients to observe the same changed mode.
    if (!D2GS.headlessSetObjectModeAndSync(
        fixtures[2], peer.playerId, Engine.Object.MODE_NU)) {
      throw new IOException("failed to mutate object and replay peer baseline");
    }
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      Visibility firstChanged = first.visibility.get(fixtures[2]);
      Visibility peerChanged = peer.visibility.get(fixtures[2]);
      if (firstChanged != null && !firstChanged.deleted
          && firstChanged.cofMode == Engine.Object.MODE_NU
          && peerChanged != null && !peerChanged.deleted
          && peerChanged.cofMode == Engine.Object.MODE_NU) break;
    }
    Visibility firstChanged = first.visibility.get(fixtures[2]);
    Visibility peerChanged = peer.visibility.get(fixtures[2]);
    if (firstChanged == null || firstChanged.deleted
        || firstChanged.cofMode != Engine.Object.MODE_NU
        || peerChanged == null || peerChanged.deleted
        || peerChanged.cofMode != Engine.Object.MODE_NU) {
      throw new IOException("recipient-scoped object update diverged after peer baseline");
    }
    log("recipient_baseline_pass", "object=" + fixtures[2]
        + " mode=" + Engine.Object.MODE_NU + " baselinePeer=" + peer.playerId
        + " existingPeer=" + first.playerId + " clients=true,true");
    if (!D2GS.headlessSetObjectModeAndSync(
        fixtures[2], peer.playerId, Engine.Object.MODE_ON)) {
      throw new IOException("failed to restore object mode after baseline regression");
    }

    if (!D2GS.headlessMovePlayerToRoom(peer.playerId, 10, rooms[1])
        || !D2GS.headlessMovePlayerToRoom(first.playerId, 10, rooms[1])) {
      throw new IOException("failed to fully unsubscribe source RoomEx");
    }
    int[] dormantState = new int[0];
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      dormantState = D2GS.headlessRoomLifecycleState(10, rooms[0]);
      Visibility firstDead = first.visibility.get(fixtures[0]);
      Visibility firstDrop = first.visibility.get(fixtures[1]);
      Visibility firstOpened = first.visibility.get(fixtures[2]);
      Visibility peerDead = peer.visibility.get(fixtures[0]);
      Visibility peerDrop = peer.visibility.get(fixtures[1]);
      Visibility peerOpened = peer.visibility.get(fixtures[2]);
      if (dormantState[1] == 0
          && dormantState[0] > com.riiablo.map.Map.RoomEx.CLIENT_IN_SIGHT
          && firstDead != null && firstDead.deleted
          && firstDrop != null && firstDrop.deleted
          && firstOpened != null && firstOpened.deleted
          && peerDead != null && peerDead.deleted
          && peerDrop != null && peerDrop.deleted
          && peerOpened != null && peerOpened.deleted) break;
    }
    Set<Integer> dormantEntities = entitySet(
        D2GS.headlessRoomDynamicEntities(10, rooms[0]));
    Visibility dormantMonsterFirst = first.visibility.get(fixtures[0]);
    Visibility dormantItemFirst = first.visibility.get(fixtures[1]);
    Visibility dormantObjectFirst = first.visibility.get(fixtures[2]);
    Visibility dormantMonsterPeer = peer.visibility.get(fixtures[0]);
    Visibility dormantItemPeer = peer.visibility.get(fixtures[1]);
    Visibility dormantObjectPeer = peer.visibility.get(fixtures[2]);
    if (dormantState.length < 11 || dormantState[1] != 0
        || dormantState[0] <= com.riiablo.map.Map.RoomEx.CLIENT_IN_SIGHT
        || !dormantEntities.equals(persistentEntities)
        || dormantMonsterFirst == null || !dormantMonsterFirst.deleted
        || dormantItemFirst == null || !dormantItemFirst.deleted
        || dormantObjectFirst == null || !dormantObjectFirst.deleted
        || dormantMonsterPeer == null || !dormantMonsterPeer.deleted
        || dormantItemPeer == null || !dormantItemPeer.deleted
        || dormantObjectPeer == null || !dormantObjectPeer.deleted) {
      throw new IOException("RoomEx deactivation changed authoritative entities: state="
          + java.util.Arrays.toString(dormantState)
          + " before=" + persistentEntities.size() + " after=" + dormantEntities.size());
    }

    if (!D2GS.headlessMovePlayerToRoom(first.playerId, 10, rooms[0])
        || !D2GS.headlessMovePlayerToRoom(peer.playerId, 10, rooms[0])) {
      throw new IOException("failed to reactivate source RoomEx");
    }
    deadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(firstInput);
      if (packet != null) first.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      activeState = D2GS.headlessRoomLifecycleState(10, rooms[0]);
      deadMonster = peer.monsters.get(fixtures[0]);
      peerItem = peer.visibility.get(fixtures[1]);
      peerObject = peer.visibility.get(fixtures[2]);
      if (activeState[1] == 2 && deadMonster != null && deadMonster.dead
          && !deadMonster.deleted && peerItem != null && !peerItem.deleted
          && peerObject != null && !peerObject.deleted
          && peerObject.cofMode == Engine.Object.MODE_ON) break;
    }
    Set<Integer> restoredEntities = entitySet(
        D2GS.headlessRoomDynamicEntities(10, rooms[0]));
    deadMonster = peer.monsters.get(fixtures[0]);
    peerItem = peer.visibility.get(fixtures[1]);
    peerObject = peer.visibility.get(fixtures[2]);
    if (activeState[1] != 2 || !restoredEntities.equals(persistentEntities)
        || deadMonster == null || !deadMonster.dead || deadMonster.deleted
        || peerItem == null || peerItem.deleted || peerItem.type != 3
        || peerObject == null || peerObject.deleted
        || peerObject.cofMode != Engine.Object.MODE_ON) {
      throw new IOException("RoomEx reactivation did not restore persistent state");
    }
    log("room_subscription_pass", "level=10 sameRoom=" + rooms[0]
        + " remoteRoom=" + rooms[1] + " dynamic=" + deletedDynamic
        + " candidates=" + dynamicCandidates.size()
        + " delete=true restore=true");
    log("room_persistence_pass", "level=10 room=" + rooms[0]
        + " refs=" + activeState[1] + " entities=" + restoredEntities.size()
        + " monster=" + fixtures[0] + " dead=true item=" + fixtures[1]
        + " object=" + fixtures[2] + " mode=" + peerObject.cofMode
        + " duplicate=false prematureUnload=false");
  }

  private static Set<Integer> entitySet(int[] entities) {
    Set<Integer> result = new HashSet<>();
    if (entities != null) for (int entityId : entities) result.add(entityId);
    return result;
  }

  private void warpAndAssert(D2GSHeadlessClient observer, DataInputStream input,
      OutputStream output, long requestId, int expectedLevel) throws Exception {
    int warpId = D2GS.headlessPrepareWarpToLevel(observer.playerId, expectedLevel);
    if (warpId == Engine.INVALID_ENTITY) {
      throw new IOException("no native Warp to level " + expectedLevel);
    }
    send(output, questRequestPacket(requestId, QuestOperation.WARP_INTERACTION, warpId, -1));
    QuestResult result = observer.awaitQuestResult(input, requestId,
        System.currentTimeMillis() + config.testTimeoutMillis);
    long snapshotDeadline = System.currentTimeMillis() + config.testTimeoutMillis;
    while (result.success() && observer.currentLevelId != expectedLevel
        && System.currentTimeMillis() < snapshotDeadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet != null) observer.consume(packet);
    }
    int[] state = D2GS.headlessWarpState(observer.playerId);
    if (!result.success() || state.length < 4 || state[0] != expectedLevel
        || state[1] < 0 || state[2] != 1 || state[3] != 0
        || observer.currentLevelId != expectedLevel) {
      throw new IOException("warp transition failed target=" + expectedLevel
          + " success=" + result.success() + " reason=" + result.reason()
          + " state=" + java.util.Arrays.toString(state)
          + " clientLevel=" + observer.currentLevelId);
    }
    log("warp_pass", "request=" + requestId + " warp=" + warpId
        + " level=" + expectedLevel + " room=" + state[1]);
  }

  private static byte[] snapshotResyncPacket(long requestId, long lastTick, String reason) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reasonOffset = builder.createString(reason);
    int payload = SnapshotResyncRequest.createSnapshotResyncRequest(builder, requestId,
        lastTick, reasonOffset);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder,
        D2GSData.SnapshotResyncRequest, payload);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    byte[] bytes = new byte[frame.remaining()];
    frame.get(bytes);
    return bytes;
  }

  /** Builds an EntitySync frame tagged with an obsolete level for fault injection. */
  private static com.riiablo.net.packet.d2gs.D2GS staleLevelEntityPacket(
      int entityId, long tick, int levelId) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int position = PositionP.createPositionP(builder, 0f, 0f);
    int types = EntitySync.createComponentTypeVector(builder,
        new byte[] {ComponentP.PositionP});
    int components = EntitySync.createComponentVector(builder, new int[] {position});
    int sync = EntitySync.createEntitySync(builder, entityId, 1, 0, types, components,
        tick, tick, 0L, 0L, 0L, levelId);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = builder.dataBuffer();
    frame.position(frame.position() + Integer.BYTES);
    return com.riiablo.net.packet.d2gs.D2GS.getRootAsD2GS(frame);
  }

  private static ByteBuffer playerLifecyclePacket(long requestId, byte operation) {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int payload = PlayerLifecycleRequest.createPlayerLifecycleRequest(builder, requestId, operation);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(builder,
        D2GSData.PlayerLifecycleRequest, payload);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static PlayerLifecycleResult awaitPlayerLifecycleResult(DataInputStream input,
      long requestId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null || packet.dataType() != D2GSData.PlayerLifecycleResult) continue;
      PlayerLifecycleResult result = (PlayerLifecycleResult) packet.data(new PlayerLifecycleResult());
      if (result.requestId() == requestId) return result;
    }
    throw new IOException("timed out waiting for player lifecycle result " + requestId);
  }

  private void verifySimulationTick() throws Exception {
    if (config.home == null) {
      throw new IllegalArgumentException(
          "--require-sim-tick requires an embedded D2GS started with --home");
    }
    long[] before = D2GS.headlessSimulationState();
    Thread.sleep(1_200L);
    long[] after = D2GS.headlessSimulationState();
    long advanced = after[0] - before[0];
    float step = Float.intBitsToFloat((int) after[2]);
    // The headless backend targets 25 Hz. Keep the wall-clock tolerance broad
    // enough for loaded CI hosts while still rejecting an unbounded render loop
    // or a stalled simulation.
    if (before[0] <= 0L || advanced < 15L || advanced > 45L) {
      throw new IllegalStateException("authoritative simulation cadence invalid: ticks="
          + before[0] + "->" + after[0] + " advanced=" + advanced);
    }
    if (before[1] < 0L || before[1] != after[1]) {
      throw new IllegalStateException("authoritative simulation writer changed: thread="
          + before[1] + "->" + after[1]);
    }
    if (Float.floatToIntBits(step)
        != Float.floatToIntBits(com.riiablo.engine.server.AuthoritativeSimulation.STEP_SECONDS)) {
      throw new IllegalStateException("authoritative simulation step changed: " + step);
    }
    log("sim_tick_pass", "player=" + playerId + " ticks=" + before[0] + "->"
        + after[0] + " advanced=" + advanced + " step=" + step
        + " writerThreadId=" + after[1]);
  }

  /**
   * Real two-socket regression: client A kills a Fallen, both clients observe
   * the death and subsequent Shaman resurrection, then client B picks up one
   * of the resulting ground drops after the native owner window expires.
   */
  private void runFallenDual(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient a = new D2GSHeadlessClient(config);
    D2GSHeadlessClient b = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket socketA = openSocket(); Socket socketB = openSocket()) {
      DataInputStream inA = input(socketA);
      DataInputStream inB = input(socketB);
      OutputStream outA = output(socketA);
      OutputStream outB = output(socketB);
      send(outA, connectionPacket(character, d2s));
      send(outB, connectionPacket(peerCharacter, peerD2s));
      a.awaitConnection(inA, deadline());
      b.awaitConnection(inB, deadline());
      verifyDualQuestSnapshots(a, b, inA, inB, outA, outB);
      Vector2 bloodMoor = D2GS.headlessFallenShamanPosition(2);
      if (bloodMoor == null) {
        throw new IOException("Blood Moor Fallen/Shaman test room unavailable");
      }
      send(outA, positionPacket(a.playerId, bloodMoor.x, bloodMoor.y));
      // Keep both peers on the exact same native RoomEx anchor.  A one-subtile
      // offset can cross an irregular RoomEx seam in exported outdoor maps,
      // which legitimately suppresses updates for the peer under D2MOO's
      // current/adjacent-room visibility rule.
      send(outB, positionPacket(b.playerId, bloodMoor.x, bloodMoor.y));
      log("dual_enter_level", "level=Blood Moor position=" + bloodMoor);
      Snapshot[] pair = a.awaitFallenShamanPair(inA, deadline());
      Snapshot fallenA = pair[0];
      Snapshot shamanA = pair[1];
      send(outA, positionPacket(a.playerId, fallenA.x - 1f, fallenA.y));
      send(outB, positionPacket(b.playerId, fallenA.x - 1f, fallenA.y));
      Snapshot fallenB = b.awaitEntity(inB, fallenA.entityId, deadline());
      Snapshot shamanB = b.awaitEntity(inB, shamanA.entityId, deadline());
      log("dual_target", "fallen=" + fallenA.entityId + " class=" + fallenA.monsterClass
          + " shaman=" + shamanA.entityId + " distance="
          + distance(fallenA, shamanA) + " clients=" + a.playerId + "," + b.playerId);

      a.attackUntilDead(inA, outA, fallenA, deadline());
      b.awaitDead(inB, fallenA.entityId, deadline());
      Vector2 observation = D2GS.headlessRoomObservationPosition(2, fallenA.x, fallenA.y);
      if (observation == null) throw new IOException("Fallen observation point unavailable");
      send(outA, positionPacket(a.playerId, observation.x, observation.y));
      send(outB, positionPacket(b.playerId, observation.x, observation.y));
      log("dual_observe", "position=" + observation + " corpse=" + fallenA.entityId);
      a.awaitRevived(inA, fallenA.entityId, deadline());
      b.awaitRevived(inB, fallenA.entityId, deadline());
      log("dual_revive_pass", "fallen=" + fallenA.entityId + " shaman="
          + shamanA.entityId + " clientA=true clientB=true");

      // D2Game SKILLS_ResurrectUnit marks resurrected monsters NOXP | NOTC.
      // Kill the same Fallen once more and verify the server retains that
      // native anti-farming state while both clients still observe its death.
      int[] rewardsBefore = D2GS.headlessMonsterRewardState(a.playerId, fallenA.entityId);
      int livingResurrectionFlags =
          com.riiablo.engine.server.component.NativeUnitFlags.MONSTER_RESURRECTION;
      if ((rewardsBefore[1] & livingResurrectionFlags) != livingResurrectionFlags) {
        throw new IllegalStateException("revived Fallen is missing native live target/reward flags: flags=0x"
            + Integer.toHexString(rewardsBefore[1]));
      }
      Snapshot revived = a.monsters.get(fallenA.entityId);
      if (revived == null || revived.dead || revived.life <= 0f) {
        throw new IllegalStateException("revived Fallen snapshot is not alive");
      }
      send(outB, positionPacket(b.playerId, revived.x - 1f, revived.y));
      a.attackUntilDead(inA, outA, revived, deadline());
      b.awaitDead(inB, revived.entityId, deadline());
      b.consumeFor(inB, 1_000L);
      int[] rewardsAfter = D2GS.headlessMonsterRewardState(a.playerId, fallenA.entityId);
      int nativeNoRewards =
          com.riiablo.engine.server.component.NativeUnitFlags.NO_RESURRECTION_REWARD;
      int monsterTarget =
          com.riiablo.engine.server.component.NativeUnitFlags.MONSTER_TARGET;
      if (rewardsAfter[0] != rewardsBefore[0]
          || rewardsAfter[2] != rewardsBefore[2]
          || (rewardsAfter[1] & nativeNoRewards) != nativeNoRewards
          || (rewardsAfter[1] & monsterTarget) != 0) {
        throw new IllegalStateException("resurrected Fallen granted a native reward: xp="
            + rewardsBefore[0] + "->" + rewardsAfter[0] + " items="
            + rewardsBefore[2] + "->" + rewardsAfter[2] + " flags=0x"
            + Integer.toHexString(rewardsAfter[1]));
      }
      log("dual_native_no_reward_pass", "fallen=" + fallenA.entityId + " xp="
          + rewardsAfter[0] + " groundItems=" + rewardsAfter[2] + " flags=0x"
          + Integer.toHexString(rewardsAfter[1]) + " clients=true,true");

      Snapshot drop = b.firstGroundItem();
      int lootKills = 1;
      Set<Integer> killedForLoot = new HashSet<>();
      killedForLoot.add(fallenA.entityId);
      // Native TreasureClassEx legitimately rolls NoDrop for ordinary Fallen.
      // Kill distinct native spawns instead of repeatedly farming one revived
      // lifecycle, then verify the first natural item through the peer path.
      while (drop == null && lootKills < 12) {
        Snapshot lootTarget = a.awaitNextLivingFallen(
            inA, killedForLoot, System.currentTimeMillis() + 5_000L);
        if (lootTarget == null) break;
        killedForLoot.add(lootTarget.entityId);
        lootKills++;
        send(outA, positionPacket(a.playerId, lootTarget.x - 1f, lootTarget.y));
        send(outB, positionPacket(b.playerId, lootTarget.x - 1f, lootTarget.y));
        b.awaitEntity(inB, lootTarget.entityId, deadline());
        a.attackUntilDead(inA, outA, lootTarget, deadline());
        b.awaitDead(inB, lootTarget.entityId, deadline());
        drop = b.awaitGroundItem(inB, System.currentTimeMillis() + 1_500L);
        log("dual_loot_roll", "kill=" + lootKills + " fallen="
            + lootTarget.entityId + " dropped=" + (drop != null));
      }
      if (drop == null) {
        throw new IOException("no native ground drop observed after " + lootKills
            + " Fallen kills");
      }
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
          + result.snapshotLength() + " nativeKills=" + lootKills);
    }
  }

  /** Two-client integration path for an actual native Rogue hireling cast. */
  private void runMercenarySkill(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient a = new D2GSHeadlessClient(config);
    D2GSHeadlessClient b = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket socketA = openSocket(); Socket socketB = openSocket()) {
      DataInputStream inA = input(socketA);
      DataInputStream inB = input(socketB);
      OutputStream outA = output(socketA);
      OutputStream outB = output(socketB);
      send(outA, connectionPacket(character, d2s));
      send(outB, connectionPacket(peerCharacter, peerD2s));
      a.awaitConnection(inA, deadline());
      b.awaitConnection(inB, deadline());

      Vector2 anchor = D2GS.headlessFallenShamanPosition(2);
      if (anchor == null) throw new IOException("Blood Moor Fallen/Shaman test room unavailable");
      send(outA, positionPacket(a.playerId, anchor.x, anchor.y));
      send(outB, positionPacket(b.playerId, anchor.x, anchor.y));
      // Let D2GS consume the real movement packet before the render-thread
      // test hook creates the hireling beside its owner.
      Thread.sleep(500L);
      Snapshot[] pairA = a.awaitFallenShamanPair(inA, deadline());
      Snapshot fallenA = pairA[0];
      Snapshot fallenB = b.awaitEntity(inB, fallenA.entityId, deadline());
      if (!D2GS.headlessGrantFreeRogue(a.playerId)) {
        throw new IllegalStateException("native free Rogue hire failed for player " + a.playerId);
      }
      Snapshot mercA = a.awaitMercenary(inA, deadline());
      Snapshot mercB = b.awaitEntity(inB, mercA.entityId, deadline());
      send(outA, positionPacket(a.playerId, fallenA.x - 1f, fallenA.y));
      send(outB, positionPacket(b.playerId, fallenB.x - 1f, fallenB.y));
      log("mercenary_target", "owner=" + a.playerId + " peer=" + b.playerId
          + " merc=" + mercA.entityId + " fallen=" + fallenA.entityId
          + " initialModes=" + mercA.lastMode + ',' + mercB.lastMode);

      long castDeadline = deadline();
      long diagnosticDeadline = System.currentTimeMillis() + 5_000L;
      int combatTargetId = Engine.INVALID_ENTITY;
      float serverDamageBefore = Float.NaN;
      float serverDamageAfter = Float.NaN;
      long damageDeadline = Long.MAX_VALUE;
      Snapshot targetA = null;
      Snapshot targetB = null;
      while (System.currentTimeMillis() < castDeadline) {
        com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
        if (packet != null) a.consume(packet);
        packet = readPacket(inB);
        if (packet != null) b.consume(packet);
        mercA = a.monsters.get(mercA.entityId);
        mercB = b.monsters.get(mercB.entityId);
        int[] state = D2GS.headlessMercenaryCastState();
        // The arrow can hit an intervening hostile rather than the AI's
        // selected target. Assert against the entity actually damaged by the
        // authoritative missile collision, matching D2's first-hit behavior.
        if (combatTargetId == Engine.INVALID_ENTITY && state[7] > 0
            && state[11] != Engine.INVALID_ENTITY) {
          int nextTargetId = state[11];
          Snapshot nextTargetA = a.monsters.get(nextTargetId);
          Snapshot nextTargetB = b.monsters.get(nextTargetId);
          targetA = nextTargetA;
          targetB = nextTargetB;
          if (targetA != null && targetB != null && targetA.hasVitals && targetB.hasVitals) {
            combatTargetId = nextTargetId;
            serverDamageBefore = Float.intBitsToFloat(state[12]);
            serverDamageAfter = Float.intBitsToFloat(state[13]);
            damageDeadline = System.currentTimeMillis() + 10_000L;
            log("mercenary_server_target", "entity=" + combatTargetId
                + " serverLife=" + serverDamageBefore + "->" + serverDamageAfter
                + " casts=" + state[0]);
          }
        } else if (combatTargetId != Engine.INVALID_ENTITY) {
          targetA = a.monsters.get(combatTargetId);
          targetB = b.monsters.get(combatTargetId);
        }
        boolean serverDamaged = Float.isFinite(serverDamageBefore)
            && Float.isFinite(serverDamageAfter) && serverDamageAfter < serverDamageBefore;
        boolean damagedA = targetA != null && targetA.hasVitals
            && (targetA.dead || targetA.life <= serverDamageAfter + 0.001f);
        boolean damagedB = targetB != null && targetB.hasVitals
            && (targetB.dead || targetB.life <= serverDamageAfter + 0.001f);
        if (mercA != null && mercA.sawActionMode && mercB != null && mercB.sawActionMode
            && serverDamaged && damagedA && damagedB) {
          log("mercenary_skill_pass", "merc=" + mercA.entityId + " mode="
              + mercA.lastMode + " peerMode=" + mercB.lastMode + " target="
              + combatTargetId + " serverLife=" + serverDamageBefore + "->"
              + serverDamageAfter + " clientLife=" + targetA.life + ',' + targetB.life
              + " clients=true,true");
          if (config.requireMercenaryLifecycle) {
            verifyMercenaryLifecycle(a, b, inA, inB, a.playerId, mercA.entityId);
          }
          if (config.requireMercenaryProgression) {
            verifyMercenaryProgression(a, b, inA, inB, a.playerId,
                mercA.entityId, combatTargetId);
          }
          return;
        }
        if (System.currentTimeMillis() >= diagnosticDeadline) {
          if (state[0] == 0) break;
          diagnosticDeadline = Long.MAX_VALUE;
        }
        if (System.currentTimeMillis() >= damageDeadline) break;
      }
      int[] serverCast = D2GS.headlessMercenaryCastState();
      throw new IllegalStateException("native mercenary skill/damage did not synchronize to both clients: "
          + "serverCasts=" + serverCast[0] + " lastTarget=" + serverCast[1]
          + " processCount=" + serverCast[2] + " blockStage=" + serverCast[3]
          + " lastSkill=" + serverCast[4]
          + " missiles=" + serverCast[5] + " collisions=" + serverCast[6]
          + " damageEvents=" + serverCast[7] + " skillDo=" + serverCast[8]
          + " configuredMissiles=" + serverCast[9] + " srvDoFunc=" + serverCast[10]
          + " damageTarget=" + serverCast[11] + " serverLife="
          + Float.intBitsToFloat(serverCast[12]) + "->"
          + Float.intBitsToFloat(serverCast[13])
          + " actions=" + (mercA != null && mercA.sawActionMode) + ','
          + (mercB != null && mercB.sawActionMode) + " clientLife="
          + (targetA == null ? "missing" : targetA.life) + ','
          + (targetB == null ? "missing" : targetB.life) + " distance="
          + (targetA == null || mercA == null ? "unknown" : distance(targetA, mercA)));
    }
  }

  private void verifyMercenaryLifecycle(D2GSHeadlessClient a, D2GSHeadlessClient b,
      DataInputStream inA, DataInputStream inB, int ownerId, int mercenaryId) throws Exception {
    int[] before = D2GS.headlessMercenaryLifecycleState(ownerId);
    if (before[0] != mercenaryId || before[1]
        != com.riiablo.engine.server.pet.MercenaryManager.STATE_HIRED || before[2] <= 0) {
      throw new IllegalStateException("invalid pre-death mercenary lifecycle state: entity="
          + before[0] + " state=" + before[1] + " cost=" + before[2]);
    }
    if (!D2GS.headlessKillMercenary(ownerId)) {
      throw new IllegalStateException("authoritative mercenary death trigger failed");
    }
    a.awaitDead(inA, mercenaryId, deadline());
    b.awaitDead(inB, mercenaryId, deadline());
    int[] dead = D2GS.headlessMercenaryLifecycleState(ownerId);
    if (dead[0] != mercenaryId || dead[1]
        != com.riiablo.engine.server.pet.MercenaryManager.STATE_DEAD
        || (dead[4] & com.riiablo.engine.server.pet.MercenaryManager.FLAG_DEAD) == 0) {
      throw new IllegalStateException("mercenary death state was not persisted: entity="
          + dead[0] + " state=" + dead[1] + " flags=0x" + Integer.toHexString(dead[4]));
    }
    log("mercenary_death_pass", "owner=" + ownerId + " entity=" + mercenaryId
        + " clients=true,true flags=0x" + Integer.toHexString(dead[4]));

    boolean resurrected = false;
    long resurrectDeadline = deadline();
    while (System.currentTimeMillis() < resurrectDeadline && !resurrected) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
      if (packet != null) a.consume(packet);
      packet = readPacket(inB);
      if (packet != null) b.consume(packet);
      resurrected = D2GS.headlessResurrectMercenary(ownerId);
      if (!resurrected) Thread.sleep(100L);
    }
    if (!resurrected) throw new IllegalStateException("paid mercenary resurrection failed");
    a.awaitRevived(inA, mercenaryId, deadline());
    b.awaitRevived(inB, mercenaryId, deadline());
    int[] after = D2GS.headlessMercenaryLifecycleState(ownerId);
    if (after[0] != mercenaryId || after[1]
        != com.riiablo.engine.server.pet.MercenaryManager.STATE_HIRED
        || (after[4] & com.riiablo.engine.server.pet.MercenaryManager.FLAG_DEAD) != 0
        || after[3] != before[3] - before[2]) {
      throw new IllegalStateException("mercenary resurrection state mismatch: entity="
          + after[0] + " state=" + after[1] + " gold=" + before[3] + "->" + after[3]
          + " expectedCost=" + before[2] + " flags=0x" + Integer.toHexString(after[4]));
    }
    log("mercenary_resurrect_pass", "owner=" + ownerId + " entity=" + mercenaryId
        + " cost=" + before[2] + " gold=" + before[3] + "->" + after[3]
        + " sameEntity=true clients=true,true");
  }

  private void verifyMercenaryProgression(D2GSHeadlessClient a, D2GSHeadlessClient b,
      DataInputStream inA, DataInputStream inB, int ownerId, int mercenaryId,
      int targetId) throws Exception {
    if (!D2GS.headlessPrepareMercenaryProgression(ownerId, targetId)) {
      throw new IllegalStateException("failed to prepare native hireling level-up boundary");
    }
    int[] before = D2GS.headlessMercenaryProgressionState(ownerId);
    int oldLevel = before[1];
    int expectedLevel = oldLevel + 1;
    long progressionDeadline = deadline();
    while (System.currentTimeMillis() < progressionDeadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(inA);
      if (packet != null) a.consume(packet);
      packet = readPacket(inB);
      if (packet != null) b.consume(packet);
      int[] after = D2GS.headlessMercenaryProgressionState(ownerId);
      Snapshot targetA = a.monsters.get(targetId);
      Snapshot targetB = b.monsters.get(targetId);
      Snapshot mercenaryA = a.monsters.get(mercenaryId);
      Snapshot mercenaryB = b.monsters.get(mercenaryId);
      float maxHp = Float.intBitsToFloat(after[9]);
      if (targetA != null && targetB != null && targetA.dead && targetB.dead
          && after[0] == mercenaryId && after[1] == expectedLevel
          && after[2] == expectedLevel && after[6] == expectedLevel
          && after[14] == expectedLevel && after[3] > before[3]
          && after[3] == after[4] && after[3] == after[5]
          && after[17] == after[3] - before[3]
          && Float.intBitsToFloat(after[15]) == maxHp
          && after[16] == com.riiablo.engine.server.pet.MercenaryManager
              .nativeResurrectionCost(expectedLevel)
          && mercenaryA != null && mercenaryB != null
          && Math.abs(mercenaryA.maxLife - maxHp) <= 0.001f
          && Math.abs(mercenaryB.maxLife - maxHp) <= 0.001f) {
        log("mercenary_progression_pass", "owner=" + ownerId + " entity=" + mercenaryId
            + " target=" + targetId + " level=" + oldLevel + "->" + expectedLevel
            + " xp=" + before[3] + "->" + after[3] + " strength=" + after[7]
            + " dexterity=" + after[8] + " maxHp=" + maxHp + " defense=" + after[10]
            + " nextExp=" + after[11] + " skill=" + after[12] + ':' + after[13]
            + " lifecycleSynced=true d2sSynced=true clients=true,true");
        return;
      }
    }
    int[] after = D2GS.headlessMercenaryProgressionState(ownerId);
    throw new IllegalStateException("native hireling progression did not complete: entity="
        + after[0] + " level=" + before[1] + "->" + after[1] + ',' + after[2]
        + " xp=" + before[3] + "->" + after[3] + ',' + after[4] + ',' + after[5]);
  }

  /**
   * Two-client D2S regression for a dead persisted Rogue. The owner connects,
   * logs out, reconnects from the same save, and pays to resurrect while the
   * peer must observe removal, reconstruction, and revival.
   */
  private void runMercenaryRestore(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient first = new D2GSHeadlessClient(config);
    D2GSHeadlessClient peer = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket peerSocket = openSocket();
         DataInputStream peerInput = input(peerSocket);
         OutputStream peerOutput = output(peerSocket)) {
      send(peerOutput, connectionPacket(peerCharacter, peerD2s));
      peer.awaitConnection(peerInput, deadline());

      int firstMercenaryId;
      try (Socket firstSocket = openSocket();
           DataInputStream firstInput = input(firstSocket);
           OutputStream firstOutput = output(firstSocket)) {
        send(firstOutput, connectionPacket(character, d2s));
        first.awaitConnection(firstInput, deadline());
        Snapshot firstMercenary = first.awaitMercenary(firstInput, deadline());
        firstMercenaryId = firstMercenary.entityId;
        if (first.playerQuestRevision < 0L || first.baselineQuestRevision < 0L
            || first.baselineInventoryRevision < 0L) {
          throw new IllegalStateException("initial reconnect baseline omitted progression state: "
              + "quest=" + first.playerQuestRevision + "/" + first.baselineQuestRevision
              + " inventory=" + first.baselineInventoryRevision);
        }
        peer.awaitEntity(peerInput, firstMercenaryId, deadline());
        first.awaitDead(firstInput, firstMercenaryId, deadline());
        peer.awaitDead(peerInput, firstMercenaryId, deadline());
        int[] state = D2GS.headlessMercenaryLifecycleState(first.playerId);
        if (state[0] != firstMercenaryId || state[1]
            != com.riiablo.engine.server.pet.MercenaryManager.STATE_DEAD
            || (state[4] & com.riiablo.engine.server.pet.MercenaryManager.FLAG_DEAD) == 0) {
          throw new IllegalStateException("saved dead mercenary was not restored: entity="
              + state[0] + " state=" + state[1] + " flags=0x"
              + Integer.toHexString(state[4]));
        }
        log("mercenary_restore_login_pass", "owner=" + first.playerId
            + " entity=" + firstMercenaryId + " clients=true,true flags=0x"
            + Integer.toHexString(state[4]));
      }

      peer.awaitDeleted(peerInput, firstMercenaryId, deadline());
      D2GSHeadlessClient reconnected = new D2GSHeadlessClient(config);
      try (Socket reconnectSocket = openSocket();
           DataInputStream reconnectInput = input(reconnectSocket);
           OutputStream reconnectOutput = output(reconnectSocket)) {
        send(reconnectOutput, connectionPacket(character, d2s));
        reconnected.awaitConnection(reconnectInput, deadline());
        Snapshot restored = reconnected.awaitMercenary(reconnectInput, deadline());
        if (reconnected.playerQuestRevision != first.playerQuestRevision
            || reconnected.baselineQuestRevision != first.baselineQuestRevision
            || reconnected.baselineInventoryRevision != first.baselineInventoryRevision) {
          throw new IllegalStateException("reconnect progression baseline mismatch: first quest="
              + first.playerQuestRevision + '/' + first.baselineQuestRevision
              + " inventory=" + first.baselineInventoryRevision + " reconnect quest="
              + reconnected.playerQuestRevision + '/' + reconnected.baselineQuestRevision
              + " inventory=" + reconnected.baselineInventoryRevision);
        }
        // Artemis may recycle the numeric id after the peer has observed the
        // authoritative deleted snapshot. ID reuse is safe and expected; the
        // deletion-before-reconnect assertion above detects stale entities.
        peer.awaitEntity(peerInput, restored.entityId, deadline());
        reconnected.awaitDead(reconnectInput, restored.entityId, deadline());
        peer.awaitDead(peerInput, restored.entityId, deadline());
        int[] before = D2GS.headlessMercenaryLifecycleState(reconnected.playerId);
        if (before[0] != restored.entityId || before[1]
            != com.riiablo.engine.server.pet.MercenaryManager.STATE_DEAD
            || before[2] <= 0 || before[3] < before[2]) {
          throw new IllegalStateException("reconnected mercenary state invalid: entity="
              + before[0] + " state=" + before[1] + " cost=" + before[2]
              + " gold=" + before[3]);
        }

        boolean resurrected = false;
        long resurrectDeadline = deadline();
        while (!resurrected && System.currentTimeMillis() < resurrectDeadline) {
          com.riiablo.net.packet.d2gs.D2GS packet = readPacket(reconnectInput);
          if (packet != null) reconnected.consume(packet);
          packet = readPacket(peerInput);
          if (packet != null) peer.consume(packet);
          resurrected = D2GS.headlessResurrectMercenary(reconnected.playerId);
          if (!resurrected) Thread.sleep(100L);
        }
        if (!resurrected) throw new IllegalStateException("restored mercenary resurrection failed");
        reconnected.awaitRevived(reconnectInput, restored.entityId, deadline());
        peer.awaitRevived(peerInput, restored.entityId, deadline());
        int[] after = D2GS.headlessMercenaryLifecycleState(reconnected.playerId);
        if (after[0] != restored.entityId || after[1]
            != com.riiablo.engine.server.pet.MercenaryManager.STATE_HIRED
            || after[3] != before[3] - before[2]
            || (after[4] & com.riiablo.engine.server.pet.MercenaryManager.FLAG_DEAD) != 0) {
          throw new IllegalStateException("restored mercenary resurrection mismatch");
        }
        log("mercenary_restore_reconnect_pass", "oldEntity=" + firstMercenaryId
            + " entity=" + restored.entityId + " owner=" + reconnected.playerId
            + " cost=" + before[2] + " gold=" + before[3] + "->" + after[3]
            + " idReused=" + (firstMercenaryId == restored.entityId)
            + " staleRemoved=true questBaseline=" + reconnected.baselineQuestRevision
            + " inventoryBaseline=" + reconnected.baselineInventoryRevision
            + " clients=true,true");
      }
    }
  }

  /**
   * Disconnect/reconnect regression for RoomEx-owned drops, objects and pets.
   * Ground entities and opened objects remain authoritative while the owner's
   * ordinary summon is deleted and never resurrected by the reconnect.
   */
  private void runReconnectVisibility(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient owner = new D2GSHeadlessClient(config);
    D2GSHeadlessClient peer = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket peerSocket = peer.openSocket();
         DataInputStream peerInput = input(peerSocket);
         OutputStream peerOutput = output(peerSocket)) {
      send(peerOutput, connectionPacket(peerCharacter, peerD2s));
      peer.awaitConnection(peerInput, deadline());
      int room = D2GS.headlessNonAdjacentRoomPair(10)[0];
      int[] persistedFixtures = null;
      int persistedSummon = Engine.INVALID_ENTITY;
      int oldOwnerId = Engine.INVALID_ENTITY;
      try (Socket ownerSocket = owner.openSocket();
           DataInputStream ownerInput = input(ownerSocket);
           OutputStream ownerOutput = output(ownerSocket)) {
        send(ownerOutput, connectionPacket(character, d2s));
        owner.awaitConnection(ownerInput, deadline());
        if (!D2GS.headlessMovePlayerToRoom(owner.playerId, 10, room)
            || !D2GS.headlessMovePlayerToRoom(peer.playerId, 10, room)) {
          throw new IOException("failed to stage reconnect visibility clients");
        }
        persistedFixtures = D2GS.headlessCreateRoomLifecycleFixtures(owner.playerId, 10, room);
        persistedSummon = D2GS.headlessCreateRoomSummon(owner.playerId, 10, room);
        if (persistedFixtures.length < 3 || persistedSummon == Engine.INVALID_ENTITY) {
          throw new IOException("failed to create reconnect fixtures: fixtures="
              + java.util.Arrays.toString(persistedFixtures) + " summon=" + persistedSummon);
        }
        awaitReconnectFixture(owner, peer, ownerInput, peerInput, persistedFixtures, persistedSummon,
            deadline());
        oldOwnerId = owner.playerId;
        // Closing the owner socket invokes the authoritative Disconnect path.
        // The peer remains in the same RoomEx and must keep seeing persistent
        // entities while observing deletion of the owner and ordinary summon.
        ownerSocket.close();
        awaitDisconnectVisibility(peer, peerInput, oldOwnerId, persistedSummon, persistedFixtures,
            deadline());
        log("reconnect_visibility_disconnect_pass", "owner=" + oldOwnerId
            + " summon=" + persistedSummon + " drop=" + persistedFixtures[1] + " object=" + persistedFixtures[2]
            + " ownerDeleted=true summonDeleted=true persistent=true");
      }

      D2GSHeadlessClient reconnected = new D2GSHeadlessClient(config);
      try (Socket reconnectSocket = reconnected.openSocket();
           DataInputStream reconnectInput = input(reconnectSocket);
           OutputStream reconnectOutput = output(reconnectSocket)) {
        send(reconnectOutput, connectionPacket(character, d2s));
        reconnected.awaitConnection(reconnectInput, deadline());
        if (!D2GS.headlessMovePlayerToRoom(reconnected.playerId, 10, room)) {
          throw new IOException("failed to stage reconnected owner");
        }
        awaitReconnectedPersistentEntities(reconnected, reconnectInput, peer, peerInput,
            persistedFixtures, persistedSummon, deadline());
        log("reconnect_visibility_pass", "owner=" + reconnected.playerId
            + " oldOwnerDeleted=true summonAbsent=true dropPersistent=true objectPersistent=true"
            + " clients=true,true");
      }
    }
  }

  private void awaitReconnectFixture(D2GSHeadlessClient owner, D2GSHeadlessClient peer,
      DataInputStream ownerInput, DataInputStream peerInput, int[] fixtures, int summonId,
      long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(ownerInput);
      if (packet != null) owner.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      if (visible(owner, fixtures[1]) && visible(peer, fixtures[1])
          && visible(owner, fixtures[2]) && visible(peer, fixtures[2])
          && visible(owner, summonId) && visible(peer, summonId)) return;
    }
    throw new IOException("reconnect fixtures were not visible to both clients");
  }

  private void awaitDisconnectVisibility(D2GSHeadlessClient peer, DataInputStream peerInput,
      int oldOwnerId, int summonId, int[] fixtures, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      Visibility ownerState = peer.visibility.get(oldOwnerId);
      Visibility summonState = peer.visibility.get(summonId);
      if (ownerState != null && ownerState.deleted
          && summonState != null && summonState.deleted
          && visible(peer, fixtures[1]) && visible(peer, fixtures[2])) return;
    }
    throw new IOException("disconnect did not delete owner/summon while retaining drop/object");
  }

  private void awaitReconnectedPersistentEntities(D2GSHeadlessClient reconnected,
      DataInputStream reconnectInput, D2GSHeadlessClient peer, DataInputStream peerInput,
      int[] fixtures, int summonId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(reconnectInput);
      if (packet != null) reconnected.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      if (visible(reconnected, fixtures[1]) && visible(reconnected, fixtures[2])
          && visible(peer, fixtures[1]) && visible(peer, fixtures[2])) {
        int[] state = D2GS.headlessReconnectPersistenceState(
            Engine.INVALID_ENTITY, reconnected.playerId, fixtures[1], fixtures[2], summonId);
        if (state.length >= 7 && state[1] == 1 && state[2] == 1 && state[3] == 1
            && state[4] == Engine.Object.MODE_ON && state[5] == 0 && state[6] == 0) {
          Visibility staleSummon = reconnected.visibility.get(summonId);
          if (staleSummon == null || staleSummon.deleted) return;
        }
      }
    }
    throw new IOException("reconnect did not restore persistent drop/object or remove summon");
  }

  private static boolean visible(D2GSHeadlessClient client, int entityId) {
    Visibility state = client.visibility.get(entityId);
    if (state == null || state.deleted) return false;
    if (state.type == 3) {
      Snapshot item = client.monsters.get(entityId);
      return item != null && !item.deleted;
    }
    return true;
  }

  /** Two real sockets observe ordinary following plus three cross-zone relocations. */
  private void runMercenaryTravel(byte[] d2s, CharacterHeader character) throws Exception {
    D2GSHeadlessClient owner = new D2GSHeadlessClient(config);
    D2GSHeadlessClient peer = new D2GSHeadlessClient(config);
    byte[] peerD2s = createGeneratedObserverSave();
    CharacterHeader peerCharacter = CharacterHeader.read(peerD2s);
    try (Socket ownerSocket = openSocket(); Socket peerSocket = openSocket();
         DataInputStream ownerInput = input(ownerSocket);
         DataInputStream peerInput = input(peerSocket);
         OutputStream ownerOutput = output(ownerSocket);
         OutputStream peerOutput = output(peerSocket)) {
      send(ownerOutput, connectionPacket(character, d2s));
      send(peerOutput, connectionPacket(peerCharacter, peerD2s));
      owner.awaitConnection(ownerInput, deadline());
      peer.awaitConnection(peerInput, deadline());
      Snapshot ownerMercenary = owner.awaitMercenary(ownerInput, deadline());
      int mercenaryId = ownerMercenary.entityId;
      peer.awaitEntity(peerInput, mercenaryId, deadline());

      int[] initial = D2GS.headlessMercenaryTravelState(owner.playerId);
      requireMercenaryTravelState(initial, owner.playerId, mercenaryId, 1, false);
      Vector2 followTarget = D2GS.headlessMercenaryFollowPosition(1,
          Float.intBitsToFloat(initial[5]), Float.intBitsToFloat(initial[6]));
      if (followTarget == null) {
        throw new IOException("Rogue Encampment has no native point in the hireling follow band");
      }
      float initialMercenaryX = Float.intBitsToFloat(initial[7]);
      float initialMercenaryY = Float.intBitsToFloat(initial[8]);
      int initialFollowCount = initial[4];
      send(ownerOutput, positionPacket(owner.playerId, followTarget.x, followTarget.y));
      send(peerOutput, positionPacket(peer.playerId, followTarget.x, followTarget.y));
      int[] followed = awaitMercenaryTravel(owner, peer, ownerInput, peerInput,
          owner.playerId, mercenaryId, 1, initialFollowCount, false, deadline());
      float followedX = Float.intBitsToFloat(followed[7]);
      float followedY = Float.intBitsToFloat(followed[8]);
      if (Vector2.dst(initialMercenaryX, initialMercenaryY, followedX, followedY) < 0.1f) {
        throw new IllegalStateException("hireling follow request did not produce movement");
      }
      log("mercenary_follow_pass", "owner=" + owner.playerId + " entity=" + mercenaryId
          + " level=1 count=" + initialFollowCount + "->" + followed[4]
          + " position=(" + initialMercenaryX + ',' + initialMercenaryY + ")->("
          + followedX + ',' + followedY + ") clients=true,true");

      int teleportCount = followed[3];
      int[] levels = {2, 4, 27};
      String[] names = {"Blood Moor", "Stony Field", "Outer Cloister"};
      for (int i = 0; i < levels.length; i++) {
        Vector2 destination = D2GS.headlessLevelPosition(levels[i]);
        if (destination == null) {
          throw new IOException("level unavailable for hireling travel: " + names[i]);
        }
        send(ownerOutput, positionPacket(owner.playerId, destination.x, destination.y));
        send(peerOutput, positionPacket(peer.playerId, destination.x, destination.y));
        int[] traveled = awaitMercenaryTravel(owner, peer, ownerInput, peerInput,
            owner.playerId, mercenaryId, levels[i], teleportCount, true, deadline());
        teleportCount = traveled[3];
        assertClientMercenaryPosition(owner, peer, mercenaryId, traveled);
        log("mercenary_travel_stage_pass", "owner=" + owner.playerId
            + " entity=" + mercenaryId + " level=" + levels[i] + " name=" + names[i]
            + " teleports=" + teleportCount + " room=" + traveled[10]
            + " flags=0x" + Integer.toHexString(traveled[9]) + " clients=true,true");
      }
      log("mercenary_travel_pass", "owner=" + owner.playerId + " entity=" + mercenaryId
          + " sameEntity=true followCount=" + followed[4] + " teleportCount="
          + teleportCount + " levels=1,2,4,27 clients=true,true");
    }
  }

  private int[] awaitMercenaryTravel(D2GSHeadlessClient owner, D2GSHeadlessClient peer,
      DataInputStream ownerInput, DataInputStream peerInput, int ownerId, int mercenaryId,
      int expectedLevel, int previousCount, boolean teleport, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(ownerInput);
      if (packet != null) owner.consume(packet);
      packet = readPacket(peerInput);
      if (packet != null) peer.consume(packet);
      int[] state = D2GS.headlessMercenaryTravelState(ownerId);
      int count = teleport ? state[3] : state[4];
      if (state[0] == mercenaryId && state[1] == expectedLevel
          && state[2] == expectedLevel && count > previousCount) {
        requireMercenaryTravelState(state, ownerId, mercenaryId, expectedLevel, true);
        Snapshot ownerSnapshot = owner.monsters.get(mercenaryId);
        Snapshot peerSnapshot = peer.monsters.get(mercenaryId);
        float authoritativeX = Float.intBitsToFloat(state[7]);
        float authoritativeY = Float.intBitsToFloat(state[8]);
        if (ownerSnapshot != null && peerSnapshot != null
            && !ownerSnapshot.deleted && !peerSnapshot.deleted
            && ownerSnapshot.hasPosition && peerSnapshot.hasPosition
            && Vector2.dst(ownerSnapshot.x, ownerSnapshot.y,
                authoritativeX, authoritativeY) <= 0.5f
            && Vector2.dst(peerSnapshot.x, peerSnapshot.y,
                authoritativeX, authoritativeY) <= 0.5f) return state;
      }
    }
    int[] state = D2GS.headlessMercenaryTravelState(ownerId);
    throw new IOException("timed out waiting for hireling "
        + (teleport ? "teleport" : "follow") + ": level=" + expectedLevel
        + " stateLevel=" + state[1] + ',' + state[2] + " counts=" + state[3] + ','
        + state[4] + " previous=" + previousCount);
  }

  private static void requireMercenaryTravelState(int[] state, int ownerId, int mercenaryId,
      int expectedLevel, boolean requireMotion) {
    float life = Float.intBitsToFloat(state[11]);
    float mercenaryX = Float.intBitsToFloat(state[7]);
    float mercenaryY = Float.intBitsToFloat(state[8]);
    float bodyX = Float.intBitsToFloat(state[12]);
    float bodyY = Float.intBitsToFloat(state[13]);
    if (state[0] != mercenaryId || state[1] != expectedLevel || state[2] != expectedLevel
        || (state[9] & com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK) != 0
        || (state[17] == 1 && state[10] < 0) || life <= 0f || state[14] != 1
        || !Float.isFinite(mercenaryX) || !Float.isFinite(mercenaryY)
        || (Float.isFinite(bodyX) && Vector2.dst(mercenaryX, mercenaryY, bodyX, bodyY) > 0.01f)
        || (requireMotion && (state[15] != mercenaryId || state[16] != ownerId))) {
      throw new IllegalStateException("invalid authoritative hireling travel state: entity="
          + state[0] + " owner=" + ownerId + " levels=" + state[1] + ',' + state[2]
          + " counts=" + state[3] + ',' + state[4] + " flags=0x"
          + Integer.toHexString(state[9]) + " room=" + state[10] + " life=" + life
          + " map=" + state[14] + " last=" + state[15] + ',' + state[16]);
    }
  }

  private static void assertClientMercenaryPosition(D2GSHeadlessClient owner,
      D2GSHeadlessClient peer, int mercenaryId, int[] state) {
    Snapshot ownerSnapshot = owner.monsters.get(mercenaryId);
    Snapshot peerSnapshot = peer.monsters.get(mercenaryId);
    float x = Float.intBitsToFloat(state[7]);
    float y = Float.intBitsToFloat(state[8]);
    if (ownerSnapshot == null || peerSnapshot == null
        || ownerSnapshot.deleted || peerSnapshot.deleted
        || Vector2.dst(ownerSnapshot.x, ownerSnapshot.y, x, y) > 0.5f
        || Vector2.dst(peerSnapshot.x, peerSnapshot.y, x, y) > 0.5f) {
      throw new IllegalStateException("hireling position not synchronized to both clients");
    }
  }

  private Snapshot awaitMercenary(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) {
        if (snapshot.deleted || snapshot.monsterClass < 0 || !snapshot.hasPosition) continue;
        com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(snapshot.monsterClass);
        if (row != null && snapshot.monsterClass == com.riiablo.engine.server.monster.MonsterType.HIRELING_ROGUE) {
          return snapshot;
        }
      }
    }
    throw new IOException("timed out waiting for spawned Rogue hireling");
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

  private static void verifyDualQuestSnapshots(D2GSHeadlessClient a,
      D2GSHeadlessClient b, DataInputStream inA, DataInputStream inB,
      OutputStream outA, OutputStream outB) throws Exception {
    send(outA, questRequestPacket(1L, QuestOperation.SNAPSHOT, -1, -1));
    send(outB, questRequestPacket(1L, QuestOperation.SNAPSHOT, -1, -1));
    QuestResult first = a.awaitQuestResult(inA, 1L, a.deadline());
    QuestResult peer = b.awaitQuestResult(inB, 1L, b.deadline());
    int expectedRecords = Riiablo.NUM_ACTS * 8;
    if (!first.success() || !peer.success()
        || first.questRecordsLength() != expectedRecords
        || peer.questRecordsLength() != expectedRecords) {
      throw new IllegalStateException("dual quest snapshot failed: first="
          + first.success() + '/' + first.questRecordsLength() + " peer="
          + peer.success() + '/' + peer.questRecordsLength());
    }

    // Same request id and intent must replay the cached authoritative result.
    long revision = first.questRevision();
    send(outA, questRequestPacket(1L, QuestOperation.SNAPSHOT, -1, -1));
    QuestResult replay = a.awaitQuestResult(inA, 1L, a.deadline());
    if (!replay.success() || replay.questRevision() != revision) {
      throw new IllegalStateException("quest request replay changed its result");
    }

    // A conflicting intent with the same id must not execute.
    send(outA, questRequestPacket(1L, QuestOperation.NPC_MESSAGE, -1, 0));
    QuestResult conflict = a.awaitQuestResult(inA, 1L, a.deadline());
    if (conflict.success() || !"REQUEST_ID_REUSED".equals(conflict.reason())) {
      throw new IllegalStateException("quest request id reuse was not rejected: "
          + conflict.reason());
    }
    log("dual_quest_pass", "clients=" + a.playerId + ',' + b.playerId
        + " records=" + expectedRecords + " revision=" + revision
        + " replay=true conflictRejected=true");
  }

  private QuestResult awaitQuestResult(DataInputStream input, long requestId,
                                       long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      if (packet.dataType() == D2GSData.QuestResult) {
        QuestResult result = (QuestResult) packet.data(new QuestResult());
        if (result.requestId() == requestId) return result;
      }
      consume(packet);
    }
    throw new IOException("timed out waiting for quest result " + requestId);
  }

  private Snapshot awaitNamedMonster(DataInputStream input, String name, boolean allowDead,
                                     long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      for (Snapshot snapshot : monsters.values()) {
        if (snapshot.deleted || !snapshot.hasPosition || !snapshot.hasVitals) continue;
        if (!allowDead && snapshot.life <= 0f) continue;
        if (snapshot.monsterClass < 0 || Riiablo.files == null || Riiablo.files.monstats == null) continue;
        com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(snapshot.monsterClass);
        if (row != null && name.equalsIgnoreCase(row.Id)) return snapshot;
      }
    }
    throw new IOException("timed out waiting for monster " + name);
  }

  /** Selects a real native spawn pair rather than unrelated monsters sharing a class id. */
  private Snapshot[] awaitFallenShamanPair(DataInputStream input, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot bestFallen = null;
      Snapshot bestShaman = null;
      float bestDistance = Float.MAX_VALUE;
      for (Snapshot shaman : monsters.values()) {
        if (!isLivingMonster(shaman, "fallenshaman1")) continue;
        for (Snapshot fallen : monsters.values()) {
          if (!isLivingMonster(fallen, "fallen1")) continue;
          float distance = distance(fallen, shaman);
          // FallenShaman PARAM_RESURRECT_DISTANCE is 15 tiles in the native
          // table. Keep a little headroom for movement before the corpse scan.
          if (distance <= 14f && distance < bestDistance) {
            bestDistance = distance;
            bestFallen = fallen;
            bestShaman = shaman;
          }
        }
      }
      if (bestFallen != null) return new Snapshot[] {bestFallen, bestShaman};
    }
    throw new IOException("timed out waiting for nearby Fallen/Shaman spawn pair");
  }

  private boolean isLivingMonster(Snapshot snapshot, String name) {
    if (snapshot == null || snapshot.deleted || !snapshot.hasPosition
        || !snapshot.hasVitals || snapshot.life <= 0f || snapshot.monsterClass < 0
        || Riiablo.files == null || Riiablo.files.monstats == null) return false;
    com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(snapshot.monsterClass);
    return row != null && name.equalsIgnoreCase(row.Id);
  }

  private static float distance(Snapshot a, Snapshot b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  private Snapshot awaitEntity(DataInputStream input, int entityId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot != null && !snapshot.deleted && snapshot.hasPosition && snapshot.hasVitals) return snapshot;
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
      if (snapshot != null && !snapshot.deleted && (snapshot.dead || snapshot.life <= 0f)) return;
    }
    throw new IOException("peer did not observe Fallen death " + entityId);
  }

  private void awaitRevived(DataInputStream input, int entityId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot == null) continue;
      // Callers already required this client to observe the preceding death;
      // this phase therefore waits only for the later positive-life snapshot.
      if (!snapshot.deleted && snapshot.life > 0f && !snapshot.dead) return;
    }
    throw new IOException("client did not observe Fallen resurrection " + entityId);
  }

  private void awaitDeleted(DataInputStream input, int entityId, long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot snapshot = monsters.get(entityId);
      if (snapshot != null && snapshot.deleted) return;
    }
    throw new IOException("peer did not observe entity removal " + entityId);
  }

  private Snapshot awaitGroundItem(DataInputStream input, long deadline) throws Exception {
    Snapshot existing = firstGroundItem();
    if (existing != null) return existing;
    while (System.currentTimeMillis() < deadline) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet == null) continue;
      consume(packet);
      Snapshot drop = firstGroundItem();
      if (drop != null) return drop;
    }
    return null;
  }

  private Snapshot firstGroundItem() {
    for (Snapshot snapshot : monsters.values()) {
      if (snapshot.groundItem && !snapshot.deleted) return snapshot;
    }
    return null;
  }

  private void consumeFor(DataInputStream input, long durationMillis) throws Exception {
    long until = System.currentTimeMillis() + Math.max(0L, durationMillis);
    while (System.currentTimeMillis() < until) {
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet != null) consume(packet);
    }
  }

  private Snapshot awaitNextLivingFallen(DataInputStream input, Set<Integer> excluded,
                                         long deadline) throws Exception {
    while (System.currentTimeMillis() < deadline) {
      for (Snapshot snapshot : monsters.values()) {
        if (!excluded.contains(snapshot.entityId) && isLivingMonster(snapshot, "fallen1")) {
          return snapshot;
        }
      }
      com.riiablo.net.packet.d2gs.D2GS packet = readPacket(input);
      if (packet != null) consume(packet);
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
    if (packet.dataType() == D2GSData.Disconnect) {
      Disconnect disconnect = (Disconnect) packet.data(new Disconnect());
      Visibility visible = visibility.get(disconnect.entityId());
      if (visible == null) {
        visible = new Visibility(disconnect.entityId());
        visibility.put(disconnect.entityId(), visible);
      }
      visible.deleted = true;
      Snapshot snapshot = monsters.get(disconnect.entityId());
      if (snapshot != null) snapshot.deleted = true;
      return;
    }
    if (packet.dataType() == D2GSData.SnapshotBaseline) {
      SnapshotBaseline marker = (SnapshotBaseline) packet.data(new SnapshotBaseline());
      if (marker.phase() == SnapshotBaselinePhase.BEGIN) {
        baselineInventoryRevision = marker.inventoryRevision();
        baselineQuestRevision = marker.questRevision();
      }
      return;
    }
    if (packet.dataType() != D2GSData.EntitySync) return;
    EntitySync sync = (EntitySync) packet.data(new EntitySync());
    int packetLevelId = sync.levelId();
    if (sync.entityId() != playerId && packetLevelId >= 0 && currentLevelId >= 0
        && packetLevelId != currentLevelId) {
      wrongLevelDrops++;
      return;
    }
    long snapshotTick = sync.tick();
    long snapshotServerTime = sync.serverTimeMillis();
    if (snapshotTick > 0L) {
      if (lastSnapshotTick > snapshotTick || lastSnapshotServerTime > snapshotServerTime) {
        throw new IllegalStateException("authoritative snapshot order regressed: tick="
            + lastSnapshotTick + "->" + snapshotTick + " serverTime="
            + lastSnapshotServerTime + "->" + snapshotServerTime);
      }
      lastSnapshotTick = snapshotTick;
      lastSnapshotServerTime = snapshotServerTime;
      snapshotTicks.add(snapshotTick);
    }
    if (sync.entityId() == playerId) {
      if (packetLevelId >= 0) currentLevelId = packetLevelId;
      if (sync.acknowledgedInputSequence() > 0L) {
        lastMovementAcknowledgement = sync.acknowledgedInputSequence();
        lastRejectedMovementSequence = sync.rejectedInputSequence();
      }
      int positionIndex = findComponent(sync, ComponentP.PositionP);
      if (positionIndex >= 0) {
        PositionP position = (PositionP) sync.component(new PositionP(), positionIndex);
        playerX = position.x();
        playerY = position.y();
      }
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
      int playerIndex = findComponent(sync, ComponentP.PlayerP);
      if (playerIndex >= 0) {
        PlayerP player = (PlayerP) sync.component(new PlayerP(), playerIndex);
        playerQuestRevision = player.questRevision();
      }
    }
    Visibility visible = visibility.get(sync.entityId());
    if (visible == null) {
      visible = new Visibility(sync.entityId());
      visibility.put(sync.entityId(), visible);
    }
    visible.type = sync.type();
    visible.levelId = packetLevelId;
    visible.deleted = (sync.flags() & EntityFlags.deleted) != 0;
    int visibleCofIndex = findComponent(sync, ComponentP.CofReferenceP);
    if (visibleCofIndex >= 0) {
      CofReferenceP cof = (CofReferenceP) sync.component(
          new CofReferenceP(), visibleCofIndex);
      visible.cofMode = cof.mode();
    }
    if (sync.type() == 3) {
      Snapshot snapshot = monsters.get(sync.entityId());
      if (snapshot == null) {
        snapshot = new Snapshot(sync.entityId());
        monsters.put(sync.entityId(), snapshot);
      }
      if ((sync.flags() & EntityFlags.deleted) != 0) {
        snapshot.deleted = true;
        snapshot.groundItem = false;
        return;
      }
      snapshot.deleted = false;
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
      // A visibility/room unload is not a combat death. Keep the last vitals
      // so tests cannot mistake recipient-scoped deletion for a kill.
      snapshot.deleted = true;
      return;
    }
    snapshot.deleted = false;
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
      snapshot.maxLife = vitals.maxHitpoints();
      snapshot.dead = vitals.dead();
      snapshot.hasVitals = true;
    }
    int cofIndex = findComponent(sync, ComponentP.CofReferenceP);
    if (cofIndex >= 0 && snapshot.monsterClass
        == com.riiablo.engine.server.monster.MonsterType.HIRELING_ROGUE) {
      CofReferenceP cof = (CofReferenceP) sync.component(new CofReferenceP(), cofIndex);
      snapshot.lastMode = cof.mode();
      snapshot.sawActionMode = cof.mode() != Engine.Monster.MODE_NU
          && cof.mode() != Engine.Monster.MODE_WL
          && cof.mode() != Engine.Monster.MODE_RN;
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
    int sync = EntitySync.createEntitySync(builder, playerId, 2, 0, types, components,
        0L, 0L, 0L, 0L, 0L, -1);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer movementPacket(
      int playerId, float x, float y, float velocityX, float velocityY) {
    return movementPacket(playerId, x, y, velocityX, velocityY, 0L);
  }

  private static ByteBuffer movementPacket(
      int playerId, float x, float y, float velocityX, float velocityY,
      long inputSequence) {
    FlatBufferBuilder builder = new FlatBufferBuilder(192);
    int position = PositionP.createPositionP(builder, x, y);
    int velocity = VelocityP.createVelocityP(builder, velocityX, velocityY);
    int angle = AngleP.createAngleP(builder, velocityX, velocityY);
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {
        ComponentP.PositionP, ComponentP.VelocityP, ComponentP.AngleP});
    int components = EntitySync.createComponentVector(
        builder, new int[] {position, velocity, angle});
    int sync = EntitySync.createEntitySync(builder, playerId, 2, 0, types, components,
        0L, 0L, inputSequence, 0L, 0L, -1);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.EntitySync, sync);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private static ByteBuffer movementIntentPacket(
      float x, float y, long sequence, long observedServerTick, long targetTick) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int intent = RunToLocation.createRunToLocation(builder,
        (short) Math.round(x), (short) Math.round(y), sequence,
        observedServerTick, targetTick);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.RunToLocation, intent);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  private ByteBuffer castPacket(int skillId, int targetId, float x, float y) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    long observedTick = Math.max(0L, lastSnapshotTick);
    long targetTick = observedTick == 0L ? 0L : observedTick + 2L;
    int request = CastSkillRequest.createCastSkillRequest(
        builder, skillId, targetId, x, y, ++combatSequence, observedTick, targetTick);
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

  private static ByteBuffer questRequestPacket(long requestId, int operation,
      int targetEntityId, int messageIndex) {
    FlatBufferBuilder builder = new FlatBufferBuilder(96);
    int request = QuestRequest.createQuestRequest(builder, requestId, operation,
        targetEntityId, messageIndex);
    int root = com.riiablo.net.packet.d2gs.D2GS.createD2GS(
        builder, D2GSData.QuestRequest, request);
    com.riiablo.net.packet.d2gs.D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder.dataBuffer();
  }

  /** Creates a normal level-one Amazon whose native starting javelin owns Throw. */
  private static byte[] createGeneratedAmazonSave() {
    return createGeneratedAmazonSave(1, 0);
  }

  private static byte[] createGeneratedAmazonSave(int level) {
    return createGeneratedAmazonSave(level, 0);
  }

  private static byte[] createGeneratedAmazonSave(int level, int gold) {
    return createGeneratedAmazonSave(level, gold, false, false);
  }

  private static byte[] createGeneratedAmazonSave(int level, int gold,
      boolean persistedMercenary, boolean deadMercenary) {
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
    // The network regression deliberately waits for autonomous monster AI;
    // keep its generated combat driver alive without changing production AI.
    int maxHp = 1_000_000;
    base.put(Stat.hitpoints, maxHp);
    base.put(Stat.maxhp, maxHp);
    base.put(Stat.mana, stats._int);
    base.put(Stat.maxmana, stats._int);
    base.put(Stat.stamina, stats.stamina);
    base.put(Stat.maxstamina, stats.stamina);
    base.put(Stat.level, Math.max(1, level));
    base.put(Stat.experience, 0);
    base.put(Stat.gold, Math.max(0, gold));
    base.put(Stat.goldbank, 0);
    base.put(Stat.armorclass, 1_000_000);
    character.getStats().reset();
    character.activateWaypoint(Riiablo.NORMAL, Riiablo.ACT1, 0);
    character.mapSeed = 0x48434D41; // "HCMA", stable for reproducible item ids.
    character.initializeStartItems(stats);
    if (persistedMercenary) {
      com.riiablo.engine.server.NativeHirelingExperienceTable hirelings =
          com.riiablo.engine.server.NativeHirelingExperienceTable.load();
      long experience = hirelings.thresholdForHireling(
          com.riiablo.engine.server.pet.MercenaryManager.MERC_TYPE_ROGUE,
          Math.max(1, level));
      if (experience <= 0L) {
        throw new IllegalStateException("native Rogue Hireling.txt row unavailable");
      }
      CharData.MercData mercenary = character.getMerc();
      mercenary.seed = 0x4D455243; // "MERC"
      mercenary.name = 0;
      mercenary.type = com.riiablo.engine.server.pet.MercenaryManager.MERC_TYPE_ROGUE;
      mercenary.xp = experience;
      mercenary.flags = deadMercenary
          ? com.riiablo.engine.server.pet.MercenaryManager.FLAG_DEAD : 0;
    }
    byte[] data = new D2SWriter96().writeD2S(D2SWriter96.createD2S(character));
    log("character_generated", "name=HeadlessAma class=amazon skill=throw bytes=" + data.length);
    return data;
  }

  /** Durable second client used only as a passive multiplayer observer/picker. */
  private static byte[] createGeneratedObserverSave() {
    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "HeadlessPeer", Riiablo.BARBARIAN);
    com.riiablo.codec.excel.CharStats.Entry stats = CharacterClass.BARBARIAN.entry();
    StatListRef base = character.getStats().base();
    base.put(Stat.strength, stats.str);
    base.put(Stat.energy, stats._int);
    base.put(Stat.dexterity, stats.dex);
    base.put(Stat.vitality, stats.vit);
    base.put(Stat.statpts, 0);
    base.put(Stat.newskills, 0);
    base.put(Stat.hitpoints, 1_000_000);
    base.put(Stat.maxhp, 1_000_000);
    base.put(Stat.mana, 1_000);
    base.put(Stat.maxmana, 1_000);
    base.put(Stat.stamina, 1_000);
    base.put(Stat.maxstamina, 1_000);
    base.put(Stat.level, 1);
    base.put(Stat.experience, 0);
    base.put(Stat.gold, 0);
    base.put(Stat.goldbank, 0);
    base.put(Stat.armorclass, 1_000_000);
    character.getStats().reset();
    character.activateWaypoint(Riiablo.NORMAL, Riiablo.ACT1, 0);
    character.mapSeed = 0x50454552; // "PEER"
    character.initializeStartItems(stats);
    return new D2SWriter96().writeD2S(D2SWriter96.createD2S(character));
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

  private static final class Visibility {
    final int entityId;
    int type = -1;
    int levelId = -1;
    int cofMode = -1;
    boolean deleted;

    Visibility(int entityId) {
      this.entityId = entityId;
    }
  }

  private static final class Snapshot {
    final int entityId;
    int monsterClass = -1;
    float x;
    float y;
    float life;
    float maxLife;
    boolean dead;
    boolean deleted;
    boolean groundItem;
    boolean hasPosition;
    boolean hasVitals;
    boolean sawActionMode;
    int lastMode = -1;

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
    boolean requireSimulationTick;
    boolean requireSnapshotOrder;
    boolean requireSnapshotResync;
    boolean requireFallenScenario;
    boolean requireMercenarySkill;
    boolean requireMercenaryLifecycle;
    boolean requireMercenaryProgression;
    boolean requireMercenaryRestore;
    boolean requireMercenaryTravel;
    boolean requireReconnectVisibility;
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
        else if ("--require-sim-tick".equals(arg)) config.requireSimulationTick = true;
        else if ("--require-snapshot-order".equals(arg)) config.requireSnapshotOrder = true;
        else if ("--require-snapshot-resync".equals(arg)) config.requireSnapshotResync = true;
        else if ("--require-fallen-scenario".equals(arg)) config.requireFallenScenario = true;
        else if ("--require-mercenary-skill".equals(arg)) config.requireMercenarySkill = true;
        else if ("--require-mercenary-lifecycle".equals(arg)) config.requireMercenaryLifecycle = true;
        else if ("--require-mercenary-progression".equals(arg)) config.requireMercenaryProgression = true;
        else if ("--require-mercenary-restore".equals(arg)) config.requireMercenaryRestore = true;
        else if ("--require-mercenary-travel".equals(arg)) config.requireMercenaryTravel = true;
        else if ("--require-reconnect-visibility".equals(arg)) config.requireReconnectVisibility = true;
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
          + " [--skill 0] [--require-missile] [--require-sim-tick]"
          + " [--require-snapshot-order] [--require-snapshot-resync]"
          + " [--require-fallen-scenario] [--require-reconnect-visibility] [--attempts 20]");
    }
  }
}
