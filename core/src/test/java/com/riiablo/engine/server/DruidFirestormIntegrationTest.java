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
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for D2MOO SrvDo117 Firestorm multi-stream creation. */
class DruidFirestormIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowUsesFirestormCallback() {
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRESTORM);
    assertNotNull(skill);
    assertEquals(117, skill.srvdofunc);
    assertTrue(skill.srvmissilea != null && !skill.srvmissilea.isEmpty(),
        "Firestorm must provide a server missile row");
  }

  @Test
  void castCreatesMultipleAuthoritativeFirestormStreams() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int druid = createDruid(world, 5);
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRESTORM);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.FIRESTORM, Engine.INVALID_ENTITY, new Vector2(12, 0),
          skill.srvdofunc, skill.cltdofunc));

      assertTrue(factory.created.size() >= 2,
          "native Firestorm emits more than one stream, actual=" + factory.created.size()
              + " srv=" + skill.srvdofunc + " id=" + skill.Id
              + " missile=" + skill.srvmissilea);
      for (Missile missile : factory.created) {
        assertEquals(skill.Id, missile.skillId);
        assertEquals(5, missile.damageLevel);
        assertTrue(missile.damageSnapshot,
            "Firestorm damage snapshot missing for EType=" + skill.EType
                + " EMin=" + skill.EMin + " EMax=" + skill.EMax);
      }
    } finally {
      world.dispose();
    }
  }

  private static int createDruid(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("firestorm", (byte) Riiablo.DRUID);
    data.setSkillLevel(SkillId.FIRESTORM, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 5);
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
