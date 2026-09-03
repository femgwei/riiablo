package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Skills;
import com.badlogic.gdx.math.Vector2;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native-data inventory for the Assassin skill tree before runtime porting. */
class AssassinSkillSpecializationTest extends RiiabloTest {
  @Test
  void auditNativeAssassinRows() {
    int rows = 0;
    for (int id = CharacterClass.ASSASSIN.firstSpell; id < CharacterClass.ASSASSIN.lastSpell; id++) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "assassin skill id=" + id);
      rows++;
      System.out.println("[ASSASSIN_SKILL] id=" + skill.Id + " name=" + skill.skill
          + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc
          + " cltDo=" + skill.cltdofunc + " srvA=" + skill.srvmissilea
          + " srvB=" + skill.srvmissileb + " cltA=" + skill.cltmissilea
          + " summon=" + skill.summon + " petType=" + skill.pettype
          + " petMax=" + skill.petmax + " auraLen=" + skill.auralencalc
          + " calc4=" + skill.calc4 + " params=" + java.util.Arrays.toString(skill.Param));
      if (skill.summon != null && !skill.summon.isEmpty()) {
        com.riiablo.codec.excel.MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
        assertNotNull(summon, skill.skill + " summon=" + skill.summon);
        System.out.println("[ASSASSIN_SUMMON] skill=" + skill.skill + " monster=" + summon.Id
            + " ai=" + summon.AI + " skill1=" + summon.Skill1 + " level1=" + summon.Sk1lvl
            + " skill2=" + summon.Skill2 + " level2=" + summon.Sk2lvl
            + " missA1=" + summon.MissA1 + " missA2=" + summon.MissA2
            + " missS1=" + summon.MissS1 + " missS2=" + summon.MissS2
            + " missS3=" + summon.MissS3 + " missS4=" + summon.MissS4);
        Skills.Entry attack = summon.Skill1 == null || summon.Skill1.isEmpty()
            ? null : Riiablo.files.skills.get(summon.Skill1);
        if (attack != null) {
          System.out.println("[ASSASSIN_SUMMON_SKILL] monster=" + summon.Id + " skill="
              + attack.skill + " srvDo=" + attack.srvdofunc + " srvA=" + attack.srvmissilea
              + " srvB=" + attack.srvmissileb + " cltA=" + attack.cltmissilea
              + " calc4=" + attack.calc4 + " params="
              + java.util.Arrays.toString(attack.Param));
        }
      }
    }
    System.out.println("[ASSASSIN_SKILL_SUMMARY] rows=" + rows);
  }

  @Test
  void shadowWarriorUsesNativeSrvDo049AndOwnedPetState() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry shadow = Riiablo.files.skills.get("Shadow Warrior");
      assertNotNull(shadow);
      data.setSkillLevel(shadow.Id, 5);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = Attributes.obtainStandard();

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, shadow.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), shadow.srvdofunc, 0));

      assertEquals(1, factory.created);
      assertEquals("shadowwarrior", factory.petType);
      assertTrue(factory.skillLevel == 5);
      assertTrue(world.getMapper(UnitStates.class).get(factory.entityId).stateList
          .hasState(StateId.SHADOWWARRIOR));
    } finally {
      world.dispose();
    }
  }

  @Test
  void sentrySrvDo045CreatesOwnedTrapWithNativeShotBudget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      assertNotNull(sentry);
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = Attributes.obtainStandard();
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), sentry.srvdofunc, 0));
      assertEquals(1, factory.created);
      assertEquals("assassintrap", factory.petType);
      assertEquals(5, factory.petMaximum);
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId));
      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertEquals("assassintrap", trap.petType);
      assertEquals(10, trap.maxShots);

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(9, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(100);
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      trap.attackCooldownFrames = 0;
      trap.maxShots = 1;
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.missiles);
      assertEquals("sentry lightning", factory.attackSkill);
      assertEquals(1, trap.shotsFired);
      world.process();
      world.process();
      assertTrue(!world.getMapper(SummonedPet.class).has(factory.entityId),
          "a native sentry must be removed after its shot budget is exhausted");
    } finally {
      world.dispose();
    }
  }

  @Test
  void bladeSentinelSrvDo044StartsAtCasterAndLaunchesTowardTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry blade = Riiablo.files.skills.get("Blade Sentinel");
      assertNotNull(blade);
      data.setSkillLevel(blade.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, blade.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), blade.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertTrue(trap.bladeSentinel);
      assertEquals(1, trap.maxShots);
      assertTrue(trap.durationFrames > 0);
      assertEquals(2f, world.getMapper(Position.class).get(factory.entityId).position.x);
      assertEquals(3f, world.getMapper(Position.class).get(factory.entityId).position.y);
      assertEquals(8f, trap.trapTargetX);
      assertEquals(3f, trap.trapTargetY);
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      trap.attackCooldownFrames = 0;
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.missiles);
      assertEquals("blade creeper", factory.missileName);
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Blade Sentinel controller survives until its native duration expires");
    } finally {
      world.dispose();
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int entityId = Engine.INVALID_ENTITY;
    int skillLevel;
    String petType;
    int petMaximum;
    int missiles;
    String missileName;
    String attackSkill;

    @Override
    public int createSummonedPet(int ownerId, com.riiablo.codec.excel.MonStats.Entry summon,
        String petType, int skillId, int skillLevel, int petMax, boolean passive,
        int durationFrames, float x, float y) {
      created++;
      this.petType = petType;
      this.petMaximum = Math.max(1, petMax);
      this.skillLevel = skillLevel;
      entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = summon;
      world.getMapper(Position.class).create(entityId).position.set(x, y);
      world.getMapper(UnitStates.class).create(entityId).init(entityId);
      world.getMapper(SummonedPet.class).create(entityId)
          .set(ownerId, petType, skillId, skillLevel, passive, durationFrames);
      return entityId;
    }

    @Override
    public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int missileId = world.create();
      world.getMapper(Missile.class).create(missileId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(missileId).position.set(position);
      world.getMapper(Velocity.class).create(missileId).velocity.set(angle).setLength(row.Vel);
      missiles++;
      missileName = row.Missile;
      Monster trapMonster = world.getMapper(Monster.class).get(ownerId);
      attackSkill = trapMonster != null && trapMonster.monstats != null
          ? trapMonster.monstats.Skill1 : null;
      return missileId;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.tohit, 1000);
    attrs.reset();
    return attrs;
  }
}
