package com.riiablo.server.d2gs;

import com.google.flatbuffers.FlatBufferBuilder;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.engine.server.SerializationManager;
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
  private final EntitySnapshotCache snapshots = new EntitySnapshotCache();
  private final IntIntMap lastRecipients = new IntIntMap();

  @Override
  protected boolean checkProcessing() {
    return players.size > 0;
  }

  // FIXME: this assumes that removing Networked component implies deletion -- may not always be case
  @Override
  protected void removed(int entityId) {
    snapshots.remove(entityId);
    lastRecipients.remove(entityId, 0);
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
    byte[] snapshot = serialize(entityId);
    int recipients = recipientMask(entityId);
    if (recipients == 0) return;
    int previousRecipients = lastRecipients.get(entityId, Integer.MIN_VALUE);
    if (previousRecipients != recipients) {
      // A newly visible client needs the unchanged baseline too.
      snapshots.remove(entityId);
      lastRecipients.put(entityId, recipients);
    }
    if (!snapshots.update(entityId, snapshot)) return;
    Packet packet = Packet.obtain(recipients, ByteBuffer.wrap(snapshot));
    boolean success = outPackets.offer(packet);
    if (!success) {
      // Do not suppress the next frame after a queue failure. Removing the
      // cached value makes the authoritative snapshot eligible for retry.
      snapshots.remove(entityId);
      Gdx.app.error(TAG, "[NET_SYNC] phase=runtime_drop entity=" + entityId
          + " reason=out_queue_full");
    }
  }

  /** D2MOO sends unit updates only to clients in the current/adjacent RoomEx. */
  private int recipientMask(int entityId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.zone == null || !source.zone.hasNativeRoomTopology()) {
      return 0xFFFFFFFF;
    }
    int mask = 0;
    if (!mPosition.has(entityId)) return 0xFFFFFFFF;
    for (IntIntMap.Entry entry : players.entries()) {
      MapWrapper target = mMapWrapper.has(entry.value) ? mMapWrapper.get(entry.value) : null;
      if (target == null || target.zone != source.zone || !mPosition.has(entry.value)) continue;
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
    int queued = 0;
    int failed = 0;
    long bytes = 0;
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = entityIds[i];
      byte[] snapshot = serialize(entityId);
      // Prime the global change cache. Existing clients already know these
      // unchanged entities, while this targeted packet initializes the joiner.
      snapshots.update(entityId, snapshot);
      if (outPackets.offer(Packet.obtain(1 << clientId, ByteBuffer.wrap(snapshot)))) {
        queued++;
        bytes += snapshot.length;
      } else {
        snapshots.remove(entityId);
        failed++;
      }
    }
    Gdx.app.log(TAG, "[NET_SYNC] phase=baseline client=" + clientId
        + " entities=" + entities.size() + " queued=" + queued
        + " failed=" + failed + " bytes=" + bytes);
  }

  private byte[] serialize(int entityId) {
    ByteBuffer buffer = sync(new FlatBufferBuilder(0), entityId).dataBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.duplicate().get(bytes);
    return bytes;
  }

  public FlatBufferBuilder sync(FlatBufferBuilder builder, int entityId) {
    int syncOffset = serializer.serialize(builder, entityId);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, syncOffset);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    return builder;
  }

  public void sync(int entityId, D2GS packet) {
    if (DEBUG_SYNC) Gdx.app.log(TAG, "syncing " + entityId);
    serializer.deserialize(entityId, packet);
  }
}
