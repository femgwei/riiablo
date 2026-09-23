package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;

class NpcHealerTest {
  @Test
  void everyActTownHealerIsRegistered() {
    assertTrue(Npc.isHealer(148)); // Akara
    assertTrue(Npc.isHealer(178)); // Fara
    assertTrue(Npc.isHealer(255)); // Ormus
    assertTrue(Npc.isHealer(406)); // Jamella
    assertTrue(Npc.isHealer(513)); // Malah
    assertFalse(Npc.isHealer(154)); // Charsi
  }

  @Test
  void healerCleansesHarmfulStatesButPreservesBenefits() {
    StateList states = new StateList(7);
    states.addState(StateId.POISON, 20);
    states.addState(StateId.AMPLIFYDAMAGE, 20);
    states.addState(StateId.MIGHT, 20);

    assertTrue(states.removeNegativeEffects(null) >= 2);
    assertFalse(states.hasState(StateId.POISON));
    assertFalse(states.hasState(StateId.AMPLIFYDAMAGE));
    assertTrue(states.hasState(StateId.MIGHT));
  }
}
