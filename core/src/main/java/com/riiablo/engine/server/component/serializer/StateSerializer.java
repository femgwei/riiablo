package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;

import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.StateP;

/** Serializes the authoritative active status list for multiplayer clients. */
public class StateSerializer implements FlatBuffersSerializer<UnitStates, StateP> {
  public static final StateP table = new StateP();

  @Override
  public byte getDataType() {
    return ComponentP.StateP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, UnitStates component) {
    if (component.stateList == null || component.stateList.isEmpty()) {
      return StateP.createStateP(builder, 0, 0, 0);
    }

    int count = component.stateList.size();
    short[] stateIds = new short[count];
    int[] durations = new int[count];
    byte[] levels = new byte[count];
    for (int i = 0; i < count; i++) {
      UnitState state = component.stateList.getStates().get(i);
      stateIds[i] = (short) Math.max(0, Math.min(0xFFFF, state.stateId));
      durations[i] = state.duration;
      levels[i] = (byte) Math.max(0, Math.min(255, state.level));
    }

    int stateIdOffset = StateP.createStateIdVector(builder, stateIds);
    int durationOffset = StateP.createDurationVector(builder, durations);
    int levelOffset = StateP.createLevelVector(builder, levels);
    return StateP.createStateP(builder, stateIdOffset, durationOffset, levelOffset);
  }

  @Override
  public StateP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public UnitStates getData(EntitySync sync, int index, UnitStates component) {
    StateP data = getTable(sync, index);
    int count = data.stateIdLength();
    int[] stateIds = new int[count];
    int[] durations = new int[count];
    int[] levels = new int[count];
    for (int i = 0; i < count; i++) {
      stateIds[i] = data.stateId(i);
      durations[i] = i < data.durationLength() ? data.duration(i) : 0;
      levels[i] = i < data.levelLength() ? data.level(i) : 1;
    }
    if (component.stateList == null) component.init(-1);
    component.stateList.replaceFromSnapshot(stateIds, durations, levels);
    return component;
  }
}
