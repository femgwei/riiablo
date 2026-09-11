package com.riiablo.engine.server.quest;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;

/** Validates native Act II quest dialogue submitted by a network client. */
public final class Act2QuestMessageValidator {
  private Act2QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int messageIndex) {
    if (data == null || messageIndex < 0) return false;
    short[] act2 = data.getQuests(Riiablo.ACT2);
    if (act2 == null || act2.length <= Act2RadamentQuest.RECORD) return false;
    // A2Q5's reward chain is shared by several Act II NPCs, including Atma
    // and Cain2, so it must be checked before their quest-specific branches.
    if (act2.length > Act2SummonerQuest.RECORD
        && Act2SummonerQuest.isRewardNpc(npcType)
        && Act2SummonerQuest.isRewardMessage(messageIndex)) {
      return Act2SummonerQuest.isRewardMessage(messageIndex)
          && NativeQuestRecord.has(act2[Act2SummonerQuest.RECORD],
              NativeQuestRecord.REWARD_PENDING);
    }
    if (npcType == MonsterType.ATMA) {
      return messageIndex == Act2RadamentQuest.selectAtmaMessage(
          act2[Act2RadamentQuest.RECORD]);
    }
    if (npcType == MonsterType.DECKARDCAIN_ACT2
        && act2.length > Act2HoradricStaffQuest.RECORD) {
      return Act2HoradricStaffQuest.isCainMessageAllowed(messageIndex,
          act2[Act2HoradricStaffQuest.RECORD], data.getItems());
    }
    return false;
  }
}
