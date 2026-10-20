package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** Validates supported A5 town quest branches and the Tyrael3 terminal dialogue. */
public final class Act5QuestMessageValidator {
  private Act5QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int levelId,
      int messageIndex) {
    return isAllowed(com.riiablo.Riiablo.files, npcType, data, levelId, messageIndex);
  }

  public static boolean isAllowed(com.riiablo.Files files, int npcType, CharData data, int levelId,
      int messageIndex) {
    if (data == null || !data.isExpansion()) return false;
    // A5Q4 Drehya messages are native quest dialogue as well.  Resolve the
    // MonStats row by hcIdx so the validator works with both the canonical
    // 1.10f table and projects that remap monster ordinals at load time.
    com.riiablo.codec.excel.MonStats.Entry npc = null;
    if (files != null && files.monstats != null) {
      npc = files.monstats.get(npcType);
    }
    int resolvedType = npc == null ? npcType : QuestPlayerView.npcType(npc);
    if (levelId == com.d2moo.common.drlg.D2LevelIds.LEVEL_HARROGATH
        && (resolvedType == com.riiablo.engine.server.monster.MonsterType.LARZUK
            || resolvedType == com.riiablo.engine.server.monster.MonsterType.QUAL_KEHK
            || resolvedType == com.riiablo.engine.server.monster.MonsterType.MALAH)) {
      QuestDialogue.Offer offer = QuestDialogue.select(resolvedType, new QuestPlayerView(data), levelId);
      return offer != null && messageIndex == offer.message;
    }
    boolean drehya = npc != null
        && ("Drehya".equalsIgnoreCase(npc.Id)
            || "Drehya".equalsIgnoreCase(npc.NameStr)
            || (npc.NameStr != null && npc.NameStr.toLowerCase().contains("drehya")));
    if (drehya || resolvedType == com.riiablo.engine.server.monster.MonsterType.DREHYA) {
      if (levelId != Act5NihlathakQuest.NIHLATHAK_TEMPLE
          && levelId != com.d2moo.common.drlg.D2LevelIds.LEVEL_HARROGATH) return false;
      short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
      if (act5 == null || act5.length <= Act5NihlathakQuest.RECORD
          || act5.length <= Act5PrisonQuest.RECORD) return false;
      short prison = act5[Act5PrisonQuest.RECORD];
      boolean prisonDone = Act5PrisonQuest.isFinished(prison)
          || NativeQuestRecord.has(prison, NativeQuestRecord.REWARD_PENDING);
      short nihlathak = act5[Act5NihlathakQuest.RECORD];
      if (messageIndex == Act5NihlathakQuest.MESSAGE_DREHYA_START) {
        return prisonDone && !Act5NihlathakQuest.isFinished(nihlathak)
            && !NativeQuestRecord.has(nihlathak, NativeQuestRecord.REWARD_PENDING);
      }
      if (messageIndex == Act5NihlathakQuest.MESSAGE_DREHYA_REWARD) {
        return Act5NihlathakQuest.canClaimReward(nihlathak);
      }
      return false;
    }
    if (!Act5BaalQuest.isTyrael3(resolvedType, npc == null ? null : npc.Id)) return false;
    short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
    if (act5 == null || act5.length <= Act5BaalQuest.RECORD) return false;
    return Act5BaalQuest.canTriggerLastPortal(
        act5[Act5BaalQuest.RECORD], levelId, messageIndex);
  }
}
