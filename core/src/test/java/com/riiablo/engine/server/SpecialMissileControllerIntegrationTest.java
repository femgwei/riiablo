package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class SpecialMissileControllerIntegrationTest extends RiiabloTest {
  private static World world(RecordingFactory factory) {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    world.setDelta(1f / 25f);
    return world;
  }

  private static int maker(World world, Missiles.Entry row, int frames, int skillId) {
    int owner = world.create();
    world.getMapper(Position.class).create(owner);
    int id = world.create();
    Missile maker = world.getMapper(Missile.class).create(id).set(row, Vector2.Zero, frames);
    maker.skillId = skillId;
    maker.ownerId = owner;
    maker.damageLevel = 4;
    world.getMapper(Position.class).create(id);
    world.getMapper(Velocity.class).create(id).velocity.set(1000, 0);
    return id;
  }

  @Test
  void firewallEmitsAtPreStepPositionsWithoutRangeDistanceClamping() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int id = maker(world, Riiablo.files.Missiles.get("firewallmaker"), 3, SkillId.FIRE_WALL);
      world.getMapper(Missile.class).get(id).fireWallMaker = true;
      for (int tick = 0; tick < 3; tick++) world.process();
      assertEquals(3, factory.origins.size());
      for (int tick = 0; tick < 3; tick++) {
        assertEquals(tick * 40f, factory.origins.get(tick).x, 0.0001f);
        assertEquals(4, factory.levels.get(tick).intValue());
      }
      world.process(); // flush native expiration
      assertEquals(3, factory.origins.size(), "no emissions after native frame lifetime");
      Missile last = world.getMapper(Missile.class).get(factory.ids.get(2));
      assertTrue(last.persistent, "the ground field retains its separate lifetime policy");
      assertEquals(Missile.nativeRangeFrames(last.missile, 4) - 1, last.remainingFrames);
    } finally {
      world.dispose();
    }
  }

  @Test
  void vinesEmitAtElapsedZeroAndPropagateSkillLevelLifetime() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      Missiles.Entry row = new Missiles.Entry();
      row.pSrvDoFunc = 26;
      row.Vel = 1000;
      row.Param = new int[] {3};
      row.SubMissile = new String[] {"plague vines trail"};
      maker(world, row, 5, -1);
      world.process();
      assertEquals(1, factory.origins.size());
      assertEquals(Vector2.Zero, factory.origins.get(0));
      Missile trail = world.getMapper(Missile.class).get(factory.ids.get(0));
      assertEquals(Missile.nativeRangeFrames(trail.missile, 4), trail.nativeLifetimeFrames);
      world.process();
      world.process();
      assertEquals(1, factory.origins.size());
      world.process();
      assertEquals(2, factory.origins.size());
      assertEquals(120f, factory.origins.get(1).x, 0.0001f);
      assertEquals(4, factory.levels.get(1).intValue());
    } finally {
      world.dispose();
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Vector2> origins = new ArrayList<>();
    final ArrayList<Integer> levels = new ArrayList<>();
    final ArrayList<Integer> ids = new ArrayList<>();

    @Override public int createMissile(Missiles.Entry row, Vector2 direction,
        Vector2 origin, int ownerId, int skillLevel) {
      int id = world.create();
      Missile child = world.getMapper(Missile.class).create(id)
          .set(row, origin, row.Range).setOwner(ownerId);
      child.nativeLifetimeFrames = Missile.nativeRangeFrames(row, skillLevel);
      world.getMapper(Position.class).create(id).position.set(origin);
      world.getMapper(Velocity.class).create(id).velocity.set(direction).setLength(row.Vel);
      ids.add(id);
      origins.add(new Vector2(origin));
      levels.add(skillLevel);
      return id;
    }

    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
