package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Actual MonSeq cursor, Pathfinder and collision movement; never inject SkillDo. */
class DiabRunIntegrationTest extends RiiabloTest {
  @Test void fractionalRemainderDies() { deathBoundary(255f / 256f, false, false); }
  @Test void exactlyOneLifeSurvives() { deathBoundary(1f, false, false); }
  @Test void aboveOneLifeSurvives() { deathBoundary(257f / 256f, false, false); }
  @Test void cancelledDamagePreservesFractionalLife() { deathBoundary(.5f, true, false); }
  @Test void zeroDamagePreservesFractionalLife() { deathBoundary(.5f, false, true); }

  private static void deathBoundary(float remainder, boolean cancel, boolean zero) {
    try (Fixture f = new Fixture("diablo", 3)) {
      Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(f.target).attrs;
      if (zero) {
        f.stat(f.target, Stat.normal_damage_reduction, 1000);
        attrs.get(Stat.hitpoints).set(remainder);
      } else {
        // Arrange exact life against the real random packet at its event
        // boundary without replacing the skill roll or animation sequence.
        f.probe.beforeDamage = hit -> {
          assertTrue(hit.physicalDamage >= 53f && hit.physicalDamage < 69f);
          attrs.get(Stat.hitpoints).set(cancel ? remainder : hit.damage + remainder);
          if (cancel) hit.damage = 0f;
        };
      }
      f.begin(); f.finish();
      boolean fatal = !cancel && !zero && remainder < 1f;
      assertEquals(fatal ? 0f : remainder, f.hp(), .0001f);
      assertEquals(fatal ? 1 : 0, f.probe.deaths);
      assertEquals(1, f.probe.hits.size());
      if (zero) assertEquals(0f, f.probe.hits.get(0).damage);
      for (int i = 0; i < 3; i++) f.tick();
      assertEquals(1, f.probe.hits.size());
      assertEquals(fatal ? 1 : 0, f.probe.deaths);
    }
  }

  @Test void partialSearchIsExplicitAndNeverTreatsBlockedGoalAsWalkable() {
    try(Fixture f=new Fixture("diablo",18)) {
      Vector2 start=new Vector2(100,100),goal=new Vector2(118,100);
      f.zone.or(118,100,com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK);
      com.riiablo.map.pfa.GraphPath path=new com.riiablo.map.pfa.GraphPath();
      assertFalse(f.map.findPath(f.zone,start,goal,com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK,1,path,null,-1,-1,false));
      assertTrue(f.map.findPartialPath(f.zone,start,goal,com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK,1,path,null,false));
      assertTrue(path.getCount()>1);assertTrue(path.getNodePosition(path.getCount()-1).dst2(goal)>0);
      for(int i=0;i<path.getCount();i++)assertFalse(path.getNodePosition(i).equals(goal));
      assertFalse(f.map.findPath(f.zone,start,goal,com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK,1,path,null,-1,-1,false));
    }
  }

  @Test void coordinateOnlyStartFailsAndPooledCastCannotRetainRunState() {
    try(Fixture f=new Fixture("diablo",18)) {
      f.world.delete(f.target);f.world.process();f.begin();assertNull(f.cast());
    }
    World world=new World();try {
      int old=world.create();Casting cast=world.getMapper(Casting.class).create(old).set(198,4,new Vector2(10,10));
      cast.diabRun=new NativeDiabRun(4,new Vector2(10,10));
      world.process();world.delete(old);world.process();
      int id=world.create();assertNull(world.getMapper(Casting.class).create(id).diabRun);
    }finally{world.dispose();}
  }
  @Test void realSequenceRunsLoopsHitsOnlyOnceAndFinishes() {
    for(String row:new String[]{"diablo","diabloclone"})try(Fixture f=new Fixture(row,18)) {
      f.begin();NativeDiabRun run=f.run();Vector2 start=f.pos(f.source).cpy();
      for(int i=0;i<5;i++)f.tick();
      assertEquals(start,f.pos(f.source)); assertFalse(run.pathStarted); assertTrue(f.probe.hits.isEmpty());
      f.finish();
      assertTrue(run.pathStarted);assertTrue(run.loops>0);assertTrue(run.tail);assertTrue(run.hitProcessed);
      assertTrue(f.pos(f.source).dst(start)>10);assertEquals(1,f.probe.hits.size());
      DamageEvent hit=f.probe.hits.get(0);assertEquals(DamageEvent.MISSILE,hit.kind);
      assertTrue(hit.damage>=53&&hit.damage<69);assertEquals(hit.damage,hit.physicalDamage);
      assertEquals(1000-hit.damage,f.hp(),.001f);assertEquals(1,f.probe.finished);
      assertFalse(f.world.getMapper(Pathfind.class).has(f.source));assertTrue(f.velocity().velocity.isZero());
    }
  }

  @Test void movingOrRemovedTargetNeverChangesSavedDestinationOrGetsRangedHit() {
    for(boolean remove:new boolean[]{false,true})try(Fixture f=new Fixture("diablo",18)) {
      f.begin();NativeDiabRun run=f.run();
      if(remove)f.world.delete(f.target);else f.pos(f.target).set(160,100);
      f.finish();assertEquals(new Vector2(118,100),run.destination);
      assertTrue(f.pos(f.source).dst(run.destination)<1);assertTrue(f.probe.hits.isEmpty());
    }
  }

  @Test void tableSuccessDoesNotRollAttackRatingBlockOrInheritWeaponDamage() {
    for(int i=0;i<6;i++)try(Fixture f=new Fixture("diablo",3)) {
      f.stat(f.source,Stat.mindamage,9999);f.stat(f.source,Stat.maxdamage,9999);f.stat(f.source,Stat.tohit,0);
      f.stat(f.target,Stat.armorclass,1000000);f.stat(f.target,Stat.toblock,100);
      f.begin();f.finish();assertEquals(1,f.probe.hits.size());
      assertTrue(f.probe.hits.get(0).damage>=53&&f.probe.hits.get(0).damage<69);
    }
  }

  @Test void physicalResistanceAndImmunityApplyToSkillPacket() {
    for(int resist:new int[]{50,100})try(Fixture f=new Fixture("diablo",3)) {
      f.stat(f.target,Stat.damageresist,resist);f.begin();f.finish();
      assertEquals(1,f.probe.hits.size());
      assertTrue(f.probe.hits.get(0).damage>=26&&f.probe.hits.get(0).damage<=34);
      assertEquals(1000-f.probe.hits.get(0).damage,f.hp(),.001f);
    }
    for(int resist:new int[]{90,100})try(Fixture f=new Fixture("diablo",3)) {
      f.world.getMapper(Player.class).remove(f.target);
      f.world.getMapper(com.riiablo.engine.server.component.Class.class).get(f.target).type=com.riiablo.engine.server.component.Class.Type.MON;
      MonStats.Entry row=Riiablo.files.monstats.get("fallen1");
      f.world.getMapper(Monster.class).create(f.target).set(row,Riiablo.files.monstats2.get(row.MonStatsEx));
      int owner=f.unit(130,100);f.world.getMapper(Player.class).create(owner);
      f.world.getMapper(SummonedPet.class).create(f.target).set(owner,"test",198,1,false,0);
      f.stat(f.target,Stat.damageresist,resist);f.world.process();f.begin();f.finish();
      assertEquals(1,f.probe.hits.size());
      if(resist==100)assertEquals(1000,f.hp());
      else assertTrue(f.probe.hits.get(0).damage>=5&&f.probe.hits.get(0).damage<=6);
    }
  }

  @Test void nativePhysicalReductionUsesFlatBeforePercentAndCanReachZero() {
    try(Fixture f=new Fixture("diablo",3)) {
      Attributes attrs=f.world.getMapper(AttributesWrapper.class).get(f.target).attrs;
      com.riiablo.engine.server.combat.CombatSystem combat=new com.riiablo.engine.server.combat.CombatSystem();
      f.stat(f.target,Stat.normal_damage_reduction,20);f.stat(f.target,Stat.damageresist,50);
      assertEquals(40,combat.calculateNativeFixedPhysicalDamage(attrs,true,100,null).totalDamage);
      f.stat(f.target,Stat.damageresist,100);
      assertEquals(40,combat.calculateNativeFixedPhysicalDamage(attrs,true,100,null).totalDamage);
      assertEquals(0,combat.calculateNativeFixedPhysicalDamage(attrs,false,100,null).totalDamage);
      f.stat(f.target,Stat.normal_damage_reduction,1000);f.begin();f.finish();
      assertEquals(1,f.probe.hits.size());assertEquals(0,f.probe.hits.get(0).damage);assertEquals(1000,f.hp());
      f.stat(f.target,Stat.normal_damage_reduction,0);f.stat(f.target,Stat.damageresist,-150);
      assertEquals(200,combat.calculateNativeFixedPhysicalDamage(attrs,true,100,null).totalDamage);
      assertEquals(200,combat.calculateNativeFixedPhysicalDamage(attrs,false,100,null).totalDamage);
    }
  }

  @Test void blockedPathTakesTailWithoutTeleportOrFakeRangedHit() {
    try(Fixture f=new Fixture("diablo",18)) {
      for(int y=0;y<200;y++) f.zone.or(110,y,com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK);
      f.begin();NativeDiabRun run=f.run();f.finish();
      assertTrue(run.pathStarted);assertTrue(run.tail);assertTrue(f.pos(f.source).x<110);
      assertTrue(f.probe.hits.isEmpty());assertEquals(1,f.probe.finished);
    }
  }

  @Test void freezePausesSequenceAndDeathAndCancelClearRuntimeMovement() {
    for(boolean death:new boolean[]{false,true})try(Fixture f=new Fixture("diablo",18)) {
      f.begin();while(!f.run().pathStarted)f.tick();
      NativeDiabRun run=f.run();int cursor=run.cursor;
      f.world.getMapper(UnitStates.class).get(f.source).stateList.addState(StateId.FREEZE,0,1,f.target);
      for(int i=0;i<4;i++)f.tick();assertEquals(cursor,run.cursor);
      if(death)f.events().dispatch(DeathEvent.obtain(f.target,f.source));
      else f.world.getSystem(Actioneer.class).cancelCasting(f.source,"test");
      f.tick();assertNull(f.cast());assertFalse(f.world.getMapper(Pathfind.class).has(f.source));
      assertTrue(f.velocity().velocity.isZero());assertTrue(f.probe.hits.isEmpty());
    }
  }

  static class Probe extends PassiveSystem {
    java.util.function.Consumer<DamageEvent> beforeDamage;
    int deaths;
    final List<DamageEvent> hits=new ArrayList<>();int finished;
    @Subscribe public void hit(DamageEvent e){hits.add(e);if(beforeDamage!=null)beforeDamage.accept(e);}
    @Subscribe public void death(DeathEvent e){deaths++;}
    @Subscribe public void end(AnimDataFinishedEvent e){finished++;}
  }
  static class Fixture implements AutoCloseable {
    final Map map=new Map(1,0);final Map.Zone zone=map.createSyntheticArena(104,4,4);
    final World world;final Probe probe=new Probe();final int source,target;int ticks;
    Fixture(String name,float distance) {
      EntityFactory factory=mock(EntityFactory.class);
      world=new World(new WorldConfigurationBuilder().with(new EventSystem(),probe,new StateUpdater(),
          new Actioneer(),new CofManager(),new ServerSkillSystem(),new AnimStepper(),new DynamicUnitCollisionSystem(),new Pathfinder(),
          new VelocityAdder(),factory).build().register("map",map).register("factory",factory));
      world.setDelta(SimulationClock.STEP_SECONDS);
      source=unit(100,100);MonStats.Entry row=Riiablo.files.monstats.get(name);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(source).type=com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(Monster.class).create(source).set(row,Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Size.class).get(source).size=3;velocity().setMonster(row.Velocity);
      target=unit(100+distance,100);world.getMapper(Player.class).create(target);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(target).type=com.riiablo.engine.server.component.Class.Type.PLR;
      world.process();
    }
    int unit(float x,float y) {
      int id=world.create();world.getMapper(Position.class).create(id).position.set(x,y);
      world.getMapper(Size.class).create(id).size=1;world.getMapper(Angle.class).create(id);
      world.getMapper(Velocity.class).create(id).set(5,5);world.getMapper(MapWrapper.class).create(id).set(map,zone);
      Attributes a=Attributes.obtainStandard();a.base().put(Stat.hitpoints,1000);a.base().put(Stat.maxhp,1000);a.base().put(Stat.level,30);a.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs=a;world.getMapper(UnitStates.class).create(id).init(world,id);return id;
    }
    void begin() {
      world.getMapper(Sequence.class).create(source).sequence(Engine.Monster.MODE_XX,Engine.Monster.MODE_NU).started=true;
      world.getMapper(Casting.class).create(source).set(198,target,
          world.getMapper(Position.class).has(target)?pos(target):new Vector2(118,100));
      D2.Entry row=D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2")).getEntry("DIRNHTH");
      AnimData anim=world.getMapper(AnimData.class).create(source);anim.speed=row.speed;anim.numFrames=row.framesPerDir<<8;anim.keyframes=row.data;
      events().dispatch(SkillStartEvent.obtain(source,198,target,new Vector2(118,100),54,0));
    }
    void stat(int id,short stat,int n){Attributes a=world.getMapper(AttributesWrapper.class).get(id).attrs;a.base().put(stat,n);a.reset();}
    EventSystem events(){return world.getSystem(EventSystem.class);}
    Casting cast(){return world.getMapper(Casting.class).get(source);}
    NativeDiabRun run(){return cast().diabRun;}
    Vector2 pos(int id){return world.getMapper(Position.class).get(id).position;}
    Velocity velocity(){return world.getMapper(Velocity.class).get(source);}
    float hp(){return world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed();}
    void tick(){assertTrue(ticks++<1000);world.process();}
    void finish(){while(cast()!=null)tick();}
    public void close(){world.dispose();map.dispose();}
  }
}
