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
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for the native Blizzard center/child missile chain. */
class SorceressBlizzardIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsUseCenterSrvDo10AndBlizzardChildSrvDo03() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLIZZARD);
    assertNotNull(skill);
    assertEquals(28, skill.srvdofunc);
    assertEquals("blizzardcenter", skill.srvmissilea);
    Missiles.Entry center = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(center);
    assertEquals(10, center.pSrvDoFunc);
    assertEquals(100, center.Range);
    assertEquals("blizzard1", center.SubMissile[0]);
    Missiles.Entry child = Riiablo.files.Missiles.get(center.SubMissile[0]);
    assertNotNull(child);
    assertEquals(3, child.pSrvDoFunc);
    assertEquals(9, child.Range);
  }

  @Test
  void centerEmitsOneChildOnEachNativeInterval() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLIZZARD);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, Engine.INVALID_ENTITY, new Vector2(8f, 3f),
          skill.srvdofunc, skill.cltdofunc));

      assertEquals(1, factory.count("blizzardcenter"));
      Missile center = factory.first("blizzardcenter");
      assertNotNull(center);
      assertTrue(center.blizzardCenter);
      assertEquals(100, center.remainingFrames);

      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.count("blizzard1"),
          "native remaining-frame phase emits at 100 % calc2 == 0");
      Missile child = factory.first("blizzard1");
      assertNotNull(child);
      assertTrue(child.damageSnapshot);
      assertTrue(child.damage.get(com.riiablo.attributes.Stat.coldmaxdam).asInt() > 0);

      int interval = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, 1));
      for (int i = 0; i < interval; i++) world.process();
      assertEquals(2, factory.count("blizzard1"),
          "the next child is emitted on the native calc2 cadence");
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("blizzard", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.BLIZZARD, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0f, 0f);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, 100f);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return id;
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> missiles = new ArrayList<>();

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

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
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
