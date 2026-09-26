package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.component.SummonedPet;

import org.junit.jupiter.api.Test;

class ValkyrieFollowSystemTest {
  @Test
  void onlyActiveValkyrieUsesMercenaryFollowPath() {
    SummonedPet valkyrie = new SummonedPet();
    valkyrie.petType = "Valkyrie";
    assertTrue(MercenaryFollowSystem.isFollowableCompanion(null, valkyrie));

    SummonedPet decoy = new SummonedPet();
    decoy.petType = "decoy";
    assertFalse(MercenaryFollowSystem.isFollowableCompanion(null, decoy));

    valkyrie.passive = true;
    assertFalse(MercenaryFollowSystem.isFollowableCompanion(null, valkyrie));
  }
}
