package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Client presentation recovered entirely from authoritative StateP data. */
class StateOverlaySystemTest extends RiiabloTest {
  @Test
  void bladeShieldOverlayAndVenomHandTransformFollowStates() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build());
    try {
      int entity = world.create();
      CofTransforms transforms = world.getMapper(CofTransforms.class).create(entity);
      byte originalRight = 3;
      byte originalLeft = 5;
      transforms.transform[COF.Component.RH] = originalRight;
      transforms.transform[COF.Component.LH] = originalLeft;
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.BLADESHIELD, 500, 1, entity);
      states.stateList.addState(StateId.VENOMCLAWS, 3000, 1, entity);

      world.process();
      assertTrue(overlays.bladeShieldActive);
      assertEquals("bladeshield", overlays.overlayId);
      byte green = StateOverlaySystem.venomPackedTransform();
      assertEquals(green, transforms.transform[COF.Component.RH]);
      assertEquals(green, transforms.transform[COF.Component.LH]);

      states.stateList.removeState(StateId.BLADESHIELD);
      states.stateList.removeState(StateId.VENOMCLAWS);
      world.process();
      assertFalse(overlays.bladeShieldActive);
      assertEquals(originalRight, transforms.transform[COF.Component.RH]);
      assertEquals(originalLeft, transforms.transform[COF.Component.LH]);
    } finally {
      world.dispose();
    }
  }

  @Test
  void barbarianStateOverlaysFollowAuthoritativeSnapshots() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build());
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.FRENZY, 100, 1, entity);
      states.stateList.addState(StateId.BERSERK, 100, 1, entity);
      states.stateList.addState(StateId.BATTLEORDERS, 100, 1, entity);
      states.stateList.addState(StateId.BATTLECOMMAND, 100, 1, entity);
      states.stateList.addState(StateId.SHOUT, 100, 1, entity);
      states.stateList.addState(StateId.BATTLECRY, 100, 1, entity);
      world.process();
      assertEquals("frenzy", overlays.overlayFor(StateId.FRENZY));
      assertEquals("berserkfront", overlays.overlayFor(StateId.BERSERK));
      assertEquals("battleorders", overlays.overlayFor(StateId.BATTLEORDERS));
      assertEquals("battlecommand", overlays.overlayFor(StateId.BATTLECOMMAND));
      assertEquals("shout", overlays.overlayFor(StateId.SHOUT));
      assertEquals("battlecry", overlays.overlayFor(StateId.BATTLECRY));

      states.stateList.removeState(StateId.BERSERK);
      world.process();
      assertFalse(overlays.overlays.containsKey(StateId.BERSERK));
    } finally {
      world.dispose();
    }
  }

  @com.artemis.annotations.All(com.riiablo.engine.client.component.Overlay.class)
  private static final class RecordingOverlayManager extends OverlayManager {
    boolean bladeShieldActive;
    String overlayId;
    final IntMap<String> overlays = new IntMap<>();

    @Override
    public void setPersistent(int entityId, int stateId, String overlayId) {
      if (stateId == StateId.BLADESHIELD) {
        bladeShieldActive = true;
        this.overlayId = overlayId;
      }
      overlays.put(stateId, overlayId);
    }

    @Override
    public void clearPersistent(int entityId, int stateId) {
      if (stateId == StateId.BLADESHIELD) bladeShieldActive = false;
      overlays.remove(stateId);
    }

    String overlayFor(int stateId) {
      return overlays.get(stateId);
    }
  }
}
