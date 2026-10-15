package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/** A scheduled native MODECHANGE, not another animation keyframe. */
public final class MonInfernoUpdateEvent implements Event {
  public int entityId;

  public static MonInfernoUpdateEvent obtain(int entityId) {
    MonInfernoUpdateEvent event = new MonInfernoUpdateEvent();
    event.entityId = entityId;
    return event;
  }
}
