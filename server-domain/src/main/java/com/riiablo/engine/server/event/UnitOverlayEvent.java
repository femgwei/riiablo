package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** One-shot target overlay chosen by an authoritative skill hit, independent of damage amount. */
public final class UnitOverlayEvent implements Event {
  @EntityId public int entityId;
  public String overlay;

  public static UnitOverlayEvent obtain(int entityId, String overlay) {
    UnitOverlayEvent event = new UnitOverlayEvent();
    event.entityId = entityId;
    event.overlay = overlay;
    return event;
  }
}
