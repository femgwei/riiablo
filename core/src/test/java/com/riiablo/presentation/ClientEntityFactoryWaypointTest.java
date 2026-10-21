package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

class ClientEntityFactoryWaypointTest {
  @Test
  void waypointIsSelectableBeforeItsFirstModeTransition() {
    Objects.Entry waypoint = new Objects.Entry();
    waypoint.SubClass = Engine.Object.SUBCLASS_WAYPOINT;
    waypoint.Selectable = new boolean[8];

    assertTrue(ClientEntityFactory.isWaypoint(waypoint));
    assertTrue(ClientEntityFactory.isInitiallySelectable(waypoint));
  }

  @Test
  void ordinaryObjectUsesNeutralModeSelectableFlag() {
    Objects.Entry object = new Objects.Entry();
    object.Selectable = new boolean[8];

    assertFalse(ClientEntityFactory.isInitiallySelectable(object));
    object.Selectable[Engine.Object.MODE_NU] = true;
    assertTrue(ClientEntityFactory.isInitiallySelectable(object));
  }

  @Test
  void nativeDrawableOperateFnIsInitiallySelectableWithoutTableFlags() {
    Objects.Entry chest = new Objects.Entry();
    chest.Draw = true;
    chest.OperateFn = 4;
    chest.Selectable = null; // genuinely absent, not eight explicit false flags

    assertTrue(ClientEntityFactory.isInitiallySelectable(chest));
  }

  @Test
  void nativeBraziersWithExplicitZeroFlagsAreNotInitiallySelectable() {
    for (int id : new int[] {285, 286}) {
      Objects.Entry brazier = new Objects.Entry();
      brazier.Id = id;
      brazier.Draw = true;
      brazier.OperateFn = 14;
      brazier.OperateRange = 2;
      brazier.Selectable = new boolean[8];
      assertFalse(ClientEntityFactory.isInitiallySelectable(brazier));
    }
  }

  @Test
  void initializedTorchUsesItsNativeOnModeSelectableFlag() {
    Objects.Entry torch = new Objects.Entry();
    torch.Draw = true;
    torch.InitFn = 8;
    torch.OperateFn = 13;
    torch.Selectable = new boolean[8];
    torch.Selectable[Engine.Object.MODE_NU] = true;

    assertFalse(ClientEntityFactory.isInitiallySelectable(torch));
    torch.Selectable[Engine.Object.MODE_ON] = true;
    assertTrue(ClientEntityFactory.isInitiallySelectable(torch));
  }
}
