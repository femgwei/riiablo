package com.riiablo.engine.client;

import com.riiablo.codec.excel.Quests;
import com.riiablo.engine.server.quest.NativeQuestRecord;

/** Converts native Act I quest records into quest-log presentation state. */
public final class Act1QuestPresentation {
  public static final int QUEST_COUNT = 6;

  private Act1QuestPresentation() {}

  /** Resolves a visual quest row to its native Act I D2S record index. */
  public static int recordIndex(Quests.Entry quest) {
    if (quest == null || quest.act != 0) return -1;
    if (quest.id >= 1 && quest.id <= QUEST_COUNT) return quest.id;
    String qstr = quest.qstr;
    if (qstr == null) return -1;
    for (int i = 1; i <= QUEST_COUNT; i++) {
      if (("qstsa1q" + i).equalsIgnoreCase(qstr)) return i;
    }
    return -1;
  }

  public static boolean isAvailable(int recordIndex, short record) {
    return recordIndex == 1 || record != 0;
  }

  public static boolean isComplete(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE)
        || (NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
            && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }

  public static int stage(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE)) return 5;
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)) return 4;
    if (NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM3)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM4)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM5)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM6)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM7)) return 3;
    if (NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) return 2;
    if (NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)) return 1;
    return 0;
  }

  public static String textId(Quests.Entry quest, short record) {
    if (quest == null || quest.qsts == null || quest.qsts.length == 0) return null;
    int selected = Math.min(stage(record), quest.qsts.length - 1);
    for (int i = selected; i >= 0; i--) {
      if (notEmpty(quest.qsts[i])) return quest.qsts[i];
    }
    for (int i = selected + 1; i < quest.qsts.length; i++) {
      if (notEmpty(quest.qsts[i])) return quest.qsts[i];
    }
    return null;
  }

  public static String replaySpeech(int recordIndex, short record) {
    int stage = stage(record);
    switch (recordIndex) {
      case 1:
        return stage >= 4 ? "akara_act1_q1_success"
            : stage >= 1 ? "akara_act1_q1_early" : "akara_act1_q1_init";
      case 2:
        return stage >= 4 ? "kashya_act1_q2_success"
            : stage >= 1 ? "kashya_act1_q2_early" : "kashya_act1_q2_init";
      case 3:
        return stage >= 4 ? "charsi_act1_q3_success" : "charsi_act1_q3_init";
      case 4:
        return stage >= 4 ? "akara_act1_q4_success"
            : stage >= 2 ? "akara_act1_q4_early" : "akara_act1_q4_init";
      case 5:
        return stage >= 4 ? "cain_act1_q5_success" : "narrator_act1_q5_tome";
      case 6:
        return stage >= 4 ? "warriv_act1_q6_success" : "cain_act1_q6_init";
      default:
        return null;
    }
  }

  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty() && !"0".equals(value);
  }
}
