package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** A3 uses the same item/state predicates for remote requests and local offers. */
public final class Act3QuestMessageValidator {
  private Act3QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int message) {
    if (data == null || !isAct3Npc(npcType)) return false;
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
