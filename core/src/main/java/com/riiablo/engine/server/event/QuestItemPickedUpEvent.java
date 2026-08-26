package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/** Published when an item is moved from the ground into a player's cursor. */
public class QuestItemPickedUpEvent implements Event {
  @EntityId public int playerId;
  @EntityId public int itemEntityId;
  public String itemCode;

  public static QuestItemPickedUpEvent obtain(int playerId, int itemEntityId,
      String itemCode) {
    QuestItemPickedUpEvent event = new QuestItemPickedUpEvent();
    event.playerId = playerId;
    event.itemEntityId = itemEntityId;
    event.itemCode = itemCode;
    return event;
  }
}
