package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native Shock Wave shape, fan, one-hit and SrvDmg07 regression coverage. */
class DruidShockWaveTest extends RiiabloTest {
  @Test
  void shockWaveIsBearOnly() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.SHOCK_WAVE);
    StateList states = new StateList(1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(skill, states));
    states.addState(StateId.WOLF, 100, 1, 1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(skill, states));
    states.removeState(StateId.WOLF);
    states.addState(StateId.BEAR, 100, 1, 1);
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(skill, states));
  }

  @Test
  void srvDo008CreatesFiveDamageSnapshotsSharingOneHitSet() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int druid = createDruid(world, 5);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.SHOCK_WAVE, Engine.INVALID_ENTITY, new Vector2(8, 0), 8, 0));

      assertEquals(5, factory.created.size());
      IntSet shared = factory.created.get(0).sharedHitTargets;
      assertNotNull(shared);
      for (Missile missile : factory.created) {
        assertEquals("shockwave", missile.missile.Missile);
        assertEquals(SkillId.SHOCK_WAVE, missile.skillId);
        assertEquals(5, missile.damageLevel);
        assertTrue(missile.damageSnapshot);
        assertTrue(shared == missile.sharedHitTargets,
            "all paths from one cast must share the one-hit-per-target set");
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void successfulShockWaveHitDamagesAndAppliesNativeStunOnce() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int druid = createDruid(world, 1);
      int target = createFallen(world, 1, 0);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.SHOCK_WAVE, target, null, 8, 0));
      for (Missile missile : factory.created) missile.usesAttackRating = false;

      world.setDelta(1f / 25f);
      world.process();

      float hp = world.getMapper(AttributesWrapper.class).get(target)
          .attrs.get(Stat.hitpoints).asFixed();
      assertTrue(hp >= 980f && hp <= 990f,
          "the five overlapping lanes must resolve one 10..20 damage packet, hp=" + hp);
      UnitState stun = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.STUNNED);
      assertNotNull(stun);
      assertEquals(40, stun.duration);
      assertEquals(SkillId.SHOCK_WAVE, stun.skillId);
      assertEquals(druid, stun.sourceEntityId);
      assertTrue(stun.needsSync, "StateP must broadcast the authoritative stun");
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeSrvDmg07RejectsBossStun() {
    Monster boss = new Monster().set(new MonStats.Entry(), new MonStats2.Entry());
    boss.monstats.Velocity = 6;
    boss.monstats.boss = true;
    assertEquals(0, BarbarianSkills.resolveWarCryStunDuration(
        boss, false, false, 40, 99));
  }

  private static int createDruid(World world, int skillLevel) {
    int id = world.create();
    CharData data = CharData.createRemote("druid", (byte) Riiablo.DRUID);
    data.setSkillLevel(SkillId.SHOCK_WAVE, skillLevel);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(20, 1000);
    world.getMapper(UnitStates.class).create(id).init(id)
        .stateList.addState(StateId.BEAR, 1000, 1, id);
    return id;
  }

  private static int createFallen(World world, float x, float y) {
    MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
    int id = world.create();
    world.getMapper(Monster.class).create(id).set(row, row2);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.base().put(Stat.tohit, 100000);
    attrs.base().put(Stat.mindamage, 0);
    attrs.base().put(Stat.maxdamage, 0);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingMissileFactory extends EntityFactory {
    final java.util.ArrayList<Missile> created = new java.util.ArrayList<>();

    @Override public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(angle).setLength(row.Vel);
      created.add(missile);
      return entity;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return createMissile(id, angle, position, Engine.INVALID_ENTITY);
    }
  }
}
