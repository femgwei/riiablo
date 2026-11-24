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
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.Overlay;
import com.riiablo.save.CharData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FenrisRagePresentationTest extends RiiabloTest {
  @Test void corpseConsumptionPublishesOneNativeRageLayerAndHiddenCorpseSnapshot() {
    try (Fixture f = new Fixture()) {
      assertEquals(137, f.skill.srvdofunc);
      f.eat(f.corpse);
      UnitState rage = f.rage();
      assertNotNull(rage); assertEquals(500, rage.duration);
      assertEquals(100, rage.resolvedDamageModifier());
      assertFalse(f.authority.getMapper(Corpse.class).get(f.corpse).usable);
      assertTrue(f.states(f.corpse).stateList.hasState(StateId.CORPSE_NODRAW));
      f.sync(f.wolf, f.replica); f.tick();
      Overlay layer = f.overlay();
      assertNotNull(layer); assertEquals("direwolfcharged", layer.entry.overlay);
      assertEquals(0, layer.layers.size, "duplicate States overlay1/2 names are one overlay");
      assertTrue(layer.usesNativeRendering());
      assertEquals(.04f, layer.animation.getFrameDuration(), .000001f);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, layer.animation.getLayer(0).getBlendMode());
      assertFalse(layer.entry.PreDraw);
      byte[] raw = Riiablo.mpqs.resolve(layer.assetDescriptor.fileName).readBytes();
      assertEquals(1, raw[2] & 255);
      assertEquals(16, ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt(3));
      int corpseReplica = f.client.create();
      f.client.getMapper(UnitStates.class).create(corpseReplica).init(corpseReplica);
      f.sync(f.corpse, corpseReplica);
      assertTrue(f.client.getMapper(UnitStates.class).get(corpseReplica).stateList.hasState(StateId.CORPSE_NODRAW));
      System.out.println("FENRIS_RAGE duration=" + rage.duration + " bonus=" + rage.resolvedDamageModifier()
          + " dcc=" + layer.assetDescriptor.fileName + " frames=16 directions=1");
    }
  }

  @Test void consumedCorpseCannotRefreshButFreshCorpseRefreshKeepsTheExistingOverlayClock() {
    try (Fixture f = new Fixture()) {
      f.eat(f.corpse); f.sync(f.wolf, f.replica); f.tick();
      Overlay layer = f.overlay(); layer.animation.setFrame(7);
      f.rage().duration = 100;
      f.eat(f.corpse);
      assertEquals(100, f.rage().duration, "one corpse cannot be consumed twice");
      int second = f.newCorpse();
      f.eat(second); assertEquals(500, f.rage().duration);
      clearInvocations(f.assets);
      f.sync(f.wolf, f.replica); f.tick();
      assertSame(layer, f.overlay());
      assertTrue(layer.animation.getFrame() >= 7);
      verify(f.assets, never()).load(any(AssetDescriptor.class));
      verify(f.assets, never()).unload(anyString());
    }
  }

  @Test void authoritativeExpiryClearsOnlyRageAndSnapshotOnlyClientNeverTicksItLocally() {
    try (Fixture f = new Fixture()) {
      f.eat(f.corpse); f.sync(f.wolf, f.replica); f.tick();
      UnitStates remote = f.client.getMapper(UnitStates.class).get(f.replica);
      assertTrue(remote.snapshotOnly);
      int duration = remote.stateList.getState(StateId.FENRIS_RAGE).duration;
      for (int i = 0; i < 30; i++) f.client.process();
      assertEquals(duration, remote.stateList.getState(StateId.FENRIS_RAGE).duration);
      String might = Riiablo.files.States.get(StateId.MIGHT).overlays[0];
      f.manager.setPersistent(f.replica, StateId.MIGHT, might);
      for (int i = 0; i < 499; i++) f.states(f.wolf).stateList.update();
      assertNotNull(f.rage()); assertEquals(1, f.rage().duration);
      f.states(f.wolf).stateList.update(); assertNull(f.rage());
      f.sync(f.wolf, f.replica); f.client.process();
      Overlay root = f.overlay();
      assertNotNull(root); assertNull(root.entry);
      assertEquals(1, root.layers.size); assertEquals(might, root.layers.first().entry.overlay);
      verify(f.assets).unload("data/global/overlays/expansion/DireWolfCharged.dcc");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager savedAssets = Riiablo.assets;
    final com.riiablo.Colors savedColors = Riiablo.colors;
    final AssetManager assets = mock(AssetManager.class);
    final OverlayManager manager = new OverlayManager();
    final World authority, client;
    final Skills.Entry skill;
    final int wolf, corpse, replica;
    Fixture() {
      Riiablo.assets = assets; Riiablo.colors = new com.riiablo.Colors();
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1);
      when(dcc.getNumFramesPerDir()).thenReturn(16);
      when(dcc.getBox(anyInt())).thenReturn(new BBox());
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
      NoopFactory factory = new NoopFactory();
      authority = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new ServerSkillSystem(true), factory).build().register("factory", factory)
          .register("map", new Map(0, 0)));
      wolf = monster("fenris", 20f); corpse = newCorpse();
      skill = Riiablo.files.skills.get(Riiablo.files.monstats.get("fenris").Skill1);
      NoopFactory clientFactory = new NoopFactory();
      client = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager(),
          new com.riiablo.engine.server.StateUpdater(), clientFactory,
          manager, new StateOverlaySystem(), new OverlayStepper())
          .build().register("factory", clientFactory).register("map", new Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      replica = client.create(); client.getMapper(UnitStates.class).create(replica).init(replica);
      client.setDelta(1f/25f);
    }
    int monster(String name, float life) {
      int id = authority.create();
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      authority.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      authority.getMapper(Position.class).create(id).position.set(10, 10);
      Attributes attrs = Attributes.obtainLarge();
      attrs.base().put(Stat.level, 20); attrs.base().put(Stat.hitpoints, life);
      attrs.base().put(Stat.maxhp, 100f); attrs.reset();
      authority.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      authority.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }
    int newCorpse() {
      int id = monster("fallen1", 0f);
      authority.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
      return id;
    }
    UnitStates states(int id) { return authority.getMapper(UnitStates.class).get(id); }
    UnitState rage() { return states(wolf).stateList.getState(StateId.FENRIS_RAGE); }
    void eat(int id) { authority.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        wolf, skill.Id, id, new Vector2(10, 10), skill.srvdofunc, skill.cltdofunc)); }
    void sync(int source, int target) {
      StateSerializer serializer = new StateSerializer();
      FlatBufferBuilder b = new FlatBufferBuilder(512);
      int state = serializer.putData(b, states(source));
      int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.StateP});
      int components = EntitySync.createComponentVector(b, new int[] {state});
      b.finish(EntitySync.createEntitySync(b, target, 0, 0, types, components, 0, 0, 0, 0, 0, -1));
      serializer.getData(EntitySync.getRootAsEntitySync(b.dataBuffer()), 0,
          client.getMapper(UnitStates.class).get(target));
    }
    void tick() { client.process(); client.process(); }
    Overlay overlay() { return client.getMapper(Overlay.class).get(replica); }
    @Override public void close() { authority.dispose(); client.dispose(); Riiablo.assets = savedAssets; Riiablo.colors = savedColors; }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a,int b,float x,float y) { return -1; }
    @Override public int createStaticObject(int a,int b,float x,float y) { return -1; }
    @Override public int createStaticObjectByClassId(int a,float x,float y) { return -1; }
    @Override public int createMonster(int a,float x,float y) { return -1; }
    @Override public int createWarp(int a,float x,float y) { return -1; }
    @Override public int createItem(Item a,float x,float y) { return -1; }
    @Override public int createMissile(int a,Vector2 b,Vector2 c) { return -1; }
  }
}
