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
import com.riiablo.engine.server.component.serializer.PlayerSerializer;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.combat.DefenseCalculator;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.google.flatbuffers.FlatBufferBuilder;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native-data regression coverage for Amazon specialist skill handlers. */
class AmazonSkillSpecializationTest extends RiiabloTest {
  @Test
  void elementalArrowsCaptureNativeSkillDamageAndFreezeSemantics() {
    Attributes owner = attributes(20, 200);
    owner.base().put(Stat.mindamage, 10);
    owner.base().put(Stat.maxdamage, 20);
    owner.base().put(Stat.tohit, 100);
    owner.reset();

    Skills.Entry fire = Riiablo.files.skills.get("Fire Arrow");
    Missile fireMissile = new Missile().set(Riiablo.files.Missiles.get("firearrow"),
        new Vector2(), 40).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkill(fireMissile, fire, owner, 10));
    assertTrue(fireMissile.damageSnapshot);
    assertTrue(fireMissile.usesAttackRating);
    assertTrue(fireMissile.damage.get(Stat.firemindam).asInt() >= 1);
    assertTrue(fireMissile.damage.get(Stat.mindamage).asInt() < 10,
        "native SrvDmg01 converts part of physical damage to fire");

    Skills.Entry cold = Riiablo.files.skills.get("Cold Arrow");
    Missile coldMissile = new Missile().set(Riiablo.files.Missiles.get("coldarrow"),
        new Vector2(), 20).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkill(coldMissile, cold, owner, 5));
    assertEquals(220, coldMissile.damage.get(Stat.coldlength).asInt());
    assertTrue(!coldMissile.freezesTarget);

    Skills.Entry exploding = Riiablo.files.skills.get("Exploding Arrow");
    Missile arrow = new Missile().set(Riiablo.files.Missiles.get("explodingarrow"),
        new Vector2(), 40).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkill(arrow, exploding, owner, 1));
    assertTrue(arrow.damage.get(Stat.firemaxdam) == null,
        "the travelling arrow carries weapon damage; its sub-missile carries the explosion");
    Missile explosion = new Missile().set(Riiablo.files.Missiles.get("explodingarrowexp2"),
        new Vector2(), 1).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkillArea(explosion, exploding, owner, 1));
    assertEquals(0, explosion.damage.get(Stat.maxdamage).asInt());
    assertEquals(6, explosion.damage.get(Stat.firemaxdam).asInt());

    Skills.Entry freezing = Riiablo.files.skills.get("Freezing Arrow");
    Missile freezeExplosion = new Missile().set(Riiablo.files.Missiles.get("freezingarrowexp3"),
        new Vector2(), 1).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkillArea(
        freezeExplosion, freezing, owner, 1));
    assertTrue(freezeExplosion.freezesTarget);
    assertEquals(50, freezeExplosion.damage.get(Stat.coldlength).asInt());
    assertEquals(50, freezeExplosion.damage.get(Stat.coldmaxdam).asInt());

    Skills.Entry ice = Riiablo.files.skills.get("Ice Arrow");
    Missile iceMissile = new Missile().set(Riiablo.files.Missiles.get("icearrow"),
        new Vector2(), 40).setOwner(1);
    assertTrue(MissileDamageResolver.initializeSkill(iceMissile, ice, owner, 1));
    assertTrue(iceMissile.freezesTarget);
  }

  @Test
  void explodingArrowCreatesAuthoritativeAreaSubMissile() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), collisions, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int amazon = world.create();
      world.getMapper(Player.class).create(amazon).data =
          CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      world.getMapper(Position.class).create(amazon).position.set(-2, 0);
      Attributes owner = attributes(20, 200);
      owner.base().put(Stat.mindamage, 10);
      owner.base().put(Stat.maxdamage, 10);
      owner.base().put(Stat.tohit, 100);
      owner.reset();
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = owner;

      int primary = monster(world, 1.2f, 0);
      int nearby = monster(world, 4f, 0);
      world.getMapper(AttributesWrapper.class).create(primary).attrs = attributes(1, 100);
      world.getMapper(AttributesWrapper.class).create(nearby).attrs = attributes(1, 100);

      Skills.Entry skill = Riiablo.files.skills.get("Exploding Arrow");
      Missiles.Entry row = Riiablo.files.Missiles.get("explodingarrow");
      int sourceId = factory.createMissile(row, new Vector2(1, 0), new Vector2(0, 0), amazon);
      MissileDamageResolver.initializeSkill(
          world.getMapper(Missile.class).get(sourceId), skill, owner, 1);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      for (int i = 0; i < 4; i++) world.process();

      assertTrue(factory.createdNames.stream().anyMatch(
          name -> "explodingarrowexp2".equalsIgnoreCase(name)));
      assertTrue(world.getMapper(AttributesWrapper.class).get(nearby).attrs
          .get(Stat.hitpoints).asFixed() < 100f,
          "SrvHit01 must apply the explosion snapshot to every hostile in radius");
    } finally {
      world.dispose();
    }
  }

  @Test
  void freezingArrowFreezesEveryHostileInsideNativeExplosionRadius() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), states, new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int amazon = world.create();
      world.getMapper(Player.class).create(amazon).data =
          CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      world.getMapper(Position.class).create(amazon).position.set(-2, 0);
      Attributes owner = attributes(20, 200);
      owner.base().put(Stat.mindamage, 10);
      owner.base().put(Stat.maxdamage, 10);
      owner.base().put(Stat.tohit, 100);
      owner.reset();
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = owner;
      int primary = monster(world, 1.2f, 0);
      int nearby = monster(world, 4f, 0);
      for (int target : new int[] {primary, nearby}) {
        world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1, 100);
        world.getMapper(UnitStates.class).create(target).init(target);
      }
      Skills.Entry skill = Riiablo.files.skills.get("Freezing Arrow");
      int sourceId = factory.createMissile(Riiablo.files.Missiles.get("freezingarrow"),
          new Vector2(1, 0), new Vector2(0, 0), amazon);
      MissileDamageResolver.initializeSkill(
          world.getMapper(Missile.class).get(sourceId), skill, owner, 1);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      for (int i = 0; i < 4; i++) world.process();
      assertTrue(world.getMapper(UnitStates.class).get(primary).stateList.hasState(StateId.FREEZE));
      assertTrue(world.getMapper(UnitStates.class).get(nearby).stateList.hasState(StateId.FREEZE));
      assertTrue(factory.createdNames.stream().anyMatch(
          name -> "freezingarrowexp3".equalsIgnoreCase(name)));
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void immolationArrowCreatesPersistentFireField() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), collisions, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int amazon = world.create();
      world.getMapper(Player.class).create(amazon).data =
          CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      world.getMapper(Position.class).create(amazon).position.set(-2, 0);
      Attributes owner = attributes(20, 200);
      owner.base().put(Stat.mindamage, 10);
      owner.base().put(Stat.maxdamage, 10);
      owner.base().put(Stat.tohit, 100);
      owner.reset();
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = owner;

      int target = monster(world, 1.2f, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1, 100);
      Skills.Entry skill = Riiablo.files.skills.get("Immolation Arrow");
      Missiles.Entry row = Riiablo.files.Missiles.get("immolationarrow");
      int sourceId = factory.createMissile(row, new Vector2(1, 0), new Vector2(0, 0), amazon);
      Missile source = world.getMapper(Missile.class).get(sourceId);
      MissileDamageResolver.initializeSkill(source, skill, owner, 1);
      assertEquals(skill.Id, source.skillId);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      for (int i = 0; i < 3; i++) world.process();

      Missile fire = null;
      for (Missile candidate : factory.created) {
        if (candidate.missile != null && "immolationfire".equalsIgnoreCase(candidate.missile.Missile)) {
          fire = candidate;
          break;
        }
      }
      assertTrue(fire != null, "SrvHit09 must create immolationfire fields");
      assertTrue(fire.persistent);
      assertTrue(fire.remainingFrames <= 100 && fire.remainingFrames >= 95);
      assertEquals(41, fire.tickInterval);
      float before = world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed();
      for (int i = 0; i < 42; i++) world.process();
      float after = world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed();
      assertTrue(after < before, "persistent fire field must tick damage at DamageRate");
    } finally {
      world.dispose();
    }
  }

  @Test
  void poisonJavelinCreatesPersistentPoisonCloud() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), states, collisions, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int amazon = world.create();
      world.getMapper(Player.class).create(amazon).data =
          CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      world.getMapper(Position.class).create(amazon).position.set(-2, 0);
      Attributes owner = attributes(20, 200);
      owner.base().put(Stat.mindamage, 10);
      owner.base().put(Stat.maxdamage, 10);
      owner.base().put(Stat.tohit, 100);
      owner.reset();
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = owner;
      Skills.Entry skill = Riiablo.files.skills.get("Poison Javelin");
      assertEquals("pois", skill.EType);
      assertTrue(skill.EMax > 0);
      Missiles.Entry row = Riiablo.files.Missiles.get("poisonjav");
      int sourceId = factory.createMissile(row, new Vector2(1, 0), new Vector2(0, 0), amazon);
      Missile source = world.getMapper(Missile.class).get(sourceId);
      MissileDamageResolver.initializeSkill(source, skill, owner, 1);
      assertEquals(skill.Id, source.skillId);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();
      assertTrue(factory.createdNames.stream().anyMatch(
          name -> "poisonjavcloud".equalsIgnoreCase(name)));
      Missile cloud = null;
      for (Missile candidate : factory.created) {
        if (candidate.missile != null && "poisonjavcloud".equalsIgnoreCase(candidate.missile.Missile)) {
          cloud = candidate;
          break;
        }
      }
      assertNotNull(cloud);
      assertTrue(cloud.persistent);
      assertEquals(60, cloud.remainingFrames);
      assertEquals(10, cloud.tickInterval);
      assertEquals(skill.Id, cloud.skillId);
      assertTrue(cloud.damageSnapshot);
      assertTrue(cloud.damage.get(Stat.poisonmaxdam).asInt() > 0);
      assertTrue(cloud.damage.get(Stat.poisonlength).asInt() > 0);

      int cloudId = factory.createdIds.get(factory.created.indexOf(cloud));
      int target = monster(world, 0, 0);
      world.getMapper(Position.class).get(target).position.set(
          world.getMapper(Position.class).get(cloudId).position);
      Attributes targetAttrs = attributes(1, 100);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;
      world.getMapper(UnitStates.class).create(target).init(target);
      CombatSystem.CombatResult poisonPreview = CombatSystem.INSTANCE.calculateAttack(
          cloud.damage, targetAttrs, true, false, true, 0, 0, 0, true,
          null, null, 0, 0, null, null, false);
      assertEquals(1, poisonPreview.elementalDamage[CombatSystem.DAMAGE_POISON]);
      assertEquals(200, poisonPreview.poisonDuration);
      world.delete(sourceId);
      for (int i = 0; i < cloud.tickInterval * 4
          && !world.getMapper(UnitStates.class).get(target).stateList.hasState(StateId.POISON); i++) {
        world.process();
      }
      assertTrue(world.getMapper(UnitStates.class).get(target).stateList.hasState(StateId.POISON),
          "Poison Javelin cloud must apply the shared poison-area state; tick=" + cloud.tickFrames
              + " hits=" + cloud.hitTargets.size + " cloudPos="
              + world.getMapper(Position.class).get(cloudId).position
              + " targetPos=" + world.getMapper(Position.class).get(target).position
              + " rowToHit=" + cloud.missile.ToHit + " usesAR=" + cloud.usesAttackRating
              + " ar=" + cloud.damage.get(Stat.tohit).asInt()
              + " poison=" + cloud.damage.get(Stat.poisonmaxdam).asInt()
              + " length=" + cloud.damage.get(Stat.poisonlength).asInt());
      float poisonedHp = world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed();
      world.process();
      assertTrue(world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed() < poisonedHp,
          "the shared poison state must deal damage on the following server tick");

      Skills.Entry plague = Riiablo.files.skills.get("Plague Javelin");
      Missiles.Entry plagueMissile = Riiablo.files.Missiles.get(plague.srvmissile);
      assertEquals(2, plagueMissile.pSrvHitFunc);
      assertEquals("plaguejavcloud", plagueMissile.HitSubMissile[0]);
      assertNotNull(Riiablo.files.Missiles.get(plagueMissile.HitSubMissile[0]));
      Missiles.Entry trapPoison = Riiablo.files.Missiles.get("trap poison ball left");
      assertEquals(2, trapPoison.pSrvDoFunc);
      assertEquals("plaguejavcloud", trapPoison.CltSubMissile[0]);
      int trapStart = factory.createdNames.size();
      factory.createMissile(
          trapPoison, new Vector2(1, 0), new Vector2(20, 20), amazon);
      world.process();
      assertTrue(factory.createdNames.subList(trapStart + 1, factory.createdNames.size()).stream()
          .anyMatch(name -> "plaguejavcloud".equalsIgnoreCase(name)),
          "poison trap must use the same authoritative persistent cloud pipeline");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void corpsePoisonCloudUsesSharedPersistentAreaLifecycle() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), states, new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int ownerId = world.create();
      world.getMapper(Monster.class).create(ownerId);
      world.getMapper(Position.class).create(ownerId).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(ownerId).attrs = attributes(10, 100);
      int targetId = world.create();
      world.getMapper(Player.class).create(targetId).data =
          CharData.createRemote("target", (byte) Riiablo.AMAZON);
      world.getMapper(Position.class).create(targetId).position.set(2, 2);
      world.getMapper(AttributesWrapper.class).create(targetId).attrs = attributes(1, 100);
      world.getMapper(UnitStates.class).create(targetId).init(targetId);
      int cloudId = factory.createMissile(Riiablo.files.Missiles.get("corpsepoisoncloud"),
          new Vector2(1, 0), new Vector2(2, 2), ownerId);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();
      Missile cloud = world.getMapper(Missile.class).get(cloudId);
      assertTrue(cloud.persistent);
      assertTrue(cloud.damageSnapshot);
      assertEquals(cloud.missile.Range - 1, cloud.remainingFrames,
          "the creation tick is the first native lifetime frame");
      assertEquals(175, cloud.damage.get(Stat.poisonlength).asInt());
      for (int i = 1; i < cloud.tickInterval * 4
          && !world.getMapper(UnitStates.class).get(targetId).stateList.hasState(StateId.POISON); i++) {
        world.process();
      }
      assertTrue(world.getMapper(UnitStates.class).get(targetId).stateList.hasState(StateId.POISON),
          "corpse poison cloud must apply the same persistent area state as javelins and traps");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void auditNativeAmazonSpecialRows() {
    String[] names = {"Magic Arrow", "Fire Arrow", "Cold Arrow", "Multiple Shot",
        "Exploding Arrow", "Ice Arrow", "Guided Arrow", "Strafe", "Immolation Arrow",
        "Freezing Arrow", "Pierce", "Charged Strike", "Cloak of Shadows",
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
          + " noammo=" + skill.noammo + " decquant=" + skill.decquant
          + " hitShift=" + skill.HitShift + " srcDam=" + skill.SrcDam
          + " min=" + skill.MinDam + " max=" + skill.MaxDam
          + " minLev=" + java.util.Arrays.toString(skill.MinLevDam)
          + " maxLev=" + java.util.Arrays.toString(skill.MaxLevDam)
          + " eType=" + skill.EType + " eMin=" + skill.EMin + " eMax=" + skill.EMax
          + " eMinLev=" + java.util.Arrays.toString(skill.EMinLev)
          + " eMaxLev=" + java.util.Arrays.toString(skill.EMaxLev)
          + " eLen=" + skill.ELen + " eLevLen=" + java.util.Arrays.toString(skill.ELevLen)
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
            + " hitSub=" + java.util.Arrays.toString(missile.HitSubMissile)
            + " srvDmg=" + missile.pSrvDmgFunc + " min=" + missile.MinDamage
            + " max=" + missile.MaxDamage + " minLev=" + java.util.Arrays.toString(missile.MinLevDam)
            + " maxLev=" + java.util.Arrays.toString(missile.MaxLevDam)
            + " eType=" + missile.EType + " eMin=" + missile.EMin + " eMax=" + missile.Emax
            + " eLen=" + missile.ELen + " srcDam=" + missile.SrcDamage
            + " hitFlags=" + missile.HitFlags + " resultFlags=" + missile.ResultFlags);
        for (String hitSub : missile.HitSubMissile) {
          if (hitSub == null || hitSub.isEmpty()) continue;
          Missiles.Entry sub = Riiablo.files.Missiles.get(hitSub);
          assertNotNull(sub, name + ":" + hitSub);
          System.out.println("[AMAZON_HIT_SUB] skill=" + name + " missile=" + hitSub
              + " srvDo=" + sub.pSrvDoFunc + " srvHit=" + sub.pSrvHitFunc
              + " srvDmg=" + sub.pSrvDmgFunc + " vel=" + sub.Vel + " range=" + sub.Range
              + " param=" + java.util.Arrays.toString(sub.Param)
              + " hitPar=" + java.util.Arrays.toString(sub.sHitPar)
              + " dmgPar=" + java.util.Arrays.toString(sub.dParam));
        }
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
      assertTrue(!guided.usesAttackRating, "Guided Arrow keeps its native always-hit behavior");

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
  void cloakOfShadowsAppliesNativeDimVisionToHostiles() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int assassin = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry cloak = Riiablo.files.skills.get("Cloak of Shadows");
      assertNotNull(cloak);
      data.setSkillLevel(cloak.Id, 1);
      world.getMapper(Player.class).create(assassin).data = data;
      world.getMapper(Position.class).create(assassin).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(assassin).attrs = attributes(20, 200);
      int target = monster(world, 4, 0);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, cloak.Id, Engine.INVALID_ENTITY, new Vector2(4, 0), cloak.srvdofunc, 0));

      UnitStates states = world.getMapper(UnitStates.class).get(target);
      assertNotNull(states);
      assertTrue(states.stateList.hasState(StateId.DIMVISION));
      UnitState dimVision = states.stateList.getState(StateId.DIMVISION);
      assertEquals(1, dimVision.level);
      assertEquals(-AssassinSkills.calculateCloakOfShadowsDefenseReduce(1),
          dimVision.defenseModifier);
      assertEquals(assassin, dimVision.sourceEntityId);
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

  @Test
  void bowUsesMatchingArrowQuiverAndConsumesOnePerCast() {
    CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
    data.getItems().unequipItem(com.riiablo.item.BodyLoc.RARM);
    data.getItems().unequipItem(com.riiablo.item.BodyLoc.LARM);
    Item bow = new Item();
    bow.reset();
    bow.setBase(Riiablo.files.weapons.get("sbw"));
    data.getItems().equipItem(com.riiablo.item.BodyLoc.RARM, data.getItems().add(bow));
    Item arrows = new Item();
    arrows.reset();
    arrows.setBase(Riiablo.files.misc.get("aqv"));
    arrows.id = 9001;
    arrows.attrs.base().put(Stat.quantity, 2);
    arrows.attrs.reset();
    data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM, data.getItems().add(arrows));

    assertEquals(arrows, data.getItems().getEquippedAmmo(bow));
    assertTrue(ServerSkillSystem.consumeRangedAmmo(data.getItems(), bow));
    assertEquals(1, arrows.attrs.base().get(Stat.quantity).asInt());
    assertTrue(ServerSkillSystem.consumeRangedAmmo(data.getItems(), bow));
    assertEquals(0, arrows.attrs.base().get(Stat.quantity).asInt());
    assertTrue(!ServerSkillSystem.consumeRangedAmmo(data.getItems(), bow));
  }

  @Test
  void bowRejectsCrossbowBolts() {
    CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
    data.getItems().unequipItem(com.riiablo.item.BodyLoc.RARM);
    data.getItems().unequipItem(com.riiablo.item.BodyLoc.LARM);
    Item bow = new Item();
    bow.reset();
    bow.setBase(Riiablo.files.weapons.get("sbw"));
    data.getItems().equipItem(com.riiablo.item.BodyLoc.RARM, data.getItems().add(bow));
    Item bolts = new Item();
    bolts.reset();
    bolts.setBase(Riiablo.files.misc.get("cqv"));
    bolts.attrs.base().put(Stat.quantity, 50);
    bolts.attrs.reset();
    data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM, data.getItems().add(bolts));

    assertTrue(data.getItems().getEquippedAmmo(bow) == null);
    assertTrue(!ServerSkillSystem.consumeRangedAmmo(data.getItems(), bow));
  }

  @Test
  void authoritativeArrowAndStrafeConsumeOneAmmoAndStopAtZero() {
    RecordingMissileFactory factory = new RecordingMissileFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      Skills.Entry fireArrow = Riiablo.files.skills.get("Fire Arrow");
      Skills.Entry strafe = Riiablo.files.skills.get("Strafe");
      data.setSkillLevel(fireArrow.Id, 1);
      data.setSkillLevel(strafe.Id, 1);
      Item bow = new Item();
      bow.reset();
      bow.setBase(Riiablo.files.weapons.get("sbw"));
      data.getItems().equipItem(com.riiablo.item.BodyLoc.RARM, data.getItems().add(bow));
      Item arrows = new Item();
      arrows.reset();
      arrows.setBase(Riiablo.files.misc.get("aqv"));
      arrows.attrs.base().put(Stat.quantity, 2);
      arrows.attrs.reset();
      data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM, data.getItems().add(arrows));

      int amazon = world.create();
      world.getMapper(Player.class).create(amazon).data = data;
      world.getMapper(Position.class).create(amazon).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = attributes(20, 200);
      int target = monster(world, 4, 0);
      monster(world, 5, 1);
      monster(world, 6, -1);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, fireArrow.Id, target, null, fireArrow.srvdofunc, fireArrow.cltdofunc));
      assertEquals(1, arrows.attrs.base().get(Stat.quantity).asInt());
      assertEquals(1, factory.created.size());

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, strafe.Id, target, null, strafe.srvdofunc, strafe.cltdofunc));
      assertEquals(0, arrows.attrs.base().get(Stat.quantity).asInt());
      int createdAtEmpty = factory.created.size();
      assertTrue(createdAtEmpty > 1, "one Strafe sequence should create several missiles");

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          amazon, fireArrow.Id, target, null, fireArrow.srvdofunc, fireArrow.cltdofunc));
      assertEquals(createdAtEmpty, factory.created.size(), "empty quiver must not create a missile");
    } finally {
      world.dispose();
    }
  }

  @Test
  void playerSnapshotCarriesAuthoritativeAmmoQuantity() {
    CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
    Item bow = new Item();
    bow.reset();
    bow.setBase(Riiablo.files.weapons.get("sbw"));
    data.getItems().equipItem(com.riiablo.item.BodyLoc.RARM, data.getItems().add(bow));
    Item arrows = new Item();
    arrows.reset();
    arrows.setBase(Riiablo.files.misc.get("aqv"));
    arrows.id = 4242;
    arrows.attrs.base().put(Stat.quantity, 17);
    arrows.attrs.reset();
    data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM, data.getItems().add(arrows));

    Player player = new Player();
    player.data = data;
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int root = new PlayerSerializer().putData(builder, player);
    builder.finish(root);
    PlayerP snapshot = PlayerP.getRootAsPlayerP(builder.dataBuffer());
    assertTrue(snapshot.ammoPresent());
    assertEquals(4242, snapshot.ammoItemId());
    assertEquals(17, snapshot.ammoQuantity());
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
    final java.util.ArrayList<Integer> createdIds = new java.util.ArrayList<>();
    final java.util.ArrayList<String> createdNames = new java.util.ArrayList<>();

    @Override public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(angle).setLength(row.Vel);
      created.add(missile);
      createdIds.add(entity);
      createdNames.add(row.Missile);
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
