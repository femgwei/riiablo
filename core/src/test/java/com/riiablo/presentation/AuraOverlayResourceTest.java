package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.excel.Overlay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.codec.DCC;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;

class AuraOverlayResourceTest extends RiiabloTest {
  @Test void fireGolemHolyFireOwnsTwoNativeLayersAndReleasesThemWithState() {
    AssetManager saved = Riiablo.assets;
    com.riiablo.Colors colors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    int resourceFrames = java.nio.ByteBuffer.wrap(Riiablo.mpqs.resolve(
        "data/global/overlays/AuraHolyFireFront.dcc").readBytes())
        .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(3);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(resourceFrames);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager manager = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new net.mostlyoriginal.api.event.common.EventSystem(),
        new com.riiablo.engine.server.CofManager(), manager, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create();
      com.riiablo.engine.server.component.UnitStates states = world.getMapper(
          com.riiablo.engine.server.component.UnitStates.class).create(id).init(id);
      states.snapshotOnly = true;
      int holyFire = com.riiablo.engine.server.state.StateId.HOLYFIRE;
      states.stateList.addStateLayer(holyFire, 0, 27, id, 102);
      world.process(); world.process();
      com.riiablo.presentation.component.Overlay root = world.getMapper(
          com.riiablo.presentation.component.Overlay.class).get(id);
      assertNotNull(root); assertEquals(1, root.layers.size);
      java.util.Set<Integer> ids = new java.util.HashSet<>();
      java.util.Set<String> keys = new java.util.HashSet<>();
      for (com.riiablo.presentation.component.Overlay layer :
          new com.riiablo.presentation.component.Overlay[] {root, root.layers.first()}) {
        int overlayId = Riiablo.files.Overlay.index(layer.entry.overlay);
        ids.add(overlayId); keys.add(layer.entry.overlay);
        assertTrue(layer.nativeAura); assertTrue(layer.persistent); assertTrue(layer.isLoaded);
        assertEquals(BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
        assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
        assertEquals(com.riiablo.codec.Animation.Mode.LOOP, layer.animation.getMode());
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(Riiablo.mpqs.resolve(
            "data/global/overlays/" + layer.entry.Filename + ".dcc").readBytes())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(resourceFrames, header.getInt(3));
        com.badlogic.gdx.utils.JsonValue observed = new com.badlogic.gdx.utils.JsonReader()
            .parse(com.badlogic.gdx.Gdx.files.classpath(
                "original/necromancer-fire-golem-draw-1.10f.json")).get("aura_observed");
        for (com.badlogic.gdx.utils.JsonValue sample : observed) {
          if (sample.getInt("overlay_id") == overlayId)
            for (int frame : sample.get("frames").asIntArray())
              assertTrue(frame >= 0 && frame < resourceFrames, "observed original aura frame " + frame);
        }
        System.out.println("FIRE_GOLEM_AURA id=" + overlayId + " key=" + layer.entry.overlay
            + " frames=" + header.getInt(3) + " preDraw=" + layer.entry.PreDraw);
      }
      assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(15, 16)), ids);
      assertTrue(keys.contains("aura_holyfire_front")); assertTrue(keys.contains("aura_holyfire_back"));
      root.animation.setFrame(7);
      world.process(); world.process();
      assertSame(root, world.getMapper(com.riiablo.presentation.component.Overlay.class).get(id));
      assertEquals(1, root.layers.size); assertEquals(7, root.animation.getFrame());
      states.stateList.removeState(holyFire);
      world.process();
      assertFalse(world.getMapper(com.riiablo.presentation.component.Overlay.class).has(id));
    } finally { world.dispose(); Riiablo.assets = saved; Riiablo.colors = colors; }
  }

  @Test void dimVisionWaitsForTheRealCastOverlayIncludingPendingAssets() {
    AssetManager saved = Riiablo.assets;
    com.riiablo.Colors savedColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(10);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager manager = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new net.mostlyoriginal.api.event.common.EventSystem(),
        new com.riiablo.engine.server.CofManager(), manager, new StateOverlaySystem())
        .build().register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      world.getMapper(com.riiablo.engine.server.component.UnitStates.class).create(entity)
          .init(entity).stateList.addState(com.riiablo.engine.server.state.StateId.DIMVISION, 100, 1, entity);
      world.process();
      world.process();
      com.riiablo.presentation.component.Overlay cast = world.getMapper(
          com.riiablo.presentation.component.Overlay.class).get(entity);
      assertFalse(cast.isLoaded);
      assertEquals(69, Riiablo.files.Overlay.index(cast.entry.overlay));
      assertEquals(0, cast.layers.size, "persistent marker must not bypass a pending cast asset");
      assertTrue(manager.isOneShotActive(entity, cast.entry.overlay));
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      world.process();
      assertTrue(cast.isLoaded);
      String castKey = cast.entry.overlay;
      cast.animation.setFrame(cast.animation.getNumFramesPerDir() - 1);
      for (int i = 0; i < 12; i++) world.process();
      assertFalse(manager.isOneShotActive(entity, castKey));
      com.riiablo.presentation.component.Overlay marker = world.getMapper(
          com.riiablo.presentation.component.Overlay.class).get(entity);
      assertNotNull(marker);
      assertTrue(marker.persistent);
      assertEquals(129, Riiablo.files.Overlay.index(marker.entry.overlay));
      assertEquals(0, marker.layers.size);
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }

  @Test void barbarianStateLayersAndActivationFlashesUseNativeAnimationParameters() {
    AssetManager saved = Riiablo.assets;
    com.riiablo.Colors savedColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(16);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager manager = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(manager).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      for (String name : new String[] {"frenzy", "berserkfront", "berserkback", "whirlwind",
          "shoutstart", "battleorderscast", "battlecommandcast"}) {
        int entity = world.create();
        manager.set(entity, name);
        world.process();
        com.riiablo.presentation.component.Overlay effect = world.getMapper(
            com.riiablo.presentation.component.Overlay.class).get(entity);
        assertTrue(effect.usesNativeRendering(), name);
        assertFalse(effect.persistent);
        assertEquals(AuraRenderPolicy.blendMode(effect.entry.Trans),
            effect.animation.getLayer(0).getBlendMode(), name);
        assertEquals(AuraRenderPolicy.frameDuration(effect.entry.AnimRate),
            effect.animation.getFrameDuration(), .000001f, name);
        assertSame(dcc, effect.animation.getLayer(0).getDC());
      }
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }
  @Test void savedRightHandAuraIsRestoredWithoutSkillSelectionEvent() {
    com.riiablo.engine.server.AuraEcsSystem auras = new com.riiablo.engine.server.AuraEcsSystem();
    World world = new World(new WorldConfigurationBuilder().with(auras).build());
    try {
      int entity = world.create();
      com.riiablo.save.CharData data = mock(com.riiablo.save.CharData.class);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(entity).data = data;
      when(data.getAction(com.badlogic.gdx.Input.Buttons.RIGHT)).thenReturn(98); // Might
      when(data.getSkill(98)).thenReturn(1);
      assertTrue(auras.restoreSelectedAura(entity));
      assertTrue(auras.manager().hasActiveAura(entity));
      Object active = auras.manager().getActiveAura(entity);
      assertTrue(auras.restoreSelectedAura(entity));
      assertSame(active, auras.manager().getActiveAura(entity), "restore must not restart the aura");
      int other = world.create();
      com.riiablo.save.CharData attack = mock(com.riiablo.save.CharData.class);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(other).data = attack;
      assertFalse(auras.restoreSelectedAura(other), "ordinary Attack is not an aura");
      assertFalse(auras.manager().hasActiveAura(other));
    } finally {
      world.dispose();
    }
  }

  @Test void realManagerInstallsNativeAuraRateBlendAndPreservesOtherOverlays() {
    AssetManager saved = Riiablo.assets;
    com.riiablo.Colors savedColors = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(16);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    OverlayManager manager = new OverlayManager();
    World world = new World(new WorldConfigurationBuilder().with(manager).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      States.Entry state = Riiablo.files.States.get("might");
      assertNotNull(state);
      int entity = world.create();
      manager.setPersistent(entity, state.id, state.overlays[0]);
      world.process();
      com.riiablo.presentation.component.Overlay effect = world.getMapper(
          com.riiablo.presentation.component.Overlay.class).get(entity);
      assertTrue(effect.nativeAura);
      assertTrue(effect.isLoaded);
      assertEquals(BlendMode.ADDITIVE, effect.animation.getLayer(0).getBlendMode());
      assertEquals(.04f, effect.animation.getFrameDuration(), .000001f);
      // The asset's frame bounds remain owned by DCC/Animation, not table offsets.
      assertSame(dcc, effect.animation.getLayer(0).getDC());
      manager.set(entity, state.overlays[0]);
      world.process();
      assertFalse(effect.layers.first().nativeAura, "unrelated transient policy is unchanged");
      assertEquals(BlendMode.LUMINOSITY,
          effect.layers.first().animation.getLayer(0).getBlendMode());
      manager.clearPersistent(entity, state.id);
      assertFalse(effect.nativeAura, "retired pooled effects must not retain aura policy");
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }

  @Test void realAuraRowsUseSupportedDrawModesAndExistingDccs() {
    int count = 0;
    for (States.Entry state : Riiablo.files.States) {
      if (!state.aura) continue;
      for (String key : state.overlays) {
        if (key == null || key.isEmpty()) continue;
        Overlay.Entry row = Riiablo.files.Overlay.get(key);
        assertNotNull(row, state.state + ": " + key);
        assertTrue(row.Trans >= 0 && row.Trans <= 3 || row.Trans == 5,
            key + " unsupported native draw mode " + row.Trans);
        assertTrue(Riiablo.mpqs.contains("data\\global\\overlays\\" + row.Filename + ".dcc"), key);
        System.out.println("AURA_DRAW " + state.state + " " + key + " trans=" + row.Trans
            + " rate=" + row.AnimRate + " offsets=" + row.Xoffset + "," + row.Yoffset
            + " height2=" + row.Height2 + " predraw=" + row.PreDraw);
        count++;
      }
    }
    assertTrue(count >= 10, "must inspect real aura resources, not an empty fixture");
  }
}
