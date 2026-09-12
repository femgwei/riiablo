package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.save.CharData;

/**
 * Native quest-Warp admission rules shared by the network and offline paths.
 * Ordinary level Warps do not enter this policy; only quest Warps with a
 * known source/destination pair are gated here.
 */
public final class QuestWarpPolicy {
  private QuestWarpPolicy() {}

  /** Returns {@code null} when the character may use the quest Warp. */
  public static String rejectionReason(CharData data, int sourceLevelId, int destinationLevelId) {
    // A3Q6 Hell Gate: Mephisto's death opens Durance -> Pandemonium Fortress.
    if (sourceLevelId == Act3MephistoQuest.MEPHISTO_LEVEL
        && destinationLevelId == Act3MephistoQuest.DESTINATION_ACT4) {
      if (data == null) return "QUEST_DATA_UNAVAILABLE";
      short[] act3 = data.getQuests(com.riiablo.Riiablo.ACT3);
      return act3 != null && act3.length > Act3MephistoQuest.RECORD
          && Act3MephistoQuest.shouldOpenExit(act3[Act3MephistoQuest.RECORD])
          ? null : "A3Q6_NOT_COMPLETE";
    }

    // A4Q2 Tyrael's end portal: the native dialogue claims the completion bit
    // before the Warp becomes usable, so a stale visual alone is insufficient.
    if (sourceLevelId == D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS
        && destinationLevelId == D2LevelIds.LEVEL_HARROGATH) {
      if (data == null) return "QUEST_DATA_UNAVAILABLE";
      short[] act4 = data.getQuests(com.riiablo.Riiablo.ACT4);
      return act4 != null && act4.length > Act4DiabloQuest.RECORD
          && NativeQuestRecord.has(act4[Act4DiabloQuest.RECORD],
              NativeQuestRecord.REWARD_GRANTED)
          ? null : "A4Q2_NOT_COMPLETE";
    }

    // A5Q4 Drehya's permanent town portal remains available only while the
    // quest is started and before the pending/completed reward transition.
    if (sourceLevelId == D2LevelIds.LEVEL_HARROGATH
        && destinationLevelId == Act5NihlathakQuest.NIHLATHAK_TEMPLE) {
      if (data == null) return "QUEST_DATA_UNAVAILABLE";
      short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
      boolean prisonDone = act5 != null && act5.length > Act5PrisonQuest.RECORD
          && (Act5PrisonQuest.isFinished(act5[Act5PrisonQuest.RECORD])
              || NativeQuestRecord.has(act5[Act5PrisonQuest.RECORD],
                  NativeQuestRecord.REWARD_PENDING));
      return act5 != null && act5.length > Act5NihlathakQuest.RECORD
          && Act5NihlathakQuest.shouldOpenPortal(
              act5[Act5NihlathakQuest.RECORD], prisonDone)
          ? null : "A5Q4_NOT_STARTED";
    }

    // A5Q6 Last Portal has a different source-level gate and CUSTOM3 is set
    // only after the terminal Tyrael message; retain its native error code.
    if (sourceLevelId == Act5BaalQuest.WORLDSTONE_CHAMBER
        && destinationLevelId == Act5BaalQuest.HARROGATH) {
      if (data == null) return "QUEST_DATA_UNAVAILABLE";
      short[] act5 = data.getQuests(com.riiablo.Riiablo.ACT5);
      return act5 != null && act5.length > Act5BaalQuest.RECORD
          && Act5BaalQuest.canUseLastPortal(
              act5[Act5BaalQuest.RECORD], sourceLevelId)
          ? null : "A5Q6_NOT_COMPLETE";
    }
    return null;
  }
}
