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
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.Overlay;
import com.riiablo.save.CharData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShockWavePresentationTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"8,0", "0,8", "-8,0", "0,-8", "8,8"})
  void localServerAndClientCallbackPublishExactlyFiveOwnedDirectionalSprites(float dx,float dy) {
    try (Fixture f=new Fixture()) {
      int caster=f.world.create();
      CharData character=CharData.createRemote("shockwave",(byte)Riiablo.DRUID); character.setSkillLevel(243,1);
      f.world.getMapper(Player.class).create(caster).data=character;
      Attributes attrs=Attributes.obtainStandard(); attrs.base().put(Stat.level,20);
      attrs.base().put(Stat.hitpoints,100f); attrs.base().put(Stat.maxhp,100f); attrs.reset();
      f.world.getMapper(AttributesWrapper.class).create(caster).attrs=attrs;
      f.world.getMapper(Position.class).create(caster).position.set(100,100);
      f.world.getMapper(UnitStates.class).create(caster).init(caster).stateList.addState(StateId.BEAR,1000,1,caster);
      f.events.dispatch(SkillDoEvent.obtain(caster,243,-1,new Vector2(100+dx,100+dy),8,17));
      assertEquals(5,f.factory.ids.size());
      f.world.process();
      Vector2 forward=new Vector2(dx,dy).nor();
      for (int id:f.factory.ids) {
        Missile missile=f.world.getMapper(Missile.class).get(id);
        assertEquals(caster,missile.ownerId); assertTrue(missile.damageSnapshot); assertEquals(243,missile.skillId);
        assertFalse(missile.presentationOnly,"visible lanes must remain authoritative combat missiles");
        Vector2 direction=f.world.getMapper(Angle.class).get(id).angle;
        assertTrue(direction.dot(forward)>0,"each lane must travel into the aimed half-plane");
        AnimationWrapper wrapper=f.world.getMapper(AnimationWrapper.class).get(id); assertNotNull(wrapper);
        assertEquals(f.shockDirections,wrapper.animation.getNumDirections());
        assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE,wrapper.animation.getLayer(0).getBlendMode());
        assertEquals(256,wrapper.animation.getFrameDelta());
      }
      assertEquals(0,f.factory.unowned,"CltDo17 must not add an unowned second fan");
      System.out.println("SHOCK_WAVE_FAN target_delta="+dx+","+dy+" count=5 directions="
          +f.shockDirections+" dcc_frames="+f.shockFrames+" table_frames="+Riiablo.files.Missiles.get("shockwave").AnimLen);
    }
  }

  @Test void remoteStunSnapshotOwnsOneLoopAndOnlyAuthoritativeRemovalClearsIt() {
    try (Fixture f=new Fixture()) {
      int target=f.world.create();
      f.world.getMapper(Position.class).create(target);
      UnitStates remote=f.world.getMapper(UnitStates.class).create(target).init(target);
      UnitStates source=new UnitStates().init(700);
      source.stateList.addState(StateId.STUNNED,40,1,99).skillId=243;
      sync(source,remote); f.world.process(); f.world.process();
      Overlay overlay=f.world.getMapper(Overlay.class).get(target); assertNotNull(overlay);
      assertEquals("stun",overlay.entry.overlay); assertTrue(overlay.persistent); assertTrue(overlay.usesNativeRendering());
      assertEquals(.04f,overlay.animation.getFrameDuration(),.000001f);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE,overlay.animation.getLayer(0).getBlendMode());
      assertEquals(-14f,AuraRenderPolicy.offsetY(overlay.entry,0));
      assertEquals(0f,AuraRenderPolicy.offsetY(overlay.entry,1));
      overlay.animation.setFrame(4);
      source.stateList.getState(StateId.STUNNED).duration=20; sync(source,remote); f.world.process();
      assertSame(overlay,f.world.getMapper(Overlay.class).get(target));
      for(int i=0;i<50;i++) f.world.process();
      assertEquals(20,remote.stateList.getState(StateId.STUNNED).duration,"snapshotOnly waits for the server");
      assertNotNull(f.world.getMapper(Overlay.class).get(target));
      source.stateList.removeState(StateId.STUNNED); sync(source,remote); f.world.process(); f.world.process();
      assertNull(f.world.getMapper(Overlay.class).get(target));
      verify(f.assets).unload("data/global/overlays/Stun.dcc");
    }
  }

  private static void sync(UnitStates source,UnitStates target) {
    StateSerializer serializer=new StateSerializer(); FlatBufferBuilder b=new FlatBufferBuilder(256);
    int data=serializer.putData(b,source);
    int types=EntitySync.createComponentTypeVector(b,new byte[]{ComponentP.StateP});
    int components=EntitySync.createComponentVector(b,new int[]{data});
    b.finish(EntitySync.createEntitySync(b,700,0,0,types,components,0,0,0,0,0,-1));
    serializer.getData(EntitySync.getRootAsEntitySync(b.dataBuffer()),0,target);
  }

  private static final class Fixture implements AutoCloseable {
    final AssetManager previous=Riiablo.assets;
    final com.riiablo.Colors previousColors=Riiablo.colors;
    final AssetManager assets=mock(AssetManager.class);
    final EventSystem events=new EventSystem();
    final Factory factory=new Factory();
    final World world;
    final int shockFrames,shockDirections;
    Fixture() {
      Riiablo.assets=assets; Riiablo.colors=new com.riiablo.Colors();
      Missiles.Entry row=Riiablo.files.Missiles.get("shockwave");
      ByteBuffer h=ByteBuffer.wrap(Riiablo.mpqs.resolve("data/global/missiles/"+row.CelFile+".dcc").readBytes())
          .order(ByteOrder.LITTLE_ENDIAN);
      shockFrames=h.getInt(3); shockDirections=h.get(2)&255;
      assertEquals(8,shockDirections);
      DCC wave=dc(shockFrames,shockDirections);
      ByteBuffer stun=ByteBuffer.wrap(Riiablo.mpqs.resolve("data/global/overlays/Stun.dcc").readBytes())
          .order(ByteOrder.LITTLE_ENDIAN);
      DCC stars=dc(stun.getInt(3),stun.get(2)&255);
      when(assets.get(any(AssetDescriptor.class))).thenAnswer(invocation ->
          ((AssetDescriptor)invocation.getArgument(0)).fileName.endsWith("Stun.dcc")?stars:wave);
      when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
      when(assets.isLoaded(anyString())).thenReturn(true); when(assets.contains(anyString())).thenReturn(true);
      world=new World(new WorldConfigurationBuilder().with(events,new CofManager(),new StateUpdater(),
          new ServerSkillSystem(true),factory,new OverlayManager(),new StateOverlaySystem(),
          new OverlayStepper(),new SkillCastHandler(),new MissileLoader()).build()
          .register("factory",factory).register("map",new Map(0,0))
          .register("clientContext",ClientGameplayContext.fromGlobals()));
      world.setDelta(1f/25f);
      System.out.println("SHOCK_WAVE_RESOURCE frames="+shockFrames+" directions="+shockDirections
          +" stun_frames="+stun.getInt(3));
    }
    static DCC dc(int frames,int directions) {
      DCC dcc=mock(DCC.class); when(dcc.getNumDirections()).thenReturn(directions);
      when(dcc.getNumFramesPerDir()).thenReturn(frames); when(dcc.getBox(anyInt())).thenReturn(new BBox()); return dcc;
    }
    public void close() {world.dispose(); Riiablo.assets=previous; Riiablo.colors=previousColors;}
  }

  private static final class Factory extends EntityFactory {
    final List<Integer> ids=new ArrayList<>(); int unowned;
    @Override public int createMissile(int id,Vector2 direction,Vector2 position,int owner) {
      int entity=world.create(); Missiles.Entry row=Riiablo.files.Missiles.get(id);
      world.getMapper(Missile.class).create(entity).set(row,position,row.Range).setOwner(owner);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Angle.class).create(entity).angle.set(direction); ids.add(entity); return entity;
    }
    @Override public int createMissile(int id,Vector2 angle,Vector2 position) { unowned++; return createMissile(id,angle,position,-1); }
    @Override public int createPlayer(CharData data,Vector2 p){return -1;}
    @Override public int createDynamicObject(int a,int b,float x,float y){return -1;}
    @Override public int createStaticObject(int a,int b,float x,float y){return -1;}
    @Override public int createStaticObjectByClassId(int a,float x,float y){return -1;}
    @Override public int createMonster(int a,float x,float y){return -1;}
    @Override public int createWarp(int a,float x,float y){return -1;}
    @Override public int createItem(Item a,float x,float y){return -1;}
  }
}
