package com.riiablo.engine.server.quest;

import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;

/**
 * Validates the small set of Act I NPC message ids which can be submitted by
 * a network client.  The client still selects and renders the dialogue, but
 * the server derives the same branch from the authoritative D2S records so a
 * stale or forged message cannot advance a quest.
 */
public final class Act1QuestMessageValidator {
  private Act1QuestMessageValidator() {}

  /** Returns {@code true} when the message is valid for the current player state. */
  public static boolean isAllowed(int npcType, CharData data, int level,
      boolean hasMalus, int messageIndex) {
    if (data == null || messageIndex < 0) return false;
    short[] act1 = data.getQuests(com.riiablo.Riiablo.ACT1);
    if (act1 == null || act1.length <= Act1AndarielQuest.RECORD) return false;
    switch (npcType) {
      case MonsterType.AKARA:
        return akaraMessage(data, act1, messageIndex);
      case MonsterType.CHARSI:
        return messageIndex == Act1MalusQuest.selectCharsiMessage(
            act1[Act1MalusQuest.RECORD], level, hasMalus);
      case MonsterType.KASHYA:
        return messageIndex == Act1BloodRavenQuest.selectKashyaMessage(
            act1[Act1BloodRavenQuest.RECORD]);
      case MonsterType.WARRIV:
        return NativeQuestRecord.has(act1[Act1AndarielQuest.RECORD],
            NativeQuestRecord.REWARD_PENDING)
            && messageIndex == Act1AndarielQuest.MESSAGE_WARRIV_REWARD;
      case MonsterType.DECKARDCAIN:
      case MonsterType.DECKARDCAIN_TOWN:
        return messageIndex == Act1CainQuest.MESSAGE_CAIN_TOWN;
      default:
        return false;
    }
  }

  private static boolean akaraMessage(CharData data, short[] act1, int messageIndex) {
    if (messageIndex == Act1CainQuest.MESSAGE_DECIPHER_SCROLL) {
      return Act1CainQuest.canDecipherScroll(act1[Act1CainQuest.RECORD],
          data.getItems() != null
              && data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE),
          data.getItems() != null
              && data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE));
    }
    short den = act1[Act1DenOfEvilQuest.RECORD];
    if (!isQuestComplete(den)) {
      return messageIndex == Act1DenOfEvilQuest.selectAkaraMessage(den);
    }
    short cain = act1[Act1CainQuest.RECORD];
    int expected = NativeQuestRecord.has(cain, NativeQuestRecord.REWARD_PENDING)
        ? Act1CainQuest.MESSAGE_REWARD
        : NativeQuestRecord.has(cain, NativeQuestRecord.STARTED)
            ? Act1CainQuest.MESSAGE_EARLY : Act1CainQuest.MESSAGE_INIT;
    return messageIndex == expected;
  }

  private static boolean isQuestComplete(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE)
        || (NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
            && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }
}
