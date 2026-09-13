package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** Validates the A5Q6 Tyrael3 terminal dialogue on the server boundary. */
public final class Act5QuestMessageValidator {
  private Act5QuestMessageValidator() {}

  public static boolean isAllowed(int npcType, CharData data, int levelId,
      int messageIndex) {
    if (data == null || !data.isExpansion()) return false;
    // A5Q4 Drehya messages are native quest dialogue as well.  Resolve the
    // MonStats row by hcIdx so the validator works with both the canonical
    // 1.10f table and projects that remap monster ordinals at load time.
    com.riiablo.codec.excel.MonStats.Entry npc = null;
    if (com.riiablo.Riiablo.files != null && com.riiablo.Riiablo.files.monstats != null) {
      npc = com.riiablo.Riiablo.files.monstats.get(npcType);
    }
    boolean drehya = npc != null
        && ("Drehya".equalsIgnoreCase(npc.Id)
            || "Drehya".equalsIgnoreCase(npc.NameStr)
            || (npc.NameStr != null && npc.NameStr.toLowerCase().contains("drehya")));
    if (drehya) {
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
        return prisonDone && Act5NihlathakQuest.shouldOpenPortal(nihlathak, prisonDone);
      }
      if (messageIndex == Act5NihlathakQuest.MESSAGE_DREHYA_REWARD) {
        return Act5NihlathakQuest.canClaimReward(nihlathak);
      }
      return false;
    }
    if (!Act5BaalQuest.isTyrael3(npcType, null)) return false;
    short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
    if (act5 == null || act5.length <= Act5BaalQuest.RECORD) return false;
    return Act5BaalQuest.canTriggerLastPortal(
        act5[Act5BaalQuest.RECORD], levelId, messageIndex);
  }
}
