package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** Explicit Charsi request for the item currently held on the player's cursor. */
public class NativeImbueRequestEvent implements Event {
  @EntityId public int playerId;
  public int itemId;
  public boolean accepted;

  public static NativeImbueRequestEvent obtain(int playerId, int itemId) {
    NativeImbueRequestEvent event = new NativeImbueRequestEvent();
    event.playerId = playerId;
    event.itemId = itemId;
    return event;
  }

  public void accept() { accepted = true; }
}
