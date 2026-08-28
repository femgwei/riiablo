package com.riiablo.server.d2gs_netty;

import com.google.flatbuffers.FlatBufferBuilder;
import java.util.concurrent.BlockingQueue;

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
import com.riiablo.net.tcp.D2GSOutboundPacketFactory;
import com.riiablo.net.tcp.OutboundPacket;

@All(Networked.class)
public class NetworkSynchronizer extends BaseEntitySystem {
  private static final String TAG = "NetworkSynchronizer";

  private static final boolean DEBUG      = true;
  private static final boolean DEBUG_SYNC = DEBUG && !true;

  protected SerializationManager serializer;

  @Wire(name = "outPackets")
  protected BlockingQueue<OutboundPacket> outPackets;

  @Wire(name = "player")
  protected IntIntMap players;

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Flags> mFlags;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;

  @Override
  protected boolean checkProcessing() {
    return players.size > 0;
  }

  // FIXME: this assumes that removing Networked component implies deletion -- may not always be case
  @Override
  protected void removed(int entityId) {
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
    FlatBufferBuilder builder = sync(new FlatBufferBuilder(0), entityId);
    int recipients = recipientMask(entityId);
    if (recipients == 0) return;
    OutboundPacket packet = D2GSOutboundPacketFactory.obtain(recipients, D2GSData.EntitySync, builder.dataBuffer());
    boolean success = outPackets.offer(packet);
    assert success;
  }

  private int recipientMask(int entityId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.zone == null || !source.zone.hasNativeRoomTopology()) return OutboundPacket.BROADCAST;
    if (!mPosition.has(entityId)) return OutboundPacket.BROADCAST;
    int mask = 0;
    for (IntIntMap.Entry entry : players.entries()) {
      MapWrapper target = mMapWrapper.has(entry.value) ? mMapWrapper.get(entry.value) : null;
      if (target == null || target.zone != source.zone || !mPosition.has(entry.value)) continue;
      if (source.zone.areRoomsAdjacent(mPosition.get(entityId).position.x,
          mPosition.get(entityId).position.y, mPosition.get(entry.value).position.x,
          mPosition.get(entry.value).position.y)) mask |= 1 << entry.key;
    }
    Gdx.app.debug(TAG, "[ROOM_NET_SYNC] entity=" + entityId + " recipients=0x"
        + Integer.toHexString(mask));
    return mask;
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
