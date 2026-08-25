package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** A native quest-dialog message acknowledged by the player. */
public class NpcQuestMessageEvent implements Event {
  @EntityId
  public int entityId;

  @EntityId
  public int npcId;

  public int messageIndex;

  public static NpcQuestMessageEvent obtain(int entityId, int npcId, int messageIndex) {
    NpcQuestMessageEvent event = new NpcQuestMessageEvent();
    event.entityId = entityId;
    event.npcId = npcId;
    event.messageIndex = messageIndex;
    return event;
  }
}
