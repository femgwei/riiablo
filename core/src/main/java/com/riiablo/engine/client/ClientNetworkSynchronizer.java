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
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.CofAlphasP;
import com.riiablo.net.packet.d2gs.CofComponentsP;
import com.riiablo.net.packet.d2gs.CofTransformsP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.NpcServiceRequest;
import com.riiablo.net.packet.d2gs.PartyOperation;
import com.riiablo.net.packet.d2gs.PartyRequest;
import com.riiablo.net.packet.d2gs.PlayerLifecycleOperation;
import com.riiablo.net.packet.d2gs.PlayerLifecycleRequest;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.SizePrefixedPacketReader;
import com.riiablo.save.CharData;
import com.riiablo.util.ArrayUtils;

@All
public class ClientNetworkSynchronizer extends IntervalSystem {
  private static final String TAG = "ClientNetworkSyncronizer";
  private static final boolean DEBUG         = true;
  private static final boolean DEBUG_PACKET  = DEBUG && !true;
  private static final boolean DEBUG_CONNECT = DEBUG && !true;

  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;

  protected NetworkIdManager idManager;
  protected ClientNetworkReceiver receiver;

  boolean init = false;
  private long nextNpcRequestId = 1;
  private long nextPartyRequestId = 1;
  private long nextLifecycleRequestId = 1;
  private long nextMovementLogTime;
  @Wire(name="client.socket") Socket socket;

  public ClientNetworkSynchronizer() {
    super(null, 1 / 60f);
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
        || !mNetworked.has(entityId) || !mPosition.has(entityId)
        || !mVelocity.has(entityId) || !mAngle.has(entityId)
        || !mCofComponents.has(entityId) || !mCofTransforms.has(entityId)
        || !mCofAlphas.has(entityId)) return;

    FlatBufferBuilder builder = new FlatBufferBuilder(0);

    int[] component2 = mCofComponents.get(entityId).component;
    byte[] component = new byte[16];
    for (int i = 0; i < 16; i++) component[i] = (byte) component2[i];

    byte[] transform = mCofTransforms.get(entityId).transform;
    byte[] alpha = ArrayUtils.toFixedPoint(mCofAlphas.get(entityId).alpha);
    Vector2 position = mPosition.get(entityId).position;
    Vector2 velocity = mVelocity.get(entityId).velocity;
    Vector2 angle = mAngle.get(entityId).target;

    long now = TimeUtils.millis();
    if (now >= nextMovementLogTime) {
      nextMovementLogTime = now + 1000L;
      Gdx.app.log(TAG, String.format(
          "[NET_MOVE] phase=client_send local=%d server=%d pos=(%.2f,%.2f) "
              + "velocity=(%.2f,%.2f) angle=(%.2f,%.2f)",
          entityId, mNetworked.get(entityId).serverId,
          position.x, position.y, velocity.x, velocity.y, angle.x, angle.y));
    }

    int cofComponents = CofComponentsP.createComponentVector(builder, component);
    int cofTransforms = CofTransformsP.createTransformVector(builder, transform);
    int cofAlphas = CofAlphasP.createAlphaVector(builder, alpha);

    byte[] dataTypes = new byte[6];
    dataTypes[0] = ComponentP.CofComponentsP;
    dataTypes[1] = ComponentP.CofTransformsP;
    dataTypes[2] = ComponentP.CofAlphasP;
    dataTypes[3] = ComponentP.PositionP;
    dataTypes[4] = ComponentP.VelocityP;
    dataTypes[5] = ComponentP.AngleP;
    int dataTypesOffset = EntitySync.createComponentTypeVector(builder, dataTypes);

    int[] data = new int[6];
    data[0] = CofComponentsP.createCofComponentsP(builder, cofComponents);
    data[1] = CofTransformsP.createCofTransformsP(builder, cofTransforms);
    data[2] = CofAlphasP.createCofAlphasP(builder, cofAlphas);
    data[3] = PositionP.createPositionP(builder, position.x, position.y);
    data[4] = VelocityP.createVelocityP(builder, velocity.x, velocity.y);
    data[5] = AngleP.createAngleP(builder, angle.x, angle.y);
    int dataOffset = EntitySync.createComponentVector(builder, data);

    EntitySync.startEntitySync(builder);
    EntitySync.addEntityId(builder, mNetworked.get(entityId).serverId);
    EntitySync.addComponentType(builder, dataTypesOffset);
    EntitySync.addComponent(builder, dataOffset);
    int syncOffset = EntitySync.endEntitySync(builder);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, syncOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);

    try {
      OutputStream out = socket.getOutputStream();
      WritableByteChannel channelOut = Channels.newChannel(out);
      channelOut.write(builder.dataBuffer());
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
      setEnabled(false);
    }
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

  /** Server entity id assigned to this client during the authenticated handshake. */
  public int serverPlayerId() {
    if (Riiablo.game == null || Riiablo.game.player < 0) return -1;
    int localEntityId = Riiablo.game.player;
    return mNetworked.has(localEntityId)
        ? mNetworked.get(localEntityId).serverId : -1;
  }
}
