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
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative SrvDo023/SrvDo024 and SrvDo05/SrvDo06 contract. */
class SorceressFireAreaIntegrationTest extends RiiabloTest {
  @Test
  void blazeStateEmitsOnlyAfterActualMovementAndKeepsFractionalDamage() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, SkillId.BLAZE, 1, 0f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLAZE);
      cast(world, caster, skill, new Vector2(1f, 0f));

      assertTrue(world.getMapper(UnitStates.class).get(caster)
          .stateList.hasState(StateId.BLAZE));
      assertEquals(120, world.getMapper(UnitStates.class).get(caster)
          .stateList.getState(StateId.BLAZE).duration);

      world.getMapper(Velocity.class).get(caster).velocity.set(4f, 0f);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("blaze"));
      Missile first = factory.first("blaze");
      assertNotNull(first);
      assertTrue(first.persistent);
      assertTrue(first.fixedElementalRate);
      assertEquals(64, first.elementalMinRateFixed);
      assertEquals(128, first.elementalMaxRateFixed);
      assertEquals(115, first.remainingFrames + 1,
          "the newly inserted trail is allowed to consume its first sim frame");

      world.process();
      assertEquals(1, factory.count("blaze"),
          "a non-zero requested velocity must not duplicate a stationary trail");
      world.getMapper(Position.class).get(caster).position.x += 0.25f;
      world.process();
      assertEquals(2, factory.count("blaze"));
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void fireWallCreatesOpposedMakersCentreAndDamagingChildSegments() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, SkillId.FIRE_WALL, 1, 0f, 0f);
      int target = monster(world, 5f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_WALL);
      cast(world, caster, skill, new Vector2(5f, 0f));

      assertEquals(2, factory.count("firewallmaker"));
      assertEquals(1, factory.count("firewall"));
      ArrayList<Vector2> makerDirections = factory.directions("firewallmaker");
      assertEquals(2, makerDirections.size());
      assertEquals(-1f, makerDirections.get(0).dot(makerDirections.get(1)), 0.0001f);

      world.setDelta(1f / 25f);
      world.process();
      assertEquals(3, factory.count("firewall"),
          "each maker emits one child after its first movement step");
      assertTrue(life(world, target) < 100f,
          "the centre and child segments must use authoritative fractional fire damage");
      for (Missile missile : factory.missiles) {
        if (missile.missile != null && "firewall".equalsIgnoreCase(missile.missile.Missile)) {
          assertTrue(missile.persistent);
          assertTrue(missile.fixedElementalRate);
          assertEquals(41, missile.elementalDamageRate);
        }
      }
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  private static World world(RecordingFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
  }

  private static int player(
      World world, int skillId, int level, float x, float y) {
    int id = world.create();
    CharData data = CharData.createRemote("fire-area", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(skillId, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    Attributes attrs = attributes(100f);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "fire-area-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100f);
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

  private static void cast(
      World world, int caster, Skills.Entry skill, Vector2 target) {
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, Engine.INVALID_ENTITY, target,
        skill.srvdofunc, skill.cltdofunc));
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> missiles = new ArrayList<>();
    final ArrayList<Vector2> missileDirections = new ArrayList<>();

    int count(String name) {
      int count = 0;
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) count++;
      }
      return count;
    }

    Missile first(String name) {
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) {
          return missile;
        }
      }
      return null;
    }

    ArrayList<Vector2> directions(String name) {
      ArrayList<Vector2> result = new ArrayList<>();
      for (int i = 0; i < missiles.size(); i++) {
        Missile missile = missiles.get(i);
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) {
          result.add(missileDirections.get(i));
        }
      }
      return result;
    }

    @Override public int createMissile(
        int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      missile.rngState = NativeRng.forUnit(Riiablo.gameSeed, entityId).state();
      world.getMapper(Position.class).create(entityId).position.set(position);
      world.getMapper(Velocity.class).create(entityId).velocity
          .set(direction).setLength(row.Vel);
      missiles.add(missile);
      missileDirections.add(new Vector2(direction).nor());
      return entityId;
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

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }
  }
}
