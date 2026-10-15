package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/** Native monster mode path-completion / SrvDo103 sequence maintenance. */
public final class DiabRunUpdateEvent implements Event {
  @com.artemis.annotations.EntityId public int entityId;
  public DiabRunUpdateEvent(int entityId) { this.entityId = entityId; }
}
