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
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BloodManaIntegrationTest extends RiiabloTest {
  @Test void ordinaryCastStillSpendsMana() {
    try (Fixture f = new Fixture(100,100,false)) {
      assertTrue(f.cast().accepted);
      assertEquals(100-f.cost, f.value(Stat.mana));
      assertEquals(100, f.value(Stat.hitpoints));
    }
  }
  @Test void cursedCastSpendsLifeEvenWithZeroMana() {
    try (Fixture f = new Fixture(100,0,true)) {
      assertTrue(f.cast().accepted);
      assertEquals(100-f.cost, f.value(Stat.hitpoints));
      assertEquals(0, f.value(Stat.mana));
      assertTrue(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void insufficientLifeClampsToOneAndRejectsWithoutManaCharge() {
    try (Fixture f = new Fixture(1,100,true)) {
      assertFalse(f.cast().accepted);
      assertEquals(1, f.value(Stat.hitpoints));
      assertEquals(100, f.value(Stat.mana));
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void exactLifeCostIsAcceptedAndCanReachZero() {
    try (Fixture f = new Fixture(100,100,true)) {
      f.set(Stat.hitpoints,f.cost);
      assertTrue(f.cast().accepted);
      assertEquals(0, f.value(Stat.hitpoints));
      assertEquals(100, f.value(Stat.mana));
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void removalThresholdIsStrictAndComesFromCurseSkill() {
    try (Fixture f = new Fixture(100,100,true)) {
      int threshold = Riiablo.files.skills.get("Blood Mana").Param[4];
      assertTrue(threshold>0);
      f.set(Stat.hitpoints,f.cost+threshold);
      assertTrue(f.cast().accepted);
      assertTrue(f.states.hasState(StateId.BLOOD_MANA));
      f.set(Stat.hitpoints,f.cost+threshold-1f/256);
      assertTrue(f.cast().accepted);
      assertEquals(threshold-1f/256,f.value(Stat.hitpoints));
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void unrelatedCurseSurvivesBloodManaRemoval() {
    try (Fixture f = new Fixture(1,100,true)) {
      f.states.addState(StateId.WEAKEN,100,1,9);
      assertFalse(f.cast().accepted);
      assertTrue(f.states.hasState(StateId.WEAKEN));
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void invalidUnlearnedSkillDoesNotSpendLifeOrRemoveCurse() {
    try (Fixture f = new Fixture(1,100,true)) {
      SkillCastEvent event = SkillCastEvent.obtain(f.id,SkillId.FIRE_BALL,Engine.INVALID_ENTITY,Vector2.Zero);
      f.world.getSystem(EventSystem.class).dispatch(event);
      assertFalse(event.accepted);
      assertEquals(1,f.value(Stat.hitpoints));
      assertTrue(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void auraConsumptionUsesLifeAndPreflightDoesNotMutate() {
    try (Fixture f = new Fixture(100,0,true)) {
      assertTrue(f.aura.canConsumeMana(f.id, 2.5f));
      assertEquals(100,f.value(Stat.hitpoints));
      assertTrue(f.aura.consumeMana(f.id, 2.5f));
      assertEquals(97.5f,f.value(Stat.hitpoints));
      assertEquals(0,f.value(Stat.mana));
    }
  }
  @Test void auraInsufficientLifeRemovesCurseAndReportsFailure() {
    try (Fixture f = new Fixture(1,100,true)) {
      assertTrue(f.aura.canConsumeMana(f.id,2.5f));
      assertFalse(f.aura.consumeMana(f.id,2.5f));
      assertEquals(1,f.value(Stat.hitpoints));
      assertEquals(100,f.value(Stat.mana));
      assertFalse(f.states.hasState(StateId.BLOOD_MANA));
    }
  }
  @Test void expiredCurseRestoresManaPayment() {
    try (Fixture f = new Fixture(100,100,true)) {
      f.states.removeState(StateId.BLOOD_MANA);
      assertTrue(f.cast().accepted);
      assertEquals(100,f.value(Stat.hitpoints));
      assertEquals(100-f.cost,f.value(Stat.mana));
    }
  }
  @Test void auraWithoutCurseStillSpendsManaAndRejectsUnfundedPayment() {
    try (Fixture f = new Fixture(100,3,false)) {
      assertTrue(f.aura.consumeMana(f.id,2.5f));
      assertEquals(.5f,f.value(Stat.mana));
      assertFalse(f.aura.canConsumeMana(f.id,2.5f));
      assertFalse(f.aura.consumeMana(f.id,2.5f));
      assertEquals(100,f.value(Stat.hitpoints));
    }
  }
  @Test void nonPlayerAuraDoesNotRedirectToLife() {
    try (Fixture f = new Fixture(100,3,true)) {
      f.world.getMapper(Player.class).remove(f.id);
      assertTrue(f.aura.consumeMana(f.id,2.5f));
      assertEquals(.5f,f.value(Stat.mana));
      assertEquals(100,f.value(Stat.hitpoints));
    }
  }

  static class Fixture implements AutoCloseable {
    final AuraEcsSystem aura = new AuraEcsSystem();
    final World world;
    final int id;
    final Attributes attrs = Attributes.obtainStandard();
    final StateList states;
    final float cost = NativeSkillResolver.manaCost(Riiablo.files.skills.get(SkillId.NOVA),1);
    Fixture(float hp,float mana,boolean cursed) {
      EntityFactory factory = new NoopFactory();
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new ServerSkillSystem(true),aura,factory).build()
          .register("factory",factory).register("map",new com.riiablo.map.Map(0,0)));
      id = world.create();
      CharData data = CharData.createRemote("bloodmana",(byte)Riiablo.SORCERESS);
      data.setSkillLevel(SkillId.NOVA,1);
      data.setSkillLevel(SkillId.STATIC_FIELD,1);
      world.getMapper(Player.class).create(id).data=data;
      world.getMapper(Position.class).create(id).position.setZero();
      attrs.base().put(Stat.level,99);
      attrs.base().put(Stat.hitpoints,hp);
      attrs.base().put(Stat.maxhp,100f);
      attrs.base().put(Stat.mana,mana);
      attrs.base().put(Stat.maxmana,100f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs=attrs;
      states=world.getMapper(UnitStates.class).create(id).init(id).stateList;
      if(cursed) {
        Skills.Entry curse=Riiablo.files.skills.get("Blood Mana");
        assertNotNull(NecromancerSkills.applyCurse(states,Riiablo.files.States,curse,1,9,
            Riiablo.files.DifficultyLevels.get(0),attrs,true));
      }
    }
    SkillCastEvent cast() {
      SkillCastEvent event=SkillCastEvent.obtain(id,SkillId.NOVA,Engine.INVALID_ENTITY,Vector2.Zero);
      world.getSystem(EventSystem.class).dispatch(event);
      return event;
    }
    float value(short stat) { return attrs.get(stat).asFixed(); }
    void set(short stat,float value) { attrs.get(stat).set(value); }
    public void close() {world.dispose();}
  }
  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act,int id,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act,int id,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int id,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item,float x,float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id,Vector2 angle,Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
