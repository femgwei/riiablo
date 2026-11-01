package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.shared.component.CofDirtyComponents;
import com.riiablo.presentation.component.CofComponentDescriptors;
import com.riiablo.presentation.component.CofWrapper;
import org.junit.jupiter.api.Test;

class HolyShieldPresentationTest extends RiiabloTest {
  @Test void snapshotAndExpiryReloadOnlyShieldWithoutChangingEquipment() {
    AssetManager previous = Riiablo.assets;
    Riiablo.assets = mock(AssetManager.class);
    World world = new World(new WorldConfigurationBuilder().with(new CofLayerLoader()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      CofReference reference = world.getMapper(CofReference.class).create(id);
      reference.set("PA", Engine.Player.MODE_NU);
      reference.wclass = Engine.WEAPON_1HS;
      world.getMapper(CofWrapper.class).create(id).cof = COF.loadFromFile(
          Riiablo.mpqs.resolve("data\\global\\chars\\PA\\cof\\PANU1HS.cof"));
      CofComponents components = world.getMapper(CofComponents.class).create(id);
      int buckler = java.util.Arrays.asList(Class.Type.PLR.COMP).indexOf("BUC");
      assertTrue(buckler > 0);
      components.component[COF.Component.SH] = buckler;
      CofComponentDescriptors descriptors = world.getMapper(CofComponentDescriptors.class).create(id);
      UnitStates states = world.getMapper(UnitStates.class).create(id).init(id);
      states.snapshotOnly = true;
      states.stateList.addState(StateId.HOLYSHIELD, 100, 1, id);
      world.process();
      assertTrue(descriptors.holyShield);
      assertNotNull(descriptors.descriptors[COF.Component.SH]);
      String holyPath = descriptors.descriptors[COF.Component.SH].fileName;
      assertTrue(holyPath.contains("HSH"), holyPath);
      assertTrue(Riiablo.mpqs.contains(holyPath), "must use a shipped shield DCC");
      assertEquals(buckler, components.component[COF.Component.SH]);
      for (byte mode : new byte[] {Engine.Player.MODE_A1, Engine.Player.MODE_BL}) {
        reference.mode = mode;
        world.getMapper(CofWrapper.class).get(id).cof = COF.loadFromFile(Riiablo.mpqs.resolve(
            "data\\global\\chars\\PA\\cof\\PA" + Class.Type.PLR.getMode(mode) + "1HS.cof"));
        world.getMapper(CofDirtyComponents.class).create(id).flags = 1 << COF.Component.SH;
        world.process();
        assertNotNull(descriptors.descriptors[COF.Component.SH]);
        assertTrue(descriptors.descriptors[COF.Component.SH].fileName.contains("HSH"));
      }
      states.stateList.removeState(StateId.HOLYSHIELD);
      world.process();
      assertFalse(descriptors.holyShield);
      assertTrue(descriptors.descriptors[COF.Component.SH].fileName.contains("BUC"));
      states.stateList.addState(StateId.HOLYSHIELD, 100, 1, id);
      world.process();
      assertTrue(descriptors.holyShield);
      components.component[COF.Component.SH] = CofComponents.COMPONENT_NIL;
      world.process();
      assertFalse(descriptors.holyShield);
      assertNull(descriptors.descriptors[COF.Component.SH]);
      components.component[COF.Component.SH] = buckler;
      world.process();
      assertTrue(descriptors.holyShield);
      assertTrue(descriptors.descriptors[COF.Component.SH].fileName.contains("HSH"));
      assertEquals(buckler, components.component[COF.Component.SH]);
      com.riiablo.attributes.Attributes equipment = com.riiablo.attributes.Attributes.obtainLarge();
      world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class).create(id).attrs = equipment;
      equipment.setCombatBlockContext(false, 30);
      world.process();
      assertFalse(descriptors.holyShield, "broken/no-equip shield must not use HSH");
      assertTrue(descriptors.descriptors[COF.Component.SH].fileName.contains("BUC"));
      equipment.setCombatBlockContext(true, 30);
      world.process();
      assertTrue(descriptors.holyShield);
      assertTrue(descriptors.descriptors[COF.Component.SH].fileName.contains("HSH"));
    } finally {
      world.dispose();
      Riiablo.assets = previous;
    }
  }
}
