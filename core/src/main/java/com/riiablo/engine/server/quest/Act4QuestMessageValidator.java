package com.riiablo.engine.server.quest;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;

/** Validates the native Act IV Cain4 dialogue at the network boundary. */
public final class Act4QuestMessageValidator {
  private Act4QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int messageIndex) {
    if (data == null || npcType != MonsterType.CAIN4) return false;
    short[] act4 = data.getQuests(Riiablo.ACT4);
    if (act4 == null || act4.length <= Act4HellforgeQuest.RECORD) return false;
    short record = act4[Act4HellforgeQuest.RECORD];
    if (messageIndex == Act4HellforgeQuest.MESSAGE_CAIN_REWARD) {
      return Act4HellforgeQuest.canClaimReward(record);
    }
    if (messageIndex == Act4HellforgeQuest.MESSAGE_CAIN_INIT_HAS_STONE) {
      return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
          && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
          && data.getItems() != null
          && data.getItems().containsItemCode(Act4HellforgeQuest.SOULSTONE);
    }
    if (messageIndex == Act4HellforgeQuest.MESSAGE_CAIN_INIT_NO_STONE) {
      return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
          && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
    }
    return false;
  }
}
