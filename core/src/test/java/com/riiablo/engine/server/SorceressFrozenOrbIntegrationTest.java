package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.codec.DCC;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.Direction;
import com.riiablo.presentation.DirectionResolver;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.missile.NativeFrozenOrbMotion;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for the native Frozen Orb SrvDo15/SrvHit29/SrvDo16 chain. */
class SorceressFrozenOrbIntegrationTest extends RiiabloTest {
  /** Diagnostic only: export actual motion/facing, without asserting visual parity. */
  @Test
  void exportBoltAndFinalNovaFacingThroughProductionSystems() throws Exception {
    Riiablo.colors = new com.riiablo.Colors();
    RecordingFactory factory = new RecordingFactory();
    factory.presentation = true;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), new AngularVelocity(), new DirectionResolver(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
    json.setOutputType(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
    ArrayList<java.util.Map<String, Object>> samples = new ArrayList<>();
    java.util.Map<String, Integer> totals = new java.util.TreeMap<>();
    java.util.Map<String, Integer> mismatches = new java.util.TreeMap<>();
    float largestError = 0;
    try {
      int caster = player(world, 3);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROZEN_ORB);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, Engine.INVALID_ENTITY, new Vector2(2f, 0f),
          skill.srvdofunc, skill.cltdofunc));
      world.setDelta(1f / 25f);
      for (int tick = 0; tick < 110; tick++) {
        world.process();
        for (int id : factory.ids) {
          if (!world.getEntityManager().isActive(id)) continue;
          Missile m = world.getMapper(Missile.class).get(id);
          if (m == null || m.missile == null) continue;
          String name = m.missile.Missile;
          if (!"frozenorbbolt".equals(name) && !"frozenorbnova".equals(name)) continue;
          Vector2 velocity = world.getMapper(Velocity.class).get(id).velocity;
          Angle angle = world.getMapper(Angle.class).get(id);
          com.riiablo.codec.Animation animation = world.getMapper(AnimationWrapper.class).get(id).animation;
          int expected = Direction.radiansToDirection(velocity.angleRad(), animation.getNumDirections());
          int actual = animation.getDirection();
          float difference = Math.abs(velocity.angleDeg() - angle.angle.angleDeg()) % 360f;
          difference = Math.min(difference, 360f - difference);
          largestError = Math.max(largestError, difference);
          totals.merge(name, 1, Integer::sum);
          if (expected != actual) mismatches.merge(name, 1, Integer::sum);
          java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
          row.put("tick", tick); row.put("entity", id); row.put("missile", name);
          row.put("age", m.nativeFrame); row.put("vx", velocity.x); row.put("vy", velocity.y);
          Vector2 position = world.getMapper(Position.class).get(id).position;
          row.put("x", position.x); row.put("y", position.y);
          row.put("facing_x", angle.angle.x); row.put("facing_y", angle.angle.y);
          row.put("expected_direction", expected); row.put("animation_direction", actual);
          row.put("heading_error_degrees", difference); row.put("cel_file", m.missile.CelFile);
          row.put("directions", animation.getNumDirections()); samples.add(row);
        }
      }
      assertTrue(totals.getOrDefault("frozenorbbolt", 0) > 0);
      assertTrue(totals.getOrDefault("frozenorbnova", 0) > 0);
      assertEquals(0, mismatches.getOrDefault("frozenorbbolt", 0).intValue(),
          "ordinary shards are the control group for the same facing pipeline");
      assertEquals(0, mismatches.getOrDefault("frozenorbnova", 0).intValue(),
          "final-ring velocity turns must update the presentation-facing target");
      java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
      report.put("scope", "headless production movement/Angle/DirectionResolver; DCC direction count stub; no GPU/pixel observation");
      report.put("sample_counts", totals); report.put("direction_mismatches", mismatches);
      report.put("largest_heading_error_degrees", largestError);
      report.put("ordinary_shards_created", factory.count("frozenorbbolt"));
      report.put("final_shards_created", factory.count("frozenorbnova"));
      report.put("samples", samples);
      java.nio.file.Path out = java.nio.file.Paths.get("build/diagnostics/frozen-orb-facing.json");
      java.nio.file.Files.createDirectories(out.getParent());
      java.nio.file.Files.write(out, json.prettyPrint(report).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      System.out.println("FROZEN_ORB_FACING samples=" + totals + " mismatches=" + mismatches
          + " max_error=" + largestError + " report=" + out.toAbsolutePath());
    } finally { world.dispose(); }
  }
  @Test
  void nativeRowsUseFrozenOrbControllerAndNovaCallbacks() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROZEN_ORB);
    assertNotNull(skill);
    assertEquals(0, skill.srvdofunc);
    assertEquals("frozenorb", skill.srvmissile);
    Missiles.Entry orb = Riiablo.files.Missiles.get("frozenorb");
    Missiles.Entry bolt = Riiablo.files.Missiles.get("frozenorbbolt");
    Missiles.Entry nova = Riiablo.files.Missiles.get("frozenorbnova");
    assertNotNull(orb);
    assertNotNull(bolt);
    assertNotNull(nova);
    assertEquals(15, orb.pSrvDoFunc);
    assertEquals(29, orb.pSrvHitFunc);
    assertEquals(1, orb.Param[0]);
    assertEquals(19, orb.Param[1]);
    assertEquals("frozenorbbolt", orb.SubMissile[0]);
    assertEquals("frozenorbnova", orb.HitSubMissile[0]);
    assertEquals(4, orb.sHitPar[0]);
    assertEquals(16, nova.pSrvDoFunc);
    assertEquals("Frozen Orb", bolt.Skill);
    assertEquals("Frozen Orb", nova.Skill);
  }

  @Test
  void castEmitsBoltsAndOnlyLifetimeExpiryFansSixteenNovaShards() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 3);
      int target = monster(world, 0.4f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROZEN_ORB);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(2f, 0f), skill.srvdofunc, skill.cltdofunc));

      assertEquals(1, factory.count("frozenorb"));
      Missile root = factory.first("frozenorb");
      assertNotNull(root);
      assertTrue(root.frozenOrbController);
      int lifetime = root.nativeLifetimeFrames;

      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("frozenorbbolt"),
          "SrvDo15 emits one bolt on the native one-frame cadence");
      assertTrue(factory.first("frozenorbbolt").damageSnapshot,
          "Frozen Orb bolt receives the skill cold packet at spawn");
      assertEquals(0, factory.count("frozenorbnova"),
          "contact with a unit must not burst the live root");
      for (int frame = 2; frame < lifetime; frame++) {
        world.process();
        assertEquals(0, factory.count("frozenorbnova"));
      }
      world.process();
      assertEquals(lifetime, factory.count("frozenorbbolt"));
      assertEquals(16, factory.count("frozenorbnova"),
          "SrvHit29 fans the native ring on CurrentFrame == 0 without a target");
      assertEquals(3, factory.lastSkillLevel.get("frozenorbbolt").intValue());
      assertEquals(3, factory.lastSkillLevel.get("frozenorbnova").intValue());
      for (Missile nova : factory.named("frozenorbnova")) {
        assertTrue(nova.frozenOrbNova);
        assertTrue(nova.damageSnapshot);
        assertEquals(25, nova.nativeLifetimeFrames);
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void zeroLifetimeRootDoesNotInventANovaAtNegativeCurrentFrame() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int id = world.create();
      Missile root = world.getMapper(Missile.class).create(id)
          .set(Riiablo.files.Missiles.get("frozenorb"), Vector2.Zero, 0);
      root.ownerId = player(world, 1);
      root.skillId = SkillId.FROZEN_ORB;
      root.damageLevel = 1;
      root.frozenOrbController = true;
      world.getMapper(Position.class).create(id);
      world.getMapper(Velocity.class).create(id);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("frozenorbbolt"), "callback still runs before decrement");
      assertEquals(0, factory.count("frozenorbnova"), "SrvHit29 requires CurrentFrame == 0");
    } finally {
      world.dispose();
    }
  }

  @Test
  void controllerEmitsAtElapsedZeroAndPreservesZeroPhaseStep() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 3);
      int id = world.create();
      Missiles.Entry row = new Missiles.Entry();
      row.pSrvDoFunc = 15;
      row.Range = 5;
      row.Vel = 1000;
      row.Param = new int[] {3, 0};
      row.SubMissile = new String[] {"frozenorbbolt"};
      Missile root = world.getMapper(Missile.class).create(id).set(row, Vector2.Zero, 5);
      root.ownerId = caster;
      root.skillId = SkillId.FROZEN_ORB;
      root.damageLevel = 3;
      root.frozenOrbController = true;
      root.frozenOrbTargetPhase = -19;
      world.getMapper(Position.class).create(id);
      world.getMapper(Velocity.class).create(id).velocity.set(1000, 0);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("frozenorbbolt"));
      assertEquals(19, root.frozenOrbTargetPhase, "native abs(TargetX % 64), zero step stays zero");
      assertEquals(Vector2.Zero, factory.firstOrigin.get("frozenorbbolt"),
          "SrvDo executes before path movement");
      world.process();
      world.process();
      assertEquals(1, factory.count("frozenorbbolt"));
      world.process();
      assertEquals(2, factory.count("frozenorbbolt"), "second emission at elapsed=3");
      assertEquals(19, root.frozenOrbTargetPhase);
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("frozen-orb", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.FROZEN_ORB, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0f, 0f);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs(100f);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    MonStats2.Entry row2 = row != null ? Riiablo.files.monstats2.get(row.MonStatsEx) : null;
    world.getMapper(Monster.class).create(id).set(row, row2);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs(200f);
    return id;
  }

  private static Attributes attrs(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.base().put(Stat.coldresist, 0);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    boolean presentation;
    final ArrayList<Integer> ids = new ArrayList<>();
    final ArrayList<Missile> missiles = new ArrayList<>();
    final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
    final java.util.Map<String, Integer> lastSkillLevel = new java.util.HashMap<>();
    final java.util.Map<String, Vector2> firstOrigin = new java.util.HashMap<>();

    int count(String name) {
      return counts.getOrDefault(name, 0);
    }

    ArrayList<Missile> named(String name) {
      ArrayList<Missile> result = new ArrayList<>();
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) {
          if (!result.contains(missile)) result.add(missile);
        }
      }
      return result;
    }

    Missile first(String name) {
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) {
          return missile;
        }
      }
      return null;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entityId).position.set(position);
      missile.frozenOrbVelocity = NativeFrozenOrbMotion.nativeVelocity(row, 1, 0);
      NativeFrozenOrbMotion.initialVelocity(direction, missile.frozenOrbVelocity,
          world.getMapper(Velocity.class).create(entityId).velocity);
      if (presentation) {
        world.getMapper(Angle.class).create(entityId).set(direction);
        DCC dcc = org.mockito.Mockito.mock(DCC.class);
        org.mockito.Mockito.when(dcc.getNumDirections()).thenReturn(Math.max(1, row.NumDirections));
        org.mockito.Mockito.when(dcc.getNumFramesPerDir()).thenReturn(Math.max(1, row.AnimLen));
        org.mockito.Mockito.when(dcc.getBox(org.mockito.ArgumentMatchers.anyInt())).thenReturn(new BBox());
        com.riiablo.codec.Animation animation = world.getMapper(AnimationWrapper.class).create(entityId).animation;
        animation.edit().layer(dcc).build();
        animation.setDirection(Direction.radiansToDirection(direction.angleRad(), animation.getNumDirections()));
      }
      ids.add(entityId);
      missiles.add(missile);
      counts.merge(row.Missile, 1, Integer::sum);
      firstOrigin.putIfAbsent(row.Missile, new Vector2(position));
      return entityId;
    }

    @Override public int createMissile(Missiles.Entry row, Vector2 direction,
        Vector2 position, int ownerId, int skillLevel) {
      int id = createMissile(row.Id, direction, position, ownerId);
      if (id >= 0) {
        world.getMapper(Missile.class).get(id).nativeLifetimeFrames =
            Missile.nativeRangeFrames(row, skillLevel);
        lastSkillLevel.put(row.Missile, skillLevel);
      }
      return id;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
