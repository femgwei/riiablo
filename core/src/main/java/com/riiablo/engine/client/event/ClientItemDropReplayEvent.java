package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable presentation fact for replaying an authoritative ground drop. */
public final class ClientItemDropReplayEvent implements Event {
  public final int entityId;

  private ClientItemDropReplayEvent(int entityId) {
    this.entityId = entityId;
  }

  public static ClientItemDropReplayEvent obtain(int entityId) {
    return new ClientItemDropReplayEvent(entityId);
  }
}
