package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Immutable authoritative object state accepted by a network replica.
 *
 * <p>The receiver owns validation and ECS state; presentation systems own the
 * interaction/animation projection of the accepted state.</p>
 */
public final class ClientObjectStateEvent implements Event {
  public final int entityId;
  public final int mode;
  public final int stateFlags;

  private ClientObjectStateEvent(int entityId, int mode, int stateFlags) {
    this.entityId = entityId;
    this.mode = mode;
    this.stateFlags = stateFlags;
  }

  public static ClientObjectStateEvent obtain(int entityId, int mode, int stateFlags) {
    return new ClientObjectStateEvent(entityId, mode, stateFlags);
  }
}
