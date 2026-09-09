package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvSt16/SrvDo032 authoritative Poison Dagger contract. */
class NecromancerPoisonDaggerIntegrationTest extends RiiabloTest {
  @Test
  void nativeFormulasUseFixedPoisonRateSynergiesDurationAndToHit() {
    Skills.Entry skill = skill();
    int[] level1 = NecromancerSkills.getPoisonDaggerDamage(skill, 1, name -> 0);
    assertEquals(36, level1[0]);
    assertEquals(80, level1[1]);
    assertEquals(50,
        NecromancerSkills.getPoisonDaggerDurationFrames(skill, 1, name -> 0));

    int[] synergized = NecromancerSkills.getPoisonDaggerDamage(skill, 1,
        name -> "Poison Explosion".equals(name) ? 2
            : "Poison Nova".equals(name) ? 3 : 0);
    assertEquals(level1[0] * 2, synergized[0]);
    assertEquals(level1[1] * 2, synergized[1]);
    assertEquals(80,
        NecromancerSkills.getPoisonDaggerDurationFrames(skill, 4, name -> 0));
    assertEquals(130, NecromancerSkills.getPoisonDaggerAttackRating(
        skill, 1, attributes(100, 0, 0, 100), true));
    assertEquals(170, NecromancerSkills.getPoisonDaggerAttackRating(
        skill, 3, attributes(100, 0, 0, 100), true));
  }

  @Test
  void castRejectsNonDaggerBeforeManaIsSpent() {
    Actioneer actioneer = new Actioneer();
    ServerSkillSystem skills = new ServerSkillSystem(false);
    World world = world(actioneer, skills);
    try {
      int necromancer = player(world, "cast-gate", 0, 0,
          attributes(100, 1, 2, 1000));
      int target = monster(world, 1, 0, attributes(100, 0, 0, 0));
      CharData data = world.getMapper(Player.class).get(necromancer).data;
      data.setSkillLevel(skill().Id, 1);
      data.getItems().equipItem(BodyLoc.RARM,
          data.getItems().add(weapon("ssd", 5, 10)));
      float beforeMana = mana(world, necromancer);

      SkillCastEvent cast = SkillCastEvent.obtain(
          necromancer, skill().Id, target, new Vector2(1, 0));
      skills.onSkillCast(cast);

      assertFalse(cast.accepted);
      assertEquals(9, cast.resultCode);
      assertEquals(beforeMana, mana(world, necromancer));
    } finally {
      world.dispose();
    }
  }

  @Test
  void preparedHitAppliesPhysicalAndPoisonAndIsConsumedOnlyOnce() {
    Actioneer actioneer = new Actioneer();
    ServerSkillSystem skills = new ServerSkillSystem(false);
    World world = world(actioneer, skills);
    try {
      int necromancer = player(world, "poison-hit", 0, 0,
          attributes(100, 0, 0, 100000));
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0));
      CharData data = world.getMapper(Player.class).get(necromancer).data;
      data.setSkillLevel(skill().Id, 1);
      Item dagger = weapon("dgr", 100, 10);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(dagger));
      Casting casting = world.getMapper(Casting.class).create(necromancer)
          .set(skill().Id, target, new Vector2(1, 0));

      MathUtils.random.setSeed(0x504F49534F4EL);
      actioneer.onSkillStart(SkillStartEvent.obtain(necromancer, skill().Id,
          target, new Vector2(1, 0), skill().srvstfunc, skill().cltstfunc));
      assertTrue(casting.poisonDaggerPrepared);
      assertNotNull(casting.poisonDaggerCombat);
      assertTrue(casting.poisonDaggerCombat.hit,
          "the deterministic high-AR fixture must retain a successful SrvSt16 record");
      assertFalse(casting.poisonDaggerCombat.blocked);
      assertTrue(casting.poisonDaggerCombat.poisonDamagePerFrame > 0f);
      int duration = casting.poisonDaggerCombat.poisonDuration;

      long durabilitySeed = durabilitySeed();
      MathUtils.random.setSeed(durabilitySeed);
      float before = hp(world, target);
      int durabilityBefore = dagger.attrs.get(Stat.durability).asInt();
      actioneer.onAnimDataKeyframe(
          AnimDataKeyframeEvent.obtain(necromancer, Engine.KEYFRAME_ATK));

      assertTrue(hp(world, target) < before);
      UnitState poison = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.POISON);
      assertNotNull(poison);
      assertEquals(duration, poison.duration);
      assertTrue(poison.exactDamagePerFrame > 0f);
      assertEquals(durabilityBefore - 1, dagger.attrs.get(Stat.durability).asInt(),
          "SrvDo032 must drain the retained dagger exactly once on a successful hit");
      assertFalse(casting.poisonDaggerPrepared);

      float afterFirst = hp(world, target);
      int durabilityAfterFirst = dagger.attrs.get(Stat.durability).asInt();
      actioneer.onAnimDataKeyframe(
          AnimDataKeyframeEvent.obtain(necromancer, Engine.KEYFRAME_ATK));
      assertEquals(afterFirst, hp(world, target));
      assertEquals(durabilityAfterFirst, dagger.attrs.get(Stat.durability).asInt());
    } finally {
      world.dispose();
    }
  }

  @Test
  void missAndWrongWeaponNeverApplyPoison() {
    Actioneer actioneer = new Actioneer();
    ServerSkillSystem skills = new ServerSkillSystem(false);
    World world = world(actioneer, skills);
    try {
      int necromancer = player(world, "poison-miss", 0, 0,
          attributes(100, 0, 0, 100000));
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0));
      CharData data = world.getMapper(Player.class).get(necromancer).data;
      data.setSkillLevel(skill().Id, 1);
      data.getItems().equipItem(BodyLoc.RARM,
          data.getItems().add(weapon("dgr", 20, 10)));
      Casting casting = world.getMapper(Casting.class).create(necromancer)
          .set(skill().Id, target, new Vector2(1, 0));
      MathUtils.random.setSeed(0xD4663L);
      actioneer.onSkillStart(SkillStartEvent.obtain(necromancer, skill().Id,
          target, new Vector2(1, 0), skill().srvstfunc, skill().cltstfunc));
      assertTrue(casting.poisonDaggerPrepared);
      casting.poisonDaggerCombat.hit = false;
      float before = hp(world, target);
      actioneer.onAnimDataKeyframe(
          AnimDataKeyframeEvent.obtain(necromancer, Engine.KEYFRAME_ATK));
      assertEquals(before, hp(world, target));
      assertNull(world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.POISON));
      assertFalse(casting.poisonDaggerPrepared);

      data.getItems().unequipItem(BodyLoc.RARM);
      data.getItems().equipItem(BodyLoc.RARM,
          data.getItems().add(weapon("ssd", 20, 10)));
      world.getMapper(Casting.class).remove(necromancer);
      casting = world.getMapper(Casting.class).create(necromancer)
          .set(skill().Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(necromancer, skill().Id,
          target, new Vector2(1, 0), skill().srvstfunc, skill().cltstfunc));
      assertFalse(casting.poisonDaggerPrepared);
    } finally {
      world.dispose();
    }
  }

  private static Skills.Entry skill() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_DAGGER);
    assertNotNull(skill);
    return skill;
  }

  private static World world(Actioneer actioneer, ServerSkillSystem skills) {
    DummyFactory factory = new DummyFactory();
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), skills, actioneer, new StateUpdater(),
            new Pathfinder(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(
      World world, String name, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote(name, (byte) Riiablo.NECROMANCER);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).monstats = Riiablo.files.monstats.get("fallen1");
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 99);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.strength, 0);
    attrs.base().put(Stat.dexterity, 0);
    attrs.reset();
    return attrs;
  }

  private static Item weapon(String code, int damage, int durability) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().put(Stat.mindamage, damage);
    item.attrs.base().put(Stat.maxdamage, damage);
    item.attrs.base().put(Stat.durability, durability);
    item.attrs.base().put(Stat.maxdurability, durability);
    item.attrs.reset();
    return item;
  }

  private static float hp(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static float mana(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.mana).asFixed();
  }

  /** Finds and rewinds a deterministic RNG state whose first durability roll succeeds. */
  private static long durabilitySeed() {
    for (long seed = 1; seed < 10000; seed++) {
      MathUtils.random.setSeed(seed);
      if (MathUtils.random(99) < 4) return seed;
    }
    throw new AssertionError("unable to find deterministic durability seed");
  }

  private static final class DummyFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
