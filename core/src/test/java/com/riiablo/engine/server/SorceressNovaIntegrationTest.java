package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for D2MOO SKILLS_SrvDo022_NovaAttack. */
class SorceressNovaIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void createsNativeSixtyFourPathRingWithOneSharedHitGate() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1);
      cast(world, caster);

      assertEquals(64, factory.created.size(),
          "D2MOO sub_6FD14170 always emits the fixed 64-offset ring");
      Missile first = factory.created.get(0);
      assertEquals("nova", first.missile.Missile);
      assertEquals(13f, first.range, EPSILON);
      assertEquals(1, first.damage.get(Stat.lightmindam).asInt());
      assertEquals(20, first.damage.get(Stat.lightmaxdam).asInt());
      assertEquals(24f, factory.velocities.get(0).len(), EPSILON);
      assertEquals(1f, factory.directions.get(0).x, EPSILON);
      assertEquals(0f, factory.directions.get(0).y, EPSILON);
      assertEquals(29f / (float) Math.sqrt(29 * 29 + 2 * 2),
          factory.directions.get(1).x, EPSILON);
      assertEquals(2f / (float) Math.sqrt(29 * 29 + 2 * 2),
          factory.directions.get(1).y, EPSILON);

      IntSet shared = first.sharedHitTargets;
      assertNotNull(shared);
      for (Missile missile : factory.created) {
        assertSame(shared, missile.sharedHitTargets,
            "every path from one cast must share the target claim set");
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void oneCastDamagesEachCrossedTargetOnlyOnce() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1);
      int east = monster(world, 5, 0);
      int north = monster(world, 0, 5);
      cast(world, caster);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 6; i++) world.process();

      float eastAfter = life(world, east);
      float northAfter = life(world, north);
      assertTrue(eastAfter >= 980f && eastAfter <= 999f);
      assertTrue(northAfter >= 980f && northAfter <= 999f);
      assertEquals(2, factory.created.get(0).sharedHitTargets.size,
          "the radial cast owns one stable claim per struck target");

      for (int i = 0; i < 6; i++) world.process();
      assertEquals(eastAfter, life(world, east), EPSILON,
          "overlapping Nova paths must not replay damage on the east target");
      assertEquals(northAfter, life(world, north), EPSILON,
          "overlapping Nova paths must not replay damage on the north target");
    } finally {
      world.dispose();
    }
  }

  @Test
  void collisionAppliesLightningResistanceAndNativeImmunityGate() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1);
      int resisted = monster(world, 5, 0, 50);
      int immune = monster(world, 0, 5, 100);

      cast(world, caster);
      world.setDelta(1f / 25f);
      // Allow the cardinal paths to sweep through the 5-unit targets while
      // keeping the assertion before any later repeated-hit opportunity.
      for (int i = 0; i < 6; i++) world.process();

      float resistedLife = life(world, resisted);
      assertTrue(resistedLife >= 990f && resistedLife <= 999f,
          "50% lightning resistance must reduce, not cancel, Nova damage: hp="
              + resistedLife);
      assertEquals(1000f, life(world, immune), EPSILON,
          "100% lightning resistance must preserve native immunity");
      assertEquals(2, factory.created.get(0).sharedHitTargets.size,
          "resisted and immune targets are each claimed once by the cast");
    } finally {
      world.dispose();
    }
  }

  @Test
  void castValidationSpendsNovaManaExactlyOnce() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1);
      float before = mana(world, caster);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.NOVA);
      float cost = NativeSkillResolver.manaCost(skill, 1);
      SkillCastEvent event = SkillCastEvent.obtain(
          caster, skill.Id, Engine.INVALID_ENTITY, Vector2.Zero);
      world.getSystem(EventSystem.class).dispatch(event);
      assertTrue(event.accepted, "Nova cast rejected resultCode=" + event.resultCode
          + " manaCost=" + event.manaCost);
      assertEquals(before - cost, mana(world, caster), EPSILON);
    } finally {
      world.dispose();
    }
  }

  @Test
  void lethalCardinalPathDoesNotStopOtherNovaPaths() {
    RecordingFactory factory = new RecordingFactory();
    DeathProbe probe = new DeathProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new ServerSkillSystem(true),
            new StateUpdater(), new MissileCollisionSystem(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      int lethal = monster(world, 5, 0);
      world.getMapper(AttributesWrapper.class).get(lethal).attrs
          .get(Stat.hitpoints).set(1f);
      int surviving = monster(world, 0, 5);
      cast(world, caster);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 6; i++) world.process();

      assertTrue(probe.deathObserved, "lethal Nova path must dispatch DeathEvent");
      assertEquals(0f, life(world, lethal), EPSILON);
      assertTrue(life(world, surviving) < 1000f,
          "one lethal cardinal path must not stop the other radial paths");
      assertEquals(2, factory.created.get(0).sharedHitTargets.size,
          "dead and surviving targets must both be claimed once");
    } finally {
      world.dispose();
    }
  }

  @Test
  void wallCollisionRemovesBlockedNovaPaths() {
    Map.Zone zone = new Map.Zone();
    Map blocked = new WallMap(zone);
    RecordingFactory factory = new RecordingFactory(blocked, zone);
    World world = world(factory, blocked);
    try {
      int caster = player(world, 1);
      cast(world, caster);

      world.setDelta(1f / 25f);
      world.process();
      assertEquals(64, factory.createdIds.size());
      for (int id : factory.createdIds) {
        assertTrue(!world.getMapper(Missile.class).has(id),
            "a blocking barrier must consume every Nova path that reaches it");
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeNovaRangeExpiresAllPathsWithoutRecreation() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1);
      cast(world, caster);
      world.setDelta(1f / 25f);
      for (int i = 0; i < 10; i++) world.process();
      boolean atLeastOneAlive = false;
      for (int id : factory.createdIds) {
        atLeastOneAlive |= world.getMapper(Missile.class).has(id);
      }
      assertTrue(atLeastOneAlive, "Nova paths must remain alive before native range 13 expires");

      for (int i = 0; i < 8; i++) world.process();
      for (int id : factory.createdIds) {
        assertTrue(!world.getMapper(Missile.class).has(id),
            "all Nova paths must expire at their native range");
      }
      assertEquals(64, factory.createdIds.size(),
          "range expiry must not recreate radial missiles");
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory) {
    return world(factory, new Map(0, 0));
  }

  private static World world(RecordingFactory factory, Map map) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true),
            new StateUpdater(), new MissileCollisionSystem(), factory)
        .build()
        .register("factory", factory)
        .register("map", map));
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("nova", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.NOVA, level);
    data.setSkillLevel(SkillId.STATIC_FIELD, 1);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y) {
    return monster(world, x, y, 0);
  }

  private static int monster(World world, float x, float y, int lightningResistance) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "nova-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    Attributes attrs = attributes(1000);
    attrs.base().put(Stat.lightresist, lightningResistance);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
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

  private static void cast(World world, int caster) {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.NOVA);
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, Engine.INVALID_ENTITY, Vector2.Zero,
        skill.srvdofunc, skill.cltdofunc));
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static float mana(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.mana).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    final Map map;
    final Map.Zone zone;
    final ArrayList<Missile> created = new ArrayList<>();
    final ArrayList<Integer> createdIds = new ArrayList<>();
    final ArrayList<Vector2> directions = new ArrayList<>();
    final ArrayList<Vector2> velocities = new ArrayList<>();

    RecordingFactory() {
      this(null, null);
    }

    RecordingFactory(Map map, Map.Zone zone) {
      this.map = map;
      this.zone = zone;
    }

    @Override public int createMissile(
        int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entityId).position.set(position);
      Velocity velocity = world.getMapper(Velocity.class).create(entityId);
      velocity.velocity.set(direction).setLength(row.Vel);
      if (map != null) world.getMapper(MapWrapper.class).create(entityId).set(map, zone);
      created.add(missile);
      createdIds.add(entityId);
      directions.add(new Vector2(direction));
      velocities.add(velocity.velocity);
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
    boolean deathObserved;

    @Subscribe
    public void onDeath(DeathEvent event) {
      deathObserved = true;
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
