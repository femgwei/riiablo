package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.presentation.component.Overlay;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FeralMaulPresentationTest extends RiiabloTest {
  @Test void levelOneUsesNativeIntegerRangeSelectionRatherThanFourEndpoints() {
    for (int skill : new int[] {SkillId.FERAL_RAGE, SkillId.MAUL}) {
      try (Fixture f = new Fixture(skill, 1)) {
        String prefix = skill == SkillId.FERAL_RAGE ? "feralrage" : "maul";
        assertEquals(3, DruidSkills.getFeralMaulMaxStacks(Riiablo.files.skills.get(skill), 1));
        for (int charge = 1; charge <= 3; charge++) {
          f.state.runtimeValue = charge;
          f.world.process(); f.world.process();
          int tier = new int[] {1, 2, 4}[charge - 1];
          List<Overlay> layers = f.layers();
          assertEquals(2, layers.size());
          assertEquals(prefix + tier, layers.get(0).entry.overlay);
          assertEquals(prefix + tier + "back", layers.get(1).entry.overlay);
          assertFalse(layers.get(0).entry.PreDraw);
          assertTrue(layers.get(1).entry.PreDraw);
        }
      }
    }
  }

  @Test void fiveTiersUseNativeBlendClockAndRetirePreviousSizes() {
    try (Fixture f = new Fixture(SkillId.FERAL_RAGE, 5)) {
      for (int charge = 1; charge <= 5; charge++) {
        f.state.runtimeValue = charge;
        f.world.process(); f.world.process();
        List<Overlay> layers = f.layers();
        assertEquals(2, layers.size());
        assertEquals("feralrage" + charge, layers.get(0).entry.overlay);
        assertEquals("feralrage" + charge + "back", layers.get(1).entry.overlay);
        for (Overlay layer : layers) {
          assertTrue(layer.usesNativeRendering());
          assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
          assertEquals(AuraRenderPolicy.blendMode(layer.entry.Trans),
              layer.animation.getLayer(0).getBlendMode());
          assertTrue(Riiablo.mpqs.contains(layer.assetDescriptor.fileName));
        }
      }
      verify(f.assets).unload("data/global/overlays/expansion/feral_rage_A_Front.dcc");
    }
  }

  @Test void sameSelectedTierAndDurationRefreshKeepExistingAnimationObjectsAndAssets() {
    try (Fixture f = new Fixture(SkillId.FERAL_RAGE, 20)) {
      f.state.runtimeValue = 6;
      f.world.process(); f.world.process();
      List<Overlay> before = f.layers();
      for (Overlay layer : before) layer.animation.setFrame(7);
      clearInvocations(f.assets);
      f.state.runtimeValue = 7; // both saturate at the largest of the five native sizes
      f.state.duration = 499;
      f.world.process();
      List<Overlay> after = f.layers();
      assertSame(before.get(0), after.get(0));
      assertSame(before.get(1), after.get(1));
      for (Overlay layer : after) assertTrue(layer.animation.getFrame() >= 7);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
    }
  }

  @Test void expiryAndStateDeletionRemoveBothHalvesButPreserveOtherEffects() {
    try (Fixture f = new Fixture(SkillId.FERAL_RAGE, 5)) {
      f.state.runtimeValue = 3;
      f.world.process(); f.world.process();
      String might = Riiablo.files.States.get(StateId.MIGHT).overlays[0];
      f.manager.setPersistent(f.entity, StateId.MIGHT, might);
      f.manager.set(f.entity, "healing");
      f.state.expired = true;
      f.world.process();
      assertEquals(0, f.layers().size());
      // The unrelated layer has a state owner but no UnitState in this fixture.
      // StateOverlaySystem must not delete a layer it never installed.
      Overlay root = f.world.getMapper(Overlay.class).get(f.entity);
      assertNotNull(root);
      assertEquals(2, active(root).size());
      assertTrue(active(root).stream().anyMatch(layer -> layer.persistent
          && layer.stateId == StateId.MIGHT && might.equals(layer.entry.overlay)));
      assertTrue(active(root).stream().anyMatch(layer -> !layer.persistent
          && "healing".equals(layer.entry.overlay)));
      f.world.getMapper(UnitStates.class).remove(f.entity);
      f.world.process();
      assertEquals(2, active(f.world.getMapper(Overlay.class).get(f.entity)).size());
    }
  }

  @Test void zeroChargesAndInvalidModifierSkillNeverRenderEndpointLayers() {
    try (Fixture f = new Fixture(SkillId.FERAL_RAGE, 1)) {
      f.state.runtimeValue = 0;
      f.world.process();
      assertTrue(f.layers().isEmpty());
      f.state.runtimeValue = 2;
      f.state.skillId = SkillId.MAUL;
      f.world.process();
      assertTrue(f.layers().isEmpty());
    }
  }

  @Test void authoritativeStateSnapshotReconstructsChargePairForARemotePlayer() {
    for (int skill : new int[] {SkillId.FERAL_RAGE, SkillId.MAUL}) {
      try (Fixture f = new Fixture(skill, 1)) {
        UnitStates source = new UnitStates().init(77);
        source.stateList.addState(skill == SkillId.FERAL_RAGE ? StateId.WOLF : StateId.BEAR,
            2000, 1, 77);
        for (int hit = 0; hit < 3; hit++) DruidSkills.applyFeralMaulState(source.stateList,
            Riiablo.files.skills.get(skill), 5, 77);
        com.riiablo.engine.server.component.serializer.StateSerializer serializer =
            new com.riiablo.engine.server.component.serializer.StateSerializer();
        com.google.flatbuffers.FlatBufferBuilder builder = new com.google.flatbuffers.FlatBufferBuilder(256);
        int data = serializer.putData(builder, source);
        int types = com.riiablo.net.packet.d2gs.EntitySync.createComponentTypeVector(builder,
            new byte[] {com.riiablo.net.packet.d2gs.ComponentP.StateP});
        int components = com.riiablo.net.packet.d2gs.EntitySync.createComponentVector(builder,
            new int[] {data});
        builder.finish(com.riiablo.net.packet.d2gs.EntitySync.createEntitySync(builder,
            77, 0, 0, types, components, 0L, 0L, 0L, 0L, 0L, -1));
        UnitStates remote = f.world.getMapper(UnitStates.class).get(f.entity);
        serializer.getData(com.riiablo.net.packet.d2gs.EntitySync.getRootAsEntitySync(
            builder.dataBuffer()), 0, remote);
        assertTrue(remote.snapshotOnly);
        assertEquals(3, remote.stateList.getState(f.stateId).runtimeValue);
        f.world.process(); f.world.process();
        String prefix = skill == SkillId.FERAL_RAGE ? "feralrage" : "maul";
        assertEquals(2, f.layers().size());
        assertEquals(prefix + "3", f.layers().get(0).entry.overlay);
        assertEquals(prefix + "3back", f.layers().get(1).entry.overlay);
      }
    }
  }

  private static List<Overlay> active(Overlay root) {
    List<Overlay> result = new ArrayList<>();
    if (root == null) return result;
    if (root.entry != null) result.add(root);
    for (Overlay layer : root.layers) if (layer.entry != null) result.add(layer);
    return result;
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager savedAssets = Riiablo.assets;
    final com.riiablo.Colors savedColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final OverlayManager manager = new OverlayManager();
    final World world;
    final int entity, stateId;
    final UnitState state;
    Fixture(int skill, int level) {
      Riiablo.assets = assets;
      Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1);
      when(dcc.getNumFramesPerDir()).thenReturn(16);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new CofManager(), manager, new StateOverlaySystem(), new OverlayStepper())
          .build().register("clientContext", ClientGameplayContext.fromGlobals()));
      entity = world.create();
      stateId = skill == SkillId.FERAL_RAGE ? StateId.FERALRAGE : StateId.MAUL;
      States.Entry definition = Riiablo.files.States.get(stateId);
      assertEquals(16, definition.setFunc);
      assertEquals(11, definition.removeFunc);
      state = world.getMapper(UnitStates.class).create(entity).init(entity).stateList
          .addState(stateId, 500, level, entity);
      state.skillId = skill;
      world.setDelta(1f / 25f);
    }
    List<Overlay> layers() {
      List<Overlay> result = active(world.getMapper(Overlay.class).get(entity));
      result.removeIf(layer -> layer.stateId != stateId);
      return result;
    }
    @Override public void close() {
      world.dispose();
      Riiablo.assets = savedAssets;
      Riiablo.colors = savedColors;
    }
  }
}
