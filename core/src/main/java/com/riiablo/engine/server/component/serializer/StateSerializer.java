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
      return StateP.createStateP(builder, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    int count = component.stateList.size();
    short[] stateIds = new short[count];
    int[] durations = new int[count];
    byte[] levels = new byte[count];
    short[] velocityModifiers = new short[count];
    short[] runtimeValues = new short[count];
    short[] animationRateModifiers = new short[count];
    short[] skillModifiers = new short[count];
    short[] maxLifeModifiers = new short[count];
    short[] maxManaModifiers = new short[count];
    short[] maxStaminaModifiers = new short[count];
    for (int i = 0; i < count; i++) {
      UnitState state = component.stateList.getStates().get(i);
      stateIds[i] = (short) Math.max(0, Math.min(0xFFFF, state.stateId));
      durations[i] = state.duration;
      levels[i] = (byte) Math.max(0, Math.min(255, state.level));
      velocityModifiers[i] = (short) Math.max(Short.MIN_VALUE,
          Math.min(Short.MAX_VALUE, state.velocityModifier));
      runtimeValues[i] = (short) Math.max(Short.MIN_VALUE,
          Math.min(Short.MAX_VALUE, state.runtimeValue));
      animationRateModifiers[i] = (short) Math.max(Short.MIN_VALUE,
          Math.min(Short.MAX_VALUE, state.animationRateModifier));
      skillModifiers[i] = clampShort(state.skillModifier);
      maxLifeModifiers[i] = clampShort(state.maxLifeModifier);
      maxManaModifiers[i] = clampShort(state.maxManaModifier);
      maxStaminaModifiers[i] = clampShort(state.maxStaminaModifier);
    }

    int stateIdOffset = StateP.createStateIdVector(builder, stateIds);
    int durationOffset = StateP.createDurationVector(builder, durations);
    int levelOffset = StateP.createLevelVector(builder, levels);
    int velocityModifierOffset = StateP.createVelocityModifierVector(builder, velocityModifiers);
    int runtimeValueOffset = StateP.createRuntimeValueVector(builder, runtimeValues);
    int animationRateModifierOffset =
        StateP.createAnimationRateModifierVector(builder, animationRateModifiers);
    int skillModifierOffset = StateP.createSkillModifierVector(builder, skillModifiers);
    int maxLifeModifierOffset = StateP.createMaxLifeModifierVector(builder, maxLifeModifiers);
    int maxManaModifierOffset = StateP.createMaxManaModifierVector(builder, maxManaModifiers);
    int maxStaminaModifierOffset =
        StateP.createMaxStaminaModifierVector(builder, maxStaminaModifiers);
    return StateP.createStateP(builder, stateIdOffset, durationOffset, levelOffset,
        velocityModifierOffset, runtimeValueOffset, animationRateModifierOffset,
        skillModifierOffset, maxLifeModifierOffset, maxManaModifierOffset,
        maxStaminaModifierOffset);
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
    for (int i = 0; i < count; i++) {
      UnitState state = component.stateList.getState(stateIds[i]);
      if (state != null) {
        state.velocityModifier = i < data.velocityModifierLength()
            ? data.velocityModifier(i) : 0;
        state.runtimeValue = i < data.runtimeValueLength() ? data.runtimeValue(i) : 0;
        state.animationRateModifier = i < data.animationRateModifierLength()
            ? data.animationRateModifier(i) : 0;
        state.skillModifier = i < data.skillModifierLength() ? data.skillModifier(i) : 0;
        state.maxLifeModifier = i < data.maxLifeModifierLength() ? data.maxLifeModifier(i) : 0;
        state.maxManaModifier = i < data.maxManaModifierLength() ? data.maxManaModifier(i) : 0;
        state.maxStaminaModifier = i < data.maxStaminaModifierLength()
            ? data.maxStaminaModifier(i) : 0;
      }
    }
    return component;
  }

  private static short clampShort(int value) {
    return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
  }
}
