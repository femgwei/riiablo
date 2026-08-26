package com.riiablo.engine.server.quest;

/** Reserved Warp.index encoding for portals created by native quest scripts. */
public final class QuestWarp {
  private static final int MASK = 0xFFFF0000;
  private static final int PREFIX = 0xA1F40000;

  private QuestWarp() {}

  public static int encode(int destinationLevelId) {
    if (destinationLevelId <= 0 || destinationLevelId > 0xFFFF) {
      throw new IllegalArgumentException("invalid quest warp destination " + destinationLevelId);
    }
    return PREFIX | destinationLevelId;
  }

  public static boolean isQuestWarp(int index) {
    return (index & MASK) == PREFIX;
  }

  public static int destinationLevelId(int index) {
    return isQuestWarp(index) ? index & 0xFFFF : 0;
  }
}
