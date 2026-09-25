package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.RiiabloTest;

class NpcHealerTest extends RiiabloTest {
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

  @Test
  void healerRestoresLivingMercenaryResources() {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.maxmana, 80f);
    attrs.base().put(Stat.maxstamina, 60f);
    attrs.base().put(Stat.hitpoints, 35f);
    attrs.base().put(Stat.mana, 10f);
    attrs.base().put(Stat.stamina, 5f);
    attrs.aggregate().put(Stat.maxhp, 100f);
    attrs.aggregate().put(Stat.maxmana, 80f);
    attrs.aggregate().put(Stat.maxstamina, 60f);
    attrs.aggregate().put(Stat.hitpoints, 35f);
    attrs.aggregate().put(Stat.mana, 10f);
    attrs.aggregate().put(Stat.stamina, 5f);

    assertTrue(Npc.restoreLivingMercenary(attrs));
    assertEquals(100f, attrs.aggregate().getValue(Stat.hitpoints, 0f));
    assertEquals(80f, attrs.aggregate().getValue(Stat.mana, 0f));
    assertEquals(60f, attrs.aggregate().getValue(Stat.stamina, 0f));
  }

  @Test
  void healerDoesNotReviveDeadMercenary() {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.hitpoints, 0f);
    attrs.aggregate().put(Stat.maxhp, 100f);
    attrs.aggregate().put(Stat.hitpoints, 0f);

    assertFalse(Npc.restoreLivingMercenary(attrs));
    assertEquals(0f, attrs.aggregate().getValue(Stat.hitpoints, 0f));
  }
}
