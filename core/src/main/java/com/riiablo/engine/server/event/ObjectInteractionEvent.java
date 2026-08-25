package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.map.NativePresetObjectResolver;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Published after an ECS object receives an interaction request.
 *
 * <p>This is intentionally an event rather than a second object registry.
 * Quest, drop and shrine-effect adapters can subscribe without creating a
 * parallel {@code ObjectManager} entity for the same map object.</p>
 */
public class ObjectInteractionEvent implements Event {
  @EntityId
  public int playerId;
  @EntityId
  public int entityId;
  public int objectClassId;
  public int operateFn;
  public NativePresetObjectResolver.Kind kind;
  /** Native lifecycle resolved from OperateFn, table flags and preset provenance. */
  public Lifecycle lifecycle;
  /** True only when this request actually changed the persistent object state. */
  public boolean stateChanged;

  public static ObjectInteractionEvent obtain(int playerId, int entityId,
      int objectClassId, int operateFn, NativePresetObjectResolver.Kind kind,
      Lifecycle lifecycle, boolean stateChanged) {
    ObjectInteractionEvent event = new ObjectInteractionEvent();
    event.playerId = playerId;
    event.entityId = entityId;
    event.objectClassId = objectClassId;
    event.operateFn = operateFn;
    event.kind = kind == null ? NativePresetObjectResolver.Kind.ORDINARY : kind;
    event.lifecycle = lifecycle == null ? Lifecycle.NONE : lifecycle;
    event.stateChanged = stateChanged;
    return event;
  }

  public boolean firstActivation() {
    return stateChanged && lifecycle != Lifecycle.NONE && lifecycle != Lifecycle.TOGGLE_DOOR;
  }
}
