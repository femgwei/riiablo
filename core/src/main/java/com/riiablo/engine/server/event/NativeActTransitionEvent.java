package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** Server-authoritative request to move a player to a new act town. */
public class NativeActTransitionEvent implements Event {
  @EntityId public int playerId;
  public int destinationLevelId;
  public boolean accepted;

  public static NativeActTransitionEvent obtain(int playerId, int destinationLevelId) {
    NativeActTransitionEvent event = new NativeActTransitionEvent();
    event.playerId = playerId;
    event.destinationLevelId = destinationLevelId;
    return event;
  }

  public void accept() { accepted = true; }
}
