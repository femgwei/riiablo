package com.riiablo.engine.server.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class MercenaryManagerTest {
  @Test
  void freeRogueCommitsOnlyAfterEntityCreation() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = 37;
    manager.setCallback(callback);

    assertTrue(manager.grantFreeRogue(7, 12));

    MercenaryManager.ActiveMercenary merc = manager.getPlayerMercenary(7);
    assertNotNull(merc);
    assertEquals(37, merc.entityId);
    assertEquals(7, merc.ownerId);
    assertEquals(MercenaryManager.MERC_TYPE_ROGUE, merc.definition.mercType);
    assertEquals(0, callback.deductCalls);
    assertEquals(1, callback.hiredCalls);
  }

  @Test
  void failedEntityCreationLeavesRewardRetryable() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = Engine.INVALID_ENTITY;
    manager.setCallback(callback);

    assertFalse(manager.grantFreeRogue(9, 8));
    assertFalse(manager.hasMercenary(9));
    assertEquals(0, callback.deductCalls);
    assertEquals(0, callback.hiredCalls);

    callback.entityId = 51;
    assertTrue(manager.grantFreeRogue(9, 8));
    assertEquals(51, manager.getPlayerMercenary(9).entityId);
  }

  @Test
  void missingEntityServiceCannotCreateLogicalMercenary() {
    MercenaryManager manager = new MercenaryManager();

    assertFalse(manager.grantFreeRogue(11, 5));
    assertFalse(manager.hasMercenary(11));
  }

  @Test
  void replenishesKashyaPoolForMultiplayerQuestRewards() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    manager.setCallback(callback);

    for (int playerId = 0; playerId < 12; playerId++) {
      callback.entityId = 100 + playerId;
      assertTrue(manager.grantFreeRogue(playerId, 10), "player " + playerId);
    }
    assertEquals(12, callback.hiredCalls);
    assertEquals(0, callback.deductCalls);
  }

  @Test
  void mirrorsNativeResurrectionCost() {
    assertEquals(7, MercenaryManager.nativeResurrectionCost(1));
    assertEquals(750, MercenaryManager.nativeResurrectionCost(10));
    assertEquals(50_000, MercenaryManager.nativeResurrectionCost(99));
  }

  @Test
  void resurrectsDeadMercenaryInPlaceAndChargesOnce() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = 73;
    callback.gold = 10_000;
    callback.resurrectResult = true;
    manager.setCallback(callback);
    assertTrue(manager.grantFreeRogue(5, 10));
    MercenaryManager.ActiveMercenary merc = manager.getPlayerMercenary(5);
    merc.level = 10;
    manager.onMercenaryDeath(5);

    assertTrue(manager.resurrectMercenary(5));
    assertEquals(73, merc.entityId);
    assertEquals(MercenaryManager.STATE_HIRED, merc.state);
    assertEquals(1, callback.resurrectCalls);
    assertEquals(1, callback.deductCalls);
    assertEquals(750, callback.lastDeductAmount);
  }

  @Test
  void failedEntityResurrectionDoesNotChargeOrChangeState() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = 81;
    callback.gold = 10_000;
    callback.resurrectResult = false;
    manager.setCallback(callback);
    assertTrue(manager.grantFreeRogue(6, 10));
    manager.onMercenaryDeath(6);

    assertFalse(manager.resurrectMercenary(6));
    assertEquals(MercenaryManager.STATE_DEAD,
        manager.getPlayerMercenary(6).state);
    assertEquals(0, callback.deductCalls);
  }

  @Test
  void restoresDeadSavedMercenaryWithoutChargingOrRewritingSave() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = 93;
    manager.setCallback(callback);

    assertTrue(manager.restoreMercenary(12, MercenaryManager.MERC_TYPE_ROGUE,
        17, 123_456L, 0x12345678, 13, true));

    MercenaryManager.ActiveMercenary merc = manager.getPlayerMercenary(12);
    assertNotNull(merc);
    assertEquals(93, merc.entityId);
    assertEquals(17, merc.level);
    assertEquals(123_456L, merc.experience);
    assertEquals(13, merc.nameId);
    assertEquals(MercenaryManager.STATE_DEAD, merc.state);
    assertEquals(0, merc.currentLife);
    assertEquals(0, callback.deductCalls);
    assertEquals(0, callback.hiredCalls);
  }

  @Test
  void logoutUnloadPreservesPersistentCallbacksAndAllowsReconnect() {
    MercenaryManager manager = new MercenaryManager();
    Callback callback = new Callback();
    callback.entityId = 101;
    manager.setCallback(callback);
    assertTrue(manager.restoreMercenary(14, MercenaryManager.MERC_TYPE_ROGUE,
        8, 12_000L, 99, 2, false));

    manager.unloadMercenary(14);

    assertFalse(manager.hasMercenary(14));
    assertEquals(1, callback.removeCalls);
    assertEquals(0, callback.dismissedCalls);
    callback.entityId = 102;
    assertTrue(manager.restoreMercenary(14, MercenaryManager.MERC_TYPE_ROGUE,
        8, 12_000L, 99, 2, false));
    assertEquals(102, manager.getPlayerMercenary(14).entityId);
  }

  private static class Callback implements MercenaryManager.MercenaryCallback {
    int entityId;
    int deductCalls;
    int lastDeductAmount;
    int hiredCalls;
    int gold;
    int resurrectCalls;
    int removeCalls;
    int dismissedCalls;
    boolean resurrectResult;

    @Override
    public int createMercenaryEntity(int playerId, MercenaryManager.MercenaryDefinition def,
        int level, int seed, int nameId) {
      return entityId;
    }

    @Override
    public void onMercenaryHired(int playerId, MercenaryManager.ActiveMercenary merc) {
      hiredCalls++;
    }

    @Override
    public boolean deductPlayerGold(int playerId, int amount) {
      deductCalls++;
      lastDeductAmount = amount;
      gold -= amount;
      return true;
    }

    @Override public void onMercenaryDismissed(int playerId, MercenaryManager.ActiveMercenary merc) {
      dismissedCalls++;
    }
    @Override public void onMercenaryDeath(int playerId, MercenaryManager.ActiveMercenary merc) {}
    @Override public void onMercenaryResurrected(int playerId, MercenaryManager.ActiveMercenary merc) {}
    @Override public void onMercenaryLevelUp(int playerId, MercenaryManager.ActiveMercenary merc,
        int oldLevel, int newLevel) {}
    @Override public void removeMercenaryEntity(int entityId) { removeCalls++; }
    @Override public boolean resurrectMercenaryEntity(int entityId, int playerId) {
      resurrectCalls++;
      return resurrectResult;
    }
    @Override public int getPlayerGold(int playerId) { return gold; }
    @Override public int getPlayerLevel(int playerId) { return 1; }
    @Override public int getDifficulty() { return 0; }
  }
}
