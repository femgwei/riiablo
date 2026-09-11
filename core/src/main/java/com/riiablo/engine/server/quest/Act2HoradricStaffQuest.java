package com.riiablo.engine.server.quest;

import com.riiablo.save.ItemData;

/** Native A2Q2 (Horadric Staff) record and item-code rules. */
public final class Act2HoradricStaffQuest {
  private Act2HoradricStaffQuest() {}

  /** Act II records: Q0 gossip, Q1 Radament, Q2 Horadric Staff. */
  public static final int RECORD = 2;

  // Item codes as stored in Items.txt (the C++ fourcc values are byte-reversed).
  public static final String HORADRIC_SCROLL = "tr1";
  public static final String STAFF_OF_KINGS = "msf";
  public static final String VIPER_AMULET = "vip";
  public static final String HORADRIC_CUBE = "box";
  public static final String HORADRIC_STAFF = "hst";

  // Cain2 messages in D2MOO A2Q2.cpp.
  public static final int MESSAGE_SCROLL = 335;
  public static final int MESSAGE_AMULET = 336;
  public static final int MESSAGE_STAFF = 337;
  public static final int MESSAGE_CUBE = 338;
  public static final int MESSAGE_ASSEMBLED = 339;

  public static short acknowledgeScroll(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short acknowledgeAmulet(short record) {
    record = NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short acknowledgeStaff(short record) {
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short acknowledgeCube(short record) {
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  /** Cain's final A2Q2 speech after the Horadric Staff was assembled. */
  public static short acknowledgeAssembly(short record) {
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM6);
    record = NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  /** D2MOO marks the shared quest-data flag when the cube creates hst. */
  public static short markStaffCubed(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM7);
  }

  public static boolean isQuestItem(String code) {
    return equals(code, HORADRIC_SCROLL) || equals(code, STAFF_OF_KINGS)
        || equals(code, VIPER_AMULET) || equals(code, HORADRIC_CUBE)
        || equals(code, HORADRIC_STAFF);
  }

  /** Returns true only when the four native cube inputs are present. */
  public static boolean canAssemble(ItemData items) {
    return items != null && items.containsItemCode(STAFF_OF_KINGS)
        && items.containsItemCode(VIPER_AMULET)
        && items.containsItemCode(HORADRIC_CUBE);
  }

  /** Selects the first valid Cain branch, matching ACT2Q2_CheckItemsAndState. */
  public static int selectCainMessage(short record, ItemData items) {
    if (items == null) return -1;
    if (items.containsItemCode(HORADRIC_STAFF)) return MESSAGE_ASSEMBLED;
    if (items.containsItemCode(HORADRIC_CUBE)
        && !NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2)) {
      return MESSAGE_CUBE;
    }
    if (items.containsItemCode(HORADRIC_SCROLL)
        && !NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)) {
      return MESSAGE_SCROLL;
    }
    if (items.containsItemCode(VIPER_AMULET)
        && !NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) {
      return MESSAGE_AMULET;
    }
    if (items.containsItemCode(STAFF_OF_KINGS)
        && !NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1)) {
      return MESSAGE_STAFF;
    }
    return -1;
  }

  public static boolean isCainMessageAllowed(int message, short record, ItemData items) {
    return message == selectCainMessage(record, items);
  }

  private static boolean equals(String a, String b) {
    return a != null && b.equalsIgnoreCase(a);
  }
}
