package com.riiablo.engine.client;

import com.google.flatbuffers.FlatBufferBuilder;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Target;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.RunToEntity;
import com.riiablo.net.packet.d2gs.RunToLocation;
import com.riiablo.net.packet.d2gs.WalkToEntity;
import com.riiablo.net.packet.d2gs.WalkToLocation;
import com.riiablo.net.packet.d2gs.NpcServiceRequest;
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.net.packet.d2gs.PartyRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleRequest;
import com.riiablo.net.packet.d2gs.QuestRequest;
import com.riiablo.net.packet.d2gs.SnapshotResyncRequest;
import com.riiablo.net.SizePrefixedPacketReader;
import com.riiablo.save.CharData;
import com.riiablo.util.ArrayUtils;

@All
public class ClientNetworkSynchronizer extends IntervalSystem {
  private static final String TAG = "ClientNetworkSyncronizer";
  private static final boolean DEBUG         = true;
  private static final boolean DEBUG_PACKET  = DEBUG && !true;
  private static final boolean DEBUG_CONNECT = DEBUG && !true;
  /** Movement is applied on the next authoritative tick; combat keeps its own +2 lead. */
  static final int MOVEMENT_TARGET_LEAD_TICKS = 1;

  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Class> mClass;

  protected NetworkIdManager idManager;
  protected ClientNetworkReceiver receiver;

  boolean init = false;
  private long nextNpcRequestId = 1;
  private long nextPartyRequestId = 1;
  private long nextLifecycleRequestId = 1;
  private long nextQuestRequestId = 1;
  private long nextSnapshotResyncRequestId = 1;
  private long nextMovementLogTime;
  private long nextMovementSequence = 1L;
  private final ClientPredictionBuffer prediction = new ClientPredictionBuffer();
  @Wire(name="client.socket") Socket socket;

  public ClientNetworkSynchronizer() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void initialize() {
    receiver.setEnabled(false);
  }

  @Override
  protected void begin() {
    if (socket == null) return;
    if (init) return;
    init = true;

    try {
      CharData charData = Riiablo.charData;

      FlatBufferBuilder builder = new FlatBufferBuilder(8192);
      int charNameOffset = builder.createString(charData.name);

      int entityId = Riiablo.game.player;
      int[] component = mCofComponents.get(entityId).component;
      builder.startVector(1, component.length, 1);
      for (int i = component.length - 1; i >= 0; i--) builder.addByte((byte) component[i]);
      int componentsOffset = builder.endVector();

      byte[] alphas = ArrayUtils.toFixedPoint(mCofAlphas.get(entityId).alpha);
      int alphasOffset = Connection.createCofAlphasVector(builder, alphas);

      byte[] transforms = mCofTransforms.get(entityId).transform;
      int transformsOffset = Connection.createCofTransformsVector(builder, transforms);

      int d2sOffset = Connection.createD2sVector(builder, charData.serialize());

      Connection.startConnection(builder);
      Connection.addCharClass(builder, charData.charClass);
      Connection.addCharName(builder, charNameOffset);
      Connection.addCofComponents(builder, componentsOffset);
      Connection.addCofAlphas(builder, alphasOffset);
      Connection.addCofTransforms(builder, transformsOffset);
      Connection.addD2s(builder, d2sOffset);
      int connectionOffset = Connection.endConnection(builder);
      int offset = D2GS.createD2GS(builder, D2GSData.Connection, connectionOffset);
      D2GS.finishSizePrefixedD2GSBuffer(builder, offset);

      OutputStream out = socket.getOutputStream();
      WritableByteChannel channelOut = Channels.newChannel(out);
      ByteBuffer connectionFrame = builder.dataBuffer();
      while (connectionFrame.hasRemaining()) channelOut.write(connectionFrame);

      // Read exactly the ACK frame. Reading an arbitrary TCP chunk here can
      // also consume the first EntitySync frames and silently discard them
      // before ClientNetworkReceiver is enabled.
      ByteBuffer frame = SizePrefixedPacketReader.readFrame(
          socket.getInputStream(), 1 << 22);
      D2GS d2gs = D2GS.getRootAsD2GS(frame);
      if (d2gs.dataType() != D2GSData.Connection) {
        throw new IllegalStateException("Expected Connection ACK, got "
            + D2GSData.name(d2gs.dataType()));
      }
      Connection connection = (Connection) d2gs.data(new Connection());
      if (connection.charName() != null) {
        throw new IllegalStateException("Connection ACK contained a character name");
      }
      int serverId = connection.entityId();
      Gdx.app.log(TAG, "assign " + entityId + " to " + serverId);
      idManager.put(serverId, Riiablo.game.player);
      Vector2 initialPosition = mPosition.get(entityId).position;
      prediction.reset(initialPosition.x, initialPosition.y);
      nextMovementSequence = 1L;
      receiver.setEnabled(true);
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
      setEnabled(false);
    }
  }

  @Override
  protected void processSystem() {
    int entityId = Riiablo.game.player;

    // A dead local player intentionally has no Velocity. Do not dereference
    // it or keep sending stale movement while waiting for server respawn.
    if (socket == null || entityId < 0 || mPlayerCorpse.has(entityId)
        || !mNetworked.has(entityId) || !mPosition.has(entityId)) return;

    FlatBufferBuilder builder = new FlatBufferBuilder(0);
    Vector2 position = mPosition.get(entityId).position;
    final long inputSequence = nextMovementSequence;
    final long observedServerTick = receiver.latestServerTick();
    final long targetTick = observedServerTick == 0L ? 0L
        : observedServerTick + MOVEMENT_TARGET_LEAD_TICKS;
    final boolean running = mRunning.has(entityId);

    int targetEntityId = Engine.INVALID_ENTITY;
    int targetType = 0;
    Target target = mTarget.get(entityId);
    if (target != null && target.target != Engine.INVALID_ENTITY
        && mNetworked.has(target.target)) {
      targetEntityId = mNetworked.get(target.target).serverId;
      Class targetClass = mClass.get(target.target);
      targetType = targetClass == null ? 0 : targetClass.type.ordinal();
    }

    Vector2 destination = position;
    Pathfind pathfind = mPathfind.get(entityId);
    if (targetEntityId == Engine.INVALID_ENTITY && pathfind != null) {
      destination = pathfind.destination;
    }
    int destinationX = Math.round(destination.x);
    int destinationY = Math.round(destination.y);
    if (targetEntityId == Engine.INVALID_ENTITY
        && (destinationX < Short.MIN_VALUE || destinationX > Short.MAX_VALUE
            || destinationY < Short.MIN_VALUE || destinationY > Short.MAX_VALUE)) {
      Gdx.app.error(TAG, "[NET_MOVE] phase=client_reject reason=coordinate_range target=("
          + destinationX + "," + destinationY + ")");
      return;
    }

    long now = TimeUtils.millis();
    if (now >= nextMovementLogTime) {
      nextMovementLogTime = now + 1000L;
      Gdx.app.log(TAG, "[NET_MOVE] phase=client_intent local=" + entityId
          + " server=" + mNetworked.get(entityId).serverId
          + " sequence=" + inputSequence + " observedTick=" + observedServerTick
          + " targetTick=" + targetTick + " mode=" + (running ? "run" : "walk")
          + (targetEntityId != Engine.INVALID_ENTITY
              ? " targetEntity=" + targetEntityId
              : " targetLocation=(" + destinationX + "," + destinationY + ")"));
    }

    final int movementOffset;
    final byte movementType;
    if (targetEntityId != Engine.INVALID_ENTITY) {
      if (running) {
        RunToEntity.startRunToEntity(builder);
        RunToEntity.addType(builder, targetType);
        RunToEntity.addEntityId(builder, targetEntityId);
        RunToEntity.addSequence(builder, inputSequence);
        RunToEntity.addObservedServerTick(builder, observedServerTick);
        RunToEntity.addTargetTick(builder, targetTick);
        movementOffset = RunToEntity.endRunToEntity(builder);
        movementType = D2GSData.RunToEntity;
      } else {
        WalkToEntity.startWalkToEntity(builder);
        WalkToEntity.addType(builder, targetType);
        WalkToEntity.addEntityId(builder, targetEntityId);
        WalkToEntity.addSequence(builder, inputSequence);
        WalkToEntity.addObservedServerTick(builder, observedServerTick);
        WalkToEntity.addTargetTick(builder, targetTick);
        movementOffset = WalkToEntity.endWalkToEntity(builder);
        movementType = D2GSData.WalkToEntity;
      }
    } else if (running) {
      RunToLocation.startRunToLocation(builder);
      RunToLocation.addX(builder, (short) destinationX);
      RunToLocation.addY(builder, (short) destinationY);
      RunToLocation.addSequence(builder, inputSequence);
      RunToLocation.addObservedServerTick(builder, observedServerTick);
      RunToLocation.addTargetTick(builder, targetTick);
      movementOffset = RunToLocation.endRunToLocation(builder);
      movementType = D2GSData.RunToLocation;
    } else {
      WalkToLocation.startWalkToLocation(builder);
      WalkToLocation.addX(builder, (short) destinationX);
      WalkToLocation.addY(builder, (short) destinationY);
      WalkToLocation.addSequence(builder, inputSequence);
      WalkToLocation.addObservedServerTick(builder, observedServerTick);
      WalkToLocation.addTargetTick(builder, targetTick);
      movementOffset = WalkToLocation.endWalkToLocation(builder);
      movementType = D2GSData.WalkToLocation;
    }
    int root = D2GS.createD2GS(builder, movementType, movementOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);

    try {
      OutputStream out = socket.getOutputStream();
      WritableByteChannel channelOut = Channels.newChannel(out);
      ByteBuffer frame = builder.dataBuffer();
      while (frame.hasRemaining()) channelOut.write(frame);
      prediction.recordSent(inputSequence, position.x, position.y);
      nextMovementSequence++;
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
      prediction.reset(position.x, position.y);
      setEnabled(false);
    }
  }

  /** Requests a complete authoritative baseline, rate-limited by the receiver. */
  void requestSnapshotResync(long lastAcceptedTick, String reason) {
    if (socket == null) return;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int reasonOffset = builder.createString(reason == null ? "unknown" : reason);
    long requestId = nextSnapshotResyncRequestId++;
    SnapshotResyncRequest.startSnapshotResyncRequest(builder);
    SnapshotResyncRequest.addRequestId(builder, requestId);
    SnapshotResyncRequest.addLastAcceptedTick(builder, lastAcceptedTick);
    SnapshotResyncRequest.addReason(builder, reasonOffset);
    int request = SnapshotResyncRequest.endSnapshotResyncRequest(builder);
    int root = D2GS.createD2GS(builder, D2GSData.SnapshotResyncRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      ByteBuffer frame = builder.dataBuffer();
      while (frame.hasRemaining()) channel.write(frame);
      Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=request request=" + requestId
          + " lastTick=" + lastAcceptedTick + " reason=" + reason);
    } catch (Throwable t) {
      Gdx.app.error(TAG, "[SNAPSHOT_RESYNC] phase=send_failed reason=" + t.getMessage(), t);
    }
  }

  ClientPredictionBuffer.Reconciliation reconcileMovement(long acknowledgedSequence,
      float authoritativeX, float authoritativeY, float predictedX, float predictedY,
      boolean hardCorrection) {
    return prediction.reconcile(acknowledgedSequence, authoritativeX, authoritativeY,
        predictedX, predictedY, hardCorrection);
  }

  void resetPrediction(float x, float y) {
    prediction.reset(x, y);
    nextMovementSequence = 1L;
  }

  void rebaseLegacyPrediction(float x, float y) {
    prediction.rebaseLegacy(x, y);
  }

  /** Sends an untrusted NPC intent; the D2GS resolves player, price and stock. */
  public long requestNpcService(int localNpcEntityId, byte service, byte operation,
                                int itemId, int itemIndex, long stockRevision) {
    if (socket == null) return 0;
    long requestId = nextNpcRequestId++;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int serverNpcId = mNetworked.has(localNpcEntityId)
        ? mNetworked.get(localNpcEntityId).serverId : localNpcEntityId;
    int request = NpcServiceRequest.createNpcServiceRequest(builder, requestId,
        serverNpcId, service, operation,
        itemId, itemIndex, stockRevision);
    int root = D2GS.createD2GS(builder, D2GSData.NpcServiceRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      channel.write(builder.dataBuffer());
      return requestId;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "Failed to send NPC service request", t);
      return 0;
    }
  }

  /** Sends a party intent. D2GS derives the source player from this connection. */
  public long requestParty(byte operation, int localTargetEntityId) {
    if (socket == null) return 0;
    long requestId = nextPartyRequestId++;
    int serverTargetId = localTargetEntityId >= 0 && mNetworked.has(localTargetEntityId)
        ? mNetworked.get(localTargetEntityId).serverId : localTargetEntityId;
    if (operation == PartyOperation.SNAPSHOT) serverTargetId = -1;

    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = PartyRequest.createPartyRequest(
        builder, requestId, operation, serverTargetId);
    int root = D2GS.createD2GS(builder, D2GSData.PartyRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      ByteBuffer frame = builder.dataBuffer();
      while (frame.hasRemaining()) channel.write(frame);
      return requestId;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "Failed to send party request", t);
      return 0;
    }
  }

  /** Requests an authenticated server-authoritative town respawn. */
  public long requestPlayerRespawn() {
    if (socket == null) return 0;
    long requestId = nextLifecycleRequestId++;
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int request = PlayerLifecycleRequest.createPlayerLifecycleRequest(
        builder, requestId, PlayerLifecycleOperation.RESPAWN);
    int root = D2GS.createD2GS(builder, D2GSData.PlayerLifecycleRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      ByteBuffer frame = builder.dataBuffer();
      while (frame.hasRemaining()) channel.write(frame);
      Gdx.app.log(TAG, "[PLAYER_RESPAWN] phase=request request=" + requestId);
      return requestId;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "Failed to send player respawn request", t);
      return 0;
    }
  }

  /** Sends an authenticated, idempotent Act I quest intent to D2GS. */
  public long requestQuest(byte operation, int localTargetEntityId, int messageIndex) {
    if (socket == null) return 0;
    long requestId = nextQuestRequestId++;
    int serverTargetId = localTargetEntityId >= 0 && mNetworked.has(localTargetEntityId)
        ? mNetworked.get(localTargetEntityId).serverId : localTargetEntityId;
    FlatBufferBuilder builder = new FlatBufferBuilder(96);
    int request = QuestRequest.createQuestRequest(builder, requestId, operation,
        serverTargetId, messageIndex);
    int root = D2GS.createD2GS(builder, D2GSData.QuestRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    try {
      WritableByteChannel channel = Channels.newChannel(socket.getOutputStream());
      ByteBuffer frame = builder.dataBuffer();
      while (frame.hasRemaining()) channel.write(frame);
      Gdx.app.log(TAG, "[QUEST_NET] phase=request request=" + requestId
          + " operation=" + operation + " target=" + serverTargetId
          + " message=" + messageIndex);
      return requestId;
    } catch (Throwable t) {
      Gdx.app.error(TAG, "Failed to send quest request", t);
      return 0;
    }
  }

  /** Server entity id assigned to this client during the authenticated handshake. */
  public int serverPlayerId() {
    if (Riiablo.game == null || Riiablo.game.player < 0) return -1;
    int localEntityId = Riiablo.game.player;
    return mNetworked.has(localEntityId)
        ? mNetworked.get(localEntityId).serverId : -1;
  }
}
