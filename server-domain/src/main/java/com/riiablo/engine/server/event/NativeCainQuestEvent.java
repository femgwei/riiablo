package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/** A1Q4 object/portal handoff; map and NPC services consume this independently. */
public class NativeCainQuestEvent implements Event {
  public static final int CAIRN_STONE = 1;
  public static final int INIFUSS_TREE = 2;
  public static final int CAIN_GIBBET = 3;
  public static final int PORTAL_TO_TRISTRAM = 4;

  @EntityId public int playerId;
  @EntityId public int objectEntityId;
  public int objectClassId;
  public int action;
  public int stoneObjectId;
  public int stoneIndex;
  public int destinationLevelId;
  public boolean accepted;

  public static NativeCainQuestEvent obtain(int playerId, int objectEntityId,
      int objectClassId, int action) {
    NativeCainQuestEvent event = new NativeCainQuestEvent();
    event.playerId = playerId;
    event.objectEntityId = objectEntityId;
    event.objectClassId = objectClassId;
    event.action = action;
    return event;
  }

  public void accept() {
    accepted = true;
  }
}
