package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.MissileP;

/** Serializes the immutable identity of a server-authoritative projectile. */
public class MissileSerializer implements FlatBuffersSerializer<Missile, MissileP> {
  public static final MissileP table = new MissileP();

  @Override
  public byte getDataType() {
    return ComponentP.MissileP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, Missile component) {
    int missileId = component.missile == null ? 0 : component.missile.Id;
    return MissileP.createMissileP(builder, missileId, component.ownerId, component.range);
  }

  @Override
  public MissileP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public Missile getData(EntitySync sync, int index, Missile component) {
    // Client creation is handled by ClientNetworkReceiver because it needs
    // Position and Angle from the same EntitySync packet.
    throw new UnsupportedOperationException("Missile creation requires entity factory");
  }
}
