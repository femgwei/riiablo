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

/** Headless contract for Meteor's delayed center and persistent fire fields. */
class SorceressMeteorIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsUseMeteorCenterSrvHit14AndMeteorFire() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.METEOR);
    assertNotNull(skill);
    assertEquals(28, skill.srvdofunc);
    assertEquals("meteorcenter", skill.srvmissilea);
    Missiles.Entry center = Riiablo.files.Missiles.get("meteorcenter");
    Missiles.Entry fire = Riiablo.files.Missiles.get("meteorfire");
    assertNotNull(center);
    assertNotNull(fire);
    assertEquals(1, center.pSrvDoFunc);
    assertEquals(14, center.pSrvHitFunc);
    assertEquals(60, center.Range);
    assertEquals("meteorfire", center.HitSubMissile[0]);
    assertEquals(3, fire.pSrvDmgFunc);
    assertEquals(90, fire.Range);
    assertEquals(30, skill.Param[2]);
    assertEquals(15, skill.Param[3]);
  }

  @Test
  void castCreatesOneDelayedCenterThenEighteenPersistentFireFields() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      int target = monster(world, 3f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.METEOR);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(3f, 0f), skill.srvdofunc, skill.cltdofunc));
      assertEquals(1, factory.count("meteorcenter"));
      Missile center = factory.first("meteorcenter");
      assertNotNull(center);
      assertTrue(center.meteorCenter);
      assertEquals(60, center.nativeLifetimeFrames);

      world.setDelta(1f / 25f);
      for (int i = 0; i < 59; i++) world.process();
      assertEquals(0, factory.count("meteorfire"));
      world.process();
      assertEquals(18, factory.count("meteorfire"),
          "SrvHit14 uses all eighteen native offsets at HitPar2=1");
      assertTrue(world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed() < 200f,
          "SrvHit14 applies the immediate fire packet before creating fields");
      for (Missile fire : factory.named("meteorfire")) {
        assertTrue(fire.persistent);
        assertTrue(fire.damageSnapshot);
        assertEquals(30, fire.remainingFrames);
      }
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("meteor", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.METEOR, level);
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
    attrs.base().put(Stat.fireresist, 0);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> missiles = new ArrayList<>();
    int count(String name) { return named(name).size(); }
    ArrayList<Missile> named(String name) {
      ArrayList<Missile> result = new ArrayList<>();
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) result.add(missile);
      }
      return result;
    }
    Missile first(String name) {
      for (Missile missile : missiles) {
        if (missile.missile != null && name.equalsIgnoreCase(missile.missile.Missile)) return missile;
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
      world.getMapper(Velocity.class).create(entityId).velocity.set(direction).setLength(row.Vel);
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
