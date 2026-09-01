package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import org.junit.jupiter.api.Test;

class MonsterRewardStateTest {
  @Test
  void claimsEachNativeRewardChannelExactlyOnce() {
    MonsterRewardState state = new MonsterRewardState().reset();

    assertTrue(state.claimExperience());
    assertTrue(state.claimTreasureClass());
    assertFalse(state.claimExperience());
    assertFalse(state.claimTreasureClass());
    assertEquals(MonsterRewardState.CLAIM_EXPERIENCE
        | MonsterRewardState.CLAIM_TREASURE_CLASS, state.flags());
  }

  @Test
  void nativeResurrectionSuppressesExperienceAndTreasureClass() {
    MonsterRewardState state = new MonsterRewardState().reset().markNativeResurrection();

    assertFalse(state.claimExperience());
    assertFalse(state.claimTreasureClass());
    assertTrue(state.noExperience());
    assertTrue(state.noTreasureClass());
    assertEquals(MonsterRewardState.NO_EXPERIENCE
        | MonsterRewardState.NO_TREASURE_CLASS, state.flags());
  }

  @Test
  void entityDeletionRemovesClaimsBeforeNumericIdCanBeReused() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      ComponentMapper<MonsterRewardState> states =
          world.getMapper(MonsterRewardState.class);
      int entityId = world.create();
      states.create(entityId).reset().claimExperience();
      assertTrue(states.has(entityId));

      world.delete(entityId);
      world.process();

      assertFalse(states.has(entityId));
    } finally {
      world.dispose();
    }
  }
}
