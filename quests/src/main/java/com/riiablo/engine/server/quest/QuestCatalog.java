package com.riiablo.engine.server.quest;

/** Maps resource-table quest keys onto the project's existing save layout.
 * Act III is deliberately NOT native table order; changing it would require
 * a versioned save migration. UI order and global QuestId are not record ids. */
public final class QuestCatalog {
  private QuestCatalog() {}

  public static int recordIndex(int act, String key) {
    if (act < 0 || act >= QuestRecords.ACTS || key == null) return -1;
    int limit = act == 3 ? 3 : 6;
    for (int quest = 1; quest <= limit; quest++) {
      if (!("qstsa" + (act + 1) + "q" + quest).equalsIgnoreCase(key)) continue;
      if (act == 2 && quest <= 4) return 5 - quest;
      return quest;
    }
    return -1;
  }
}
