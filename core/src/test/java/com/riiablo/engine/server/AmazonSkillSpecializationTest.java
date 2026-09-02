package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.badlogic.gdx.math.Vector2;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.combat.DefenseCalculator;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native-data regression coverage for Amazon specialist skill handlers. */
class AmazonSkillSpecializationTest extends RiiabloTest {
  @Test
  void auditNativeAmazonSpecialRows() {
    String[] names = {"Magic Arrow", "Fire Arrow", "Cold Arrow", "Multiple Shot",
        "Exploding Arrow", "Ice Arrow", "Guided Arrow", "Strafe", "Immolation Arrow",
        "Freezing Arrow", "Pierce", "Charged Strike",
        "Dopplezon", "Valkyrie", "Lightning Strike", "Lightning Fury"};
    for (String name : names) {
      Skills.Entry skill = Riiablo.files.skills.get(name);
      assertNotNull(skill, name);
      System.out.println("[AMAZON_SKILL] name=" + name + " id=" + skill.Id
          + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc + " calc1=" + skill.calc1
          + " calc2=" + skill.calc2 + " srv=" + skill.srvmissile
          + " srvA=" + skill.srvmissilea + " srvB=" + skill.srvmissileb
          + " cltDo=" + skill.cltdofunc + " clt=" + skill.cltmissile
          + " cltA=" + skill.cltmissilea + " cltB=" + skill.cltmissileb
          + " auraRange=" + skill.aurarangecalc + " summon=" + skill.summon
          + " pettype=" + skill.pettype + " petmax=" + skill.petmax
          + " params=" + java.util.Arrays.toString(skill.Param));
      String[] missiles = {skill.srvmissile, skill.srvmissilea, skill.srvmissileb};
      for (String missileName : missiles) {
        if (missileName == null || missileName.isEmpty()) continue;
        Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
        assertNotNull(missile, name + ":" + missileName);
        System.out.println("[AMAZON_MISSILE] skill=" + name + " missile=" + missileName
            + " srvDo=" + missile.pSrvDoFunc + " srvHit=" + missile.pSrvHitFunc
            + " sHitPar=" + java.util.Arrays.toString(missile.sHitPar)
            + " sub=" + java.util.Arrays.toString(missile.SubMissile)
            + " hitSub=" + java.util.Arrays.toString(missile.HitSubMissile));
      }
    }
  }

  @Test
  void chargedStrikeUsesNativeBoltCountAndNormalizedSpread() {
    Skills.Entry skill = Riiablo.files.skills.get("Charged Strike");
    assertNotNull(skill);
    assertEquals(3, ServerSkillSystem.chargedStrikeBoltCount(skill, 1));
    assertEquals(4, ServerSkillSystem.chargedStrikeBoltCount(skill, 5));
    assertEquals(5, ServerSkillSystem.chargedStrikeBoltCount(skill, 10));

    Vector2 base = new Vector2(1, 0);
    Vector2 first = ServerSkillSystem.chargedStrikeDirection(base, 0, 3, new Vector2());
    Vector2 centre = ServerSkillSystem.chargedStrikeDirection(base, 1, 3, new Vector2());
    Vector2 last = ServerSkillSystem.chargedStrikeDirection(base, 2, 3, new Vector2());
    assertEquals(1f, first.len(), 0.0001f);
    assertEquals(1f, centre.len(), 0.0001f);
    assertEquals(1f, last.len(), 0.0001f);
    assertEquals(first.y, -last.y, 0.0001f);
  }

  @Test
  void lightningStrikeUsesNativeRangeAndJumpCalc() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Strike");
    assertNotNull(skill);
    assertEquals(20f, ServerSkillSystem.lightningStrikeRange(skill, 1), 0.0001f);
    assertEquals(3, ServerSkillSystem.lightningStrikeJumpCount(skill, 1));
    assertEquals(12, ServerSkillSystem.lightningStrikeJumpCount(skill, 10));
  }

  @Test
  void lightningFuryUsesNativeHitFunctionRangeAndBoltCount() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Fury");
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissile);
    assertNotNull(missile);
    assertEquals(20, missile.pSrvHitFunc);
    assertEquals("furylightning", missile.HitSubMissile[0]);
    assertEquals(15, MissileCollisionSystem.lightningFuryRange(missile, skill, 1));
    assertEquals(3, MissileCollisionSystem.lightningFuryBoltCount(missile, skill, 1));
    assertEquals(12, MissileCollisionSystem.lightningFuryBoltCount(missile, skill, 10));
  }

  @Test
  void guidedArrowCapturesTargetAndStrafeSelectsUniqueTargets() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int amazon = world.create();
      CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      data.setSkillLevel(SkillId.GUIDED_ARROW, 5);
      data.setSkillLevel(SkillId.STRAFE, 5);
      data.setSkillLevel(SkillId.PIERCE, 1);
      world.getMapper(Player.class).create(amazon).data = data;
      world.getMapper(Position.class).create(amazon).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = attributes(20, 200);
      int target = monster(world, 3, 0);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, SkillId.GUIDED_ARROW, target, null, 10, 0));
      assertEquals(1, factory.created.size());
      Missile guided = factory.created.get(0);
      assertEquals(target, guided.targetId);
      assertTrue(guided.homing);
      assertTrue(guided.pierceEnabled);
      assertTrue(guided.damageMultiplier > 1f);

      factory.created.clear();
      for (int i = 4; i <= 10; i++) monster(world, i, 0);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, SkillId.STRAFE, Engine.INVALID_ENTITY, new Vector2(6, 0), 12, 0));
      assertTrue(factory.created.size() >= 3 && factory.created.size() <= 8);
      java.util.HashSet<Integer> strafeTargets = new java.util.HashSet<>();
      for (Missile arrow : factory.created) {
        assertTrue(!arrow.homing);
        if (arrow.targetId >= 0) assertTrue(strafeTargets.add(arrow.targetId));
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void passiveDodgeAvoidEvadeUseNativeAttackContext() {
    DefenseCalculator defense = DefenseCalculator.INSTANCE;
    assertEquals(DefenseCalculator.DEFENSE_DODGE,
        defense.checkPassiveDefense(DefenseCalculator.ATTACK_MELEE, false, 100, 0, 0, 0));
    assertEquals(DefenseCalculator.DEFENSE_AVOID,
        defense.checkPassiveDefense(DefenseCalculator.ATTACK_RANGED, false, 0, 100, 0, 0));
    assertEquals(DefenseCalculator.DEFENSE_EVADE,
        defense.checkPassiveDefense(DefenseCalculator.ATTACK_MELEE, true, 0, 0, 100, 0));

    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.alwaysHit = true;
    attacker.level = 1;
    attacker.minDamage = attacker.maxDamage = 10;
    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 1;
    defender.currentLife = defender.maxLife = 100;
    defender.attackType = DefenseCalculator.ATTACK_RANGED;
    defender.isMoving = true;
    defender.passiveEvade = 100;
    CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(attacker, defender);
    assertTrue(result.hit);
    assertTrue(result.blocked);
    assertEquals(0, result.totalDamage);
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    return id;
  }

  @Test
  void decoyAndValkyrieUseNativeSummonRowsStatsAndStates() {
    RecordingSummonFactory factory = new RecordingSummonFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int amazon = world.create();
      CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      Skills.Entry decoy = Riiablo.files.skills.get("Dopplezon");
      Skills.Entry valkyrie = Riiablo.files.skills.get("Valkyrie");
      data.setSkillLevel(decoy.Id, 5);
      data.setSkillLevel(valkyrie.Id, 7);
      world.getMapper(Player.class).create(amazon).data = data;
      world.getMapper(Position.class).create(amazon).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = attributes(20, 200);
      world.getMapper(UnitStates.class).create(amazon).init(amazon);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, decoy.Id, Engine.INVALID_ENTITY, new Vector2(10, 11), 15, 0));
      assertEquals("dopplezon", factory.summon.Id);
      assertEquals("dopplezon", factory.petType);
      assertTrue(factory.passive);
      assertEquals(1, factory.petMax);
      assertEquals(SkillFormula.evaluate(decoy.calc2, decoy, 5), factory.durationFrames);
      int hpPercent = SkillFormula.evaluate(decoy.calc3, decoy, 5);
      assertEquals(200f * hpPercent / 100f,
          world.getMapper(AttributesWrapper.class).get(factory.lastEntity)
              .attrs.get(Stat.maxhp).asFixed(), 0.001f);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, valkyrie.Id, Engine.INVALID_ENTITY, new Vector2(12, 13), 16, 0));
      assertEquals("valkyrie", factory.summon.Id);
      assertEquals("valkyrie", factory.petType);
      assertTrue(!factory.passive);
      assertEquals(0, factory.durationFrames);
      assertTrue(world.getMapper(UnitStates.class).get(factory.lastEntity)
          .stateList.hasState(StateId.VALKYRIE));
      assertEquals(ServerSkillSystem.summonBaseLevel(20, 7),
          world.getMapper(AttributesWrapper.class).get(factory.lastEntity)
              .attrs.get(Stat.level).asInt());
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeSummonBaseLevelIsCappedByOwnerLevel() {
    assertEquals(8, ServerSkillSystem.summonBaseLevel(10, 1));
    assertEquals(10, ServerSkillSystem.summonBaseLevel(10, 5));
    assertEquals(1, ServerSkillSystem.summonBaseLevel(1, 1));
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingSummonFactory extends EntityFactory {
    com.riiablo.codec.excel.MonStats.Entry summon;
    String petType;
    int petMax;
    boolean passive;
    int durationFrames;
    int lastEntity = Engine.INVALID_ENTITY;

    @Override
    public int createSummonedPet(int ownerId, com.riiablo.codec.excel.MonStats.Entry summon,
        String petType, int skillId, int skillLevel, int petMax, boolean passive,
        int durationFrames, float x, float y) {
      this.summon = summon;
      this.petType = petType;
      this.petMax = petMax;
      this.passive = passive;
      this.durationFrames = durationFrames;
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 10);
      world.getMapper(UnitStates.class).create(id).init(id);
      return lastEntity = id;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
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
      return createMissile(id, angle, position, -1);
    }
  }
}
