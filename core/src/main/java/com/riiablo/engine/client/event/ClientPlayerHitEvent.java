package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable presentation fact emitted after an accepted player life decrease. */
public final class ClientPlayerHitEvent implements Event {
  public final int entityId;

  private ClientPlayerHitEvent(int entityId) {
    this.entityId = entityId;
  }

  public static ClientPlayerHitEvent obtain(int entityId) {
    return new ClientPlayerHitEvent(entityId);
  }
}
