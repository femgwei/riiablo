package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Pure native-data and special-state regression coverage for barbarian war cries. */
class BarbarianWarCryTest extends RiiabloTest {
  @Test
  void howlAndWarCryApplyNativeRuntimeValues() {
    Skills.Entry howl = skill("Howl");
    StateList states = new StateList(10);
    UnitState terror = BarbarianSkills.applyHowlState(states, howl, 1, 2, 3, 7, true);
    assertNotNull(terror);
    assertEquals(StateId.TERROR, terror.stateId);
    assertEquals(75, terror.duration);
    assertEquals(24, terror.runtimeValue);
    assertFalse(BarbarianSkills.canHowlTarget(howl, 1, 1, 3));

    Skills.Entry shout = skill("Shout");
    states = new StateList(11);
    UnitState shoutState = BarbarianSkills.applyWarCryState(
        states, shout, 2, 7, false, name -> 0);
    assertNotNull(shoutState);
    assertEquals(StateId.SHOUT, shoutState.stateId);
    assertEquals(750, shoutState.duration);
    assertEquals(110, shoutState.defenseModifier);

    Skills.Entry taunt = skill("Taunt");
    states = new StateList(12);
    UnitState tauntState = BarbarianSkills.applyWarCryState(
        states, taunt, 3, 7, true, name -> 0);
    assertNotNull(tauntState);
    assertEquals(0, tauntState.duration, "Taunt has an empty AuraLenCalc in native Skills.txt");
    assertEquals(-9, tauntState.attackModifier);
    assertEquals(-9, tauntState.damageModifier);

    Skills.Entry battleCry = skill("Battle Cry");
    states = new StateList(13);
    UnitState battleState = BarbarianSkills.applyWarCryState(
        states, battleCry, 3, 7, true, name -> 0);
    assertNotNull(battleState);
    assertEquals(StateId.BATTLECRY, battleState.stateId);
    assertEquals(420, battleState.duration);
    assertEquals(-54, battleState.defenseModifier);
    assertEquals(-27, battleState.damageModifier);

    Skills.Entry orders = skill("Battle Orders");
    states = new StateList(14);
    UnitState ordersState = BarbarianSkills.applyWarCryState(
        states, orders, 3, 7, false, name -> 0);
    assertNotNull(ordersState);
    assertEquals(StateId.BATTLEORDERS, ordersState.stateId);
    assertEquals(1250, ordersState.duration);
    assertEquals(41, ordersState.maxLifeModifier);
    assertEquals(41, ordersState.maxManaModifier);
    assertEquals(41, ordersState.maxStaminaModifier);

    Skills.Entry command = skill("Battle Command");
    states = new StateList(15);
    UnitState commandState = BarbarianSkills.applyWarCryState(
        states, command, 1, 7, false, name -> 0);
    assertNotNull(commandState);
    assertEquals(StateId.BATTLECOMMAND, commandState.stateId);
    assertEquals(125, commandState.duration);
    assertEquals(1, commandState.skillModifier);
  }

  @Test
  void canSwitchAiMatchesNativeWalkSwitchAndRankGates() {
    Monster monster = new Monster().set(new MonStats.Entry(), new MonStats2.Entry());
    monster.monstats.switchai = true;
    monster.monstats.boss = false;
    monster.monstats2.mMode = new boolean[16];
    monster.monstats2.mMode[Engine.Monster.MODE_WL] = true;
    assertTrue(BarbarianSkills.canSwitchWarCryAi(monster, new StateList(1)));

    monster.monstats2.mMode[Engine.Monster.MODE_WL] = false;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.monstats2.mMode[Engine.Monster.MODE_WL] = true;
    monster.rank = MonsterRank.SUPER_UNIQUE;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.rank = MonsterRank.NORMAL;
    monster.monstats.boss = true;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.monstats.boss = false;
    StateList blocked = new StateList(1);
    blocked.addState(StateId.UNINTERRUPTABLE, 20);
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, blocked));
  }

  @Test
  void warCryStunUsesNativeBossUniqueVelocityAndHirelingRules() {
    Monster normal = new Monster().set(new MonStats.Entry(), new MonStats2.Entry());
    normal.monstats.Velocity = 6;
    assertEquals(75,
        BarbarianSkills.resolveWarCryStunDuration(normal, false, false, 75, 0));
    assertEquals(13,
        BarbarianSkills.resolveWarCryStunDuration(normal, false, true, 75, 0));
    normal.rank = MonsterRank.UNIQUE;
    assertEquals(0,
        BarbarianSkills.resolveWarCryStunDuration(normal, false, false, 75, 89));
    assertEquals(75,
        BarbarianSkills.resolveWarCryStunDuration(normal, false, false, 75, 90));
    normal.rank = MonsterRank.NORMAL;
    normal.monstats.boss = true;
    assertEquals(0,
        BarbarianSkills.resolveWarCryStunDuration(normal, false, false, 75, 99));
    assertEquals(250,
        BarbarianSkills.resolveWarCryStunDuration(null, true, false, 300, 0));
  }

  @Test
  void battleOrdersMaximumResourcesApplyOnceAndRestoreOnExpiry() {
    StateUpdater updater = new StateUpdater();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), updater, factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int unit = world.create();
      Attributes attrs = attributes(1, 100);
      world.getMapper(AttributesWrapper.class).create(unit).attrs = attrs;
      UnitStates states = world.getMapper(UnitStates.class).create(unit).init(unit);
      UnitState orders = BarbarianSkills.applyWarCryState(
          states.stateList, skill("Battle Orders"), 1, unit, false, name -> 0);
      assertNotNull(orders);

      world.process();
      assertEquals(135f, attrs.get(Stat.maxhp).asFixed(), 0.001f);
      assertEquals(135f, attrs.get(Stat.maxmana).asFixed(), 0.001f);
      assertEquals(135f, attrs.get(Stat.maxstamina).asFixed(), 0.001f);
      world.process();
      assertEquals(135f, attrs.get(Stat.maxhp).asFixed(), 0.001f,
          "the state percentage must not compound on later ticks");

      attrs.aggregate().put(Stat.maxhp, 120f);
      world.process();
      assertEquals(162f, attrs.get(Stat.maxhp).asFixed(), 0.001f,
          "a fresh equipment/stat aggregate must become the new unmodified base");

      attrs.aggregate().put(Stat.hitpoints, 162f);
      orders.duration = 1;
      world.process();
      assertFalse(states.stateList.hasState(StateId.BATTLEORDERS));
      assertEquals(120f, attrs.get(Stat.maxhp).asFixed(), 0.001f);
      assertEquals(120f, attrs.get(Stat.hitpoints).asFixed(), 0.001f);
      assertEquals(100f, attrs.get(Stat.maxmana).asFixed(), 0.001f);
      assertEquals(100f, attrs.get(Stat.maxstamina).asFixed(), 0.001f);
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void howlMissileAppliesTerrorWithoutOrdinaryDamage() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      int barbarian = world.create();
      world.getMapper(Player.class).create(barbarian);
      world.getMapper(Position.class).create(barbarian).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(barbarian).attrs = attributes(2, 100);

      MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      MonStats2.Entry fallen2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      assertNotNull(fallen2);
      assertTrue(fallen.switchai);
      assertTrue(fallen2.mMode[Engine.Monster.MODE_WL]);
      int target = world.create();
      world.getMapper(Monster.class).create(target).set(fallen, fallen2);
      world.getMapper(Position.class).create(target).position.set(1, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(3, 100);
      world.getMapper(UnitStates.class).create(target).init(target);

      Skills.Entry howl = skill("Howl");
      Missiles.Entry row = Riiablo.files.Missiles.get(howl.srvmissilea);
      assertNotNull(row);
      int missileId = world.create();
      Missile missile = world.getMapper(Missile.class).create(missileId)
          .set(row, new Vector2(1, 0), row.Range).setOwner(barbarian);
      missile.skillId = howl.Id;
      missile.damageLevel = 1;
      world.getMapper(Position.class).create(missileId).position.set(1, 0);
      world.getMapper(Velocity.class).create(missileId).velocity.setZero();

      world.setDelta(1f / 25f);
      world.process();

      UnitState terror = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.TERROR);
      assertNotNull(terror);
      assertEquals(75, terror.duration);
      assertEquals(24, terror.runtimeValue);
      assertEquals(100f, world.getMapper(AttributesWrapper.class).get(target)
          .attrs.get(Stat.hitpoints).asFixed(), 0.001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void warCryMissileDealsSkillDamageAndAppliesNativeStun() {
    StateUpdater updater = new StateUpdater();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), updater, new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int barbarian = world.create();
      world.getMapper(Player.class).create(barbarian);
      world.getMapper(Position.class).create(barbarian).position.set(0, 0);
      Attributes ownerAttrs = attributes(20, 100);
      world.getMapper(AttributesWrapper.class).create(barbarian).attrs = ownerAttrs;

      MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      MonStats2.Entry fallen2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      int target = world.create();
      world.getMapper(Monster.class).create(target).set(fallen, fallen2);
      world.getMapper(Position.class).create(target).position.set(1, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1, 100);
      world.getMapper(UnitStates.class).create(target).init(target);

      Skills.Entry skill = skill("War Cry");
      Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
      int missileId = world.create();
      Missile missile = world.getMapper(Missile.class).create(missileId)
          .set(row, new Vector2(1, 0), row.Range).setOwner(barbarian);
      assertTrue(MissileDamageResolver.initializeSkill(missile, skill, ownerAttrs, 3));
      world.getMapper(Position.class).create(missileId).position.set(1, 0);
      world.getMapper(Velocity.class).create(missileId).velocity.setZero();

      world.setDelta(1f / 25f);
      world.process();

      assertTrue(world.getMapper(AttributesWrapper.class).get(target)
          .attrs.get(Stat.hitpoints).asFixed() < 100f);
      UnitState stun = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.STUNNED);
      assertNotNull(stun);
      assertEquals(35, stun.duration);
      assertEquals(skill.Id, stun.skillId);
      assertEquals(barbarian, stun.sourceEntityId);
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void battleOrdersWaveBuffsOwnedSummonWithoutDamage() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      int barbarian = world.create();
      world.getMapper(Player.class).create(barbarian);
      world.getMapper(Position.class).create(barbarian).position.set(0, 0);

      MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      MonStats2.Entry fallen2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      int summon = world.create();
      world.getMapper(Monster.class).create(summon).set(fallen, fallen2);
      world.getMapper(SummonedPet.class).create(summon)
          .set(barbarian, "test", -1, 1, false, 0);
      world.getMapper(Position.class).create(summon).position.set(1, 0);
      world.getMapper(AttributesWrapper.class).create(summon).attrs = attributes(1, 100);
      world.getMapper(UnitStates.class).create(summon).init(summon);

      Skills.Entry skill = skill("Battle Orders");
      Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
      int missileId = world.create();
      Missile missile = world.getMapper(Missile.class).create(missileId)
          .set(row, new Vector2(1, 0), row.Range).setOwner(barbarian);
      missile.skillId = skill.Id;
      missile.damageLevel = 1;
      world.getMapper(Position.class).create(missileId).position.set(1, 0);
      world.getMapper(Velocity.class).create(missileId).velocity.setZero();

      world.setDelta(1f / 25f);
      world.process();

      UnitState orders = world.getMapper(UnitStates.class).get(summon)
          .stateList.getState(StateId.BATTLEORDERS);
      assertNotNull(orders);
      assertEquals(35, orders.maxLifeModifier);
      assertEquals(35, orders.maxManaModifier);
      assertEquals(35, orders.maxStaminaModifier);
      assertEquals(100f, world.getMapper(AttributesWrapper.class).get(summon)
          .attrs.get(Stat.hitpoints).asFixed(), 0.001f);
    } finally {
      world.dispose();
    }
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.stamina, 100);
    attrs.base().put(Stat.maxstamina, 100);
    attrs.reset();
    return attrs;
  }

  private static Skills.Entry skill(String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill, name);
    return skill;
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
