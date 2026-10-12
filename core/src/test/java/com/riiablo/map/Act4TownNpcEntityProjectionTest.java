package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Verifies that native Act IV town UNIT_MONSTER records become ECS NPCs. */
class Act4TownNpcEntityProjectionTest {
  @Test
  void materializesEveryNativeTownNpcAtItsProjectedPosition() {
    Map.Zone town = new Map.Zone();
    town.town = true;
    town.setPosition(100, 200);
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.HALBU,
        0, 12, 18, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.JAMELLA,
        0, 20, 21, -1, null, false));
    town.getNativeMonsters().add(new Map.NativeMonster(0, MonsterType.TYRAEL2,
        0, 28, 24, -1, null, false));

    RecordingFactory factory = new RecordingFactory();
    new TestMapManager(factory).createEntities(town);

    assertEquals(3, factory.monsterIds.length);
    assertEquals(MonsterType.HALBU, factory.monsterIds[0]);
    assertEquals(MonsterType.JAMELLA, factory.monsterIds[1]);
    assertEquals(MonsterType.TYRAEL2, factory.monsterIds[2]);
    assertEquals(112f, factory.x[0]);
    assertEquals(218f, factory.y[0]);
    assertEquals(128f, factory.x[2]);
    assertEquals(224f, factory.y[2]);
    assertEquals(3, town.entities.size);
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
