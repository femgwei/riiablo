package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/** MonsterMode::sub_6FC64790 skill update, not a MonSeq attack/missile action. */
public final class SubmergeSequenceUpdateEvent implements Event {
  @EntityId public int entityId;

  public static SubmergeSequenceUpdateEvent obtain(int entityId) {
    SubmergeSequenceUpdateEvent event = new SubmergeSequenceUpdateEvent();
    event.entityId = entityId;
    return event;
  }
}
