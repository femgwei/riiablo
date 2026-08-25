package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.map.NativePresetObjectResolver;

class ObjectInteractionEventTest {
  @Test
  void carriesNativeObjectIdentityWithoutCreatingASecondObject() {
    ObjectInteractionEvent event = ObjectInteractionEvent.obtain(
        12, 34, 371, 4, NativePresetObjectResolver.Kind.SPECIAL_CHEST,
        Lifecycle.ANIMATED_CONTAINER, true);

    assertEquals(12, event.playerId);
    assertEquals(34, event.entityId);
    assertEquals(371, event.objectClassId);
    assertEquals(4, event.operateFn);
    assertEquals(NativePresetObjectResolver.Kind.SPECIAL_CHEST, event.kind);
    assertEquals(Lifecycle.ANIMATED_CONTAINER, event.lifecycle);
    assertTrue(event.stateChanged);
    assertTrue(event.firstActivation());

    ObjectInteractionEvent repeated = ObjectInteractionEvent.obtain(
        12, 34, 371, 4, NativePresetObjectResolver.Kind.SPECIAL_CHEST,
        Lifecycle.ANIMATED_CONTAINER, false);
    assertFalse(repeated.firstActivation());

    ObjectInteractionEvent doorToggle = ObjectInteractionEvent.obtain(
        12, 35, 13, 8, NativePresetObjectResolver.Kind.ORDINARY,
        Lifecycle.TOGGLE_DOOR, true);
    assertFalse(doorToggle.firstActivation());
  }
}
