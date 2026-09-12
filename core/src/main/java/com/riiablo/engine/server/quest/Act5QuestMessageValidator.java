package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** Validates the A5Q6 Tyrael3 terminal dialogue on the server boundary. */
public final class Act5QuestMessageValidator {
  private Act5QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int levelId,
      int messageIndex) {
    if (data == null || !data.isExpansion()) return false;
    if (!Act5BaalQuest.isTyrael3(npcType, null)) return false;
    short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
    if (act5 == null || act5.length <= Act5BaalQuest.RECORD) return false;
    return Act5BaalQuest.canTriggerLastPortal(
        act5[Act5BaalQuest.RECORD], levelId, messageIndex);
  }
}
