package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** A3 uses the same item/state predicates for remote requests and local offers. */
public final class Act3QuestMessageValidator {
  private Act3QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int message) {
    if (data == null || !isAct3Npc(npcType)) return false;
    short[] act3 = data.getQuests(com.riiablo.Riiablo.ACT3);
    if (npcType == com.riiablo.engine.server.monster.MonsterType.HRATLI
        && act3 != null && act3.length > Act3HratliIntroQuest.RECORD
        && (message == Act3HratliIntroQuest.MESSAGE
            || message == Act3HratliIntroQuest.MESSAGE_SORCERESS)) {
      return Act3HratliIntroQuest.isAvailable(act3[Act3HratliIntroQuest.RECORD]);
    }
    QuestDialogue.Offer offer = QuestDialogue.select(npcType, new QuestPlayerView(data), -1);
    return offer != null && message == offer.message;
  }

  private static boolean isAct3Npc(int npc) {
    return Act3GoldenBirdQuest.isCain(npc) || Act3GoldenBirdQuest.isMeshif(npc)
        || Act3GoldenBirdQuest.isAlkor(npc)
        || npc == com.riiablo.engine.server.monster.MonsterType.HRATLI
        || npc == com.riiablo.engine.server.monster.MonsterType.ORMUS
        || npc == com.riiablo.engine.server.monster.MonsterType.ASHEARA;
  }
}
