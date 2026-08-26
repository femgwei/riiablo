package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Native quest reward handoff. The owning service acknowledges the reward
 * only after its own transaction succeeds (for A1Q3 this is Charsi imbue).
 */
public class NativeQuestRewardEvent implements Event {
  public static final int CHARSI_IMBUE = 1;
  public static final int AVAILABLE = 1;
  public static final int GRANTED = 2;

  @EntityId public int playerId;
  public int questId;
  public int rewardKind;
  public int phase;

  public static NativeQuestRewardEvent available(int playerId, int questId,
      int rewardKind) {
    return obtain(playerId, questId, rewardKind, AVAILABLE);
  }

  public static NativeQuestRewardEvent granted(int playerId, int questId,
      int rewardKind) {
    return obtain(playerId, questId, rewardKind, GRANTED);
  }

  private static NativeQuestRewardEvent obtain(int playerId, int questId,
      int rewardKind, int phase) {
    NativeQuestRewardEvent event = new NativeQuestRewardEvent();
    event.playerId = playerId;
    event.questId = questId;
    event.rewardKind = rewardKind;
    event.phase = phase;
    return event;
  }
}
