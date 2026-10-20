package com.riiablo.engine.client;

import com.riiablo.codec.excel.Quests;
import com.riiablo.engine.server.quest.QuestCatalog;

/** Shared quest-log adapter, preserving the existing Act I stage heuristic.
 * Individual native quest status filters remain distinct from this UI summary. */
public final class QuestPresentation {
  private QuestPresentation() {}

  public static int recordIndex(Quests.Entry quest) {
    if (quest == null) return -1;
    return quest.act == 0 ? Act1QuestPresentation.recordIndex(quest)
        : QuestCatalog.recordIndex(quest.act, quest.qstr);
  }

  public static boolean isAvailable(Quests.Entry quest, int index, short record, int level) {
    return quest.act == 0 ? Act1QuestPresentation.isAvailable(index, record, level)
        : index == 1 || record != 0;
  }

  public static boolean isComplete(Quests.Entry quest, short record) {
    if (quest.act == 2 && recordIndex(quest) == 2) {
      // Returning the Gidbinn sets PRIMARY_GOAL_DONE before either individual
      // reward. The generic primary/no-pending heuristic would finish it early.
      return com.riiablo.engine.server.quest.NativeQuestRecord.has(record,
          com.riiablo.engine.server.quest.NativeQuestRecord.REWARD_GRANTED)
          || com.riiablo.engine.server.quest.NativeQuestRecord.has(record,
              com.riiablo.engine.server.quest.NativeQuestRecord.COMPLETED_BEFORE);
    }
    return Act1QuestPresentation.isComplete(record);
  }
  public static String textId(Quests.Entry quest, short record) {
    return Act1QuestPresentation.textId(quest, record);
  }

  public static String replaySpeech(Quests.Entry quest, int index, short record) {
    // Non-A1 speech mappings are not supplied by assets/data/speech.txt.
    return quest.act == 0 ? Act1QuestPresentation.replaySpeech(index, record) : null;
  }
}
