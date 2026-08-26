package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** Requests the native Countess treasure class after quest state is committed. */
public class NativeCountessQuestEvent implements Event {
  @EntityId public int playerId;
  @EntityId public int countessId;
  public int difficulty;

  public static NativeCountessQuestEvent obtain(int playerId, int countessId,
      int difficulty) {
    NativeCountessQuestEvent event = new NativeCountessQuestEvent();
    event.playerId = playerId;
    event.countessId = countessId;
    event.difficulty = difficulty;
    return event;
  }
}
