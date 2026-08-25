package com.riiablo.engine.server.object;

import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ANIMATED_CONTAINER;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ARCANE_SYMBOL;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.INSTANT_CONTAINER;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.NONE;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ONE_WAY_DOOR;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.SHRINE;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.TOGGLE_DOOR;
import static com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.WELL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.map.NativePresetObjectResolver;
import org.junit.jupiter.api.Test;

class NativeObjectOperateTableTest {
  @Test
  void classifiesAct1ContainersFromTheNativeOperateTable() {
    int[] animated = {1, 3, 4, 5, 6, 7, 14, 30, 33};
    for (int operateFn : animated) {
      assertEquals(ANIMATED_CONTAINER, resolve(operateFn));
    }
    assertEquals(INSTANT_CONTAINER, resolve(19));
    assertEquals(INSTANT_CONTAINER, resolve(20));
    assertEquals(INSTANT_CONTAINER, resolve(26));
  }

  @Test
  void keepsSecretDoorOneWayAndOrdinaryDoorReversible() {
    assertEquals(ONE_WAY_DOOR, resolve(18));
    assertEquals(TOGGLE_DOOR, resolve(8));
    assertEquals(TOGGLE_DOOR, NativeObjectOperateTable.resolve(
        0, true, NativePresetObjectResolver.Kind.ORDINARY));
  }

  @Test
  void classifiesNativeWellOperateFunctionSeparately() {
    assertEquals(WELL, resolve(22));
  }

  @Test
  void honorsD2MooReservedPresetKinds() {
    assertEquals(SHRINE, NativeObjectOperateTable.resolve(
        0, false, NativePresetObjectResolver.Kind.SHRINE));
    assertEquals(ANIMATED_CONTAINER, NativeObjectOperateTable.resolve(
        0, false, NativePresetObjectResolver.Kind.SPECIAL_CHEST));
    assertEquals(ANIMATED_CONTAINER, NativeObjectOperateTable.resolve(
        0, false, NativePresetObjectResolver.Kind.PRESET_CHEST));
    assertEquals(ARCANE_SYMBOL, NativeObjectOperateTable.resolve(
        0, false, NativePresetObjectResolver.Kind.ARCANE_SYMBOL));
    assertEquals(NONE, resolve(23));
  }

  private static NativeObjectOperateTable.Lifecycle resolve(int operateFn) {
    return NativeObjectOperateTable.resolve(
        operateFn, false, NativePresetObjectResolver.Kind.ORDINARY);
  }
}
