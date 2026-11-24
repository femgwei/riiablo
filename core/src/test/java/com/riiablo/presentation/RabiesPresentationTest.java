package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.Overlay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class RabiesPresentationTest extends RiiabloTest {
  @Test void remoteInfectionUsesActualThirtyFrameResourceNativeClockBlendAndOffsets() {
    try (Fixture f = new Fixture()) {
      f.source.stateList.addState(StateId.RABIES, 100, 1, 77).skillId = 238;
      f.sync(); f.tick();
      Overlay layer = f.overlay();
      assertNotNull(layer); assertEquals("rabiesplague", layer.entry.overlay);
      assertTrue(layer.usesNativeRendering()); assertFalse(layer.entry.PreDraw);
      assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
      assertEquals(-14f, AuraRenderPolicy.offsetY(layer.entry, 0));
      assertEquals(0f, AuraRenderPolicy.offsetY(layer.entry, 1));
      assertEquals(14f, AuraRenderPolicy.offsetY(layer.entry, 2));
      byte[] dcc = Riiablo.mpqs.resolve(layer.assetDescriptor.fileName).readBytes();
      assertEquals(1, dcc[2] & 255);
      assertEquals(30, ByteBuffer.wrap(dcc).order(ByteOrder.LITTLE_ENDIAN).getInt(3));
      UnitStates remote = f.world.getMapper(UnitStates.class).get(f.entity);
      assertTrue(remote.snapshotOnly);
      for (int i = 0; i < 105; i++) f.world.process();
      assertEquals(100, remote.stateList.getState(StateId.RABIES).duration,
          "the client StateUpdater waits for authoritative infection removal");
      assertSame(layer, f.overlay());
      System.out.println("RABIES_OVERLAY path=" + layer.assetDescriptor.fileName
          + " frames=30 directions=1 rate=" + layer.entry.AnimRate + " trans=" + layer.entry.Trans);
    }
  }

  @Test void poisonAndInfectionSnapshotsHaveIndependentRemovalAndRefreshDoesNotRestartTheRing() {
    try (Fixture f = new Fixture()) {
      f.source.stateList.addState(StateId.RABIES, 100, 1, 77).skillId = 238;
      f.source.stateList.addState(StateId.POISON, 30, 1, 77);
      f.sync(); f.tick();
      Overlay ring = f.overlay(); ring.animation.setFrame(10);
      clearInvocations(f.assets);
      f.source.stateList.removeState(StateId.POISON);
      f.source.stateList.getState(StateId.RABIES).duration = 50;
      f.sync(); f.tick();
      assertSame(ring, f.overlay()); assertTrue(ring.animation.getFrame() >= 10);
      assertEquals(1, ring.layers.size);
      assertEquals("poisonhit", ring.layers.first().entry.overlay,
          "ordinary poison also owns its independent native activation flash");
      assertFalse(ring.layers.first().persistent);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
      f.source.stateList.addState(StateId.POISON, 20, 1, 77);
      f.source.stateList.removeState(StateId.RABIES);
      f.sync(); f.tick();
      assertNotNull(f.overlay());
      assertNull(f.overlay().entry, "the persistent infection root must be retired");
      assertEquals(1, f.overlay().layers.size);
      assertEquals("poisonhit", f.overlay().layers.first().entry.overlay);
      assertTrue(f.world.getMapper(UnitStates.class).get(f.entity).stateList.hasState(StateId.POISON));
      verify(f.assets).unload("data/global/overlays/Expansion/rabies.dcc");
    }
  }

  @Test void pendingInfectionIsCancelledAndEntityComponentRemovalPreservesAnUnrelatedBuff() {
    try (Fixture f = new Fixture()) {
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false);
      f.source.stateList.addState(StateId.RABIES, 100, 1, 77);
      f.sync(); f.tick(); assertFalse(f.overlay().isLoaded);
      f.source.stateList.removeState(StateId.RABIES); f.sync(); f.tick();
      assertNull(f.overlay());
      verify(f.assets).unload("data/global/overlays/Expansion/rabies.dcc");
      when(f.assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      f.source.stateList.addState(StateId.RABIES, 100, 1, 77);
      f.sync(); f.tick();
      String might = Riiablo.files.States.get(StateId.MIGHT).overlays[0];
      f.manager.setPersistent(f.entity, StateId.MIGHT, might);
      f.world.getMapper(UnitStates.class).remove(f.entity); f.world.process();
      assertNotNull(f.overlay()); assertNull(f.overlay().entry);
      assertEquals(1, f.overlay().layers.size); assertEquals(might, f.overlay().layers.first().entry.overlay);
    }
  }

  @Test void propagationRowsHaveNullCelAndCastEventDoesNotManufactureAVisibleProjectile() {
    try (Fixture f = new Fixture()) {
      for (String name : new String[] {"rabiesplague", "rabiescontagion"}) {
        Missiles.Entry missile = Riiablo.files.Missiles.get(name);
        assertEquals("null", missile.CelFile);
        System.out.println("RABIES_CARRIER name=" + name + " cel=" + missile.CelFile
            + " do=" + missile.pSrvDoFunc + " hit=" + missile.pSrvHitFunc);
      }
      Skills.Entry skill = Riiablo.files.skills.get(238);
      assertTrue(skill.castoverlay == null || skill.castoverlay.isEmpty());
      assertTrue(skill.cltmissile == null || skill.cltmissile.isEmpty());
      clearInvocations(f.factory);
      f.world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          f.entity, 238, -1, new Vector2(1,0), skill.srvdofunc, skill.cltdofunc));
      verifyNoInteractions(f.factory);
      assertNull(f.overlay(), "the infected target state, not the cast event, owns this visual");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager savedAssets = Riiablo.assets;
    final com.riiablo.Colors savedColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final EntityFactory factory = mock(EntityFactory.class);
    final OverlayManager manager = new OverlayManager();
    final UnitStates source = new UnitStates().init(8);
    final World world;
    final int entity;
    Fixture() {
      Riiablo.assets = assets; Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1); when(dcc.getNumFramesPerDir()).thenReturn(30);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true); when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
          new StateUpdater(), factory, manager, new StateOverlaySystem(), new OverlayStepper(), new SkillCastHandler())
          .build().register("factory", factory).register("map", new Map(0,0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      entity = world.create(); world.getMapper(UnitStates.class).create(entity).init(entity);
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(entity);
      world.setDelta(1f/25f);
    }
    void sync() {
      StateSerializer serializer = new StateSerializer(); FlatBufferBuilder b = new FlatBufferBuilder(256);
      int data = serializer.putData(b, source);
      int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
      int components = EntitySync.createComponentVector(b, new int[] {data});
      b.finish(EntitySync.createEntitySync(b, entity, 0, 0, types, components, 0,0,0,0,0,-1));
      serializer.getData(EntitySync.getRootAsEntitySync(b.dataBuffer()), 0, world.getMapper(UnitStates.class).get(entity));
    }
    void tick() { world.process(); world.process(); }
    Overlay overlay() { return world.getMapper(Overlay.class).get(entity); }
    @Override public void close() { world.dispose(); Riiablo.assets = savedAssets; Riiablo.colors = savedColors; }
  }
}
