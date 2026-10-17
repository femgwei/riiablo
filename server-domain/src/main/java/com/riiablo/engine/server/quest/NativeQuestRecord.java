package com.riiablo.engine.server.quest;

/** Utilities for Diablo II's native 16-bit per-quest save record. */
public final class NativeQuestRecord {
  private NativeQuestRecord() {}

  // D2C_OriginalQuestFlags in D2CommonDefinitions/D2Constants.h. These are
  // bit indexes, not masks.
  public static final int REWARD_GRANTED = 0;
  public static final int REWARD_PENDING = 1;
  public static final int STARTED = 2;
  public static final int LEFT_TOWN = 3;
  public static final int ENTERED_AREA = 4;
  public static final int CUSTOM1 = 5;
  public static final int CUSTOM2 = 6;
  public static final int CUSTOM3 = 7;
  public static final int CUSTOM4 = 8;
  public static final int CUSTOM5 = 9;
  public static final int CUSTOM6 = 10;
  public static final int CUSTOM7 = 11;
  public static final int UPDATE_QUEST_LOG = 12;
  public static final int PRIMARY_GOAL_DONE = 13;
  public static final int COMPLETED_NOW = 14;
  public static final int COMPLETED_BEFORE = 15;

  public static boolean has(short record, int flag) {
    validateFlag(flag);
    return (Short.toUnsignedInt(record) & (1 << flag)) != 0;
  }

  public static short set(short record, int flag) {
    validateFlag(flag);
    int value = Short.toUnsignedInt(record) | (1 << flag);
    // QFLAG_UPDATEQUESTLOG is the native acknowledgement bit written when
    // the quest journal has been viewed.  Actual quest progress makes the
    // journal stale again, but merely leaving town or entering the objective
    // area does not create a new journal notification in the native client.
    // Setting the acknowledgement itself is intentionally left untouched.
    if (flag != UPDATE_QUEST_LOG && flag != LEFT_TOWN && flag != ENTERED_AREA) {
      value &= ~(1 << UPDATE_QUEST_LOG);
    }
    return (short) value;
  }

  public static short clear(short record, int flag) {
    validateFlag(flag);
    return (short) (Short.toUnsignedInt(record) & ~(1 << flag));
  }

  /** Mirrors QUESTRECORD_ResetIntermediateStateFlags. */
  public static short resetIntermediate(short record) {
    int first = (1 << STARTED) - 1;
    int throughCustom7 = (1 << (CUSTOM7 + 1)) - 1;
    int intermediateMask = throughCustom7 & ~first;
    return (short) (Short.toUnsignedInt(record) & ~intermediateMask);
  }

  private static void validateFlag(int flag) {
    if (flag < 0 || flag >= Short.SIZE) {
      throw new IllegalArgumentException("Invalid native quest flag: " + flag);
    }
  }
}
