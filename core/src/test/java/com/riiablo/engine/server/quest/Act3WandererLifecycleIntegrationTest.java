package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.codec.excel.Objects;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class Act3WandererLifecycleIntegrationTest extends RiiabloTest {
  @Test void wandererMarkerRunsOnTheSimulationClockAndSpawnsOnlyOnce() {
    RecordingFactory factory = new RecordingFactory();
    Act3QuestSystem quests = new Act3QuestSystem();
    World world = new World(new WorldConfigurationBuilder().with(quests, factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int id = world.create();
      Objects.Entry marker = new Objects.Entry();
      marker.InitFn = 43;
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = marker;
      world.getMapper(Position.class).create(id).position.set(10, 20);
      world.setDelta(1f / 25f);
      world.process();
      world.process();
      assertEquals(1, factory.monsters);
      assertEquals(17f, factory.spawnX);
      assertEquals(20f, factory.spawnY);
      assertTrue(quests.isEnabled());
    } finally { world.dispose(); }
  }

  @Test void afterglowOpeningAndExpiryAreDrivenByTwentySevenSimulationFrames() {
    Act3QuestSystem quests = new Act3QuestSystem();
    Map map = new Map(0, 0);
    Map.Zone zone = mock(Map.Zone.class);
    World world = new World(new WorldConfigurationBuilder().with(quests).build().register("map", map));
    try {
      int id = world.create();
      Objects.Entry row = new Objects.Entry();
      row.InitFn = 59;
      com.riiablo.engine.server.component.Object object = world.getMapper(
          com.riiablo.engine.server.component.Object.class).create(id);
      object.base = row;
      object.mode = Engine.Object.MODE_NU;
      world.getMapper(Position.class).create(id);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(Engine.Object.MODE_OP, object.mode);
      for (int i = 0; i < 26; i++) world.process();
      assertEquals(Engine.Object.MODE_OP, object.mode);
      world.process();
      assertEquals(Engine.Object.MODE_ON, object.mode);
      world.process();
      assertEquals(Engine.Object.MODE_ON, object.mode);
    } finally { world.dispose(); }
  }
  static class RecordingFactory extends EntityFactory {
    int monsters, missiles;
    float spawnX, spawnY;
    @Override public int createPlayer(com.riiablo.save.CharData data, com.badlogic.gdx.math.Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { monsters++; spawnX=x; spawnY=y; return 42; }
    @Override public int createWarp(int id, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, com.badlogic.gdx.math.Vector2 a, com.badlogic.gdx.math.Vector2 p) { missiles++; return -1; }
  }
}
