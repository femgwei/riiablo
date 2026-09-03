package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo034/SrvDo035 regression coverage. */
class AssassinMartialArtsTest extends RiiabloTest {
  @Test
  void progressiveStateUsesAuraStateCapsAtThreeAndDoesNotChangeMovementSpeed() {
    Skills.Entry tiger = new Skills.Entry();
    tiger.Id = 254;
    tiger.skill = "Tiger Strike";
    tiger.aurastate = "progressive_damage";
    tiger.auralencalc = "100+lvl";
    StateList states = new StateList(7);

    UnitState charge = null;
    for (int i = 0; i < 5; i++) {
      charge = AssassinSkills.addProgressiveCharge(states, tiger, 4, 7);
      assertNotNull(charge);
    }

    assertEquals(StateId.PROGRESSIVE_DAMAGE, charge.stateId);
    assertEquals(3, AssassinSkills.progressiveCharges(charge));
    assertEquals(104, charge.duration);
    assertEquals(254, charge.skillId);
    assertEquals(0, states.getTotalVelocityModifier(),
        "the StateP scalar carrying charge count must not affect movement");
  }

  @Test
  void tigerAndCobraUseNativeChargeFormulas() {
    Skills.Entry tiger = new Skills.Entry();
    tiger.calc1 = "par1+(lvl-1)*par2";
    tiger.Param = new int[] {100, 20, 0, 0, 0, 0, 0, 0};
    assertEquals(280, AssassinSkills.calculateTigerStrikeDamageBonus(tiger, 3, 2));

    Skills.Entry cobra = new Skills.Entry();
    cobra.Param = new int[] {40, 5, 0, 0, 0, 0, 0, 0};
    assertEquals(50, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 1)[0]);
    assertEquals(0, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 1)[1]);
    assertEquals(50, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 2)[1]);
    assertEquals(100, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 3)[0]);
    assertEquals(100, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 3)[1]);
  }

  @Test
  void actioneerAddsChargesOnlyFromSuccessfulMeleeRecords() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(10000, 1, 1, 100000));
      int target = createMonster(world, 1, 0, attributes(10000, 0, 0, 0));
      Skills.Entry tiger = Riiablo.files.skills.get("Tiger Strike");
      Skills.Entry cobra = Riiablo.files.skills.get("Cobra Strike");
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      assertNotNull(tiger);
      assertNotNull(cobra);
      assertNotNull(fists);
      assertEquals(34, tiger.srvdofunc);
      assertEquals(34, cobra.srvdofunc);
      assertEquals(35, fists.srvdofunc);

      MathUtils.random.setSeed(0xA551551L);
      buildToThreeCharges(world, assassin, target, tiger);
      buildToThreeCharges(world, assassin, target, cobra);
      buildToThreeCharges(world, assassin, target, fists);

      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_DAMAGE));
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_STEAL));
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_FIRE));

      int distant = createMonster(world, 50, 50, attributes(10000, 0, 0, 0));
      states.removeState(StateId.PROGRESSIVE_DAMAGE);
      world.getMapper(Casting.class).get(assassin)
          .set(tiger.Id, distant, world.getMapper(Position.class).get(distant).position);
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      assertNull(states.getState(StateId.PROGRESSIVE_DAMAGE));
    } finally {
      world.dispose();
    }
  }

  @Test
  void serverSkillSystemDoesNotSpawnReleaseMissilesWhileBuildingCharge() {
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(100, 1, 1, 100));
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, fists.Id, Engine.INVALID_ENTITY, new Vector2(1, 0),
          fists.srvdofunc, fists.cltdofunc));
      assertEquals(0, factory.missilesCreated);
    } finally {
      world.dispose();
    }
  }

  private static void buildToThreeCharges(
      World world, int assassin, int target, Skills.Entry skill) {
    world.getMapper(Casting.class).get(assassin)
        .set(skill.Id, target, world.getMapper(Position.class).get(target).position);
    int stateId = AssassinSkills.progressiveStateId(skill);
    for (int i = 0; i < 20; i++) {
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      if (AssassinSkills.progressiveCharges(states, stateId) == 3) return;
    }
    assertEquals(3, AssassinSkills.progressiveCharges(
        world.getMapper(UnitStates.class).get(assassin).stateList, stateId));
  }

  private static int createPlayer(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Casting.class).create(id);
    return id;
  }

  private static int createMonster(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static final class DummyFactory extends EntityFactory {
    int missilesCreated;

    @Override public int createPlayer(CharData charData, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      missilesCreated++;
      return -1;
    }
  }
}
