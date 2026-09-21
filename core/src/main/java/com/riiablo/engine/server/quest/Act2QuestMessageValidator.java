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
    // Duriel's Tyrael appears in the lair after the boss death.  Message 302
    // is the native portal offer; it is valid only once A2Q6 has reached the
    // post-Duriel state and remains replayable so a second client can receive
    // the same authoritative portal without changing progression again.
    if ((npcType == MonsterType.TYRAEL1 || npcType == 257)
      && messageIndex == Act2DurielQuest.MESSAGE_TYRAEL_PORTAL
        && act2.length > Act2DurielQuest.RECORD) {
      return Act2DurielQuest.canAcceptTyraelPortal(
          act2[Act2DurielQuest.RECORD]);
    }
    if (act2.length > Act2DurielQuest.RECORD
        && npcType == MonsterType.JERHYN
        && messageIndex == Act2DurielQuest.MESSAGE_JERHYN_END) {
      // Jerhyn consumes Tyrael's temporary LEFT_TOWN flag and advances the
      // player into the entered-area state.  Do not let an arbitrary dialog
      // packet manufacture that transition.
      return NativeQuestRecord.has(act2[Act2DurielQuest.RECORD],
          NativeQuestRecord.LEFT_TOWN);
    }
    if (act2.length > Act2DurielQuest.RECORD
        && npcType == MonsterType.MESHIF1
        && messageIndex == Act2DurielQuest.MESSAGE_MESHIF_TRAVEL) {
      // Meshif is the actual A2Q6 reward turn-in and is only valid after
      // Jerhyn has moved the player to ENTERED_AREA.
      return NativeQuestRecord.has(act2[Act2DurielQuest.RECORD],
          NativeQuestRecord.ENTERED_AREA);
    }
    return false;
  }
}
