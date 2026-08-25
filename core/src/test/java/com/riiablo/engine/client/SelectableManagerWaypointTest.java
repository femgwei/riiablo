package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

class SelectableManagerWaypointTest {
  @Test
  void waypointRemainsSelectableInEveryObjectMode() {
    Objects.Entry waypoint = new Objects.Entry();
    waypoint.SubClass = Engine.Object.SUBCLASS_WAYPOINT;
    waypoint.Selectable = new boolean[8];

    for (int mode = Engine.Object.MODE_NU; mode <= Engine.Object.MODE_S5; mode++) {
      assertTrue(SelectableManager.isSelectable(waypoint, mode));
    }
  }

  @Test
  void ordinaryObjectStillUsesItsModeSpecificSelectableFlag() {
    Objects.Entry object = new Objects.Entry();
    object.Selectable = new boolean[8];
    object.Selectable[Engine.Object.MODE_OP] = true;

    assertFalse(SelectableManager.isSelectable(object, Engine.Object.MODE_NU));
    assertTrue(SelectableManager.isSelectable(object, Engine.Object.MODE_OP));
    assertFalse(SelectableManager.isSelectable(object, Engine.Object.MODE_ON));
    assertFalse(SelectableManager.isSelectable(object, -1));
    assertFalse(SelectableManager.isSelectable(object, object.Selectable.length));
  }

  @Test
  void nativeDrawableOperateFnRemainsSelectableWithoutTableFlags() {
    Objects.Entry chest = new Objects.Entry();
    chest.Draw = true;
    chest.OperateFn = 4;
    chest.Selectable = new boolean[8];

    assertTrue(SelectableManager.isSelectable(chest, Engine.Object.MODE_NU));
    assertTrue(SelectableManager.isSelectable(chest, Engine.Object.MODE_ON));
    assertFalse(SelectableManager.isSelectable(chest, Engine.Object.MODE_OP));
  }
}
