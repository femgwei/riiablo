package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.COF;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.presentation.*;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.save.CharData;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class ArcticBlastSequenceIntegrationTest extends RiiabloTest {
  @Test void originalLoadedTableSelectsS1AndRetainsSourceNineAtItsActionPoint() throws Exception {
    JsonValue fixture;
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/arctic-blast-sequence-1.10f.json")) {
      assertNotNull(in);
      fixture = new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(0x9FE10, fixture.get("sequence_table").getInt("rva"));
    int point = 0;
    for (JsonValue record : fixture.get("sequence_table").get("records")) {
      assertEquals(Engine.Player.MODE_S1, record.getInt("mode"));
      assertEquals(NativeArcticBlastSequence.sourceFrame(point << 8), record.getInt("frame"));
      assertEquals(point == 10 ? 1 : 0, record.getInt("event")); point++;
    }
    assertEquals(15, point);
    assertEquals(Engine.Player.MODE_S1, new Actioneer().getMode(Riiablo.files.skills.get(230),
        com.riiablo.engine.server.component.Class.Type.PLR));
  }

  @Test void realCastRunsWindupHeldLoopAndBoundedReleaseWithoutInjectedSkillCallbacks() {
    try (Fixture f = new Fixture()) {
      f.start();
      for (int tick=1; tick<=40; tick++) {
        f.input(true, new Vector2(4530,5642)); f.step();
        if (tick<=10) assertEquals(tick-1,f.reference().sequenceFrame);
        if (tick<10) assertEquals(0,f.probe.callbacks.size());
        if (tick>=10) assertEquals(9,f.reference().sequenceFrame);
        assertEquals(Engine.Player.MODE_S1,f.reference().mode);
      }
      assertTrue(f.probe.callbacks.size()>=15);
      for(int i=1;i<f.probe.callbacks.size();i++)
        assertEquals(2,f.probe.callbacks.get(i)-f.probe.callbacks.get(i-1));
      assertTrue(f.visuals()>0); assertTrue(f.carriers()>0);
      f.input(false,new Vector2(4530,5642));
      boolean tail=false;
      for(int i=0;i<35;i++) {
        f.step(); tail |= f.reference().sequenceFrame>=10;
      }
      assertTrue(tail);
      assertFalse(f.world.getMapper(Casting.class).has(f.caster));
      assertFalse(f.world.getMapper(Sequence.class).has(f.caster));
      assertEquals(Engine.Player.MODE_NU,f.reference().mode);
      assertEquals(-1,f.reference().sequenceFrame);
      assertEquals(0,f.liveStreams());
    }
  }

  @Test void lostInputAndManaExhaustionCannotLeavePermanentS1OrIce() {
    for(boolean lowMana:new boolean[]{false,true}) try(Fixture f=new Fixture()) {
      f.start();
      if(lowMana) f.world.getMapper(AttributesWrapper.class).get(f.caster).attrs.get(Stat.mana).set(0f);
      for(int i=0;i<80;i++) {
        if(lowMana) f.input(true,new Vector2(4530,5642));
        f.step();
      }
      assertFalse(f.world.getMapper(Casting.class).has(f.caster));
      assertEquals(Engine.Player.MODE_NU,f.reference().mode);
      assertEquals(0,f.liveStreams());
    }
  }

  @Test void repeatedRequestsUpdateAimWithoutRestartingTheCastOrInitialFrames() {
    try(Fixture f=new Fixture()) {
      f.start();
      long action=f.world.getMapper(Casting.class).get(f.caster).actionId;
      for(int i=0;i<20;i++) {f.input(true,new Vector2(4530,5642));f.step();}
      assertTrue(f.actions.castAndReport(f.caster,230,-1,new Vector2(4522,5650)));
      f.step();
      assertEquals(action,f.world.getMapper(Casting.class).get(f.caster).actionId);
      assertEquals(new Vector2(4522,5650),f.world.getMapper(Casting.class).get(f.caster).targetVec);
      assertEquals(9,f.reference().sequenceFrame);
      f.input(false,Vector2.Zero);
      for(int i=0;i<35;i++) f.step();
      assertEquals(Engine.Player.MODE_NU,f.reference().mode);
    }
  }

  @Test void sequenceFrameReachesResidentS1AnimationInsteadOfPlayingScIndependently() {
    try(Fixture f=new Fixture()) {
      f.start();
      for(int i=0;i<35;i++) {
        f.input(true,new Vector2(4530,5642));f.step();
        assertEquals(f.reference().sequenceFrame,
            f.world.getMapper(AnimationWrapper.class).get(f.caster).animation.getFrame());
      }
    }
  }

  @Test void cancellationClearsTheHeldFrameAndItsLoopingState() {
    try(Fixture f=new Fixture()) {
      f.start();for(int i=0;i<25;i++){f.input(true,new Vector2(4530,5642));f.step();}
      f.actions.cancelCasting(f.caster,"test-interruption");
      assertFalse(f.world.getMapper(Sequence.class).has(f.caster));
      assertFalse(f.world.getMapper(UnitStates.class).get(f.caster).stateList.hasState(StateId.INFERNO));
      assertEquals(-1,f.reference().sequenceFrame);
      assertEquals(Engine.Player.MODE_NU,f.reference().mode);
      for(int i=0;i<25;i++)f.step();
      assertEquals(0,f.liveStreams());
    }
  }

  public static class Probe extends PassiveSystem {
    int tick;
    final List<Integer> callbacks=new ArrayList<>();
    @Subscribe public void skill(SkillDoEvent event){if(event.skillId==230)callbacks.add(tick);}
  }

  static final class Fixture implements AutoCloseable {
    final AssetManager saved=Riiablo.assets;
    final com.riiablo.codec.D2 savedAnim=Riiablo.anim;
    final AssetManager assets=Riiablo.assets=mock(AssetManager.class);
    final List<DCC> dccs=new ArrayList<>();
    final Map<String,DCC> resources=new HashMap<>();
    final Actioneer actions=new Actioneer();
    final Probe probe=new Probe();
    final RecordingFactory factory=new RecordingFactory();
    final CastOverlays overlays=new CastOverlays();
    final int[] localPlayer={-1};
    int overlayLoadAfter;
    final World world;
    final int caster;
    Fixture() { this(true); }
    Fixture(boolean clientAssemblyRelativeOrder) {
      Riiablo.colors=new com.riiablo.Colors();
      Riiablo.anim=com.riiablo.codec.D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
      for(String name:new String[]{"arcticblast1","arcticblast2"}) {
        Missiles.Entry row=Riiablo.files.Missiles.get(name);
        DCC dcc=spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/"+row.CelFile+".dcc")));
        doNothing().when(dcc).loadDirection(anyInt());dccs.add(dcc);
        resources.put(row.CelFile.replace('\\','/').toLowerCase(Locale.ROOT),dcc);
      }
      DCC castDcc=spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/overlays/Expansion/ColdWindOverlay.dcc")));
      doNothing().when(castDcc).loadDirection(anyInt());dccs.add(castDcc);
      resources.put("expansion/coldwindoverlay",castDcc);
      when(assets.contains(anyString())).thenReturn(true);
      when(assets.isLoaded(any(AssetDescriptor.class))).thenAnswer(call->{
        AssetDescriptor<?> descriptor=call.getArgument(0);
        return !descriptor.fileName.toLowerCase(Locale.ROOT).contains("coldwindoverlay") || probe.tick>=overlayLoadAfter;
      });
      when(assets.get(any(AssetDescriptor.class))).thenAnswer(call->{
        AssetDescriptor<?> descriptor=call.getArgument(0);
        String name=descriptor.fileName.replace('\\','/').toLowerCase(Locale.ROOT);
        for(Map.Entry<String,DCC> r:resources.entrySet())if(name.endsWith(r.getKey()+".dcc"))return r.getValue();
        throw new IllegalArgumentException(name);
      });
      WorldConfigurationBuilder configuration=new WorldConfigurationBuilder().with(new EventSystem(),new ItemManager(),
          new ObjectInteractor(),new WarpInteractor(),new ItemInteractor(),
          new StateUpdater(),new MissileCollisionSystem(),actions,new Pathfinder(),new CofManager());
      if(clientAssemblyRelativeOrder) {
        // Match the relevant relative order in ClientSimulationAssembly: presentation
        // motion precedes sequence animation; newly cast missiles are not moved here.
        configuration.with(overlays,new InfernoPresentationSystem(),new MissileImpactPresentationSystem(),
            new OverlayStepper(),new ServerSkillSystem(true),factory,new AnimDataResolver());
        configuration.with(com.riiablo.engine.SimulationSystemGroups.sequenceAnimation());
      } else {
        configuration.with(new AnimDataResolver(),new SequenceHandler(),new AnimStepper(),new ServerSkillSystem(true),
            overlays,new MissileImpactPresentationSystem(),new InfernoPresentationSystem(),new OverlayStepper(),factory);
      }
      world=new World(configuration.with(new MissileLoader(),new AnimationStepper(),new DirectionResolver(),probe).build()
          .register("factory",factory).register("map",new com.riiablo.map.Map(0,0))
          .register("townPortalRegistry",new com.riiablo.engine.server.portal.TownPortalRegistry())
          .register("clientContext",new ClientGameplayContext(Riiablo.charData,()->localPlayer[0],()->null,
              null,Riiablo.assets,Riiablo.audio,Riiablo.cursor,Riiablo.files,Riiablo.batch,
              Riiablo.fonts,Riiablo.colors,Riiablo.string,Riiablo.saves,Riiablo.home,null,Riiablo.mpqs)));
      caster=world.create();
      localPlayer[0]=caster;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(caster).type=
          com.riiablo.engine.server.component.Class.Type.PLR;
      CharData data=CharData.createRemote("arctic-sequence",Riiablo.DRUID);data.setSkillLevel(230,1);
      world.getMapper(Player.class).create(caster).data=data;
      world.getMapper(Position.class).create(caster).position.set(4522,5642);
      world.getMapper(Angle.class).create(caster);world.getMapper(Velocity.class).create(caster);
      world.getMapper(MovementModes.class).create(caster).set(Engine.Player.MODE_NU,Engine.Player.MODE_WL,Engine.Player.MODE_RN);
      world.getMapper(CofReference.class).create(caster).set("DZ",Engine.Player.MODE_NU);
      world.getMapper(UnitStates.class).create(caster).init(world,caster);
      Attributes attrs=Attributes.obtainStandard();
      for(short stat:new short[]{Stat.level,Stat.hitpoints,Stat.maxhp,Stat.mana,Stat.maxmana})
        attrs.base().put(stat,stat==Stat.level?99:1000);
      attrs.reset();world.getMapper(AttributesWrapper.class).create(caster).attrs=attrs;
      AnimationWrapper wrapper=world.getMapper(AnimationWrapper.class).create(caster);
      wrapper.animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/chars/DZ/COF/DZS1HTH.cof")));
      world.setDelta(1f/25f);world.process();
    }
    CofReference reference(){return world.getMapper(CofReference.class).get(caster);}
    void start(){assertTrue(actions.castAndReport(caster,230,-1,new Vector2(4530,5642)));}
    void input(boolean held,Vector2 aim){actions.updateArcticBlastInput(caster,held,-1,aim);}
    void step(){probe.tick++;world.process();}
    int visuals(){int n=0;for(int id:factory.created)if(world.getMapper(Missile.class).has(id)&&world.getMapper(Missile.class).get(id).presentationOnly)n++;return n;}
    int carriers(){int n=0;for(int id:factory.created)if(world.getMapper(Missile.class).has(id)&&!world.getMapper(Missile.class).get(id).presentationOnly)n++;return n;}
    int liveStreams(){return visuals()+carriers();}
    @Override public void close(){world.dispose();Riiablo.assets=saved;Riiablo.anim=savedAnim;for(DCC d:dccs)d.dispose();}
  }
  public static class RecordingFactory extends ServerEntityFactory {
    final Set<Integer> created=new HashSet<>();
    java.util.function.IntConsumer allocationObserver;
    @Override public int createMissile(Missiles.Entry row,Vector2 aim,Vector2 at,int owner,int level){
      int id=super.createMissile(row,aim,at,owner,level);
      if(id>=0){created.add(id);if(allocationObserver!=null)allocationObserver.accept(id);}
      return id;
    }
  }
  @com.artemis.annotations.All(com.riiablo.presentation.component.Overlay.class)
  public static class CastOverlays extends OverlayManager {
    int requests;
    @Override public void set(int entity,String name) {
      if("arctic_blast_cast".equals(name))requests++;
      super.set(entity,name);
    }
  }
}
