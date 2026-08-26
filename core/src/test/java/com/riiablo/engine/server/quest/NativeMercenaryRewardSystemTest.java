package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.Wire;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.pet.MercenaryManager;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class NativeMercenaryRewardSystemTest {
  @Test
  void acknowledgesQuestOnlyAfterMercenaryEntityExists() {
    StubRewardSystem rewards = new StubRewardSystem(73);
    RewardProbe probe = new RewardProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), rewards, probe)
        .build());
    try {
      world.getSystem(EventSystem.class).dispatch(NativeQuestRewardEvent.available(4,
          QuestId.A1Q2_BLOOD_RAVEN, NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE));

      assertEquals(1, probe.granted);
      assertTrue(rewards.mercenaries().hasMercenary(4));
      assertEquals(73, rewards.mercenaries().getPlayerMercenary(4).entityId);
    } finally {
      world.dispose();
    }
  }

  @Test
  void creationFailureDoesNotAcknowledgeQuest() {
    StubRewardSystem rewards = new StubRewardSystem(Engine.INVALID_ENTITY);
    RewardProbe probe = new RewardProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), rewards, probe)
        .build());
    try {
      world.getSystem(EventSystem.class).dispatch(NativeQuestRewardEvent.available(6,
          QuestId.A1Q2_BLOOD_RAVEN, NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE));

      assertEquals(0, probe.granted);
      assertFalse(rewards.mercenaries().hasMercenary(6));
    } finally {
      world.dispose();
    }
  }

  @Wire(failOnNull = false, injectInherited = true)
  private static class StubRewardSystem extends NativeMercenaryRewardSystem {
    private final int entityId;

    StubRewardSystem(int entityId) {
      this.entityId = entityId;
    }

    @Override
    @Subscribe
    public void onNativeQuestReward(NativeQuestRewardEvent reward) {
      super.onNativeQuestReward(reward);
    }

    @Override
    public int createMercenaryEntity(int playerId, MercenaryManager.MercenaryDefinition def,
        int level, int seed, int nameId) {
      return entityId;
    }
  }

  private static class RewardProbe extends PassiveSystem {
    int granted;

    @Subscribe
    public void onReward(NativeQuestRewardEvent reward) {
      if (reward.phase == NativeQuestRewardEvent.GRANTED) granted++;
    }
  }
}
