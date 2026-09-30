package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  void paidAuraWithNoValidRangeTargetKeepsSelectionAndDefersManaUntilUsefulPulse() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition paid = definition(9012, 501);
    paid.manaCostPerSecond = 1f;
    manager.registerAuraDefinition(paid);

    RedemptionCallback callback = new RedemptionCallback();
    callback.rangeTarget = 9;
    callback.validTarget = false;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, paid.skillId, 1));

    manager.update(0f);
    assertEquals(10f, callback.mana, 0.001f,
        "an empty/invalid target scan must not consume the paid aura pulse");
    assertEquals(0, callback.appliedStates);
    assertTrue(manager.hasActiveAura(7),
        "a no-target pulse must not cancel the selected aura");

    callback.validTarget = true;
    for (int i = 0; i < paid.perDelayFrames; i++) manager.update(0f);
    assertEquals(9f, callback.mana, 0.001f,
        "the first useful pulse consumes exactly one native mana cost");
    assertEquals(1, callback.appliedStates);
    assertTrue(manager.hasActiveAura(7));
  }

  @Test
  void damageAuraSelfLayerDoesNotCountAsUsefulWithoutAValidDamageTarget() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition damage = definition(9013, 502);
    damage.auraType = AuraManager.AURA_TYPE_DAMAGE;
    damage.affectsSelf = true;
    damage.selfStateId = 502;
    damage.manaCostPerSecond = 1f;
    manager.registerAuraDefinition(damage);

    RedemptionCallback callback = new RedemptionCallback();
    callback.includeCaster = true;
    callback.rangeTarget = 9;
    callback.validTarget = false;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, damage.skillId, 1));
    manager.update(0f);

    assertEquals(10f, callback.mana, 0.001f,
        "SrvDo066 self/passive stats do not replace a missing hostile damage pulse");
    assertTrue(manager.hasActiveAura(7));

    callback.validTarget = true;
    for (int i = 0; i < damage.perDelayFrames; i++) manager.update(0f);
    assertEquals(9f, callback.mana, 0.001f,
        "the first valid damage target makes exactly one pulse useful");
    assertTrue(manager.hasActiveAura(7));
  }

  @Test
  void unfundedStrongerSameStateAuraKeepsItsWinnerRelation() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition weak = definition(9014, 503);
    AuraManager.AuraDefinition strong = definition(9015, 503);
    manager.registerAuraDefinition(weak);
    manager.registerAuraDefinition(strong);

    RedemptionCallback callback = new RedemptionCallback();
    callback.rangeTarget = 9;
    callback.unfundedCaster = 8;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, weak.skillId, 1));
    assertTrue(manager.activateAura(8, strong.skillId, 5));
    manager.update(0f);

    assertEquals(8, callback.lastSourceEntityId,
        "mana failure must not make the native winner fall back to a weaker source");
    assertEquals(strong.skillId, callback.lastSkillId);
    assertEquals(1, manager.getEntityAuraEffects(9).size);
    assertEquals(strong.skillId, manager.getEntityAuraEffects(9).first().skillId);
    assertTrue(manager.hasActiveAura(8));

    callback.unfundedCaster = -1;
    for (int i = 0; i < strong.perDelayFrames; i++) manager.update(0f);
    assertEquals(8, callback.lastSourceEntityId,
        "the same source remains selected once its next pulse is funded");
    assertEquals(strong.skillId, manager.getEntityAuraEffects(9).first().skillId);
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

  @Test
  void differentSkillsUsingOneStateReplaceAndRestoreWithoutLeavingHiddenEffects() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition first = definition(9001, 500);
    AuraManager.AuraDefinition second = definition(9002, 500);
    manager.registerAuraDefinition(first);
    manager.registerAuraDefinition(second);

    RedemptionCallback callback = new RedemptionCallback();
    callback.rangeTarget = 9;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, first.skillId, 1));
    assertTrue(manager.activateAura(8, second.skillId, 2),
        "the higher-level second skill should win the shared state slot");
    manager.update(0f);

    assertEquals(1, callback.appliedStates,
        "a shared state must publish one native layer, even when skills differ");
    assertEquals(8, callback.lastSourceEntityId);
    assertEquals(second.skillId, callback.lastSkillId);
    assertEquals(1, manager.getEntityAuraEffects(9).size);
    assertEquals(second.skillId, manager.getEntityAuraEffects(9).first().skillId);

    manager.deactivateAura(8);
    assertTrue(manager.getEntityAuraEffects(9) == null,
        "removing the winning source must not leave a public effect for its old skill");

    // The native layer is allowed to expire for perdelay+1 frames. The weaker
    // source becomes visible again on its next pulse, rather than stacking
    // underneath the old skill or depending on activation order.
    for (int i = 0; i < first.perDelayFrames; i++) manager.update(0f);
    assertEquals(1, manager.getEntityAuraEffects(9).size);
    assertEquals(first.skillId, manager.getEntityAuraEffects(9).first().skillId);
    assertEquals(7, callback.lastSourceEntityId);

    // Switching skills on one caster follows the same source replacement path.
    assertTrue(manager.activateAura(7, second.skillId, 1));
    assertTrue(manager.getEntityAuraEffects(9) == null,
        "switching a caster's selected skill must clear the old skill layer");
    manager.update(0f);
    assertEquals(1, manager.getEntityAuraEffects(9).size);
    assertEquals(second.skillId, manager.getEntityAuraEffects(9).first().skillId);

    manager.deactivateAura(7);
    assertTrue(manager.getEntityAuraEffects(9) == null,
        "deactivating the final source must leave no hidden same-state effect");
  }

  @Test
  void cleansingPulseAlsoProcessesTheCaster() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition cleansing = new AuraManager.AuraDefinition();
    cleansing.skillId = SkillId.CLEANSING;
    cleansing.name = "Cleansing fixture";
    cleansing.perDelayFrames = 25;
    cleansing.baseRange = 16f;
    cleansing.selfStateId = 9001;
    cleansing.targetStateId = 9001;
    cleansing.stateId = 9001;
    cleansing.statIds[0] = Stat.damagepercent;
    cleansing.baseStatValues[0] = 50;
    cleansing.affectsSelf = true;
    cleansing.affectsParty = true;
    cleansing.auraFilter = AuraManager.FILTER_PLAYER | AuraManager.FILTER_FIND_ALLY;
    manager.registerAuraDefinition(cleansing);

    RedemptionCallback callback = new RedemptionCallback();
    callback.includeCaster = true;
    callback.rangeTarget = 9;
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, SkillId.CLEANSING, 1));
    manager.update(0f);

    assertEquals(2, callback.cleansingTargets,
        "Cleansing must shorten the caster and the allied range target");
    assertTrue(callback.cleansingCasterSeen,
        "the caster must receive the native Cleansing pulse");
  }

  @Test
  void paidAuraPublishesZeroStatsWhenUnfundedAndSuppressesManaRegenOnlyWhenUseful() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition paid = definition(9010, 9011);
    paid.affectsSelf = true;
    paid.affectsParty = false;
    paid.selfStateId = 9011;
    paid.targetStateId = -1;
    paid.statIds[0] = Stat.damagepercent;
    paid.baseStatValues[0] = 25;
    paid.manaCostPerSecond = 1f;
    manager.registerAuraDefinition(paid);

    PaidCallback callback = new PaidCallback();
    manager.setCallback(callback);
    assertTrue(manager.activateAura(7, paid.skillId, 1));

    callback.mana = 0f;
    manager.update(0f);
    assertEquals(0, callback.lastValue,
        "an unfunded native pulse keeps the state but carries no aura stat");
    assertFalse(callback.suppressed);

    callback.mana = 10f;
    for (int i = 0; i < paid.perDelayFrames; i++) manager.update(0f);
    assertEquals(25, callback.lastValue);
    assertEquals(9f, callback.mana, 0.001f);
    assertTrue(callback.suppressed,
        "a useful paid pulse must hold STATE_NOMANAREGEN for its short layer");
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
    int lastSourceEntityId;
    int lastSkillId;
    int lastStateId;
    boolean validTarget = true;
    int unfundedCaster = -1;
    boolean includeCaster;
    int cleansingTargets;
    boolean cleansingCasterSeen;

    @Override public void onAuraActivated(int casterId, int skillId, int skillLevel) {}
    @Override public void onAuraDeactivated(int casterId, int skillId) {}
    @Override public void onEntityEnterAura(int entityId, int casterId, int skillId, int[] statValues) {}
    @Override public void onEntityLeaveAura(int entityId, int casterId, int skillId) {}
    @Override public float[] getEntityPosition(int entityId) { return new float[] {0f, 0f}; }
    @Override public Array<Integer> getEntitiesInRange(float x, float y, float range) {
      Array<Integer> result = new Array<>();
      if (includeCaster) result.add(7);
      result.add(rangeTarget);
      return result;
    }
    @Override public boolean isAlly(int entityId1, int entityId2) { return true; }
    @Override public int getBaseSkillLevel(int entityId, String skillName) { return 0; }
    @Override public boolean isValidTarget(
        int casterId, int targetId, int skillId, int auraFilter, boolean checkMonsterNoAura) {
      return validTarget;
    }
    @Override public boolean isInTown(int entityId) { return false; }
    @Override public boolean canConsumeMana(int casterId, float amount) {
      return casterId != unfundedCaster && mana + 0.0001f >= amount;
    }
    @Override public boolean consumeMana(int casterId, float amount) {
      if (!canConsumeMana(casterId, amount)) return false;
      mana -= amount;
      return true;
    }
    @Override public void applyCleansingEffect(int targetId, int percent,
        int sourceEntityId, int skillId) {
      cleansingTargets++;
      cleansingCasterSeen |= targetId == sourceEntityId;
    }
    @Override public void applyState(int targetId, int stateId, int duration,
        int sourceEntityId, int skillId, int skillLevel, int[] statIds, int[] statValues) {
      appliedStates++;
      lastSourceEntityId = sourceEntityId;
      lastSkillId = skillId;
      lastStateId = stateId;
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

  private static final class PaidCallback implements AuraManager.AuraCallback {
    float mana;
    int lastValue;
    boolean suppressed;

    @Override public void onAuraActivated(int casterId, int skillId, int skillLevel) {}
    @Override public void onAuraDeactivated(int casterId, int skillId) {}
    @Override public void onEntityEnterAura(int entityId, int casterId, int skillId,
        int[] statValues) {}
    @Override public void onEntityLeaveAura(int entityId, int casterId, int skillId) {}
    @Override public float[] getEntityPosition(int entityId) { return new float[] {0f, 0f}; }
    @Override public Array<Integer> getEntitiesInRange(float x, float y, float range) {
      Array<Integer> result = new Array<>();
      result.add(7);
      return result;
    }
    @Override public boolean isAlly(int entityId1, int entityId2) { return true; }
    @Override public int getBaseSkillLevel(int entityId, String skillName) { return 0; }
    @Override public boolean isValidTarget(int casterId, int targetId, int skillId,
        int auraFilter, boolean checkMonsterNoAura) { return true; }
    @Override public boolean isInTown(int entityId) { return false; }
    @Override public boolean canConsumeMana(int casterId, float amount) {
      return mana >= amount;
    }
    @Override public boolean consumeMana(int casterId, float amount) {
      if (!canConsumeMana(casterId, amount)) return false;
      mana -= amount;
      return true;
    }
    @Override public void setManaRegenSuppression(int casterId, boolean value, int duration,
        int sourceEntityId, int skillId) { suppressed = value; }
    @Override public void applyState(int targetId, int stateId, int duration,
        int sourceEntityId, int skillId, int skillLevel, int[] statIds, int[] statValues) {
      lastValue = statValues != null && statValues.length > 0 ? statValues[0] : 0;
    }
    @Override public void removeState(int targetId, int stateId, int sourceEntityId, int skillId) {}
    @Override public boolean applyDirectStat(int targetId, int statId, int fixedValue,
        int sourceEntityId, int skillId) { return false; }
    @Override public void applyPeriodicDamage(int casterId, int targetId, int skillId,
        int skillLevel, int minimum, int maximum, String elementType) {}
    @Override public void updateHolyFreezeShatter(int casterId, int targetId, int skillId,
        int skillLevel, int duration) {}
  }
}
