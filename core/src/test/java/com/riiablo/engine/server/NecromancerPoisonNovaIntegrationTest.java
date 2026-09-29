package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo022 authoritative Poison Nova contract. */
class NecromancerPoisonNovaIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void createsNativeSixtyFourPoisonNovaSnapshotsWithSharedHitGate() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1, 25, 10);
      cast(world, caster);

      Skills.Entry skill = skill();
      int[] poison = NecromancerSkills.getPoisonNovaDamage(skill, 1, name -> 0);
      poison[0] = poison[0] * 125 / 100;
      poison[1] = poison[1] * 125 / 100;
      int duration = NecromancerSkills.getPoisonNovaDurationFrames(skill, 1);
      assertEquals(64, factory.created.size());
      IntSet shared = factory.created.get(0).sharedHitTargets;
      assertNotNull(shared);
      for (Missile missile : factory.created) {
        assertEquals("poisonnova", missile.missile.Missile);
        assertTrue(missile.fixedPoisonRate);
        assertEquals(poison[0], missile.poisonMinRateFixed);
        assertEquals(poison[1], missile.poisonMaxRateFixed);
        assertEquals(duration, missile.poisonDurationFrames);
        assertEquals(10, missile.poisonPiercePercent);
        assertTrue(missile.poisonAttackerPlayer);
        assertSame(shared, missile.sharedHitTargets);
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void oneCastAppliesPoisonOncePerTargetUsingCastTimeSnapshot() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1, 25, 10);
      int east = monster(world, 5, 0, 0);
      int north = monster(world, 0, 5, 0);
      cast(world, caster);
      Missile first = factory.created.get(0);
      for (Missile missile : factory.created) {
        assertEquals(first.poisonDurationFrames, missile.poisonDurationFrames);
      }
      int min = first.poisonMinRateFixed;
      int max = first.poisonMaxRateFixed;

      // In-flight missiles must retain mastery/pierce captured at cast time.
      Attributes casterAttrs = world.getMapper(AttributesWrapper.class).get(caster).attrs;
      casterAttrs.base().put(Stat.passive_pois_mastery, 200);
      casterAttrs.base().put(Stat.item_pierce_pois, 90);
      casterAttrs.reset();

      world.setDelta(1f / 25f);
      for (int i = 0; i < 14; i++) world.process();

      UnitState eastPoison = poison(world, east);
      UnitState northPoison = poison(world, north);
      assertNotNull(eastPoison);
      assertNotNull(northPoison);
      int expectedDuration = first.poisonDurationFrames
          * (100 + first.poisonPiercePercent) / 100;
      assertEquals(expectedDuration, eastPoison.initialDuration,
          "poison pierce applies to the independent poison-length packet");
      assertEquals(expectedDuration, northPoison.initialDuration,
          "poison pierce applies to the independent poison-length packet");
      assertTrue(eastPoison.duration > 0);
      assertTrue(northPoison.duration > 0);
      assertTrue(eastPoison.exactDamagePerFrame >= min / 256f - EPSILON);
      assertTrue(eastPoison.exactDamagePerFrame <= max / 256f + EPSILON);
      assertTrue(northPoison.exactDamagePerFrame >= min / 256f - EPSILON);
      assertTrue(northPoison.exactDamagePerFrame <= max / 256f + EPSILON);
      assertEquals(caster, eastPoison.sourceEntityId);
      assertEquals(caster, northPoison.sourceEntityId);
      assertEquals(2, first.sharedHitTargets.size,
          "one cast must claim each crossed target once across all 64 paths");
      assertEquals(10, first.poisonPiercePercent,
          "changing caster pierce after cast must not alter in-flight missiles");
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativePoisonImmunitySuppressesStateButStillConsumesCastHitClaim() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, 1, 0, 0);
      int immune = monster(world, 5, 0, 100);
      int normal = monster(world, 0, 5, 0);
      cast(world, caster);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 14; i++) world.process();

      assertNull(poison(world, immune), "100% monster poison resistance is native immunity");
      assertNotNull(poison(world, normal));
      assertEquals(2, factory.created.get(0).sharedHitTargets.size,
          "immune targets are still claimed once by the radial cast");
    } finally {
      world.dispose();
    }
  }

  private static Skills.Entry skill() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_NOVA);
    assertNotNull(skill);
    return skill;
  }

  private static World world(RecordingFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true),
            new StateUpdater(), new MissileCollisionSystem(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
  }

  private static int player(World world, int level, int mastery, int pierce) {
    int id = world.create();
    CharData data = CharData.createRemote("poison-nova", (byte) Riiablo.NECROMANCER);
    data.setSkillLevel(SkillId.POISON_NOVA, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.setZero();
    Attributes attrs = attributes(1000);
    attrs.base().put(Stat.passive_pois_mastery, mastery);
    attrs.base().put(Stat.item_pierce_pois, pierce);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, int poisonResistance) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "poison-nova-target";
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    Attributes attrs = attributes(1000);
    attrs.base().put(Stat.poisonresist, poisonResistance);
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
    Skills.Entry skill = skill();
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, Engine.INVALID_ENTITY, Vector2.Zero,
        skill.srvdofunc, skill.cltdofunc));
  }

  private static UnitState poison(World world, int entityId) {
    return world.getMapper(UnitStates.class).get(entityId)
        .stateList.getState(StateId.POISON);
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> created = new ArrayList<>();

    @Override public int createMissile(
        int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entityId).position.set(position);
      world.getMapper(Velocity.class).create(entityId).velocity.set(direction).setLength(row.Vel);
      created.add(missile);
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
}
