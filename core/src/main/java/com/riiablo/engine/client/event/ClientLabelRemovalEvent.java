package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable presentation fact for removing a stale world label from a replica. */
public final class ClientLabelRemovalEvent implements Event {
  public final int entityId;

  private ClientLabelRemovalEvent(int entityId) {
    this.entityId = entityId;
  }

  public static ClientLabelRemovalEvent obtain(int entityId) {
    return new ClientLabelRemovalEvent(entityId);
  }
}
