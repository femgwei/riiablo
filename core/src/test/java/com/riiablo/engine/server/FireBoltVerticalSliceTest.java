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
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** M5 batch 1: the authoritative Fire Bolt command-to-hit vertical slice. */
class FireBoltVerticalSliceTest extends RiiabloTest {
  /** Captured original 1.10f execution, runs/20261113-cast-session/010-cast.
   * Replays the impact boundary using the observed missile's two seed words.
   * Cast/Do are dispatched explicitly here; animation timing is not compared.
   */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void levelOneImpactReplaysOriginalMissileSeedAndExactHpLoss(boolean unrelatedDraws) {
    RecordingFactory factory = new RecordingFactory();
    ReplayProbe probe = new ReplayProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false), new MissileCollisionSystem(), probe, factory)
        .build().register("factory", factory).register("map", new Map(0, 0))
        .register("serverContext", new ServerGameplayContext(Riiablo.files, 127897359)));
    try {
      int caster = player(world, 1000f);
      int target = monster(world, 7f);
      Attributes owner = world.getMapper(AttributesWrapper.class).get(caster).attrs;
      owner.base().put(Stat.level, 99); owner.reset();
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_BOLT);
      SkillCastEvent cast = SkillCastEvent.obtain(caster, skill.Id, target, new Vector2(7, 0));
      world.getSystem(EventSystem.class).dispatch(cast);
      assertTrue(cast.accepted);
      assertEquals(2.5f, cast.manaCost, 0f);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(7, 0), skill.srvdofunc, skill.cltdofunc));
      assertEquals(1, factory.created);
      Missile missile = factory.last;
      // This restores captured INPUT state, not a mocked roll or damage result.
      missile.nativeSeed = new com.d2moo.common.drlg.D2Seed(564996791, 666);
      missile.rngState = 564996791;
      if (unrelatedDraws) {
        com.riiablo.engine.server.NativeRng shared = world.getSystem(MissileCollisionSystem.class)
            .checkpointCombatRng();
        assertNotNull(shared);
        for (int i = 0; i < 37; i++) shared.nextInt(1000);
      }
      world.setDelta(1f / 25f);
      for (int i = 0; i < 20 && life(world, target) == 1000f; i++) world.process();
      assertEquals(1, probe.hits);
      assertEquals(1389, probe.fireFixed, "original SUNITDMG packet in 8.8");
      assertEquals(254611, world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).encodedValues(), "original target HP after settlement");
      assertEquals(567464301, probe.low, "original missile seed after damage roll");
      assertEquals(235655850, probe.high, "original missile seed carry word");
      System.out.println("ORIGINAL_FIREBOLT_REPLAY skill=36 level=1 fire_fixed=" + probe.fireFixed
          + " hp_after_fixed=254611 seed_after_low=" + probe.low + " seed_after_high=" + probe.high);
    } finally { world.dispose(); }
  }

  private static final class ReplayProbe extends net.mostlyoriginal.api.system.core.PassiveSystem {
    int hits, fireFixed, low, high;
    @net.mostlyoriginal.api.event.common.Subscribe
    public void damage(com.riiablo.engine.server.event.DamageEvent event) {
      Missile missile = world.getMapper(Missile.class).get(event.missileId);
      hits++; fireFixed = Math.round(event.fireDamage * 256f);
      assertNotNull(missile.nativeSeed);
      low = missile.nativeSeed.getNLowSeed(); high = missile.nativeSeed.getNHighSeed();
    }
  }

  @Test
  void acceptedCastConsumesManaOnceAndRejectedCastDoesNot() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 100f);
      int target = monster(world, 5f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_BOLT);

      SkillCastEvent accepted = SkillCastEvent.obtain(caster, skill.Id, target, new Vector2(5, 0));
      world.getSystem(EventSystem.class).dispatch(accepted);
      assertTrue(accepted.accepted);
      assertTrue(accepted.manaCost > 0f);
      assertEquals(100f - accepted.manaCost, mana(world, caster), 0.0001f);

      world.getMapper(AttributesWrapper.class).get(caster).attrs.get(Stat.mana).set(0f);
      SkillCastEvent rejected = SkillCastEvent.obtain(caster, skill.Id, target, new Vector2(5, 0));
      world.getSystem(EventSystem.class).dispatch(rejected);
      assertFalse(rejected.accepted);
      assertEquals(0f, mana(world, caster), 0.0001f);
      assertEquals(0, factory.created, "cast rejection must not create a missile");
    } finally {
      world.dispose();
    }
  }

  @Test
  void acceptedCastThenSkillDoCreatesFireBoltAndSettlesFixedPointHit() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 100f);
      int target = monster(world, 5f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_BOLT);
      SkillCastEvent cast = SkillCastEvent.obtain(caster, skill.Id, target, new Vector2(5, 0));
      world.getSystem(EventSystem.class).dispatch(cast);
      assertTrue(cast.accepted);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(5, 0), skill.srvdofunc, skill.cltdofunc));
      assertEquals(1, factory.created);
      assertNotNull(factory.last);
      assertEquals(SkillId.FIRE_BOLT, factory.last.skillId);
      assertTrue(factory.last.damageSnapshot);
      assertEquals(3, factory.last.damage.get(Stat.firemindam).asInt());
      assertEquals(6, factory.last.damage.get(Stat.firemaxdam).asInt());

      world.setDelta(1f / 25f);
      for (int i = 0; i < 8 && life(world, target) >= 1000f; i++) world.process();
      assertTrue(life(world, target) < 1000f, "Fire Bolt must apply its fixed-point fire packet");
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
  }

  private static int player(World world, float mana) {
    int id = world.create();
    CharData data = CharData.createRemote("firebolt", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.FIRE_BOLT, 1);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000f, mana);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "firebolt-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000f, 0f);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float life, float mana) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.mana, mana);
    attrs.base().put(Stat.maxmana, Math.max(mana, 100f));
    attrs.reset();
    return attrs;
  }

  private static float mana(World world, int entity) {
    return world.getMapper(AttributesWrapper.class).get(entity).attrs.get(Stat.mana).asFixed();
  }

  private static float life(World world, int entity) {
    return world.getMapper(AttributesWrapper.class).get(entity).attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    Missile last;

    @Override public int createMissile(Missiles.Entry row, Vector2 direction,
        Vector2 position, int ownerId, int skillLevel) {
      int id = world.create();
      last = world.getMapper(Missile.class).create(id)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(id).position.set(position);
      world.getMapper(Velocity.class).create(id).velocity.set(direction).setLength(row.Vel);
      created++;
      return id;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
