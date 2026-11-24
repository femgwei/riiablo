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
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.Overlay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidSpiritPresentationTest extends RiiabloTest {
  @Test void linkedAuraRowsSeparateControllerAndBeneficiaryStates() {
    for (int id : new int[] {226, 236, 246}) {
      Skills.Entry summon = Riiablo.files.skills.get(id);
      Skills.Entry aura = DruidSkills.getSummonAuraSkill(Riiablo.files, summon);
      assertNotNull(aura);
      System.out.println("SPIRIT_AURA skill=" + id + " aura=" + aura.Id + " name=" + aura.skill
          + " source=" + aura.aurastate + " target=" + aura.auratargetstate
          + " filter=" + aura.aurafilter + " range=" + aura.aurarangecalc);
      assertTrue(aura.aurastate.endsWith("control"));
      assertFalse(aura.auratargetstate.endsWith("control"));
      int controller = Riiablo.files.States.get(aura.aurastate).id;
      assertEquals(controller, DruidSkills.getSummonAuraControllerState(Riiablo.files, summon));
      for (String name : Riiablo.files.States.get(controller).overlays)
        assertTrue(name == null || name.isEmpty());
    }
  }

  @Test void controllerSnapshotsStayInvisibleAndBeneficiariesUseNativeResourceClockAndBlend() {
    for (int[] ids : new int[][] {{226, 161, 149, 11}, {236, 162, 148, 30}, {246, 163, 147, 12}}) {
      try (Fixture f = new Fixture(ids[3])) {
        UnitStates controller = new UnitStates().init(77);
        controller.stateList.addStateLayer(ids[1], 0, 8, 11, ids[0]);
        f.snapshot(controller); f.tick();
        assertNull(f.overlay(), "native controller carries no beneficiary ring");
        UnitStates ally = new UnitStates().init(78);
        ally.stateList.addStateLayer(ids[2], 0, 8, 77, ids[0]);
        f.snapshot(ally); f.tick();
        Overlay layer = f.overlay();
        assertNotNull(layer); assertEquals(0, layer.layers.size);
        assertEquals(ids[2], layer.stateId);
        assertTrue(layer.nativeAura); assertTrue(layer.entry.PreDraw);
        assertEquals(AuraRenderPolicy.frameDuration(layer.entry.AnimRate),
            layer.animation.getFrameDuration(), .000001f);
        assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
        byte[] dcc = Riiablo.mpqs.resolve(layer.assetDescriptor.fileName).readBytes();
        ByteBuffer header = ByteBuffer.wrap(dcc).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, dcc[2] & 255);
        assertEquals(ids[3], header.getInt(3));
        System.out.println("SPIRIT_DCC path=" + layer.assetDescriptor.fileName
            + " frames=" + header.getInt(3) + " rate=" + layer.entry.AnimRate);
      }
    }
  }

  @Test void twoWolverineSourcesShareOneRingAndRemovingOneDoesNotRestartIt() {
    try (Fixture f = new Fixture(30)) {
      UnitStates ally = new UnitStates().init(11);
      ally.stateList.addStateLayer(StateId.WOLVERINE, 0, 8, 77, 236);
      ally.stateList.addStateLayer(StateId.WOLVERINE, 0, 12, 88, 236);
      f.snapshot(ally); f.tick();
      Overlay ring = f.overlay();
      assertEquals(0, ring.layers.size);
      ring.animation.setFrame(10);
      clearInvocations(f.assets);
      ally.stateList.removeStateLayer(StateId.WOLVERINE, 77, 236);
      f.snapshot(ally); f.tick();
      assertSame(ring, f.overlay());
      assertTrue(ring.animation.getFrame() >= 10);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
      f.manager.setPersistent(f.entity, StateId.MIGHT,
          Riiablo.files.States.get(StateId.MIGHT).overlays[0]);
      ally.stateList.removeStateLayer(StateId.WOLVERINE, 88, 236);
      f.snapshot(ally); f.world.process();
      assertNotNull(f.overlay());
      assertTrue(f.overlay().entry == null || !"heart_of_wolverine".equals(f.overlay().entry.overlay));
      assertEquals(1, f.overlay().layers.size, "unrelated Might remains after the primary ring retires");
      verify(f.assets).unload("data/global/overlays/expansion/Heart_of_Wolverine.dcc");
    }
  }

  @Test void leavingRangeBeforeAssetLoadCancelsPendingRingAndReentryLoadsOnce() {
    try (Fixture f = new Fixture(30)) {
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false);
      UnitStates ally = new UnitStates().init(11);
      ally.stateList.addStateLayer(StateId.WOLVERINE, 0, 8, 77, 236);
      f.snapshot(ally); f.tick();
      assertFalse(f.overlay().isLoaded);
      f.snapshot(new UnitStates().init(11)); f.tick();
      assertNull(f.overlay());
      verify(f.assets).unload("data/global/overlays/expansion/Heart_of_Wolverine.dcc");
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      clearInvocations(f.assets);
      f.snapshot(ally); f.tick();
      assertTrue(f.overlay().isLoaded);
      verify(f.assets).load(any(AssetDescriptor.class));
      f.world.getMapper(UnitStates.class).remove(f.entity); f.world.process();
      assertNull(f.overlay());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager savedAssets = Riiablo.assets;
    final com.riiablo.Colors savedColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final OverlayManager manager = new OverlayManager();
    final World world;
    final int entity;
    Fixture(int frames) {
      Riiablo.assets = assets; Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1);
      when(dcc.getNumFramesPerDir()).thenReturn(frames);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
          manager, new StateOverlaySystem(), new OverlayStepper())
          .build().register("clientContext", ClientGameplayContext.fromGlobals()));
      entity = world.create(); world.getMapper(UnitStates.class).create(entity).init(entity);
      world.setDelta(1f/25f);
    }
    void snapshot(UnitStates source) {
      StateSerializer serializer = new StateSerializer();
      FlatBufferBuilder b = new FlatBufferBuilder(512);
      int data = serializer.putData(b, source);
      int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
      int components = EntitySync.createComponentVector(b, new int[] {data});
      b.finish(EntitySync.createEntitySync(b, entity, 0, 0, types, components, 0, 0, 0, 0, 0, -1));
      serializer.getData(EntitySync.getRootAsEntitySync(b.dataBuffer()), 0,
          world.getMapper(UnitStates.class).get(entity));
    }
    void tick() { world.process(); world.process(); }
    Overlay overlay() { return world.getMapper(Overlay.class).get(entity); }
    @Override public void close() { world.dispose(); Riiablo.assets = savedAssets; Riiablo.colors = savedColors; }
  }
}
