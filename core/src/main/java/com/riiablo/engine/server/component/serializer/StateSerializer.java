package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;

import com.riiablo.Riiablo;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
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
      return StateP.createStateP(builder, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0);
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
    int[] sourceEntityIds = new int[count];
    int[] skillIds = new int[count];
    int[] periodicDelayFrames = new int[count];
    int[] periodicCountdownFrames = new int[count];
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
      sourceEntityIds[i] = state.sourceEntityId;
      skillIds[i] = state.skillId;
      periodicDelayFrames[i] = state.periodicDelayFrames;
      periodicCountdownFrames[i] = state.periodicCountdownFrames;
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
    int sourceEntityIdOffset = StateP.createSourceEntityIdVector(builder, sourceEntityIds);
    int skillIdOffset = StateP.createSkillIdVector(builder, skillIds);
    int periodicDelayFramesOffset =
        StateP.createPeriodicDelayFramesVector(builder, periodicDelayFrames);
    int periodicCountdownFramesOffset =
        StateP.createPeriodicCountdownFramesVector(builder, periodicCountdownFrames);
    return StateP.createStateP(builder, stateIdOffset, durationOffset, levelOffset,
        velocityModifierOffset, runtimeValueOffset, animationRateModifierOffset,
        skillModifierOffset, maxLifeModifierOffset, maxManaModifierOffset,
        maxStaminaModifierOffset, sourceEntityIdOffset, skillIdOffset,
        periodicDelayFramesOffset, periodicCountdownFramesOffset);
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
        state.sourceEntityId = i < data.sourceEntityIdLength()
            ? data.sourceEntityId(i) : -1;
        state.skillId = i < data.skillIdLength() ? data.skillId(i) : -1;
        state.periodicDelayFrames = i < data.periodicDelayFramesLength()
            ? data.periodicDelayFrames(i) : 0;
        state.periodicCountdownFrames = i < data.periodicCountdownFramesLength()
            ? data.periodicCountdownFrames(i) : -1;
        // StateP predates generic native stat-list serialization. Rebuild
        // Holy Shield's data-driven block/defense payload from its replicated
        // skill level so remote clients run the same combat formulas.
        if (state.stateId == StateId.HOLYSHIELD && Riiablo.files != null
            && Riiablo.files.skills != null) {
          com.riiablo.codec.excel.Skills.Entry holyShield =
              Riiablo.files.skills.get(com.riiablo.engine.server.skill.SkillId.HOLY_SHIELD);
          int level = Math.max(1, state.level);
          int replicatedDefense = state.runtimeValue;
          state.clearModifiers();
          state.setStatContribution(Stat.toblock, 0,
              NativeStatResolver.Operation.ADD,
              PaladinSkills.getHolyShieldBlockBonus(holyShield, level));
          state.setNativeModifier(Stat.skill_armor_percent, replicatedDefense > 0
              ? replicatedDefense
              : PaladinSkills.getHolyShieldDefenseBonus(holyShield, level, name -> 0));
          state.runtimeValue = replicatedDefense > 0 ? replicatedDefense
              : PaladinSkills.getHolyShieldDefenseBonus(holyShield, level, name -> 0);
          state.needsSync = false;
        }
        if ((state.stateId == StateId.FROZENARMOR
            || state.stateId == StateId.SHIVERARMOR
            || state.stateId == StateId.CHILLINGARMOR)
            && Riiablo.files != null && Riiablo.files.skills != null) {
          int skillId = state.stateId == StateId.FROZENARMOR ? SkillId.FROZEN_ARMOR
              : state.stateId == StateId.SHIVERARMOR ? SkillId.SHIVER_ARMOR
              : SkillId.CHILLING_ARMOR;
          com.riiablo.codec.excel.Skills.Entry armor = Riiablo.files.skills.get(skillId);
          int defense = state.runtimeValue > 0 ? state.runtimeValue
              : SorceressSkills.getDefensiveArmorDefensePercent(
                  armor, Math.max(1, state.level));
          state.clearModifiers();
          state.setStatContribution(Stat.skill_armor_percent, 0,
              NativeStatResolver.Operation.ADD, defense);
          state.runtimeValue = defense;
          state.skillId = skillId;
          state.needsSync = false;
        }
        if (state.stateId == StateId.FIREMASTERY && Riiablo.files != null
            && Riiablo.files.skills != null) {
          com.riiablo.codec.excel.Skills.Entry mastery =
              Riiablo.files.skills.get(SkillId.FIRE_MASTERY);
          state.clearModifiers();
          state.setStatContribution(Stat.passive_fire_mastery, 0,
              NativeStatResolver.Operation.ADD,
              SorceressSkills.getFireMasteryPercent(mastery, Math.max(1, state.level)));
          state.skillId = SkillId.FIRE_MASTERY;
          state.needsSync = false;
        }
        if (state.stateId == StateId.ENCHANT && Riiablo.files != null
            && Riiablo.files.skills != null) {
          com.riiablo.codec.excel.Skills.Entry enchant =
              Riiablo.files.skills.get(SkillId.ENCHANT);
          int[] damage = SorceressSkills.getEnchantDamage(
              enchant, Math.max(1, state.level), name -> 0, 0);
          int replicatedMinimum = state.runtimeValue;
          state.clearModifiers();
          state.setStatContribution(Stat.firemindam, 0,
              NativeStatResolver.Operation.ADD,
              replicatedMinimum > 0 ? replicatedMinimum : damage[0]);
          state.setStatContribution(Stat.firemaxdam, 0,
              NativeStatResolver.Operation.ADD, damage[1]);
          state.setStatContribution(Stat.item_tohit_percent, 0,
              NativeStatResolver.Operation.ADD,
              SorceressSkills.getEnchantAttackRatingPercent(
                  enchant, Math.max(1, state.level)));
          state.skillId = SkillId.ENCHANT;
          state.runtimeValue = replicatedMinimum > 0 ? replicatedMinimum : damage[0];
          state.needsSync = false;
        }
      }
    }
    // StateP is an authoritative projection. A deserialized replica may
    // render the restored state but must never decrement clocks or emit DOT,
    // aura or periodic missiles locally.
    component.snapshotOnly = true;
    return component;
  }

  private static short clampShort(int value) {
    return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
  }
}
