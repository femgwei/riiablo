package com.riiablo.engine.client.event;

import net.mostlyoriginal.api.event.common.Event;

/** Immutable corpse lifecycle fact consumed by the client presentation adapter. */
public final class ClientCorpseLifecycleEvent implements Event {
  public enum Phase { FADING, REMOVED }

  public final int entityId;
  public final Phase phase;

  private ClientCorpseLifecycleEvent(int entityId, Phase phase) {
    this.entityId = entityId;
    this.phase = phase;
  }

  public static ClientCorpseLifecycleEvent fading(int entityId) {
    return new ClientCorpseLifecycleEvent(entityId, Phase.FADING);
  }

  public static ClientCorpseLifecycleEvent removed(int entityId) {
    return new ClientCorpseLifecycleEvent(entityId, Phase.REMOVED);
  }
}
