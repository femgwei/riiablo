package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/** Native trap trigger boundary; damage/projectile consumers stay outside map objects. */
public class NativeTrapInteractionEvent implements Event {
  @EntityId public int playerId;
  @EntityId public int entityId;
  public int objectClassId;
  public int operateFn;
  public int trapProbability;
  public int trapType;
  public boolean firstActivation;

  public static NativeTrapInteractionEvent obtain(int playerId, int entityId,
      int objectClassId, int operateFn, int trapProbability, int trapType,
      boolean firstActivation) {
    NativeTrapInteractionEvent event = new NativeTrapInteractionEvent();
    event.playerId = playerId;
    event.entityId = entityId;
    event.objectClassId = objectClassId;
    event.operateFn = operateFn;
    event.trapProbability = trapProbability;
    event.trapType = trapType;
    event.firstActivation = firstActivation;
    return event;
  }
}
