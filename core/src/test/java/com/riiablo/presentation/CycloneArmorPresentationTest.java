package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.DCC;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.Overlay;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class CycloneArmorPresentationTest extends RiiabloTest {
  @Test void nativeRoundedThirdBoundariesSelectThreeTwoAndOnePairs() {
    try (Fixture f = new Fixture()) {
      assertEquals(8, Riiablo.files.States.get(StateId.CYCLONEARMOR).setFunc);
      assertEquals(5, Riiablo.files.States.get(StateId.CYCLONEARMOR).removeFunc);
      int third = 40 * 256 / 3;
      for (int[] sample : new int[][] {{10240, 3}, {2*third, 3},
          {2*third-1, 2}, {third, 2}, {third-1, 1}, {1, 1}, {0, 0}}) {
        f.pool(sample[0], 10240);
        f.tick();
        assertEquals(sample[1], NativeCycloneArmorPresentation.count(f.state()));
        assertPairs(f.layers(), sample[1]);
      }
    }
  }

  @Test void damageDropsHigherPairsWithoutRestartingSurvivingAnimations() {
    try (Fixture f = new Fixture()) {
      f.tick();
      Overlay front = f.find("cyclonearmor1front"), back = f.find("cyclonearmor1back");
      front.animation.setFrame(5); back.animation.setFrame(5);
      clearInvocations(f.assets);
      f.pool(2000, 10240); f.tick();
      assertPairs(f.layers(), 1);
      assertSame(front, f.find("cyclonearmor1front"));
      assertSame(back, f.find("cyclonearmor1back"));
      assertTrue(front.animation.getFrame() >= 5);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets).unload("data/global/overlays/Expansion/CycloneArmorBFront.dcc");
      verify(f.assets).unload("data/global/overlays/Expansion/CycloneArmorCBack.dcc");
    }
  }

  @Test void countIncreaseRebuildsPairsButSameLevelFullRecastKeepsTheirClocks() {
    try (Fixture f = new Fixture()) {
      f.pool(1000, 10240); f.tick();
      com.riiablo.codec.Animation old = f.find("cyclonearmor1front").animation;
      old.setFrame(7);
      clearInvocations(f.assets);
      f.pool(10240, 10240); f.tick();
      assertPairs(f.layers(), 3);
      // The pooled component may reuse its Animation object; rebuilding must
      // reset its cursor and reacquire every selected asset regardless.
      assertTrue(f.find("cyclonearmor1front").animation.getFrame() < 7);
      verify(f.assets, times(6)).load(any(AssetDescriptor.class));
      f.find("cyclonearmor1front").animation.setFrame(7);
      clearInvocations(f.assets);
      f.world.process();
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
      int revision = f.state().presentationRevision;
      f.cast(1); f.tick();
      assertEquals(revision, f.state().presentationRevision);
      assertTrue(f.find("cyclonearmor1front").animation.getFrame() >= 7);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      f.cast(5); f.tick();
      assertTrue(f.state().presentationRevision > revision);
      assertTrue(f.find("cyclonearmor1front").animation.getFrame() < 7);
      verify(f.assets, times(6)).load(any(AssetDescriptor.class));
    }
  }

  @Test void remoteSnapshotKeepsExactLargeSynergizedPoolsAndStages() {
    try (Fixture f = new Fixture()) {
      UnitStates source = new UnitStates().init(77);
      UnitState state = DruidSkills.applyCycloneArmorState(source.stateList,
          Riiablo.files.skills.get(235), 20, 77, name -> 20);
      int max = state.getStatContributionValue(Stat.bonearmormax);
      assertTrue(max > Short.MAX_VALUE);
      for (int[] sample : new int[][] {{max, 3}, {max/2, 2}, {max/4, 1}}) {
        state.setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, sample[0]);
        state.runtimeValue = sample[0];
        EntitySync packet = packet(source);
        UnitStates remote = f.world.getMapper(UnitStates.class).get(f.entity);
        new StateSerializer().getData(packet, 0, remote);
        f.tick();
        assertTrue(remote.snapshotOnly);
        assertEquals(sample[0], f.state().runtimeValue);
        assertEquals(sample[0], f.state().getStatContributionValue(Stat.bonearmor));
        assertEquals(max, f.state().getStatContributionValue(Stat.bonearmormax));
        assertPairs(f.layers(), sample[1]);
      }
    }
  }

  @Test void removalClearsAllSixLayersAndPreservesUnrelatedEffects() {
    try (Fixture f = new Fixture()) {
      f.tick();
      String might = Riiablo.files.States.get(StateId.MIGHT).overlays[0];
      f.manager.setPersistent(f.entity, StateId.MIGHT, might);
      f.manager.set(f.entity, "healing");
      f.states().stateList.removeState(StateId.CYCLONEARMOR);
      f.world.process();
      assertTrue(f.layers().isEmpty());
      assertNotNull(f.find(might));
      assertNotNull(f.find("healing"));
      f.cast(1); f.tick();
      assertPairs(f.layers(), 3);
      f.world.getMapper(UnitStates.class).remove(f.entity);
      f.world.process();
      assertTrue(f.layers().isEmpty());
      assertNotNull(f.find(might));
    }
  }

  @Test void allSixNativeAssetsUseSplitDrawAdditiveAndTwentyFiveHzClock() {
    try (Fixture f = new Fixture()) {
      f.tick();
      for (Overlay layer : f.layers()) {
        assertTrue(layer.usesNativeRendering());
        assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
        assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
        assertEquals(layer.entry.overlay.endsWith("back"), layer.entry.PreDraw);
        assertTrue(Riiablo.mpqs.contains(layer.assetDescriptor.fileName));
      }
    }
  }

  private static EntitySync packet(UnitStates source) {
    FlatBufferBuilder b = new FlatBufferBuilder(512);
    int data = new StateSerializer().putData(b, source);
    int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
    int components = EntitySync.createComponentVector(b, new int[] {data});
    b.finish(EntitySync.createEntitySync(b, 77, 0, 0, types, components, 0, 0, 0, 0, 0, -1));
    return EntitySync.getRootAsEntitySync(b.dataBuffer());
  }

  private static void assertPairs(List<Overlay> layers, int count) {
    assertEquals(count * 2, layers.size());
    for (int i = 1; i <= count; i++) {
      final String prefix = "cyclonearmor" + i;
      assertTrue(layers.stream().anyMatch(o -> (prefix + "front").equals(o.entry.overlay)));
      assertTrue(layers.stream().anyMatch(o -> (prefix + "back").equals(o.entry.overlay)));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager savedAssets = Riiablo.assets;
    final com.riiablo.Colors savedColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final OverlayManager manager = new OverlayManager();
    final World world;
    final int entity;
    Fixture() {
      Riiablo.assets = assets; Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1);
      when(dcc.getNumFramesPerDir()).thenReturn(11);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
          manager, new StateOverlaySystem(), new OverlayStepper())
          .build().register("clientContext", ClientGameplayContext.fromGlobals()));
      entity = world.create();
      world.getMapper(UnitStates.class).create(entity).init(entity);
      world.setDelta(1f/25f); cast(1);
    }
    UnitStates states() { return world.getMapper(UnitStates.class).get(entity); }
    UnitState state() { return states().stateList.getState(StateId.CYCLONEARMOR); }
    void cast(int level) { DruidSkills.applyCycloneArmorState(states().stateList,
        Riiablo.files.skills.get(235), level, entity, name -> 0); }
    void pool(int current, int max) {
      state().setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, current);
      state().setStatContribution(Stat.bonearmormax, 0, NativeStatResolver.Operation.ADD, max);
      state().runtimeValue = current;
    }
    void tick() { world.process(); world.process(); }
    List<Overlay> all() {
      List<Overlay> layers = new ArrayList<>();
      Overlay root = world.getMapper(Overlay.class).get(entity);
      if (root == null) return layers;
      if (root.entry != null) layers.add(root);
      for (Overlay layer : root.layers) if (layer.entry != null) layers.add(layer);
      return layers;
    }
    List<Overlay> layers() {
      List<Overlay> layers = all(); layers.removeIf(o -> o.stateId != StateId.CYCLONEARMOR); return layers;
    }
    Overlay find(String name) { return all().stream().filter(o -> name.equals(o.entry.overlay)).findFirst().orElse(null); }
    @Override public void close() { world.dispose(); Riiablo.assets = savedAssets; Riiablo.colors = savedColors; }
  }
}
