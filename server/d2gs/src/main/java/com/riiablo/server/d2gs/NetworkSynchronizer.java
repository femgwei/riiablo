package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Flags;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.EntitySnapshotCache;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

@All(Networked.class)
public class NetworkSynchronizer extends BaseEntitySystem {
  private static final String TAG = "NetworkSynchronizer";

  private static final boolean DEBUG      = true;
  private static final boolean DEBUG_SYNC = DEBUG && !true;

  protected SerializationManager serializer;

  @Wire(name = "outPackets")
  protected BlockingQueue<Packet> outPackets;

  @Wire(name = "player")
  protected IntIntMap players;

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Flags> mFlags;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  /**
   * Each connection needs its own last-sent state. A baseline sent to a newly
   * connected client must never suppress an incremental update still pending
   * for an existing client.
   */
  private final IntMap<EntitySnapshotCache> snapshotsByRecipient = new IntMap<>();
  private final IntIntMap lastRecipients = new IntIntMap();
  private final IntMap<MovementAcknowledgement> movementAcknowledgements = new IntMap<>();

  @Override
  protected boolean checkProcessing() {
    return players.size > 0;
  }

  // FIXME: this assumes that removing Networked component implies deletion -- may not always be case
  @Override
  protected void removed(int entityId) {
    removeSnapshots(entityId);
    lastRecipients.remove(entityId, 0);
    movementAcknowledgements.remove(entityId);
    Class.Type type = mClass.get(entityId).type;
    switch (type) {
      case PLR:
        // TODO: handled by disconnection packet, need to handle here also
        break;
      default:
        mFlags.get(entityId).flags |= EntityFlags.deleted;
        process(entityId);
    }
  }

  @Override
  protected void processSystem() {
    IntBag entities = subscription.getEntities();
    int[] entityIds = entities.getData();
    for (int i = 0, s = entities.size(); i < s; i++) {
      process(entityIds[i]);
    }
  }

  protected void process(int entityId) {
    int recipients = recipientMask(entityId);
    int previousRecipients = lastRecipients.get(entityId, Integer.MIN_VALUE);
    if (previousRecipients != Integer.MIN_VALUE && previousRecipients != recipients) {
      int departed = previousRecipients & ~recipients;
      if (departed != 0) sendVisibilityDeletion(entityId, departed);
      // Re-entering a RoomEx must receive a complete state even when the
      // entity itself did not change while the client was away.
      // Re-prime every currently visible recipient after a topology change.
      // This also repairs a client that retained a stale baseline while the
      // other client moved through the shared RoomEx ring.
      removeSnapshots(entityId, departed | recipients);
      Gdx.app.log(TAG, "[NET_SYNC] phase=recipient_change entity=" + entityId
          + " previous=0x" + Integer.toHexString(previousRecipients)
          + " current=0x" + Integer.toHexString(recipients)
          + " reenter=0x" + Integer.toHexString(recipients & ~previousRecipients));
    }
    if (previousRecipients != recipients) {
      lastRecipients.put(entityId, recipients);
    }
    if (recipients == 0) return;
    byte[] state = serialize(entityId, false);
    int changedRecipients = 0;
    for (IntIntMap.Entry entry : players.entries()) {
      int clientId = entry.key;
      int recipient = 1 << clientId;
      if ((recipients & recipient) == 0) continue;
      if (snapshotsFor(clientId).update(entityId, state)) changedRecipients |= recipient;
    }
    if (changedRecipients == 0) return;
    if (previousRecipients != recipients) {
      Gdx.app.log(TAG, "[NET_SYNC] phase=recipient_baseline entity=" + entityId
          + " recipients=0x" + Integer.toHexString(changedRecipients));
    }
    byte[] snapshot = serialize(entityId, true);
    Packet packet = Packet.obtain(changedRecipients, ByteBuffer.wrap(snapshot));
    boolean success = outPackets.offer(packet);
    if (!success) {
      // Do not suppress the next frame after a queue failure. Removing the
      // cached value makes the authoritative snapshot eligible for retry.
      removeSnapshots(entityId, changedRecipients);
      Gdx.app.error(TAG, "[NET_SYNC] phase=runtime_drop entity=" + entityId
          + " reason=out_queue_full");
    }
  }

  /** Sends a deletion only to clients that just lost room visibility. */
  private void sendVisibilityDeletion(int entityId, int recipients) {
    AuthoritativeSimulation simulation = AuthoritativeSimulation.current();
    long tick = simulation == null ? 0L : simulation.tickNumber();
    long serverTimeMillis = simulation == null ? 0L : simulation.serverTimeMillis();
    FlatBufferBuilder builder = new FlatBufferBuilder(0);
    int syncOffset = serializer.serializeDeleted(builder, entityId, tick, serverTimeMillis);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, syncOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    byte[] bytes = new byte[builder.dataBuffer().remaining()];
    builder.dataBuffer().duplicate().get(bytes);
    if (!outPackets.offer(Packet.obtain(recipients, ByteBuffer.wrap(bytes)))) {
      Gdx.app.error(TAG, "[NET_SYNC] phase=visibility_delete_drop entity=" + entityId
          + " recipients=0x" + Integer.toHexString(recipients));
      return;
    }
    Gdx.app.log(TAG, "[NET_SYNC] phase=visibility_delete entity=" + entityId
        + " recipients=0x" + Integer.toHexString(recipients)
        + " tick=" + tick);
  }

  /** D2MOO sends unit updates only to clients in the current/adjacent RoomEx. */
  private int recipientMask(int entityId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.zone == null || !source.zone.hasNativeRoomTopology()) {
      if (source == null || source.zone == null) return 0xFFFFFFFF;
      int mask = 0;
      for (IntIntMap.Entry entry : players.entries()) {
        MapWrapper target = mMapWrapper.has(entry.value) ? mMapWrapper.get(entry.value) : null;
        if (sameLevel(source, target)) mask |= 1 << entry.key;
      }
      return mask;
    }
    int mask = 0;
    if (!mPosition.has(entityId)) return 0xFFFFFFFF;
    for (IntIntMap.Entry entry : players.entries()) {
      MapWrapper target = mMapWrapper.has(entry.value) ? mMapWrapper.get(entry.value) : null;
      if (target == null || !sameLevel(source, target) || target.zone != source.zone
          || !mPosition.has(entry.value)) continue;
      if (source.zone.areRoomsAdjacent(
          mPosition.get(entityId).position.x, mPosition.get(entityId).position.y,
          mPosition.get(entry.value).position.x, mPosition.get(entry.value).position.y)) {
        mask |= 1 << entry.key;
      }
    }
    return mask;
  }

  /** Sends one complete authoritative baseline to a newly connected client. */
  public void syncAllTo(int clientId) {
    IntBag entities = subscription.getEntities();
    int[] entityIds = entities.getData();
    int recipient = 1 << clientId;
    int queued = 0;
    int failed = 0;
    long bytes = 0;
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = entityIds[i];
      if ((recipientMask(entityId) & recipient) == 0) continue;
      byte[] state = serialize(entityId, false);
      byte[] snapshot = serialize(entityId, true);
      // Prime only the joining client's cache. Existing clients may still have
      // an unsent update for this entity and must not be affected by a joiner
      // baseline.
      snapshotsFor(clientId).update(entityId, state);
      if (outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(snapshot)))) {
        queued++;
        bytes += snapshot.length;
      } else {
        snapshotsFor(clientId).remove(entityId);
        failed++;
      }
    }
    Gdx.app.log(TAG, "[NET_SYNC] phase=baseline client=" + clientId
        + " entities=" + queued + " queued=" + queued
        + " failed=" + failed + " bytes=" + bytes);
  }

  /** Number of currently networked entities included in a baseline. */
  public int subscriptionSize() {
    return subscription.getEntities().size();
  }

  /** Number of entities in the requesting player's authoritative level. */
  public int visibleCount(int clientId) {
    int recipient = 1 << clientId;
    IntBag entities = subscription.getEntities();
    int count = 0;
    int[] entityIds = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      if ((recipientMask(entityIds[i]) & recipient) != 0) count++;
    }
    return count;
  }

  private static boolean sameLevel(MapWrapper source, MapWrapper target) {
    if (source == null || source.zone == null || source.zone.level == null
        || target == null || target.zone == null || target.zone.level == null) return true;
    return source.zone.level.Id == target.zone.level.Id;
  }

  private byte[] serialize(int entityId, boolean includeClock) {
    FlatBufferBuilder builder = new FlatBufferBuilder(0);
    MovementAcknowledgement movement = movementAcknowledgements.get(entityId);
    long acknowledged = movement == null ? 0L : movement.acknowledged;
    long rejected = movement == null ? 0L : movement.rejected;
    AuthoritativeSimulation simulation = AuthoritativeSimulation.current();
    long tick = includeClock && simulation != null ? simulation.tickNumber() : 0L;
    long serverTimeMillis = includeClock && simulation != null
        ? simulation.serverTimeMillis() : 0L;
    int syncOffset = serializer.serialize(builder, entityId, tick, serverTimeMillis,
        acknowledged, rejected);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, syncOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer buffer = builder.dataBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.duplicate().get(bytes);
    return bytes;
  }

  public FlatBufferBuilder sync(FlatBufferBuilder builder, int entityId) {
    MovementAcknowledgement movement = movementAcknowledgements.get(entityId);
    AuthoritativeSimulation simulation = AuthoritativeSimulation.current();
    int syncOffset = serializer.serialize(builder, entityId,
        simulation == null ? 0L : simulation.tickNumber(),
        simulation == null ? 0L : simulation.serverTimeMillis(),
        movement == null ? 0L : movement.acknowledged,
        movement == null ? 0L : movement.rejected);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, syncOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder;
  }

  public void sync(int entityId, D2GS packet) {
    if (DEBUG_SYNC) Gdx.app.log(TAG, "syncing " + entityId);
    serializer.deserialize(entityId, packet);
  }

  /** Publishes the latest processed input even when movement was rejected. */
  public void acknowledgeMovement(int entityId, long sequence, boolean rejected) {
    MovementAcknowledgement movement = movementAcknowledgements.get(entityId);
    if (movement == null) {
      movement = new MovementAcknowledgement();
      movementAcknowledgements.put(entityId, movement);
    }
    if (sequence < movement.acknowledged) return;
    movement.acknowledged = sequence;
    if (rejected) movement.rejected = sequence;
  }

  public void clearMovementAcknowledgement(int entityId) {
    movementAcknowledgements.remove(entityId);
    removeSnapshots(entityId);
  }

  /**
   * Drops every recipient-scoped cache entry for a disconnected connection.
   *
   * <p>Connection slots are reusable. Keeping the old cache would allow a
   * reconnecting character in the same slot to inherit the previous client's
   * last-sent entity states, suppressing the first incremental update after
   * the new baseline. D2GS calls this before releasing the socket.</p>
   */
  public void clearClient(int clientId) {
    if (clientId < 0) return;
    snapshotsByRecipient.remove(clientId);
    Gdx.app.log(TAG, "[NET_SYNC] phase=client_cache_clear client=" + clientId);
  }

  private EntitySnapshotCache snapshotsFor(int clientId) {
    EntitySnapshotCache cache = snapshotsByRecipient.get(clientId);
    if (cache == null) {
      cache = new EntitySnapshotCache();
      snapshotsByRecipient.put(clientId, cache);
    }
    return cache;
  }

  private void removeSnapshots(int entityId) {
    for (IntMap.Entry<EntitySnapshotCache> entry : snapshotsByRecipient.entries()) {
      entry.value.remove(entityId);
    }
  }

  private void removeSnapshots(int entityId, int recipients) {
    if (recipients == 0) return;
    for (int clientId = 0; clientId < Integer.SIZE; clientId++) {
      if ((recipients & (1 << clientId)) != 0) {
        EntitySnapshotCache cache = snapshotsByRecipient.get(clientId);
        if (cache != null) cache.remove(entityId);
      }
    }
  }

  private static final class MovementAcknowledgement {
    long acknowledged;
    long rejected;
  }
}
