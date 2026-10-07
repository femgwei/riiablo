package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/**
 * Tenth DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code SkillSor.cpp:812 SKILLS_SrvDo026_ChainLightning},
 * {@code MissMode.cpp:2522 MISSMODE_SrvHit12_ChainLightning_LightningStrike},
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one chain-lightning segment applied once to one target.
 * SrvDo026 creates the root and SrvHit12 creates later segments with the same
 * skill id and level. Jump count, target selection, resistances, synergies, and
 * cast-wide totals remain later audit work.</p>
 */
class ChainLightningGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
      256, 256, 256, 256, 256, 256, 256, 256, 256, 256
  };
  private static final int[] D2MOO_FIXED_MAX = {
      10240, 13056, 15872, 18688, 21504, 24320, 27136, 29952, 33280, 36608,
      39936, 43264, 46592, 49920, 53248, 56576, 60416, 64256, 68096, 71936
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      40, 51, 62, 73, 84, 95, 106, 117, 130, 143,
      156, 169, 182, 195, 208, 221, 236, 251, 266, 281
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.CHAIN_LIGHTNING);
    Missiles.Entry missile = Riiablo.files.Missiles.get("chainlightning");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Chain Lightning", skill.skill);
    assertEquals(26, skill.srvdofunc);
    assertEquals("chainlightning", skill.srvmissilea);
    assertEquals("chainlightning", missile.Missile);
    assertEquals("Chain Lightning", missile.Skill);
    assertEquals(12, missile.pSrvHitFunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(40, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {11, 13, 15, 15, 15}, skill.EMaxLev);
    assertEquals("(skill('Charged Bolt'.blvl)+skill('Lightning'.blvl)"
        + "+skill('Nova'.blvl))*par8", skill.EDmgSymPerCalc);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(skill.EMin, skill.EMinLev,
          skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(skill.EMax, skill.EMaxLev,
          skill.HitShift, level);
      assertEquals(D2MOO_FIXED_MIN[level - 1], fixedMin, "fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], fixedMax, "fixed max level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1], fixedMin >> 8,
          "integer min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], fixedMax >> 8,
          "integer max level " + level);

      Missile projectile = new Missile();
      projectile.missile = missile;
      assertTrue(MissileDamageResolver.initializeSkill(
          projectile, skill, owner, level, name -> 0));
      assertEquals(D2MOO_INTEGER_MIN[level - 1], statInt(projectile, Stat.lightmindam),
          "riiablo lightning min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], statInt(projectile, Stat.lightmaxdam),
          "riiablo lightning max level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "riiablo displayed min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "riiablo displayed max level " + level);
    }
  }

  @Test
  void continuationSegmentKeepsTheAuthoritativeSkillSnapshot() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = createPlayer(world, -2, 0, combatAttributes(1000));
      createMonster(world, 1, 0, combatAttributes(10000));
      createMonster(world, 5, 0, combatAttributes(10000));
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.CHAIN_LIGHTNING);
      Missiles.Entry row = Riiablo.files.Missiles.get("chainlightning");
      int rootId = factory.createMissile(row, Vector2.X, Vector2.Zero, caster);
      Missile root = world.getMapper(Missile.class).get(rootId);
      assertTrue(MissileDamageResolver.initializeSkill(
          root, skill, world.getMapper(AttributesWrapper.class).get(caster).attrs,
          1, name -> 0));
      root.skillId = skill.Id;
      root.damageLevel = 1;
      root.chainHitsRemaining = 3;
      root.shareHitTargets(new IntSet());
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);

      world.process();

      assertTrue(factory.missileEntityIds.size() >= 2,
          "SrvHit12 must create at least one continuation segment");
      for (int i = 1; i < factory.missileEntityIds.size(); i++) {
        Missile child = world.getMapper(Missile.class).get(factory.missileEntityIds.get(i));
        assertEquals(skill.Id, child.skillId);
        assertEquals(1, child.damageLevel);
        assertTrue(child.damageSnapshot,
            "every Chain Lightning continuation must snapshot Skills.txt damage");
        assertEquals(1, statInt(child, Stat.lightmindam));
        assertEquals(40, statInt(child, Stat.lightmaxdam));
      }
    } finally {
      world.dispose();
    }
  }

  private static int d2mooElementalDamageFixed(
      int base, int[] perLevel, int hitShift, int level) {
    return (base + d2mooDamageBonusByLevel(level, perLevel)) << hitShift;
  }

  private static int d2mooDamageBonusByLevel(int level, int[] values) {
    if (level <= 1) return 0;
    if (level > 28) {
      return 7 * values[0] + values[4] * (level - 28)
          + 6 * (values[2] + values[3]) + 8 * values[1];
    }
    if (level > 22) {
      return 7 * values[0] + values[3] * (level - 22)
          + 6 * values[2] + 8 * values[1];
    }
    if (level > 16) {
      return 7 * values[0] + values[2] * (level - 16) + 8 * values[1];
    }
    if (level > 8) return 7 * values[0] + values[1] * (level - 8);
    return values[0] * (level - 1);
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static int createPlayer(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
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

  private static Attributes combatAttributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.tohit, 100000);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final java.util.ArrayList<Integer> missileEntityIds = new java.util.ArrayList<>();

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }

    @Override public int createMissile(int missileId, Vector2 direction, Vector2 position) {
      return createMissile(missileId, direction, position, Engine.INVALID_ENTITY);
    }

    @Override public int createMissile(
        int missileId, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(missileId);
      if (row == null) return Engine.INVALID_ENTITY;
      int id = world.create();
      world.getMapper(Missile.class).create(id).set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(id).position.set(position);
      world.getMapper(Velocity.class).create(id).velocity.set(direction).setLength(row.Vel);
      missileEntityIds.add(id);
      return id;
    }
  }
}
