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
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for the native Frozen Orb SrvDo15/SrvHit29/SrvDo16 chain. */
class SorceressFrozenOrbIntegrationTest extends RiiabloTest {
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
  void castEmitsAuthoritativeBoltsAndImpactFansSixteenNovaShards() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      int target = monster(world, 0.4f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROZEN_ORB);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(2f, 0f), skill.srvdofunc, skill.cltdofunc));

      assertEquals(1, factory.count("frozenorb"));
      Missile root = factory.first("frozenorb");
      assertNotNull(root);
      assertTrue(root.frozenOrbController);

      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("frozenorbbolt"),
          "SrvDo15 emits one bolt on the native one-frame cadence");
      assertTrue(factory.first("frozenorbbolt").damageSnapshot,
          "Frozen Orb bolt receives the skill cold packet at spawn");
      assertEquals(16, factory.count("frozenorbnova"),
          "SrvHit29 emits one nova every four entries in the native 64-point ring");
      for (Missile nova : factory.named("frozenorbnova")) {
        assertTrue(nova.frozenOrbNova);
        assertTrue(nova.damageSnapshot);
        assertEquals(25, nova.nativeLifetimeFrames);
      }
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
    final ArrayList<Missile> missiles = new ArrayList<>();

    int count(String name) {
      return named(name).size();
    }

    ArrayList<Missile> named(String name) {
      ArrayList<Missile> result = new ArrayList<>();
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) {
          result.add(missile);
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
      world.getMapper(Velocity.class).create(entityId).velocity
          .set(direction).setLength(row.Vel);
      missiles.add(missile);
      return entityId;
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
