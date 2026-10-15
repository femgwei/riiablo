package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BladeFuryBloodManaIntegrationTest extends RiiabloTest {
  @Test void cursedBladePaysLifeAfterCreationWithNoMana() {
    try(Fixture f=new Fixture(100,0,true)) {
      f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(100-f.cost,f.hp());
      assertEquals(0,f.mana());
    }
  }
  @Test void insufficientLifeKeepsCreatedBladeAndRemovesOnlyBloodMana() {
    try(Fixture f=new Fixture(.5f,100,true)) {
      f.states.addState(StateId.WEAKEN,100,1,9);
      f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(1,f.hp());
      assertEquals(100,f.mana());
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
      assertTrue(f.states.hasState(StateId.WEAKEN));
    }
  }
  @Test void equalLifeCostCreatesBladeAndReachesZero() {
    try(Fixture f=new Fixture(100,100,true)) {
      f.attrs.get(Stat.hitpoints).set(f.cost);
      f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(0,f.hp());
      assertEquals(100,f.mana());
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void cadenceRejectedBladeDoesNotChargeTwice() {
    try(Fixture f=new Fixture(100,100,true)) {
      f.blade(); f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(100-f.cost,f.hp());
      for(int i=0;i<5;i++){f.world.setDelta(1f/25);f.world.process();}
      f.blade();
      assertEquals(2,f.factory.missiles);
      assertEquals(100-2*f.cost,f.hp());
      assertEquals(100,f.mana());
    }
  }
  @Test void failedMissileCreationDoesNotSpendLifeOrClearCurse() {
    try(Fixture f=new Fixture(.5f,100,true)) {
      f.factory.fail=true;
      f.blade();
      assertEquals(0,f.factory.missiles);
      assertEquals(.5f,f.hp());
      assertEquals(100,f.mana());
      assertTrue(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void initialStartManaGateRemainsManaBasedUnderCurse() {
    try(Fixture f=new Fixture(100,0,true)) {
      assertTrue(f.skill.startmana>0);
      assertFalse(f.cast().accepted);
      assertEquals(100,f.hp());
      f.attrs.get(Stat.mana).set((float)f.skill.startmana);
      assertTrue(f.cast().accepted);
      assertEquals(100,f.hp());
      assertEquals(f.skill.startmana,f.mana());
    }
  }
  @Test void activeInfernoAllowsContinuationWithoutStartMana() {
    try(Fixture f=new Fixture(100,0,true)) {
      f.states.addState(StateId.INFERNO,21,1,f.id);
      assertTrue(f.cast().accepted);
      assertEquals(100,f.hp());
      f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(100-f.cost,f.hp());
    }
  }
  @Test void unCursedBladeStillUsesMana() {
    try(Fixture f=new Fixture(100,100,false)) {
      f.blade();
      assertEquals(1,f.factory.missiles);
      assertEquals(100,f.hp());
      assertEquals(100-f.cost,f.mana());
    }
  }
  private static final class Fixture implements AutoCloseable {
    final Factory factory=new Factory();
    final World world;
    final Skills.Entry skill=Riiablo.files.skills.get("Blade Fury");
    final float cost=NativeSkillResolver.manaCost(skill,1);
    final Attributes attrs=Attributes.obtainStandard();
    final StateList states;
    final int id;
    Fixture(float hp,float mana,boolean curse) {
      world=new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new ServerSkillSystem(true),factory).build().register("factory",factory)
          .register("map",new com.riiablo.map.Map(0,0)));
      id=world.create();
      CharData data=CharData.createRemote("bladeblood",(byte)Riiablo.ASSASSIN);
      data.setSkillLevel(skill.Id,1);
      for(String name:new String[]{skill.reqskill1,skill.reqskill2,skill.reqskill3})
        if(name!=null&&!name.isEmpty())data.setSkillLevel(Riiablo.files.skills.get(name).Id,1);
      world.getMapper(Player.class).create(id).data=data;
      world.getMapper(Position.class).create(id).position.setZero();
      attrs.base().put(Stat.level,99);
      attrs.base().put(Stat.hitpoints,hp); attrs.base().put(Stat.maxhp,100f);
      attrs.base().put(Stat.mana,mana); attrs.base().put(Stat.maxmana,100f);
      attrs.base().put(Stat.mindamage,40); attrs.base().put(Stat.maxdamage,40); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs=attrs;
      states=world.getMapper(UnitStates.class).create(id).init(id).stateList;
      if(curse)assertNotNull(NecromancerSkills.applyCurse(states,Riiablo.files.States,
          Riiablo.files.skills.get("Blood Mana"),1,9,Riiablo.files.DifficultyLevels.get(0),attrs,true));
    }
    void blade(){world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        id,skill.Id,Engine.INVALID_ENTITY,new Vector2(8,0),skill.srvdofunc,skill.cltdofunc));}
    SkillCastEvent cast(){SkillCastEvent e=SkillCastEvent.obtain(id,skill.Id,Engine.INVALID_ENTITY,new Vector2(8,0));
      world.getSystem(EventSystem.class).dispatch(e);return e;}
    float hp(){return attrs.get(Stat.hitpoints).asFixed();}
    float mana(){return attrs.get(Stat.mana).asFixed();}
    public void close(){world.dispose();}
  }
  private static final class Factory extends EntityFactory {
    int missiles; boolean fail;
    @Override public int createMissile(int id,Vector2 angle,Vector2 position,int owner){
      if(fail)return Engine.INVALID_ENTITY;
      com.riiablo.codec.excel.Missiles.Entry row=Riiablo.files.Missiles.get(id);
      int entity=world.create();
      world.getMapper(Missile.class).create(entity).set(row,position,row.Range).setOwner(owner);
      world.getMapper(Position.class).create(entity).position.set(position);
      missiles++;return entity;
    }
    @Override public int createPlayer(CharData data,Vector2 pos){return -1;}
    @Override public int createDynamicObject(int act,int id,float x,float y){return -1;}
    @Override public int createStaticObject(int act,int id,float x,float y){return -1;}
    @Override public int createStaticObjectByClassId(int id,float x,float y){return -1;}
    @Override public int createMonster(int id,float x,float y){return -1;}
    @Override public int createWarp(int id,float x,float y){return -1;}
    @Override public int createItem(com.riiablo.item.Item item,float x,float y){return -1;}
    @Override public int createMissile(int id,Vector2 angle,Vector2 pos){return -1;}
  }
}
