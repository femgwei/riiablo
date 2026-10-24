package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable presentation fact for a committed ground-item pickup. */
public final class ClientItemPickupEvent implements Event {
  private ClientItemPickupEvent() {}

  public static ClientItemPickupEvent obtain() {
    return new ClientItemPickupEvent();
  }
}
