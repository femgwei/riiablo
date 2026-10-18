package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** One transition from a sand leaper's airborne path to its landing animation. */
public final class LeapLandEvent implements Event {
  @EntityId public int entityId;

  public static LeapLandEvent obtain(int entityId) {
    LeapLandEvent event = new LeapLandEvent();
    event.entityId = entityId;
    return event;
  }
}
