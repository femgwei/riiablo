package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
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
  void reviveAppliesPl2GrayToLoadedLayersAndRestoresBaseColor() throws Exception {
    com.riiablo.Colors previousColors = Riiablo.colors;
    com.riiablo.Colormaps previousMaps = Riiablo.colormaps;
    Riiablo.colors = new com.riiablo.Colors();
    Riiablo.colormaps = org.mockito.Mockito.mock(com.riiablo.Colormaps.class);
    byte[] hue = com.riiablo.codec.PL2.loadFromFile(Riiablo.mpqs.resolve(
        "data/global/palette/ACT1/Pal.pl2")).getHueVariation(73);
    byte[][] rows = new byte[com.riiablo.codec.Index.INDEXES][256];
    System.arraycopy(hue, 0, rows[1], 0, 256);
    com.riiablo.codec.Index gray = com.riiablo.codec.Index.fromColormaps("revive-gray", rows);
    org.mockito.Mockito.when(Riiablo.colormaps.getStateTransform(73)).thenReturn(gray);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), new RecordingOverlayManager(), new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(id).init(id);
      states.snapshotOnly = true;
      states.stateList.addState(StateId.REVIVE, 4500, 5, 7);
      CofTransforms base = world.getMapper(CofTransforms.class).create(id);
      com.riiablo.codec.Animation animation = world.getMapper(
          com.riiablo.presentation.component.AnimationWrapper.class).create(id).animation;
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/SK/COF/SKNUHTH.cof"));
      animation.setCOF(cof);
      com.riiablo.codec.DC6 sheet = org.mockito.Mockito.mock(com.riiablo.codec.DC6.class);
      org.mockito.Mockito.when(sheet.getNumDirections()).thenReturn(cof.getNumDirections());
      org.mockito.Mockito.when(sheet.getNumFramesPerDir()).thenReturn(cof.getNumFramesPerDir());
      org.mockito.Mockito.when(sheet.getRegions()).thenReturn(
          new com.badlogic.gdx.graphics.g2d.TextureRegion[cof.getNumDirections()][cof.getNumFramesPerDir()]);
      java.lang.reflect.Field transform = com.riiablo.codec.Animation.Layer.class.getDeclaredField("transform");
      java.lang.reflect.Field color = com.riiablo.codec.Animation.Layer.class.getDeclaredField("transformColor");
      transform.setAccessible(true); color.setAccessible(true);
      for (int i = 0; i < cof.getNumLayers(); i++) animation.setLayer(cof.getLayer(i).component, sheet);
      world.process();
      for (int i = 0; i < cof.getNumLayers(); i++) {
        int component = cof.getLayer(i).component;
        org.junit.jupiter.api.Assertions.assertSame(gray, transform.get(animation.getLayer(component)));
        assertEquals(1, color.getInt(animation.getLayer(component)));
        assertEquals(CofTransforms.TRANSFORM_NULL, base.transform[component]);
      }
      states.stateList.removeState(StateId.REVIVE);
      world.process();
      for (int i = 0; i < cof.getNumLayers(); i++)
        assertNull(transform.get(animation.getLayer(cof.getLayer(i).component)));
    } finally {
      world.dispose(); Riiablo.colors = previousColors; Riiablo.colormaps = previousMaps;
    }
  }

  @Test
  void reviveUsesNativeGrayShiftAndRestoresElementalPolicy() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    StateOverlaySystem system = new StateOverlaySystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, system)
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      assertEquals(-1, system.stateColorShift(states));
      states.stateList.addState(StateId.REVIVE, 4500, 5, 7);
      assertEquals(73, Riiablo.files.States.get(StateId.REVIVE).colorShift);
      assertEquals(73, system.stateColorShift(states));
      states.stateList.addState(StateId.COLD, 100, 1, 7);
      int expected = Riiablo.files.States.get(StateId.COLD).colorPriority
          > Riiablo.files.States.get(StateId.REVIVE).colorPriority ? 107 : 73;
      assertEquals(expected, system.stateColorShift(states));
      states.stateList.removeState(StateId.COLD);
      assertEquals(73, system.stateColorShift(states));
      states.stateList.removeState(StateId.REVIVE);
      assertEquals(-1, system.stateColorShift(states));
    } finally { world.dispose(); }
  }

  @Test
  void decoySummonOverlayPlaysOnceFromAuthoritativeSummonRow() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      world.getMapper(UnitStates.class).create(entity).init(entity);
      world.process();
      world.getMapper(com.riiablo.engine.server.component.SummonedPet.class).create(entity)
          .set(7, "dopplezon", 28, 5, true, 250);
      world.process();
      world.process();
      assertEquals(1, overlays.spawnOverlayCount);
      assertEquals("dopllezon_appear", overlays.spawnOverlay);
    } finally { world.dispose(); }
  }
  @Test
  void auraTransitionPlaysNativeStateSoundOnceNotOnRefresh() {
    com.riiablo.audio.Audio audio = org.mockito.Mockito.mock(com.riiablo.audio.Audio.class);
    java.util.concurrent.atomic.AtomicInteger listener = new java.util.concurrent.atomic.AtomicInteger(-1);
    ClientGameplayContext context = new ClientGameplayContext(null, listener::get, () -> null,
        null, Riiablo.assets, audio, null, Riiablo.files, null, null, null, null, null, null);
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", context));
    try {
      int entity = world.create();
      listener.set(entity);
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(entity);
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      String might = Riiablo.files.States.get(StateId.MIGHT).onSound;
      String prayer = Riiablo.files.States.get(StateId.PRAYER).onSound;
      assertEquals("paladin_aura_might", might);
      assertEquals("paladin_aura_prayer", prayer);
      states.stateList.addStateLayer(StateId.MIGHT, 50, 1, entity, 98);
      world.process();
      world.process();
      org.mockito.Mockito.verify(audio).play(might, true);
      states.stateList.removeState(StateId.MIGHT);
      states.stateList.addStateLayer(StateId.PRAYER, 50, 1, entity, 99);
      world.process();
      world.process();
      org.mockito.Mockito.verify(audio).play(prayer, true);
      assertFalse(overlays.overlays.containsKey(StateId.MIGHT));
      assertTrue(overlays.overlays.containsKey(StateId.PRAYER));
      org.mockito.Mockito.verifyNoMoreInteractions(audio);
    } finally {
      world.dispose();
    }
  }

  @Test
  void existingItemAndSpiritAuraSnapshotsDisplayOnFirstClientTick() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.snapshotOnly = true;
      int[] ids = {StateId.FANATICISM, StateId.CONVICTION, StateId.MEDITATION,
          StateId.OAKSAGE, StateId.WOLVERINE, StateId.BARBS};
      for (int state : ids) states.stateList.addStateLayer(state, 50, 9, 123, state);
      world.process();
      for (int state : ids) {
        com.riiablo.codec.excel.States.Entry row = Riiablo.files.States.get(state);
        assertTrue(row.overlays != null && row.overlays.length > 0);
        assertTrue(overlays.overlays.containsKey(state), "first snapshot state " + row.state);
      }
      for (int state : ids) states.stateList.removeState(state);
      world.process();
      for (int state : ids) assertFalse(overlays.overlays.containsKey(state));
    } finally {
      world.dispose();
    }
  }

  @Test
  void enchantHandTransformFollowsStateAndEquipmentColorChanges() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      CofTransforms transforms = world.getMapper(CofTransforms.class).create(entity);
      transforms.transform[COF.Component.RH] = 3;
      transforms.transform[COF.Component.LH] = 5;
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.ENCHANT, 500, 1, entity);
      world.process();
      byte red = (byte) (Riiablo.files.colors.index("cred") + 1);
      assertEquals(red, transforms.transform[COF.Component.RH]);
      transforms.transform[COF.Component.RH] = 7;
      world.process();
      assertEquals(red, transforms.transform[COF.Component.RH]);
      states.stateList.removeState(StateId.ENCHANT);
      world.process();
      assertEquals(7, transforms.transform[COF.Component.RH]);
      assertEquals(5, transforms.transform[COF.Component.LH]);
    } finally { world.dispose(); }
  }

  @Test
  void enchantAndVenomShareWeaponBaseWithoutColorDrift() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      CofTransforms transforms = world.getMapper(CofTransforms.class).create(entity);
      transforms.transform[COF.Component.RH] = 3;
      transforms.transform[COF.Component.LH] = 5;
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.VENOMCLAWS, 500, 1, entity);
      states.stateList.addState(StateId.ENCHANT, 500, 1, entity);
      world.process();
      byte red = (byte) (Riiablo.files.colors.index("cred") + 1);
      assertEquals(red, transforms.transform[COF.Component.RH]);
      states.stateList.removeState(StateId.ENCHANT);
      world.process();
      byte green = StateOverlaySystem.venomPackedTransform(ClientGameplayContext.fromGlobals());
      assertEquals(green, transforms.transform[COF.Component.RH]);
      states.stateList.removeState(StateId.VENOMCLAWS);
      world.process();
      assertEquals(3, transforms.transform[COF.Component.RH]);
      assertEquals(5, transforms.transform[COF.Component.LH]);
    } finally { world.dispose(); }
  }

  @Test
  void bladeShieldOverlayAndVenomHandTransformFollowStates() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", clientContext));
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
      byte green = StateOverlaySystem.venomPackedTransform(clientContext);
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
  void bladeShieldOverlayUsesStateLifetimeInsteadOfAClientTimer() {
    assertTrue(Riiablo.files.Overlay.get("bladeshield") != null,
        "stock Overlay.txt must contain the Blade Shield visual row");
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.BLADESHIELD, 3, 1, entity);

      world.process();
      assertTrue(overlays.bladeShieldActive);
      assertEquals("bladeshield", overlays.overlayId);

      // A StateP duration update must not restart or clear the persistent
      // visual. The authoritative state removal is the only stop signal.
      states.stateList.getState(StateId.BLADESHIELD).duration = 1;
      world.process();
      assertTrue(overlays.bladeShieldActive);

      states.stateList.removeState(StateId.BLADESHIELD);
      world.process();
      assertFalse(overlays.bladeShieldActive);
    } finally {
      world.dispose();
    }
  }

  @Test
  void bladeShieldOverlayRowDeclaresNativeDccTiming() {
    com.riiablo.codec.excel.Overlay.Entry entry =
        Riiablo.files.Overlay.get("bladeshield");
    assertTrue(entry != null, "stock Overlay.txt must contain the Blade Shield row");
    assertTrue(entry.Filename != null && !entry.Filename.isEmpty(),
        "Blade Shield must resolve to a DCC filename");
    assertTrue(entry.Frames > 0,
        "Blade Shield overlay must declare at least one native frame");
    assertTrue(entry.AnimRate > 0,
        "Blade Shield overlay must declare a native AnimRate for client playback");
    assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\Expansion\\bladeshield front.dcc"),
        "Blade Shield front DCC must be present in the 1.10f MPQ set");
    assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\Expansion\\bladeshield back.dcc"),
        "Blade Shield back DCC must be present in the 1.10f MPQ set");
    assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\Expansion\\bladeshieldfrontfade.dcc"),
        "Blade Shield front fade DCC must be present in the 1.10f MPQ set");
    assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\Expansion\\bladeshieldbackfade.dcc"),
        "Blade Shield back fade DCC must be present in the 1.10f MPQ set");
  }

  @Test
  void coldAndFreezeStatesDoNotOverwriteBaseComponentTransforms() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      CofTransforms transforms = world.getMapper(CofTransforms.class).create(entity);
      byte originalBody = 3;
      byte originalWeapon = 5;
      transforms.transform[COF.Component.HD] = originalBody;
      transforms.transform[COF.Component.RH] = originalWeapon;
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);

      states.stateList.addState(StateId.COLD, 100, 1, entity);
      world.process();
      assertEquals(originalBody, transforms.transform[COF.Component.HD]);
      assertEquals(originalWeapon, transforms.transform[COF.Component.RH]);

      states.stateList.removeState(StateId.COLD);
      world.process();
      assertEquals(originalBody, transforms.transform[COF.Component.HD]);
      assertEquals(originalWeapon, transforms.transform[COF.Component.RH]);
    } finally {
      world.dispose();
    }
  }

  @Test
  void barbarianStateOverlaysFollowAuthoritativeSnapshots() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
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
      assertTrue(overlays.overlays.get(StateId.BERSERK).contains("berserkback", false));
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

  @Test
  void nativeDimVisionAndConversionUseOverlayTxtKeys() {
    // Overlay.txt keys are case-sensitive. These rows are present in the
    // stock 1.10f data set and should be selected before mod aliases.
    assertTrue(Riiablo.files.Overlay.get("cursedimvision") != null,
        "stock Overlay.txt must contain the Dim Vision overlay row");
    assertTrue(Riiablo.files.Overlay.get("conversionaura") != null,
        "stock Overlay.txt must contain the Conversion overlay row");

    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.DIMVISION, 100, 1, entity);
      states.stateList.addState(StateId.CONVERSION, 100, 1, entity);
      world.process();
      assertNull(overlays.overlayFor(StateId.DIMVISION));
      world.process(); // The recording manager has no active one-shot assets.
      assertEquals("cursedimvision", overlays.overlayFor(StateId.DIMVISION));
      assertEquals("conversionaura", overlays.overlayFor(StateId.CONVERSION));
    } finally {
      world.dispose();
    }
  }

  @Test
  void slowMissilesUsesNativeInnerSightOverheadOverlay() {
    assertEquals("innersight",
        Riiablo.files.States.get(StateId.SLOWMISSILES).overlays[0]);
    assertTrue(Riiablo.files.Overlay.get("innersight") != null,
        "stock Overlay.txt must contain the Slow Missiles marker row");

    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.SLOWMISSILES, 100, 1, entity);

      world.process();
      assertEquals("innersight", overlays.overlayFor(StateId.SLOWMISSILES));

      states.stateList.removeState(StateId.SLOWMISSILES);
      world.process();
      assertFalse(overlays.overlays.containsKey(StateId.SLOWMISSILES));
    } finally {
      world.dispose();
    }
  }

  @Test
  void allNativeCurseRowsRenderFromStatesWithoutAnyDamageEvent() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), overlays, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      int[] curses = {StateId.AMPLIFYDAMAGE, StateId.DIMVISION, StateId.TERROR,
          StateId.LIFETAP, StateId.CONFUSE, StateId.DECREPIFY, StateId.LOWERRESIST};
      for (int id : curses) {
        states.stateList.addState(id, 100, 1, entity);
        world.process();
        if (id == StateId.DIMVISION) world.process();
        String nativeOverlay = Riiablo.files.States.get(id).overlays[0];
        assertFalse(nativeOverlay.isEmpty(), "native curse must declare an overlay: " + id);
        assertEquals(nativeOverlay, overlays.overlayFor(id));
      }
      states.stateList.removeState(StateId.AMPLIFYDAMAGE);
      world.process();
      assertFalse(overlays.overlays.containsKey(StateId.AMPLIFYDAMAGE));
      assertTrue(overlays.overlays.containsKey(StateId.LOWERRESIST));
      world.getMapper(UnitStates.class).remove(entity);
      world.process();
      assertEquals(0, overlays.overlays.size);
    } finally { world.dispose(); }
  }

  @com.artemis.annotations.All(com.riiablo.presentation.component.Overlay.class)
  private static final class RecordingOverlayManager extends OverlayManager {
    int spawnOverlayCount;
    String spawnOverlay;
    boolean bladeShieldActive;
    String overlayId;
    final IntMap<Array<String>> overlays = new IntMap<>();

    @Override
    public void setPersistent(int entityId, int stateId, String overlayId) {
      if (stateId == StateId.BLADESHIELD) {
        bladeShieldActive = true;
        this.overlayId = overlayId;
      }
      Array<String> layers = overlays.get(stateId);
      if (layers == null) { layers = new Array<>(); overlays.put(stateId, layers); }
      if (!layers.contains(overlayId, false)) layers.add(overlayId);
    }

    @Override
    public void clearPersistent(int entityId, int stateId) {
      if (stateId == StateId.BLADESHIELD) bladeShieldActive = false;
      overlays.remove(stateId);
    }

    String overlayFor(int stateId) {
      Array<String> layers = overlays.get(stateId);
      return layers == null ? null : layers.first();
    }

    @Override public void set(int entityId, String overlayId) {
      spawnOverlayCount++;
      spawnOverlay = overlayId;
    }
  }
}

