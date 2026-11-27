package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
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
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.Overlay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Actual 1.10f resources and state/asset ownership, with no window, texture or GPU. */
class BoneArmorPresentationTest extends RiiabloTest {
  @Test void originalEarlyHitAndDepletedOverlayStagesReplayWithoutCancellingNullTimers() {
    JsonValue original = new JsonReader().parse(com.badlogic.gdx.Gdx.files.classpath(
        "original/necromancer-bone-armor-hit-1.10f.json"));
    JsonValue full = original.get("full"), hit = original.get("early_hit");
    try (Fixture f = new Fixture()) {
      f.pool(full.getInt("current_fixed"), full.getInt("maximum_fixed"));
      java.util.Set<String> stages = new java.util.HashSet<>();
      stages.add("0:[]");
      for (int tick = 0; tick < 60; tick++) {
        if (tick == 3) f.pool(hit.getInt("current_fixed"), hit.getInt("maximum_fixed"));
        if (tick == 40) f.states().stateList.removeState(StateId.BONEARMOR);
        f.tick();
        int count = tick >= 40 ? 0 : NativeCycloneArmorPresentation.count(f.state());
        List<Integer> ids = new ArrayList<>();
        for (Overlay layer : f.layers()) ids.add(Riiablo.files.Overlay.index(layer.entry.overlay));
        java.util.Collections.sort(ids); stages.add(count + ":" + ids);
      }
      int compared = 0;
      for (JsonValue stage : original.get("state_and_overlay_stages")) {
        List<Integer> ids = new ArrayList<>();
        for (int id : stage.get("overlay_ids").asIntArray()) ids.add(id);
        java.util.Collections.sort(ids);
        String key = stage.getInt("native_count") + ":" + ids;
        assertTrue(stages.contains(key), "original state/overlay stage " + key);
        compared++;
      }
      assertEquals(8, compared);
      assertTrue(f.layers().isEmpty());
    }
  }

  @Test void earlyDamageRemovesMatchingEmergenceAndKeepsNativeNullTimerContinuations() {
    try (Fixture f = new Fixture()) {
      f.ticks(2);
      assertNotNull(f.find(1, "bonearmor_cast"));
      Overlay timer2 = f.find(2, "bonearmor_null1"), timer3 = f.find(3, "bonearmor_null2");
      f.pool(5999, 9000); f.tick(); // Cross one integer-third boundary during emergence.
      assertNull(f.find(1, "bonearmor_cast"), "native removal matches mode4's front continuation");
      assertSame(timer2, f.find(2, "bonearmor_null1"));
      assertSame(timer3, f.find(3, "bonearmor_null2"), "null timer is not a front/back match");
      assertEquals(2, f.layers().size());
      f.ticks(27);
      assertNotNull(f.find(2, "bonearmor_front"));
      assertNotNull(f.find(3, "bonearmor_front"));
      assertNull(f.find(1, "bonearmor_front"));
      assertEquals(4, f.layers().size());
      Overlay surviving = f.find(2, "bonearmor_front");
      int cursor = surviving.animation.getFrame();
      f.pool(1, 9000); f.tick();
      assertSame(surviving, f.find(2, "bonearmor_front"));
      assertEquals((cursor + 1) % 24, surviving.animation.getFrame());
      assertNull(f.find(3, "bonearmor_front"), "later hit retires the newest matching loop pair");
      f.states().stateList.removeState(StateId.BONEARMOR); f.tick(); f.ticks(24);
      assertTrue(f.layers().isEmpty());
    }
  }

  @Test void everySampledOriginalThreePairBatchIsReproducedByTheProductionClock() {
    JsonValue original = new JsonReader().parse(com.badlogic.gdx.Gdx.files.classpath(
        "original/necromancer-bone-armor-draw-1.10f.json"));
    try (Fixture f = new Fixture()) {
      java.util.Set<String> produced = new java.util.HashSet<>();
      for (int tick = 0; tick < 80; tick++) {
        f.tick();
        List<String> values = new ArrayList<>();
        for (Overlay layer : f.layers()) {
          int id = Riiablo.files.Overlay.index(layer.entry.overlay);
          if (id == 141 || id == 142) values.add(id + ":" + layer.animation.getFrame());
        }
        if (values.size() == 6) {
          java.util.Collections.sort(values); produced.add(values.toString());
        }
      }
      int matched = 0;
      for (JsonValue batch : original.get("render_batches")) {
        JsonValue counts = batch.get("overlay_counts");
        if (counts.getInt("141", 0) != 3 || counts.getInt("142", 0) != 3) continue;
        List<String> values = new ArrayList<>();
        for (JsonValue draw : batch.get("draws")) {
          int id = draw.getInt("overlay");
          if (id == 141 || id == 142) values.add(id + ":" + draw.getInt("frame"));
        }
        java.util.Collections.sort(values);
        assertTrue(produced.contains(values.toString()), "original render batch " + batch.getInt("index")
            + " phase tuple " + values + " was not produced");
        matched++;
      }
      assertEquals(47, matched, "preserve all observed complete triple pairs, including duplicate ids");
      System.out.println("BONE_ARMOR_ORIGINAL_REPLAY matchedBatches=" + matched
          + " localPhaseTuples=" + produced.size());
    }
  }

  @Test void actualNativeAssetsContainEveryObservedOriginalVisibleAndTimerFrame() {
    JsonValue original = new JsonReader().parse(com.badlogic.gdx.Gdx.files.classpath(
        "original/necromancer-bone-armor-draw-1.10f.json"));
    for (JsonValue observed : original.get("observed")) {
      com.riiablo.codec.excel.Overlay.Entry row = Riiablo.files.Overlay.get(observed.getInt("overlay"));
      assertEquals(observed.getString("name"), row.overlay);
      int frames = ByteBuffer.wrap(Riiablo.mpqs.resolve(
          "data/global/overlays/" + row.Filename + ".dcc").readBytes())
          .order(ByteOrder.LITTLE_ENDIAN).getInt(3);
      for (int frame : observed.get("frames").asIntArray())
        assertTrue(frame >= 0 && frame < frames, row.overlay + " frame " + frame);
      assertEquals("minimized", observed.get("window_states").getString(0));
    }
  }

  @Test void retailStateUsesSetfuncSixAndTheThreeStaggeredEmergenceChains() {
    try (Fixture f = new Fixture()) {
      assertEquals(6, Riiablo.files.States.get(StateId.BONEARMOR).setFunc);
      assertEquals(3, Riiablo.files.States.get(StateId.BONEARMOR).removeFunc);
      f.tick();
      assertEquals(3, f.layers().size());
      assertNotNull(f.find(1, "bonearmor_cast"));
      assertNotNull(f.find(2, "bonearmor_null1"));
      assertNotNull(f.find(3, "bonearmor_null2"));
      f.ticks(7);
      assertEquals(7, f.find(1, "bonearmor_cast").animation.getFrame());
      f.tick(); // Eight ticks at AnimRate=4 consume both null1 frames.
      assertNotNull(f.find(2, "bonearmor_cast"));
      assertNull(f.find(2, "bonearmor_null1"));
      f.ticks(3);
      assertEquals(11, f.find(1, "bonearmor_cast").animation.getFrame());
      f.tick();
      assertNull(f.find(1, "bonearmor_cast"));
      assertPair(f, 1, 1);
      f.ticks(4);
      assertNotNull(f.find(3, "bonearmor_cast"));
      assertNull(f.find(3, "bonearmor_null2"));
      f.ticks(4);
      assertPair(f, 1, 9); assertPair(f, 2, 2);
      f.ticks(8);
      assertPair(f, 1, 17); assertPair(f, 2, 10); assertPair(f, 3, 2);
      assertEquals(6, f.layers().size(), "same resource must coexist in three phase groups");
      f.ticks(24);
      assertPair(f, 1, 17); assertPair(f, 2, 10); assertPair(f, 3, 2);
      for (Overlay layer : f.layers()) {
        assertTrue(layer.usesNativeRendering());
        assertEquals(24, layer.animation.getNumFramesPerDir());
        assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
        assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
        assertEquals(layer.entry.overlay.endsWith("back"), layer.entry.PreDraw);
      }
    }
  }

  @Test void retailDccLengthsAndRatesDriveBothTimersAndVisibleStages() {
    try (Fixture f = new Fixture()) {
      for (String name : new String[] {"bonearmor_cast", "bonearmor_front", "bonearmor_back",
          "bonearmor_null1", "bonearmor_null2"}) {
        com.riiablo.codec.excel.Overlay.Entry row = Riiablo.files.Overlay.get(name);
        byte[] raw = Riiablo.mpqs.resolve("data/global/overlays/" + row.Filename + ".dcc").readBytes();
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, raw[2]);
        assertEquals(row.Frames, header.getInt(3));
        System.out.println("BONE_ARMOR_RESOURCE name=" + name + " id=" + Riiablo.files.Overlay.index(name)
            + " frames=" + header.getInt(3) + " rate=" + row.AnimRate + " predraw=" + row.PreDraw);
      }
      assertEquals(140, Riiablo.files.Overlay.index("bonearmor_cast"));
      assertEquals(141, Riiablo.files.Overlay.index("bonearmor_front"));
      assertEquals(142, Riiablo.files.Overlay.index("bonearmor_back"));
    }
  }

  @Test void damageRemovesNewestPairsAndKeepsSurvivingClocksAndReferences() {
    try (Fixture f = new Fixture()) {
      f.ticks(31);
      Overlay front = f.find(1, "bonearmor_front"), back = f.find(1, "bonearmor_back");
      int frame = front.animation.getFrame();
      clearInvocations(f.assets);
      f.pool(3000, 9000); f.tick();
      assertEquals(4, f.layers().size(), "rounded threshold equality retains two groups");
      assertNull(f.find(3, "bonearmor_front"));
      f.pool(2999, 9000); f.tick();
      assertEquals(2, f.layers().size());
      assertSame(front, f.find(1, "bonearmor_front"));
      assertSame(back, f.find(1, "bonearmor_back"));
      assertEquals((frame + 2) % 24, front.animation.getFrame());
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, times(2)).unload("data/global/overlays/BoneShieldFront.dcc");
      verify(f.assets, times(2)).unload("data/global/overlays/BoneShieldBack.dcc");
      f.pool(0, 9000); f.tick();
      assertTrue(f.layers().isEmpty());
    }
  }

  @Test void restoringAbsorptionRebuildsWhileUnchangedStateDoesNotRestartOrReload() {
    try (Fixture f = new Fixture()) {
      f.ticks(32); clearInvocations(f.assets);
      f.ticks(3);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
      f.pool(1, 9000); f.tick();
      f.pool(9000, 9000); f.tick();
      assertEquals(3, f.layers().size());
      assertEquals(0, f.find(1, "bonearmor_cast").animation.getFrame());
      assertNotNull(f.find(2, "bonearmor_null1"));
      f.ticks(30);
      Overlay front = f.find(1, "bonearmor_front");
      f.cast(); f.tick();
      assertSame(front, f.find(1, "bonearmor_front"), "same-level full recast retains the native list clocks");
      f.cast(21); f.tick();
      assertNotNull(f.find(1, "bonearmor_cast"));
      assertEquals(3, f.layers().size());
    }
  }

  @Test void removingStateOrUnitStatesCancelsPendingChainsAndPreservesOtherEffects() {
    try (Fixture f = new Fixture()) {
      f.tick();
      f.manager.setPersistent(f.entity, StateId.MIGHT, "aura_might_front");
      f.manager.set(f.entity, "healing");
      f.states().stateList.removeState(StateId.BONEARMOR); f.tick();
      assertTrue(f.layers().isEmpty());
      assertTrue(f.all().stream().anyMatch(o -> o.stateId == StateId.MIGHT));
      assertTrue(f.all().stream().anyMatch(o -> "healing".equals(o.entry.overlay)));
      f.ticks(32);
      assertTrue(f.layers().isEmpty(), "retired null timers must not resurrect a shield");
      f.cast(); f.tick();
      f.world.getMapper(UnitStates.class).remove(f.entity); f.tick();
      assertTrue(f.layers().isEmpty());
      assertTrue(f.all().stream().anyMatch(o -> o.stateId == StateId.MIGHT));
    }
  }

  @Test void pendingAssetsDoNotCreateUnloadedPairsOrRepeatedlyAcquireTheSameStage() {
    try (Fixture f = new Fixture()) {
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false);
      f.tick(); clearInvocations(f.assets);
      f.ticks(30);
      assertEquals(3, f.layers().size());
      assertTrue(f.layers().stream().noneMatch(o -> o.isLoaded));
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      f.ticks(29);
      assertEquals(6, f.layers().size());
      assertTrue(f.layers().stream().allMatch(o -> o.isLoaded));
    }
  }

  @Test void authoritativeSnapshotRetainsLargeBoneArmorPoolsAndStageRemoval() {
    try (Fixture f = new Fixture()) {
      f.pool(80000, 120000);
      UnitStates source = f.states();
      FlatBufferBuilder b = new FlatBufferBuilder(512);
      int data = new StateSerializer().putData(b, source);
      int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
      int components = EntitySync.createComponentVector(b, new int[] {data});
      b.finish(EntitySync.createEntitySync(b, 77, 0, 0, types, components, 0, 0, 0, 0, 0, -1));
      new StateSerializer().getData(EntitySync.getRootAsEntitySync(b.dataBuffer()), 0, source);
      assertTrue(source.snapshotOnly);
      assertEquals(80000, f.state().getStatContributionValue(Stat.bonearmor));
      assertEquals(120000, f.state().getStatContributionValue(Stat.bonearmormax));
      f.ticks(29);
      assertEquals(6, f.layers().size());
      f.pool(1, 120000); f.tick();
      assertEquals(2, f.layers().size());
    }
  }

  private static void assertPair(Fixture f, int group, int frame) {
    for (String side : new String[] {"front", "back"}) {
      Overlay layer = f.find(group, "bonearmor_" + side);
      assertNotNull(layer, "missing " + side + " group " + group);
      assertEquals(frame, layer.animation.getFrame(), side + " group " + group);
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
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenAnswer(call -> {
        AssetDescriptor descriptor = call.getArgument(0);
        byte[] raw = Riiablo.mpqs.resolve(descriptor.fileName).readBytes();
        DCC dcc = mock(DCC.class);
        when(dcc.getNumDirections()).thenReturn(raw[2] & 255);
        when(dcc.getNumFramesPerDir()).thenReturn(ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt(3));
        when(dcc.getBox(anyInt())).thenReturn(new BBox());
        return dcc;
      });
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
          manager, new StateOverlaySystem(), new OverlayStepper()).build()
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      entity = world.create(); world.getMapper(UnitStates.class).create(entity).init(entity);
      world.setDelta(1f / 25f); cast();
    }
    void cast() { cast(20); }
    void cast(int level) { NecromancerSkills.applyBoneArmorState(states().stateList, Riiablo.files.skills.get(68),
        level, entity, name -> 0, name -> Riiablo.files.skills.get(name)); }
    UnitStates states() { return world.getMapper(UnitStates.class).get(entity); }
    UnitState state() { return states().stateList.getState(StateId.BONEARMOR); }
    void pool(int current, int max) {
      state().setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, current);
      state().setStatContribution(Stat.bonearmormax, 0, NativeStatResolver.Operation.ADD, max);
    }
    void tick() { world.process(); }
    void ticks(int n) { for (int i = 0; i < n; i++) tick(); }
    List<Overlay> all() {
      List<Overlay> layers = new ArrayList<>();
      Overlay root = world.getMapper(Overlay.class).get(entity);
      if (root == null) return layers;
      if (root.entry != null) layers.add(root);
      for (Overlay layer : root.layers) if (layer.entry != null) layers.add(layer);
      return layers;
    }
    List<Overlay> layers() {
      List<Overlay> list = all(); list.removeIf(o -> o.stateId != StateId.BONEARMOR); return list;
    }
    Overlay find(int group, String name) {
      return layers().stream().filter(o -> o.boneArmorGroup == group && name.equals(o.entry.overlay))
          .findFirst().orElse(null);
    }
    @Override public void close() { world.dispose(); Riiablo.assets = savedAssets; Riiablo.colors = savedColors; }
  }
}
