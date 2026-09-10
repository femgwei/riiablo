package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
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
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative contract for D2MOO SrvDo022 Frost Nova. */
class SorceressFrostNovaIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void createsExactNativeRingWithSkillDamageAndColdMasterySnapshot() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, false);
    try {
      int caster = player(world, 1, 0, 0, 20);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROST_NOVA);

      cast(world, caster, skill);

      assertEquals(64, factory.created.size());
      Missile first = factory.created.get(0);
      assertEquals("frostnova", first.missile.Missile);
      assertEquals(14f, first.range, EPSILON);
      assertEquals(2, first.damage.get(Stat.coldmindam).asInt());
      assertEquals(4, first.damage.get(Stat.coldmaxdam).asInt());
      assertEquals(200, first.damage.get(Stat.coldlength).asInt());
      assertEquals(20, first.damage.get(Stat.passive_cold_pierce).asInt());
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
            "all 64 paths must share one native per-cast target gate");
      }
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void collisionAppliesResistedColdDurationAndNativeImmunityItemGates() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, true);
    try {
      int caster = player(world, 1, 0, 0, 20);
      int resisted = monster(world, 5, 0, 50, false, false);
      int immune = monster(world, 0, 5, 100, false, false);
      int cannotFreeze = monster(world, -5, 0, 0, true, false);
      int halfDuration = monster(world, 0, -5, 0, false, true);

      cast(world, caster, Riiablo.files.skills.get(SkillId.FROST_NOVA));
      world.setDelta(1f / 25f);
      // At speed 24 the cardinal paths first enter the target radius on tick
      // four. Stop there so StateUpdater has not yet consumed the freshly
      // applied duration on a later tick.
      for (int i = 0; i < 4; i++) world.process();

      assertTrue(life(world, resisted) >= 98f && life(world, resisted) <= 99f);
      assertEquals(140, cold(world, resisted).duration,
          "50 cold resistance minus 20 Cold Mastery leaves 30%, so 200 frames become 140");
      assertEquals(100f, life(world, immune), EPSILON,
          "Cold Mastery must not break a monster immunity that started at 100+");
      assertTrue(cold(world, immune) == null);
      assertTrue(life(world, cannotFreeze) < 100f);
      assertTrue(cold(world, cannotFreeze) == null,
          "Cannot Be Frozen removes cold state but not cold damage");
      assertTrue(life(world, halfDuration) < 100f);
      assertEquals(120, cold(world, halfDuration).duration,
          "20 Cold Mastery first raises 200 frames to 240, then Half Freeze Duration halves it");
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  private static World world(RecordingFactory factory, boolean states) {
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true));
    if (states) builder.with(new StateUpdater());
    return new World(builder.with(new MissileCollisionSystem(), factory).build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
  }

  private static int player(
      World world, int level, float x, float y, int coldPierce) {
    int id = world.create();
    CharData data = CharData.createRemote("frost-nova", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.FROST_NOVA, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(x, y);
    Attributes attrs = attributes(100, 0);
    attrs.base().put(Stat.passive_cold_pierce, coldPierce);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, int coldResistance,
      boolean cannotFreeze, boolean halfDuration) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "frost-nova-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    Attributes attrs = attributes(100, coldResistance);
    if (cannotFreeze) attrs.base().put(Stat.item_cannotbefrozen, 1);
    if (halfDuration) attrs.base().put(Stat.item_halffreezeduration, 1);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float life, int coldResistance) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.base().put(Stat.coldresist, coldResistance);
    attrs.reset();
    return attrs;
  }

  private static void cast(World world, int caster, Skills.Entry skill) {
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, Engine.INVALID_ENTITY, Vector2.Zero,
        skill.srvdofunc, skill.cltdofunc));
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static UnitState cold(World world, int entityId) {
    return world.getMapper(UnitStates.class).get(entityId).stateList.getState(StateId.COLD);
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> created = new ArrayList<>();
    final ArrayList<Vector2> directions = new ArrayList<>();
    final ArrayList<Vector2> velocities = new ArrayList<>();

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
      created.add(missile);
      directions.add(new Vector2(direction));
      velocities.add(velocity.velocity);
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
