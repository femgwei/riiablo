package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Verifies that native Act V town UNIT_MONSTER records become ECS NPCs. */
class Act5TownNpcEntityProjectionTest {
  @Test
  void materializesEveryNativeTownNpcAtItsProjectedPosition() {
    Map.Zone town = new Map.Zone();
    town.town = true;
    town.setPosition(100, 200);
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.LARZUK,
        0, 12, 18, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.DREHYA,
        0, 20, 21, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.MALAH,
        0, 28, 24, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.NIHLATHAK,
        0, 35, 26, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.QUAL_KEHK,
        0, 42, 30, -1, null, false));

    RecordingFactory factory = new RecordingFactory();
    new TestMapManager(factory).createEntities(town);

    assertEquals(5, factory.monsterIds.length);
    assertEquals(MonsterType.LARZUK, factory.monsterIds[0]);
    assertEquals(MonsterType.DREHYA, factory.monsterIds[1]);
    assertEquals(MonsterType.MALAH, factory.monsterIds[2]);
    assertEquals(MonsterType.NIHLATHAK, factory.monsterIds[3]);
    assertEquals(MonsterType.QUAL_KEHK, factory.monsterIds[4]);
    assertEquals(112f, factory.x[0]);
    assertEquals(218f, factory.y[0]);
    assertEquals(142f, factory.x[4]);
    assertEquals(230f, factory.y[4]);
    assertEquals(5, town.entities.size);
  }

  private static final class TestMapManager extends MapManager {
    TestMapManager(EntityFactory factory) {
      this.factory = factory;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    int[] monsterIds = new int[0];
    float[] x = new float[0];
    float[] y = new float[0];

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }

    @Override public int createMonster(int monster, float x, float y) {
      int oldSize = monsterIds.length;
      int newSize = oldSize + 1;
      int[] ids = new int[newSize];
      float[] xs = new float[newSize];
      float[] ys = new float[newSize];
      System.arraycopy(monsterIds, 0, ids, 0, oldSize);
      System.arraycopy(this.x, 0, xs, 0, oldSize);
      System.arraycopy(this.y, 0, ys, 0, oldSize);
      ids[oldSize] = monster;
      xs[oldSize] = x;
      ys[oldSize] = y;
      monsterIds = ids;
      this.x = xs;
      this.y = ys;
      return oldSize + 1;
    }

    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
