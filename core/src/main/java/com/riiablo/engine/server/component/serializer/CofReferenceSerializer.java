package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;

/** Serializes the server-selected animation mode and weapon class. */
public class CofReferenceSerializer
    implements FlatBuffersSerializer<CofReference, CofReferenceP> {
  public static final CofReferenceP table = new CofReferenceP();

  @Override
  public byte getDataType() {
    return ComponentP.CofReferenceP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, CofReference component) {
    return CofReferenceP.createCofReferenceP(builder, component.mode, component.wclass);
  }

  @Override
  public CofReferenceP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public CofReference getData(EntitySync sync, int index, CofReference component) {
    CofReferenceP data = getTable(sync, index);
    component.mode = (byte) data.mode();
    component.wclass = (byte) data.weaponClass();
    return component;
  }
}
