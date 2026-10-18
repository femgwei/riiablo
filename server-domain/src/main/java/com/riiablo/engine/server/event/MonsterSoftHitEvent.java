package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/** Accepted monster hit whose reaction preserves the current action instead of GH.
 * This is an adapter outcome, not a complete native damage-result bit field. */
public final class MonsterSoftHitEvent implements Event {
  public int victim;
  public static MonsterSoftHitEvent obtain(int victim) {
    MonsterSoftHitEvent event = new MonsterSoftHitEvent();
    event.victim = victim;
    return event;
  }
}
