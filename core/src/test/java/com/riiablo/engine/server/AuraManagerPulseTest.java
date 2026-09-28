package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Focused pulse-transaction contracts extracted from D2MOO SkillPal.cpp. */
class AuraManagerPulseTest {
  @Test
  void redemptionConsumesManaOnlyAfterACorpseIsActuallyConsumed() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition definition = new AuraManager.AuraDefinition();
    definition.skillId = SkillId.REDEMPTION;
    definition.name = "Redemption fixture";
    definition.perDelayFrames = 25;
    definition.manaCostPerSecond = 1f;
    definition.baseRange = 16f;
    manager.registerAuraDefinition(definition);

    RedemptionCallback callback = new RedemptionCallback();
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, SkillId.REDEMPTION, 1));

    callback.redemptionSucceeds = false;
    manager.update(0f);
    assertEquals(10f, callback.mana, 0.001f,
        "an empty Redemption pulse must not consume mana");

    callback.redemptionSucceeds = true;
    for (int i = 0; i < 25; i++) manager.update(0f);
    assertEquals(9f, callback.mana, 0.001f,
        "a successful corpse pulse consumes exactly one native cost");
  }

  @Test
  void differentSkillsUsingOneStateShareOneNativeWinnerSlot() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition first = definition(9001, 500);
    AuraManager.AuraDefinition second = definition(9002, 500);
    manager.registerAuraDefinition(first);
    manager.registerAuraDefinition(second);

    RedemptionCallback callback = new RedemptionCallback();
    callback.rangeTarget = 9;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, first.skillId, 1));
    assertTrue(manager.activateAura(8, second.skillId, 1));
    manager.update(0f);

    assertEquals(1, callback.appliedStates,
        "D2MOO replaces a same-state stat-list instead of stacking skill IDs");
  }

  private static AuraManager.AuraDefinition definition(int skillId, int stateId) {
    AuraManager.AuraDefinition definition = new AuraManager.AuraDefinition();
    definition.skillId = skillId;
    definition.name = "same-state fixture";
    definition.perDelayFrames = 25;
    definition.baseRange = 16f;
    definition.affectsParty = true;
    definition.targetStateId = stateId;
    definition.stateId = stateId;
    definition.auraFilter = AuraManager.FILTER_PLAYER;
    definition.statIds[0] = Stat.damagepercent;
    definition.baseStatValues[0] = 10;
    return definition;
  }

  private static final class RedemptionCallback implements AuraManager.AuraCallback {
    float mana = 10f;
    boolean redemptionSucceeds;
    int rangeTarget = 7;
    int appliedStates;

    @Override public void onAuraActivated(int casterId, int skillId, int skillLevel) {}
    @Override public void onAuraDeactivated(int casterId, int skillId) {}
    @Override public void onEntityEnterAura(int entityId, int casterId, int skillId, int[] statValues) {}
    @Override public void onEntityLeaveAura(int entityId, int casterId, int skillId) {}
    @Override public float[] getEntityPosition(int entityId) { return new float[] {0f, 0f}; }
    @Override public Array<Integer> getEntitiesInRange(float x, float y, float range) {
      Array<Integer> result = new Array<>();
      result.add(rangeTarget);
      return result;
    }
    @Override public boolean isAlly(int entityId1, int entityId2) { return true; }
    @Override public int getBaseSkillLevel(int entityId, String skillName) { return 0; }
    @Override public boolean isValidTarget(
        int casterId, int targetId, int skillId, int auraFilter, boolean checkMonsterNoAura) {
      return true;
    }
    @Override public boolean isInTown(int entityId) { return false; }
    @Override public boolean canConsumeMana(int casterId, float amount) {
      return mana + 0.0001f >= amount;
    }
    @Override public boolean consumeMana(int casterId, float amount) {
      if (!canConsumeMana(casterId, amount)) return false;
      mana -= amount;
      return true;
    }
    @Override public void applyState(int targetId, int stateId, int duration,
        int sourceEntityId, int skillId, int skillLevel, int[] statIds, int[] statValues) {
      appliedStates++;
    }
    @Override public void removeState(int targetId, int stateId, int sourceEntityId, int skillId) {}
    @Override public boolean applyDirectStat(int targetId, int statId, int fixedValue,
        int sourceEntityId, int skillId) { return false; }
    @Override public void applyPeriodicDamage(int casterId, int targetId, int skillId,
        int skillLevel, int minimum, int maximum, String elementType) {}
    @Override public void updateHolyFreezeShatter(int casterId, int targetId, int skillId,
        int skillLevel, int duration) {}
    @Override public boolean applyRedemptionEffect(int casterId, int skillId,
        int skillLevel, float range) { return redemptionSucceeds; }
  }
}
