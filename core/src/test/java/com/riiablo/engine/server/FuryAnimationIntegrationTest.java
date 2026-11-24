package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.serializer.CofReferenceSerializer;
import com.riiablo.engine.server.event.*;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FuryAnimationIntegrationTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"1,false,false", "4,false,false", "4,true,false", "4,true,true"})
  void realWolfAnimationRewindsAtEachIntermediateHitAndPlaysOnlyTheLastTail(
      int level, boolean killFirst, boolean removeAll) {
    D2 previous=Riiablo.anim;
    Riiablo.anim=D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    EntityFactory factory=mock(EntityFactory.class); Actioneer actioneer=new Actioneer();
    World world=new World(new WorldConfigurationBuilder().with(new EventSystem(),new CofManager(),
        new DruidShapeShiftResolver(),new AnimDataResolver(),new SequenceHandler(),actioneer,
        new Pathfinder(),factory,new AnimStepper()).build().register("factory",factory).register("map",new Map(0,0)));
    try {
      int source=unit(world,true,0,0),first=unit(world,false,1,0),second=unit(world,false,0,1);
      CharData character=CharData.createRemote("fury-animation",(byte)Riiablo.DRUID); character.setSkillLevel(248,level);
      world.getMapper(Player.class).get(source).data=character;
      world.getMapper(UnitStates.class).get(source).stateList.addState(StateId.WOLF,1000,1,source);
      CofReference ref=world.getMapper(CofReference.class).create(source).set("DZ",Engine.Player.MODE_NU);
      world.getMapper(AnimData.class).create(source);
      world.getMapper(Casting.class).create(source).set(248,first,new Vector2(1,0));
      world.getMapper(Sequence.class).create(source).sequence(Engine.Player.MODE_A1,Engine.Player.MODE_NU);
      Skills.Entry skill=Riiablo.files.skills.get(248);
      assertEquals(100,DruidSkills.getFuryRollbackPercent(skill));
      actioneer.onSkillStart(SkillStartEvent.obtain(source,248,first,new Vector2(1,0),37,53));
      List<Integer> targets=new ArrayList<>(),hitTicks=new ArrayList<>(); int[] tick={0},finishes={0};
      world.getSystem(EventSystem.class).registerEvents(new java.lang.Object() {
        @Subscribe public void hit(MeleeAttackEvent e) {
          if(e.attacker==source) {targets.add(e.victim);hitTicks.add(tick[0]);}
        }
        @Subscribe public void finished(AnimDataFinishedEvent e) {if(e.entityId==source) finishes[0]++;}
      });
      world.setDelta(1f/25f);
      int previousHits=0,previousRestart=-1,lastHitTick=-1;
      while(world.getMapper(Casting.class).has(source) && tick[0]<300) {
        tick[0]++; world.process();
        if(targets.size()==previousHits) continue;
        assertEquals(previousHits+1,targets.size(),"no multiple strikes in one simulation tick");
        previousHits=targets.size();lastHitTick=tick[0];
        Casting cast=world.getMapper(Casting.class).get(source);assertNotNull(cast);
        if(killFirst && targets.size()==1) {
          life(world,first).set(0f); if(removeAll) life(world,second).set(0f);
        }
        if(cast.furyRemainingStrikes>0) {
          assertEquals(0,world.getMapper(AnimData.class).get(source).frame,
              "native 100% rollback happens on the hit, without waiting for ENDANIM");
          assertEquals(0,finishes[0]); assertTrue(world.getMapper(Sequence.class).get(source).started);
          FlatBufferBuilder b=new FlatBufferBuilder(64); b.finish(new CofReferenceSerializer().putData(b,ref));
          CofReferenceP wire=CofReferenceP.getRootAsCofReferenceP(b.dataBuffer());
          assertEquals(Engine.Player.MODE_A1,wire.mode());
          assertTrue(wire.animationRestartId()>previousRestart,"same-mode rollback must reach remote clients");
          previousRestart=(int)wire.animationRestartId();
        }
      }
      assertTrue(tick[0]<300,"the combo must end, not loop indefinitely");
      assertEquals(removeAll?1:DruidSkills.getFuryHitCount(skill,level),targets.size());
      assertEquals(first,targets.get(0));
      if(killFirst) for(int i=1;i<targets.size();i++) assertEquals(second,targets.get(i));
      assertEquals(1,finishes[0],"only the final attack plays through its tail");
      assertTrue(tick[0]>lastHitTick); assertFalse(world.getMapper(Sequence.class).has(source));
      assertEquals(Engine.Player.MODE_NU,ref.mode);
      D2.Entry wolf=Riiablo.anim.getEntry("40A1HTH");
      for(int i=1;i<hitTicks.size();i++) assertTrue(hitTicks.get(i)-hitTicks.get(i-1)<wolf.framesPerDir,
          "repeat cadence must not wait for a full thirteen-frame A1");
      System.out.println("FURY_ANIMATION level="+level+" killFirst="+killFirst+" removeAll="+removeAll
          +" frames="+wolf.framesPerDir+" hit_ticks="+hitTicks+" finish_tick="+tick[0]+" targets="+targets);
    } finally {world.dispose();Riiablo.anim=previous;}
  }

  private static com.riiablo.attributes.StatRef life(World world,int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints);
  }
  private static int unit(World world,boolean player,int x,int y) {
    int id=world.create();
    if(player) world.getMapper(Player.class).create(id);else world.getMapper(Monster.class).create(id);
    world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type=player
        ?com.riiablo.engine.server.component.Class.Type.PLR:com.riiablo.engine.server.component.Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x,y);
    Attributes a=Attributes.obtainStandard();a.base().put(Stat.hitpoints,10000f);a.base().put(Stat.maxhp,10000f);
    a.base().put(Stat.level,20);a.base().put(Stat.tohit,100000);a.base().put(Stat.mindamage,10);a.base().put(Stat.maxdamage,10);a.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs=a;world.getMapper(UnitStates.class).create(id).init(id);return id;
  }
}
