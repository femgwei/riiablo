package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
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
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for D2MOO MISSMODE_SrvHit01 Fire Ball impact behavior. */
class SorceressFireBallIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void impactFansOutToNearbyTargetsButDoesNotRedamageTheCenter() {
    RecordingFactory factory = new RecordingFactory(null, null);
    World world = world(factory, new Map(0, 0));
    try {
      int caster = player(world, 1);
      int center = monster(world, 5, 0);
      int nearby = monster(world, 5, 3);
      cast(world, caster, center);

      world.setDelta(1f / 25f);
      IntSet hitGate = null;
      float impactRadius = -1f;
      for (int i = 0; i < 12; i++) {
        world.process();
        for (int n = 0; n < factory.created.size(); n++) {
          if (!"explodingarrowexp".equalsIgnoreCase(factory.createdNames.get(n))) continue;
          int childId = factory.createdIds.get(n);
          if (!world.getMapper(Missile.class).has(childId)) continue;
          Missile activeChild = world.getMapper(Missile.class).get(childId);
          hitGate = activeChild.sharedHitTargets;
          impactRadius = activeChild.areaRadiusOverride;
        }
        if (hitGate != null && hitGate.size >= 2) break;
      }

      assertTrue(factory.createdNames.contains("fireball"),
          "Fire Ball must create its travelling parent missile");
      assertTrue(factory.createdNames.contains("explodingarrowexp"),
          "SrvHit01 must create the impact presentation missile: " + factory.createdNames);
      assertEquals(4f, impactRadius, EPSILON);
      assertNotNull(hitGate,
          "the impact child must carry a cast-lifetime target gate");
      assertTrue(life(world, center) < 1000f,
          "the parent must resolve direct damage on the impact center");
      assertTrue(life(world, nearby) < 1000f,
          "the child must fan the same packet to a nearby hostile target");
      assertEquals(2, hitGate.size,
          "the center and nearby target must each be claimed once");

      float centerAfterImpact = life(world, center);
      float nearbyAfterImpact = life(world, nearby);
      for (int i = 0; i < 10; i++) world.process();
      assertEquals(centerAfterImpact, life(world, center), EPSILON,
          "the impact child must not redamage the center target");
      assertEquals(nearbyAfterImpact, life(world, nearby), EPSILON,
          "the impact child must not replay damage on later ticks");
    } finally {
      world.dispose();
    }
  }

  @Test
  void wallImpactSpawnsPresentationButCannotDamageUnitBehindBarrier() {
    Map.Zone zone = new Map.Zone();
    Map blocked = new WallMap(zone);
    RecordingFactory factory = new RecordingFactory(blocked, zone);
    World world = world(factory, blocked);
    try {
      int caster = player(world, 1);
      int behindWall = monster(world, 8, 0);
      cast(world, caster, behindWall);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 4; i++) world.process();

      assertTrue(factory.createdNames.contains("fireball"));
      assertTrue(factory.createdNames.contains("explodingarrowexp"),
          "a map barrier must still produce Fire Ball's impact presentation");
      assertEquals(1000f, life(world, behindWall), EPSILON,
          "the parent must stop at the wall before reaching the unit behind it");
    } finally {
      world.dispose();
    }
  }

  @Test
  void lethalCenterStillCreatesImpactBeforeDeathAndDamagesNearbyTarget() {
    RecordingFactory factory = new RecordingFactory(null, null);
    DeathProbe probe = new DeathProbe(factory);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new ServerSkillSystem(false),
            new MissileCollisionSystem(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      int center = monster(world, 5, 0);
      world.getMapper(AttributesWrapper.class).get(center).attrs.get(Stat.hitpoints).set(1f);
      int nearby = monster(world, 5, 3);
      cast(world, caster, center);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 12; i++) world.process();

      assertTrue(probe.deathObserved, "lethal Fire Ball must dispatch DeathEvent");
      assertTrue(probe.impactCreatedAtDeath,
          "SrvHit01 must create the impact child before the lethal DeathEvent");
      assertEquals(0f, life(world, center), EPSILON);
      assertTrue(life(world, nearby) < 1000f,
          "the impact child must still fan out after the center target dies");
    } finally {
      world.dispose();
    }
  }

  @Test
  void impactPresentationUsesNativeLifetimeAndExpiresWithoutRecreating() {
    Map.Zone zone = new Map.Zone();
    Map blocked = new WallMap(zone);
    RecordingFactory factory = new RecordingFactory(blocked, zone);
    World world = world(factory, blocked);
    try {
      int caster = player(world, 1);
      int behindWall = monster(world, 8, 0);
      cast(world, caster, behindWall);

      world.setDelta(1f / 25f);
      world.process();
      assertTrue(factory.createdNames.contains("explodingarrowexp"));
      int childId = factory.createdIds.get(factory.createdIds.size() - 1);
      Missile child = world.getMapper(Missile.class).get(childId);
      assertEquals(16, child.nativeLifetimeFrames,
          "the impact presentation must use the native ExplosionMissile range");

      for (int i = 0; i < 20; i++) world.process();
      assertTrue(!world.getMapper(Missile.class).has(childId),
          "the one-shot impact presentation must expire after its native lifetime");
      assertEquals(2, factory.createdNames.size(),
          "impact expiry must not recreate another explosion child");
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory, Map map) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false),
            new MissileCollisionSystem(), factory)
        .build()
        .register("factory", factory)
        .register("map", map));
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("fireball", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.FIRE_BALL, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "fireball-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float life) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.reset();
    return attrs;
  }

  private static void cast(World world, int caster, int target) {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_BALL);
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, target, new Vector2(5, 0), skill.srvdofunc, skill.cltdofunc));
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    final Map map;
    final Map.Zone zone;
    final ArrayList<Missile> created = new ArrayList<>();
    final ArrayList<String> createdNames = new ArrayList<>();
    final ArrayList<Integer> createdIds = new ArrayList<>();

    RecordingFactory(Map map, Map.Zone zone) {
      this.map = map;
      this.zone = zone;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entityId).position.set(position);
      world.getMapper(Velocity.class).create(entityId).velocity.set(direction).setLength(row.Vel);
      if (map != null) world.getMapper(MapWrapper.class).create(entityId).set(map, zone);
      created.add(missile);
      createdNames.add(row.Missile);
      createdIds.add(entityId);
      return entityId;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }

    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
  }

  private static final class DeathProbe extends PassiveSystem {
    final RecordingFactory factory;
    boolean deathObserved;
    boolean impactCreatedAtDeath;

    DeathProbe(RecordingFactory factory) {
      this.factory = factory;
    }

    @Subscribe
    public void onDeath(DeathEvent event) {
      deathObserved = true;
      impactCreatedAtDeath = factory.createdNames.contains("explodingarrowexp");
    }
  }

  private static final class WallMap extends Map {
    private final Zone zone;

    WallMap(Zone zone) {
      super(0, 0);
      this.zone = zone;
    }

    @Override public Zone getZone(float x, float y) {
      return zone;
    }

    @Override public boolean castRay(Ray<Vector2> ray, int flags, int size,
        Collision<Vector2> collision) {
      collision.point.set(ray.end);
      return true;
    }
  }
}
