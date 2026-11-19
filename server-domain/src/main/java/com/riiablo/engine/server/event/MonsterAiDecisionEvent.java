package com.riiablo.engine.server.event;

import net.mostlyoriginal.api.event.common.Event;

/** Optional diagnostic observation; never participates in AI state or RNG. */
public final class MonsterAiDecisionEvent implements Event {
  public int entityId, targetId, roll, secondaryRoll;
  public String action;
  public boolean accepted;
}
