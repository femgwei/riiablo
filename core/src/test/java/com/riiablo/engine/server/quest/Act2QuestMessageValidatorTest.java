package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class Act2QuestMessageValidatorTest {
  @Test
  void acceptsSummonerRewardOnlyForNativeNpcAndPendingRecord() {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, "A2Q5Validation", Riiablo.AMAZON);
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 419));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.SUMMONER, data, 419));

    data.getQuests(Riiablo.ACT2)[Act2SummonerQuest.RECORD] =
        Act2SummonerQuest.completeObjective((short) 0);
    assertTrue(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 419));
    assertTrue(Act2QuestMessageValidator.isAllowed(MonsterType.DECKARDCAIN_ACT2, data, 429));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 419));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 418));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 430));
  }

  @Test
  void acceptsDurielTyraelPortalMessageOutsideByteRange() {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, "A2Q6Validation", Riiablo.AMAZON);
    data.getQuests(Riiablo.ACT2)[Act2DurielQuest.RECORD] =
        Act2DurielQuest.markDurielKilled((short) 0);
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.TYRAEL1, data, Act2DurielQuest.MESSAGE_TYRAEL_PORTAL));
    assertFalse(Act2QuestMessageValidator.isAllowed(
        MonsterType.TYRAEL1, data, Act2DurielQuest.MESSAGE_TYRAEL_PORTAL - 1));
  }

  @Test
  void acceptsJerhynAndMeshifOnlyAtTheirNativeA2Q6States() {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, "A2Q6NpcValidation", Riiablo.AMAZON);
    assertFalse(Act2QuestMessageValidator.isAllowed(
        MonsterType.JERHYN, data, Act2DurielQuest.MESSAGE_JERHYN_END));
    assertFalse(Act2QuestMessageValidator.isAllowed(
        MonsterType.MESHIF1, data, Act2DurielQuest.MESSAGE_MESHIF_TRAVEL));

    short killed = Act2DurielQuest.markDurielKilled((short) 0);
    short tyrael = Act2DurielQuest.acceptTyraelPortal(killed);
    data.getQuests(Riiablo.ACT2)[Act2DurielQuest.RECORD] = tyrael;
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.JERHYN, data, Act2DurielQuest.MESSAGE_JERHYN_END));
    assertFalse(Act2QuestMessageValidator.isAllowed(
        MonsterType.MESHIF1, data, Act2DurielQuest.MESSAGE_MESHIF_TRAVEL));

    data.getQuests(Riiablo.ACT2)[Act2DurielQuest.RECORD] =
        Act2DurielQuest.acknowledgeJerhyn(tyrael);
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.MESHIF1, data, Act2DurielQuest.MESSAGE_MESHIF_TRAVEL));

    data.getQuests(Riiablo.ACT2)[Act2DurielQuest.RECORD] =
        Act2DurielQuest.travelWithMeshif(
            data.getQuests(Riiablo.ACT2)[Act2DurielQuest.RECORD]);
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.JERHYN, data, Act2DurielQuest.MESSAGE_JERHYN_END));
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.MESHIF1, data, Act2DurielQuest.MESSAGE_MESHIF_TRAVEL));
  }
}
