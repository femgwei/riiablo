package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.event.NativeTrapInteractionEvent;
import com.riiablo.map.NativePresetObjectResolver;

class NativeTrapObjectTest {
  @Test
  void routesExplodingBarrelToTrapLifecycleInsteadOfContainerDrops() {
    Objects.Entry barrel = new Objects.Entry();
    barrel.Id = 11;
    barrel.OperateFn = 7;
    barrel.TrapProb = 100;
    assertEquals(NativeObjectOperateTable.Lifecycle.TRAP,
        NativeObjectOperateTable.resolve(barrel,
            NativePresetObjectResolver.Kind.ORDINARY));
  }

  @Test
  void eventCarriesNativeTrapContextWithoutApplyingDamage() {
    NativeTrapInteractionEvent event = NativeTrapInteractionEvent.obtain(
        1, 2, 11, 7, 100, -1, true);
    assertEquals(1, event.playerId);
    assertEquals(2, event.entityId);
    assertEquals(11, event.objectClassId);
    assertEquals(7, event.operateFn);
    assertEquals(100, event.trapProbability);
    assertEquals(-1, event.trapType);
    assertTrue(event.firstActivation);
  }
}
