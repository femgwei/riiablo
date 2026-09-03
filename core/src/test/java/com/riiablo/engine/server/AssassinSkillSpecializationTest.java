package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.riiablo.engine.server.component.Corpse;
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
            + " missS3=" + summon.MissS3 + " missS4=" + summon.MissS4
            + " ai1=" + java.util.Arrays.toString(summon.aip1)
            + " ai2=" + java.util.Arrays.toString(summon.aip2)
            + " ai3=" + java.util.Arrays.toString(summon.aip3)
            + " ai4=" + java.util.Arrays.toString(summon.aip4));
        Skills.Entry attack = summon.Skill1 == null || summon.Skill1.isEmpty()
            ? null : Riiablo.files.skills.get(summon.Skill1);
        if (attack != null) {
          System.out.println("[ASSASSIN_SUMMON_SKILL] monster=" + summon.Id + " skill="
              + attack.skill + " srvDo=" + attack.srvdofunc + " srvA=" + attack.srvmissilea
              + " srvB=" + attack.srvmissileb + " cltA=" + attack.cltmissilea
              + " calc1=" + attack.calc1 + " calc2=" + attack.calc2
              + " calc3=" + attack.calc3 + " calc4=" + attack.calc4
              + " auraRange=" + attack.aurarangecalc + " eType=" + attack.EType + " params="
              + java.util.Arrays.toString(attack.Param));
          Missiles.Entry serverMissile = attack.srvmissilea == null
              || attack.srvmissilea.isEmpty() ? null
              : Riiablo.files.Missiles.get(attack.srvmissilea);
          if (serverMissile != null) {
            System.out.println("[ASSASSIN_SUMMON_MISSILE] skill=" + attack.skill
                + " missile=" + serverMissile.Missile
                + " srvDo=" + serverMissile.pSrvDoFunc
                + " srvHit=" + serverMissile.pSrvHitFunc
                + " velocity=" + serverMissile.Vel + " range=" + serverMissile.Range
                + " params=" + java.util.Arrays.toString(serverMissile.Param)
                + " sub=" + java.util.Arrays.toString(serverMissile.SubMissile));
            String subName = serverMissile.SubMissile != null
                && serverMissile.SubMissile.length > 0 ? serverMissile.SubMissile[0] : null;
            Missiles.Entry subMissile = subName == null || subName.isEmpty()
                ? null : Riiablo.files.Missiles.get(subName);
            if (subMissile != null) {
              System.out.println("[ASSASSIN_SUMMON_SUBMISSILE] parent=" + serverMissile.Missile
                  + " missile=" + subMissile.Missile
                  + " srvDo=" + subMissile.pSrvDoFunc
                  + " srvHit=" + subMissile.pSrvHitFunc
                  + " velocity=" + subMissile.Vel + " range=" + subMissile.Range
                  + " size=" + subMissile.Size + " damageRate=" + subMissile.DamageRate
                  + " nextHit=" + subMissile.NextHit
                  + " nextDelay=" + subMissile.NextDelay);
            }
          }
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
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
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
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry blade = Riiablo.files.skills.get("Blade Sentinel");
      assertNotNull(blade);
      data.setSkillLevel(blade.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      Attributes ownerAttrs = attributes(100);
      ownerAttrs.base().put(Stat.mindamage, 10);
      ownerAttrs.base().put(Stat.maxdamage, 20);
      ownerAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = ownerAttrs;
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
      Missile bladeMissile = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(bladeMissile);
      assertTrue(bladeMissile.attached);
      assertEquals(factory.entityId, bladeMissile.attachedEntityId);
      assertEquals(owner, bladeMissile.ownerId,
          "the player remains the authoritative damage owner");
      assertEquals(blade.Id, bladeMissile.skillId);
      assertTrue(bladeMissile.damageSnapshot,
          "Blade Sentinel must inherit the caster's weapon damage snapshot");
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Blade Sentinel controller survives until its native duration expires");

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(5, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);

      boolean reachedTarget = false;
      boolean startedReturn = false;
      for (int i = 0; i < 80; i++) {
        world.process();
        float controllerX = world.getMapper(Position.class).get(factory.entityId).position.x;
        float missileX = world.getMapper(Position.class).get(factory.missileEntityId).position.x;
        assertEquals(controllerX, missileX, 0.0001f,
            "SrvDo20 keeps the blade missile attached to its controller");
        if (controllerX >= 7.99f) reachedTarget = true;
        if (reachedTarget && controllerX < 7f) startedReturn = true;
      }
      assertTrue(reachedTarget, "Blade Creeper must reach the selected endpoint");
      assertTrue(startedReturn, "Blade Creeper must switch back toward its cast origin");
      assertEquals(1, factory.missiles, "Blade Creeper creates one attached missile only");
      assertTrue(bladeMissile.nextHitFrame.containsKey(target),
          "the attached blade applies native per-target NextHit suppression");
      assertTrue(world.getEntityManager().isActive(factory.missileEntityId),
          "SrvHit37 unit collisions must not destroy Blade Creeper");

      world.delete(factory.entityId);
      world.process();
      world.process();
      assertFalse(world.getEntityManager().isActive(factory.missileEntityId),
          "SrvDo20 removes the blade when its controller disappears");
    } finally {
      world.dispose();
    }
  }

  @Test
  void wakeOfFireSrvDo125CreatesMakerThenOppositeFireWaves() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry wake = Riiablo.files.skills.get("Wake of Fire Sentry");
      assertNotNull(wake);
      data.setSkillLevel(wake.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, wake.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), wake.srvdofunc, 0));
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      world.getMapper(SummonedPet.class).get(factory.entityId).maxShots = 1;

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      // Wake traps are deployed at the clicked point (8,3); keep the hostile
      // unit a few tiles away so the maker has a real travel segment.
      world.getMapper(Position.class).create(target).position.set(14, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      for (int i = 0; i < 30; i++) world.process();

      assertEquals(3, factory.missileNames.size(),
          "SrvDo125 creates one maker and two SrvDo31 wave missiles");
      assertEquals("wake of destruction maker", factory.missileNames.get(0));
      assertEquals("wake of destruction", factory.missileNames.get(1));
      assertEquals("wake of destruction", factory.missileNames.get(2));
      assertEquals(-factory.missileDirections.get(1).x, factory.missileDirections.get(2).x, 0.0001f);
      assertEquals(-factory.missileDirections.get(1).y, factory.missileDirections.get(2).y, 0.0001f);
      assertFalse(world.getEntityManager().isActive(factory.missileEntityIds.get(0)),
          "the maker is consumed after spawning its waves");
      assertTrue(factory.missileEntityIds.get(1) != factory.missileEntityIds.get(2),
          "the two wave missiles are distinct authoritative entities");
    } finally {
      world.dispose();
    }
  }

  @Test
  void infernoSentrySrvDo095RepeatsMissilesAndTracksTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry inferno = Riiablo.files.skills.get("Inferno Sentry");
      Skills.Entry wake = Riiablo.files.skills.get("Wake of Fire Sentry");
      assertNotNull(inferno);
      assertNotNull(wake);
      data.setSkillLevel(inferno.Id, 4);
      data.setSkillLevel(wake.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, inferno.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), inferno.srvdofunc, 0));
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1, factory.missiles);
      assertEquals("inferno sentry 1", factory.missileName);
      Missile channel = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(channel);
      assertFalse(channel.persistent,
          "SrvDo95 emits separate missiles instead of one synthetic persistent area");
      assertEquals(23f, channel.range, 0.0001f,
          "calc1 = ln34/2 + Wake of Fire synergy controls each missile path");
      assertTrue(trap.infernoChanneling);
      assertEquals(18, trap.infernoRemainingFrames,
          "calc2 = par1 + Wake of Fire synergy controls the repeat window");
      assertEquals(3, trap.infernoPulseFrames,
          "calc3 controls the native inferno repeat cadence");

      world.getMapper(Position.class).get(target).position.set(12, 8);
      for (int i = 0; i < 3; i++) world.process();
      assertEquals(2, factory.missiles,
          "SrvDo95 creates another missile on the calc3 animation event");
      assertTrue(factory.missileDirections.get(1).y > 0f,
          "each inferno pulse updates its direction toward the moving target");
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathSentrySrvDo055ConsumesOneCorpseAndDamagesNearbyEnemies() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      assertEquals(7, trap.maxShots,
          "calc4 includes one shot per three base Fire Blast levels");
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      int corpseId = world.create();
      Monster corpseMonster = world.getMapper(Monster.class).create(corpseId);
      corpseMonster.monstats = fallen;
      corpseMonster.monstats2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      assertNotNull(corpseMonster.monstats2);
      assertTrue(corpseMonster.monstats2.corpseSel);
      world.getMapper(Position.class).create(corpseId).position.set(11, 3);
      Attributes corpseAttrs = attributes(100);
      corpseAttrs.get(Stat.hitpoints).set(0);
      world.getMapper(AttributesWrapper.class).create(corpseId).attrs = corpseAttrs;
      Corpse corpse = world.getMapper(Corpse.class).create(corpseId).reset(
          Corpse.DEFAULT_DURATION, true);
      world.getMapper(UnitStates.class).create(corpseId).init(corpseId);

      int target = world.create();
      world.getMapper(Monster.class).create(target).monstats = fallen;
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1000);
      int outerTarget = world.create();
      world.getMapper(Monster.class).create(outerTarget).monstats = fallen;
      world.getMapper(Position.class).create(outerTarget).position.set(18.2f, 3);
      world.getMapper(AttributesWrapper.class).create(outerTarget).attrs = attributes(1000);

      world.setDelta(1f / 25f);
      world.process();

      assertFalse(corpse.usable, "SrvDo55 reserves and consumes the selected corpse");
      assertTrue(world.getMapper(UnitStates.class).get(corpseId).stateList
          .hasState(StateId.CORPSE_NODRAW));
      assertEquals(corpseId, trap.deathLastCorpseId);
      assertEquals(1, trap.shotsFired, "one corpse explosion consumes one sentry shot");
      float innerHp = world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed();
      assertTrue(innerHp >= 920f && innerHp <= 960f,
          "the inner radius receives the native 40%-80% corpse-life roll");
      float outerHp = world.getMapper(AttributesWrapper.class).get(outerTarget).attrs
          .get(Stat.hitpoints).asFixed();
      assertTrue(outerHp >= 960f && outerHp <= 980f,
          "the outer half-tile ring retains the native elemental portion");
      assertEquals(1, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"),
          "the consumed corpse creates one synchronized explosion visual");

      for (int i = 0; i < 5; i++) {
        trap.attackCooldownFrames = 0;
        world.process();
      }
      assertEquals(1, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"),
          "an already hidden corpse cannot be selected or exploded again");

      Skills.Entry fallback = AssassinTrapSystem.resolveAttackSkill(
          world.getMapper(Monster.class).get(factory.entityId), deathSentry);
      assertNotNull(fallback);
      assertEquals("death sentry ltng", fallback.skill,
          "without a legal corpse Fn104 falls back to Skill2 lightning");
      assertTrue(hasText(fallback.srvmissile) || hasText(fallback.srvmissilea)
              || hasText(fallback.cltmissile) || hasText(fallback.cltmissilea),
          "the fallback row must provide an authoritative lightning missile");
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
    int missileEntityId = Engine.INVALID_ENTITY;
    String missileName;
    String attackSkill;
    final java.util.ArrayList<String> missileNames = new java.util.ArrayList<>();
    final java.util.ArrayList<Vector2> missileDirections = new java.util.ArrayList<>();
    final java.util.ArrayList<Integer> missileEntityIds = new java.util.ArrayList<>();

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
      missileEntityId = missileId;
      world.getMapper(Missile.class).create(missileId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(missileId).position.set(position);
      world.getMapper(Velocity.class).create(missileId).velocity.set(angle).setLength(row.Vel);
      missiles++;
      missileName = row.Missile;
      missileNames.add(row.Missile);
      missileDirections.add(new Vector2(angle));
      missileEntityIds.add(missileId);
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

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
