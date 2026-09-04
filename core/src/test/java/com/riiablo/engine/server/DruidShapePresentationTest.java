package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidShapePresentationTest {
  @Test
  void stateChangesVisualCompositeButKeepsLogicalPlayerType() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), new DruidShapeShiftResolver()).build());
    try {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      CofReference cof = world.getMapper(CofReference.class).create(id)
          .set("DZ", Engine.Player.MODE_RN);
      UnitStates states = world.getMapper(UnitStates.class).create(id).init(id);
      world.process();

      states.stateList.addState(StateId.WOLF, 100, 1, id);
      world.process();
      assertEquals(Class.Type.PLR, world.getMapper(Class.class).get(id).type);
      assertEquals(Class.Type.MON, cof.effectiveType(Class.Type.PLR));
      assertEquals("40", cof.effectiveToken());
      assertEquals(Engine.Monster.MODE_RN, cof.effectiveMode(Class.Type.PLR));
      assertEquals(Engine.WEAPON_HTH, cof.effectiveWClass());

      states.stateList.removeState(StateId.WOLF);
      states.stateList.addState(StateId.BEAR, 100, 1, id);
      world.process();
      assertEquals("TG", cof.effectiveToken());

      states.stateList.removeState(StateId.BEAR);
      world.process();
      assertNull(cof.visualType);
      assertEquals("DZ", cof.effectiveToken());
      assertEquals(Engine.Player.MODE_RN, cof.effectiveMode(Class.Type.PLR));
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativePlayerModesMapToMonsterFormModes() {
    CofReference cof = new CofReference().set("DZ", Engine.Player.MODE_NU)
        .setVisualOverride(Class.Type.MON, "40", Engine.WEAPON_HTH);
    assertEquals(Engine.Monster.MODE_NU, cof.effectiveMode(Class.Type.PLR));
    cof.mode = Engine.Player.MODE_WL;
    assertEquals(Engine.Monster.MODE_WL, cof.effectiveMode(Class.Type.PLR));
    cof.mode = Engine.Player.MODE_A1;
    assertEquals(Engine.Monster.MODE_A1, cof.effectiveMode(Class.Type.PLR));
    cof.mode = Engine.Player.MODE_A2;
    assertEquals(Engine.Monster.MODE_A2, cof.effectiveMode(Class.Type.PLR));
    cof.mode = Engine.Player.MODE_BL;
    assertEquals(Engine.Monster.MODE_BL, cof.effectiveMode(Class.Type.PLR));
    cof.mode = Engine.Player.MODE_SC;
    assertEquals(Engine.Monster.MODE_SC, cof.effectiveMode(Class.Type.PLR));
  }
}
