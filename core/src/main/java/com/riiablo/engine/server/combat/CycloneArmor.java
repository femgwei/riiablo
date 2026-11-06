package com.riiablo.engine.server.combat;

import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;

/** Host adapter for EventFunc25's state-owned 8.8 pool; Bone Armor stays independent. */
final class CycloneArmor {
  private CycloneArmor() {}

  static int capacity(StateList states) {
    UnitState armor = states == null ? null : states.getState(StateId.CYCLONEARMOR);
    return armor == null || armor.getStatContributionValue(Stat.bonearmormax) <= 0
        ? 0 : Math.max(0, armor.getStatContributionValue(Stat.bonearmor));
  }

  static void consume(StateList states, int consumedFixed) {
    if (states == null || consumedFixed <= 0) return;
    UnitState armor = states.getState(StateId.CYCLONEARMOR);
    if (armor == null) return;
    int next = Math.max(0, capacity(states) - consumedFixed);
    if (next == 0) {
      states.removeStateLayer(StateId.CYCLONEARMOR, armor.sourceEntityId, armor.skillId);
    } else {
      armor.runtimeValue = next;
      armor.setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, next);
      armor.needsSync = true;
    }
  }
}
