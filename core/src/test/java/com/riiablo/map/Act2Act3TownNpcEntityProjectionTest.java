package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/**
 * Verifies the native town UNIT_MONSTER projection for Acts II and III.
 *
 * <p>The test supplies the records that D2MOO's town export emits and checks
 * the shared MapManager path used by both the Act II and Act III bridges.</p>
 */
class Act2Act3TownNpcEntityProjectionTest {
  private static final int[] ACT2_NPCS = {
      MonsterType.WARRIV2,
      MonsterType.ATMA,
      MonsterType.DROGNAN,
      MonsterType.FARA,
      MonsterType.GREIZ,
      MonsterType.ELZIX,
      MonsterType.GEGLASH,
      MonsterType.JERHYN,
      MonsterType.LYSANDER,
      MonsterType.MESHIF1,
      MonsterType.DECKARDCAIN_ACT2
  };

  private static final int[] ACT3_NPCS = {
      MonsterType.ASHEARA,
      MonsterType.HRATLI,
      MonsterType.ALKOR,
      MonsterType.ORMUS,
      MonsterType.CAIN3,
      MonsterType.MESHIF2,
      MonsterType.NATALYA
  };

  @Test
  void materializesAllAct2AndAct3TownNpcsWithoutCrossZoneLeakage() {
    Map.Zone act2Town = town(100, 200, ACT2_NPCS);
    Map.Zone act3Town = town(400, 600, ACT3_NPCS);
    RecordingFactory factory = new RecordingFactory();
    TestMapManager manager = new TestMapManager(factory);

    manager.createEntities(act2Town);
    assertEquals(ACT2_NPCS.length, act2Town.entities.size);
    assertArrayEquals(ACT2_NPCS, factory.ids());

    manager.createEntities(act3Town);
    assertEquals(ACT3_NPCS.length, act3Town.entities.size);
    assertEquals(ACT2_NPCS.length + ACT3_NPCS.length, factory.ids().length);
    for (int i = 0; i < ACT3_NPCS.length; i++) {
      assertEquals(ACT3_NPCS[i], factory.ids()[ACT2_NPCS.length + i]);
    }
    assertEquals(112f, factory.x()[0]);
    assertEquals(630f, factory.y()[factory.y().length - 1]);
  }

  @Test
  void nativeAct3TownExportSuppressesOnlyMatchingDynamicPresetUnits() {
    Map.Zone town = new Map.Zone();
    town.town = true;
    town.replaceNativeTownMonsters("Act3/Town/KurastTown.ds1",
        new com.badlogic.gdx.utils.Array<Map.NativeMonster>() {{
          add(new Map.NativeMonster(0, MonsterType.ASHEARA, 0, 10, 12, -1, null, false));
        }});

    assertEquals(1, town.getNativeMonsters().size);
    assertEquals("Act3/Town/KurastTown.ds1", town.nativeTownMonsterSource());
    assertEquals(false, MapManager.shouldCreateCompatibilityPresetUnit(
        town, "data\\global\\tiles\\act3\\town\\kurasttown.ds1",
        DS1.Object.DYNAMIC_TYPE));
    assertEquals(true, MapManager.shouldCreateCompatibilityPresetUnit(
        town, "Act3/Town/KurastTown.ds1", DS1.Object.STATIC_TYPE));
    assertEquals(true, MapManager.shouldCreateCompatibilityPresetUnit(
        town, "Act3/Town/other.ds1", DS1.Object.DYNAMIC_TYPE));
  }

  private static Map.Zone town(int x, int y, int[] npcIds) {
    Map.Zone town = new Map.Zone();
    town.town = true;
    town.setPosition(x, y);
    for (int i = 0; i < npcIds.length; i++) {
      town.getNativeMonsters().add(new Map.NativeMonster(0, npcIds[i],
          0, 12 + i * 3, 18 + i * 2, -1, null, false));
    }
    return town;
  }

  private static final class TestMapManager extends MapManager {
    TestMapManager(EntityFactory factory) {
      this.factory = factory;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    private int[] monsterIds = new int[0];
    private float[] xs = new float[0];
    private float[] ys = new float[0];

    int[] ids() { return monsterIds; }
    float[] x() { return xs; }
    float[] y() { return ys; }

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }

    @Override public int createMonster(int monster, float x, float y) {
      int oldSize = monsterIds.length;
      int newSize = oldSize + 1;
      int[] ids = new int[newSize];
      float[] nextX = new float[newSize];
      float[] nextY = new float[newSize];
      System.arraycopy(monsterIds, 0, ids, 0, oldSize);
      System.arraycopy(xs, 0, nextX, 0, oldSize);
      System.arraycopy(ys, 0, nextY, 0, oldSize);
      ids[oldSize] = monster;
      nextX[oldSize] = x;
      nextY[oldSize] = y;
      monsterIds = ids;
      xs = nextX;
      ys = nextY;
      return newSize;
    }

    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
