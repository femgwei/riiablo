package com.riiablo.engine.client;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.UnitState;

/** The receiver's explicit write port from committed values to mutable replicas. */
final class ClientUnitSnapshotWriter {
  private ClientUnitSnapshotWriter() {}

  static void states(int entityId, UnitStates replica,
      AuthoritativeUnitLifecycleState.Snapshot snapshot) {
    if (replica.stateList == null) replica.init(entityId);
    replica.snapshotOnly = true;
    int size = snapshot.states.size();
    int[] ids = new int[size], durations = new int[size], levels = new int[size];
    int[] sources = new int[size], skills = new int[size];
    for (int i = 0; i < size; i++) {
      AuthoritativeUnitLifecycleState.State state = snapshot.states.get(i);
      ids[i] = state.id; durations[i] = state.duration; levels[i] = state.level;
      sources[i] = state.sourceEntityId; skills[i] = state.skillId;
    }
    replica.stateList.replaceFromSnapshot(ids, durations, levels, sources, skills);
    for (AuthoritativeUnitLifecycleState.State source : snapshot.states) {
      UnitState target = replica.stateList.getStateLayer(source.id, source.sourceEntityId, source.skillId);
      if (target == null) continue;
      target.velocityModifier = source.velocityModifier;
      target.runtimeValue = source.runtimeValue;
      target.animationRateModifier = source.animationRateModifier;
      target.skillModifier = source.skillModifier;
      target.maxLifeModifier = source.maxLifeModifier;
      target.maxManaModifier = source.maxManaModifier;
      target.maxStaminaModifier = source.maxStaminaModifier;
      target.sourceEntityId = source.sourceEntityId;
      target.skillId = source.skillId;
      target.periodicDelayFrames = source.periodicDelayFrames;
      target.periodicCountdownFrames = source.periodicCountdownFrames;
    }
  }

  static void vitals(AttributesWrapper replica, AuthoritativeUnitLifecycleState.Vitals source) {
    if (replica == null || replica.attrs == null || source == null) return;
    Attributes attrs = replica.attrs;
    attrs.aggregate().put(Stat.maxhp, source.maxHitpoints);
    attrs.aggregate().put(Stat.hitpoints, source.hitpoints);
    attrs.aggregate().put(Stat.maxmana, source.maxMana);
    attrs.aggregate().put(Stat.mana, source.mana);
    attrs.aggregate().put(Stat.maxstamina, source.maxStamina);
    attrs.aggregate().put(Stat.stamina, source.stamina);
  }
}
