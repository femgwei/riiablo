package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
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
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Actual world ticks, not direct calls that bypass missile expiration. */
class NativeExplosionLifecycleTest extends RiiabloTest {
  @Test
  void freezingArrowResolvesWeaponThenColdChildUsingNativeDeferredBranch() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1000);
      int near = f.monster(1, 3, 1000);
      int outside = f.monster(1, 10, 1000);
      f.cast("Freezing Arrow", "freezingarrow");
      f.tick();
      assertEquals(1, f.probe.hits(center).size());
      assertEquals(10f, f.probe.hits(center).get(0).physicalDamage);
      assertEquals(0, f.probe.hits(near).size());
      f.tick();
      assertEquals(2, f.probe.hits(center).size());
      assertTrue(f.probe.hits(center).get(1).coldDamage > 0,
          "native sHitPar1=0 defers cold until the child expiration callback");
      assertTrue(f.probe.hits(near).get(0).coldDamage > 0);
      assertTrue(f.frozen(center));
      assertTrue(f.frozen(near));
      assertEquals(1000f, f.life(outside));
      float centerLife = f.life(center), nearLife = f.life(near);
      for (int i = 0; i < 6; i++) f.tick();
      assertEquals(centerLife, f.life(center));
      assertEquals(nearLife, f.life(near));
    }
  }

  @Test
  void explodingArrowAlsoResolvesItsOneFrameChildAtExpiration() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1000);
      int near = f.monster(1, 2, 1000);
      f.cast("Exploding Arrow", "explodingarrow");
      f.tick();
      f.tick();
      assertEquals(2, f.probe.hits(center).size());
      assertEquals(10f, f.probe.hits(center).get(0).physicalDamage);
      assertTrue(f.probe.hits(center).get(1).fireDamage > 0);
      assertTrue(f.probe.hits(near).get(0).fireDamage > 0);
    }
  }

  @Test
  void lethalColdChildShattersBeforeDeathAndDoesNotLoseParentPierce() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1);
      int near = f.monster(1, 3, 1000);
      int arrow = f.cast("Freezing Arrow", "freezingarrow");
      f.immediateHit04(arrow);
      f.pierce(arrow);
      f.tick();
      assertEquals(1, f.probe.deaths);
      assertTrue(f.probe.frozenAtDeath);
      assertTrue(f.probe.shatterAtDeath);
      assertEquals(1, f.probe.hits(center).size(), "no weapon hit on the cold-killed corpse");
      assertTrue(f.frozen(near));
      assertTrue(f.world.getMapper(Missile.class).has(arrow));
      assertEquals(0, f.world.getMapper(Missile.class).get(arrow).pierceRemaining);
      for (int i = 0; i < 3; i++) f.tick();
      assertEquals(1, f.probe.deaths);
    }
  }

  @Test
  void distinctPierceExplosionsMayHitTheSameNearbyMonsterAgain() {
    try (Fixture f = new Fixture()) {
      f.monster(.8f, 0, 1000);
      f.monster(2.8f, 0, 1000);
      int near = f.monster(2, 2, 1000);
      int arrow = f.cast("Freezing Arrow", "freezingarrow");
      f.pierce(arrow);
      for (int i = 0; i < 4; i++) f.tick();
      assertEquals(2, f.probe.hits(near).size(),
          "each SrvHit04 child owns its hit gate, not one gate shared with the arrow");
      assertTrue(f.probe.hits(near).stream().allMatch(hit -> hit.coldDamage > 0));
    }
  }

  @Test
  void deferredColdKillFreezesAndShattersAfterNonlethalWeaponPacket() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 15);
      f.cast("Freezing Arrow", "freezingarrow");
      f.tick();
      assertEquals(5f, f.life(center));
      assertEquals(0, f.probe.deaths);
      f.tick();
      assertEquals(1, f.probe.deaths);
      assertTrue(f.probe.frozenAtDeath);
      assertTrue(f.probe.shatterAtDeath);
    }
  }

  @Test
  void weaponKillKeepsNormalDeathButDeferredExplosionStillHitsNeighbors() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1);
      int near = f.monster(1, 3, 1000);
      f.cast("Freezing Arrow", "freezingarrow");
      f.tick();
      assertEquals(1, f.probe.deaths);
      assertFalse(f.probe.frozenAtDeath);
      assertFalse(f.probe.shatterAtDeath);
      f.tick();
      assertEquals(1, f.probe.deaths);
      assertEquals(1, f.probe.hits(center).size());
      assertTrue(f.frozen(near));
    }
  }

  @Test
  void coldImmuneCenterStillReceivesWeaponDamageWithoutFreeze() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1000);
      Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(center).attrs;
      attrs.base().put(Stat.coldresist, 100);
      attrs.reset();
      f.cast("Freezing Arrow", "freezingarrow");
      f.tick();
      f.tick();
      assertEquals(990f, f.life(center));
      assertFalse(f.frozen(center));
    }
  }

  @Test
  void freezingArrowExpiryCallsHit04EvenWithoutLastCollideOrAlwaysExplode() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(0, 3, 1000);
      int arrow = f.cast("Freezing Arrow", "freezingarrow");
      f.expireNextTick(arrow);
      f.tick();
      f.tick();
      assertTrue(f.frozen(near));
      assertEquals(1, f.probe.hits(near).size());
      assertEquals(1, f.factory.names.stream().filter("freezingarrowexp3"::equals).count());
    }
  }

  @Test
  void oneFrameStandaloneColdExplosionResolvesNullHitBeforeRemoval() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(0, 3, 1000);
      int child = f.factory.createMissile(Riiablo.files.Missiles.get("freezingarrowexp3"),
          Vector2.X, Vector2.Zero, f.owner);
      MissileDamageResolver.initializeSkillArea(f.world.getMapper(Missile.class).get(child),
          Riiablo.files.skills.get("Freezing Arrow"), f.ownerAttrs, 1);
      f.expireNextTick(child);
      f.tick();
      assertEquals(1, f.probe.hits(near).size());
      assertTrue(f.frozen(near));
      assertFalse(f.world.getMapper(Missile.class).has(child));
    }
  }

  @Test
  void fireBallFanoutIsImmediateAndPresentationCannotDamageNewTargets() {
    try (Fixture f = new Fixture()) {
      int center = f.monster(.8f, 0, 1000);
      int near = f.monster(1, 3, 1000);
      int later = f.monster(1, 10, 1000);
      f.cast("Fire Ball", "fireball");
      f.tick();
      assertEquals(1, f.probe.hits(center).size());
      assertEquals(1, f.probe.hits(near).size());
      assertTrue(f.probe.hits(near).get(0).fireDamage > 0);
      f.world.getMapper(Position.class).get(later).position.set(1, 2);
      for (int i = 0; i < 20; i++) f.tick();
      assertEquals(1, f.probe.hits(center).size());
      assertEquals(1, f.probe.hits(near).size());
      assertEquals(1000f, f.life(later), "a visual child must not own delayed combat work");
    }
  }

  @Test
  void fireBallExpiryResolvesHit01WithoutLastCollide() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(0, 3, 1000);
      int fireball = f.cast("Fire Ball", "fireball");
      f.expireNextTick(fireball);
      f.tick();
      assertEquals(1, f.probe.hits(near).size());
      assertTrue(f.probe.hits(near).get(0).fireDamage > 0);
      for (int i = 0; i < 20; i++) f.tick();
      assertEquals(1, f.probe.hits(near).size());
    }
  }

  @Test
  void immolationArrowExpiryResolvesItsInitialAreaAndCreatesFireField() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(0, 3, 1000);
      int arrow = f.cast("Immolation Arrow", "immolationarrow");
      f.expireNextTick(arrow);
      f.tick();
      assertTrue(f.probe.hits(near).get(0).fireDamage > 0);
      assertTrue(f.factory.names.contains("immolationfire"));
    }
  }

  @Test
  void glacialSpikeExpiryStillFreezesTargetsInItsSkillRadius() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(0, 3, 1000);
      int spike = f.cast("Glacial Spike", "glacialspike");
      f.expireNextTick(spike);
      f.tick();
      assertEquals(1, f.probe.hits(near).size());
      assertTrue(f.frozen(near));
    }
  }

  @Test
  void freezingArrowWallHitCreatesAndResolvesExactlyOneColdChild() {
    try (Fixture f = new Fixture()) {
      int near = f.monster(1, 3, 1000);
      int arrow = f.cast("Freezing Arrow", "freezingarrow");
      Map.Zone zone = new Map.Zone();
      Map wall = new Map(0, 0) {
        @Override public Zone getZone(float x, float y) { return zone; }
        @Override public boolean castRay(Ray<Vector2> ray, int flags, int size,
            Collision<Vector2> collision) {
          collision.point.set(ray.end);
          return true;
        }
      };
      f.world.getMapper(MapWrapper.class).create(arrow).set(wall, zone);
      f.tick();
      f.tick();
      assertTrue(f.frozen(near));
      assertEquals(1, f.probe.hits(near).size());
      assertEquals(1, f.factory.names.stream().filter("freezingarrowexp3"::equals).count());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final RecordingFactory factory = new RecordingFactory();
    final Probe probe = new Probe();
    final World world;
    final int owner;
    final Attributes ownerAttrs;

    Fixture() {
      StateUpdater states = new StateUpdater();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, states, new MissileCollisionSystem(), factory)
          .build().register("factory", factory).register("map", new Map(0, 0)));
      probe.world = world;
      owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(-2, 0);
      ownerAttrs = attributes(1000);
      ownerAttrs.base().put(Stat.mindamage, 10);
      ownerAttrs.base().put(Stat.maxdamage, 10);
      ownerAttrs.base().put(Stat.tohit, 10000);
      ownerAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = ownerAttrs;
      world.setDelta(1f / 25f);
    }

    int monster(float x, float y, float hp) {
      int target = world.create();
      world.getMapper(Monster.class).create(target).set(new MonStats.Entry(), new MonStats2.Entry());
      world.getMapper(Position.class).create(target).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(hp);
      world.getMapper(UnitStates.class).create(target).init(target);
      return target;
    }

    int cast(String skillName, String missileName) {
      int id = factory.createMissile(Riiablo.files.Missiles.get(missileName),
          Vector2.X, Vector2.Zero, owner);
      Missile missile = world.getMapper(Missile.class).get(id);
      Skills.Entry skill = Riiablo.files.skills.get(skillName);
      MissileDamageResolver.initializeSkill(missile, skill, ownerAttrs, 1);
      // The test isolates callback order from random attack-rating misses.
      missile.missile = copyRow(missile.missile);
      missile.missile.ToHit = false;
      missile.usesAttackRating = false;
      world.getMapper(Velocity.class).get(id).velocity.set(25, 0);
      return id;
    }

    void pierce(int id) {
      Missile missile = world.getMapper(Missile.class).get(id);
      missile.pierceEnabled = true;
      missile.pierceRemaining = 1;
      missile.pierceChance = 100;
    }

    void immediateHit04(int id) {
      Missile missile = world.getMapper(Missile.class).get(id);
      missile.missile.sHitPar = new int[] {1, 0, 0};
    }

    void expireNextTick(int id) {
      Missile missile = world.getMapper(Missile.class).get(id);
      Missiles.Entry row = new Missiles.Entry();
      row.Missile = missile.missile.Missile;
      row.pSrvHitFunc = missile.missile.pSrvHitFunc;
      row.pSrvDoFunc = 1;
      row.sHitPar = missile.missile.sHitPar;
      row.HitSubMissile = missile.missile.HitSubMissile;
      row.EType = missile.missile.EType;
      // Deliberately leave LastCollide and AlwaysExplode clear.
      missile.missile = row;
      missile.nativeLifetimeFrames = 1;
      world.getMapper(Velocity.class).get(id).velocity.setZero();
    }

    void tick() { world.process(); }
    float life(int id) { return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed(); }
    boolean frozen(int id) { return world.getMapper(UnitStates.class).get(id).stateList.hasState(StateId.FREEZE); }
    @Override public void close() {
      world.dispose();
    }
  }

  private static Attributes attributes(float life) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.reset();
    return attrs;
  }

  private static Missiles.Entry copyRow(Missiles.Entry source) {
    Missiles.Entry copy = new Missiles.Entry();
    try {
      for (java.lang.reflect.Field field : Missiles.Entry.class.getFields()) {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(copy, field.get(source));
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
    return copy;
  }

  private static final class Probe extends PassiveSystem {
    World world;
    final List<DamageEvent> damage = new ArrayList<>();
    int deaths;
    boolean frozenAtDeath, shatterAtDeath;
    @Subscribe public void onDamage(DamageEvent event) { damage.add(event); }
    @Subscribe public void onDeath(DeathEvent event) {
      deaths++;
      UnitStates states = world.getMapper(UnitStates.class).get(event.victim);
      frozenAtDeath = states.stateList.hasState(StateId.FREEZE);
      shatterAtDeath = states.stateList.hasState(StateId.SHATTER);
    }
    List<DamageEvent> hits(int target) {
      List<DamageEvent> hits = new ArrayList<>();
      for (DamageEvent event : damage) if (event.victim == target) hits.add(event);
      return hits;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    final List<String> names = new ArrayList<>();
    @Override public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      int entity = world.create();
      world.getMapper(Missile.class).create(entity).set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(angle).setLength(row.Vel);
      names.add(row.Missile);
      return entity;
    }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return createMissile(id, angle, position, Engine.INVALID_ENTITY);
    }
    @Override public int createPlayer(com.riiablo.save.CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
