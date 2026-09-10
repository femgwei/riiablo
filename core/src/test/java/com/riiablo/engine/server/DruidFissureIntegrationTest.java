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
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for the native SrvDo028 Fissure controller. */
class DruidFissureIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowUsesNovaCallbackAndFireMissile() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FISSURE);
    assertNotNull(skill);
    assertEquals(28, skill.srvdofunc);
    assertTrue(skill.srvmissilea != null && !skill.srvmissilea.isEmpty());
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(missile);
  }

  @Test
  void castCreatesNativeControllerWithFireSnapshot() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int druid = createDruid(world, 3);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.FISSURE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.FISSURE, Engine.INVALID_ENTITY, new Vector2(10, 0),
          skill.srvdofunc, skill.cltdofunc));

      assertEquals(1, factory.created.size());
      Missile controller = factory.created.get(0);
      assertTrue(controller.druidFissureController);
      for (Missile missile : factory.created) {
        assertEquals(SkillId.FISSURE, missile.skillId);
        assertEquals(3, missile.damageLevel);
        assertTrue(missile.damageSnapshot);
      }
    } finally {
      world.dispose();
    }
  }

  private static int createDruid(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("fissure", (byte) Riiablo.DRUID);
    data.setSkillLevel(SkillId.FISSURE, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 3);
    attrs.base().put(Stat.hitpoints, 1000);
    attrs.base().put(Stat.maxhp, 1000);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> created = new ArrayList<>();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(direction);
      created.add(missile);
      return entity;
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
