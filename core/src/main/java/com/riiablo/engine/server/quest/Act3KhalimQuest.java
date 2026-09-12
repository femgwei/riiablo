package com.riiablo.engine.server.quest;

import com.riiablo.save.ItemData;

/** Native D2MOO A3Q2 (project-facing A3Q3) Khalim's Will rules. */
public final class Act3KhalimQuest {
  private Act3KhalimQuest() {}

  /** Act III record slot used by {@link com.riiablo.engine.server.quest.QuestId#A3Q3_KHALIMS_WILL}. */
  public static final int RECORD = 3;

  // Items.txt codes; D2MOO fourcc values are byte-reversed in the Java tables.
  public static final String KHALIM_FLAIL = "qf1";
  public static final String KHALIM_EYE = "qey";
  public static final String KHALIM_HEART = "qhr";
  public static final String KHALIM_BRAIN = "qbr";
  public static final String KHALIM_WILL = "qf2";

  public static final int KHALIM_CHEST1 = 405;
  public static final int KHALIM_CHEST2 = 406;
  public static final int KHALIM_CHEST3 = 407;
  public static final int COMPELLING_ORB = 404;

  public static short start(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short markPicked(short record, String code) {
    if (equals(code, KHALIM_FLAIL)) return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
    if (equals(code, KHALIM_HEART)) return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
    if (equals(code, KHALIM_BRAIN)) return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM3);
    if (equals(code, KHALIM_EYE)) return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
    return record;
  }

  public static boolean hasAllParts(ItemData items) {
    return items != null && items.containsItemCode(KHALIM_FLAIL)
        && items.containsItemCode(KHALIM_EYE)
        && items.containsItemCode(KHALIM_HEART)
        && items.containsItemCode(KHALIM_BRAIN);
  }

  public static boolean equals(String a, String b) {
    return a != null && b.equalsIgnoreCase(a);
  }

  public static boolean isPart(String code) {
    return equals(code, KHALIM_FLAIL) || equals(code, KHALIM_EYE)
        || equals(code, KHALIM_HEART) || equals(code, KHALIM_BRAIN)
        || equals(code, KHALIM_WILL);
  }
}
