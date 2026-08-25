package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.map.NativePresetObjectResolver;

class ObjectInteractionEventTest {
  @Test
  void carriesNativeObjectIdentityWithoutCreatingASecondObject() {
    ObjectInteractionEvent event = ObjectInteractionEvent.obtain(
        12, 34, 371, 4, NativePresetObjectResolver.Kind.SPECIAL_CHEST);

    assertEquals(12, event.playerId);
    assertEquals(34, event.entityId);
    assertEquals(371, event.objectClassId);
    assertEquals(4, event.operateFn);
    assertEquals(NativePresetObjectResolver.Kind.SPECIAL_CHEST, event.kind);
  }
}
