package com.riiablo.engine.server.quest;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act3QuestObjectPrewarmTest {
  @Test void missingChestBlocksArrivalAndDoesNotDuplicateBeforeFirstTick() throws Exception {
    Map map = new Map(12345, 0);
    Map.Zone zone = new Map.Zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = com.d2moo.common.drlg.D2LevelIds.LEVEL_SPIDERCAVERN;
    java.lang.reflect.Field flags = Map.Zone.class.getDeclaredField("flags");
    flags.setAccessible(true);
    flags.set(zone, new byte[0]);
    zone.setNativeTileGrid(new TileGrid(10, 10), 0);
    Act3QuestSystem quests = new Act3QuestSystem();
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), quests, new ObjectCollisionUpdater(), factory).build()
        .register("map", map).register("factory", factory));
    try {
      quests.prepareZoneQuestObjects(zone);
      assertEquals(1, factory.created);
      Map.Zone deferred = new Map.Zone();
      deferred.level = zone.level;
      deferred.getNativeObjects().add(new Map.NativeObject(Act3KhalimQuest.KHALIM_CHEST2,
          0, 10, 10, false, false));
      quests.prepareZoneQuestObjects(deferred);
      assertEquals(1, factory.created, "Deferred native chest must not get a room-center duplicate");
      assertTrue(map.isBlockedForPath(zone, 25, 25,
          NativeGroundPathProfile.PLAYER.terrainFlags, 2, false));
      quests.prepareZoneQuestObjects(zone);
      assertEquals(1, factory.created, "Prewarm is idempotent before ECS subscriptions update");
      world.process();
      quests.prepareZoneQuestObjects(zone);
      assertEquals(1, factory.created);
    } finally { world.dispose(); }
  }

  static final class RecordingFactory extends EntityFactory {
    int created;
    @Override public int createStaticObjectByClassId(int classId, float x, float y) {
      created++;
      int id = world.create();
      com.riiablo.codec.excel.Objects.Entry base = new com.riiablo.codec.excel.Objects.Entry();
      base.Id = classId;
      base.SizeX = base.SizeY = 3;
      base.HasCollision = new boolean[] {true};
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = base;
      world.getMapper(Position.class).create(id).position.set(x, y);
      return id;
    }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
