package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class Act2HoradricStaffQuestTest extends RiiabloTest {
  @Test
  void CainBranchesFollowNativeItemAndFlagOrder() {
    CharData data = character("CainStaff");
    add(data, Act2HoradricStaffQuest.HORADRIC_SCROLL);
    assertEquals(Act2HoradricStaffQuest.MESSAGE_SCROLL,
        Act2HoradricStaffQuest.selectCainMessage(record(data), data.getItems()));

    short record = Act2HoradricStaffQuest.acknowledgeScroll(record(data));
    add(data, Act2HoradricStaffQuest.HORADRIC_CUBE);
    assertEquals(Act2HoradricStaffQuest.MESSAGE_CUBE,
        Act2HoradricStaffQuest.selectCainMessage(record, data.getItems()));
    record = Act2HoradricStaffQuest.acknowledgeCube(record);
    add(data, Act2HoradricStaffQuest.VIPER_AMULET);
    assertEquals(Act2HoradricStaffQuest.MESSAGE_AMULET,
        Act2HoradricStaffQuest.selectCainMessage(record, data.getItems()));
    record = Act2HoradricStaffQuest.acknowledgeAmulet(record);
    add(data, Act2HoradricStaffQuest.STAFF_OF_KINGS);
    assertEquals(Act2HoradricStaffQuest.MESSAGE_STAFF,
        Act2HoradricStaffQuest.selectCainMessage(record, data.getItems()));

    add(data, Act2HoradricStaffQuest.HORADRIC_STAFF);
    assertEquals(Act2HoradricStaffQuest.MESSAGE_ASSEMBLED,
        Act2HoradricStaffQuest.selectCainMessage(record, data.getItems()));
    record = Act2HoradricStaffQuest.acknowledgeAssembly(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM6));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED),
        "A2Q2 assembly is not the Duriel/A2Q6 completion reward");
  }

  @Test
  void staffAndCubeChestDropsArePerPlayerAndIdempotent() {
    Harness h = new Harness();
    try {
      CharData first = character("First");
      CharData second = character("Second");
      int firstId = h.createPlayer(first, D2LevelIds.LEVEL_HALLSOFTHEDEADLVL3);
      h.createPlayer(second, D2LevelIds.LEVEL_HALLSOFTHEDEADLVL3);
      int chest = h.createChest(D2LevelIds.LEVEL_HALLSOFTHEDEADLVL3);
      h.process();

      h.events.dispatch(ObjectInteractionEvent.obtain(firstId, chest, 999, 39,
          com.riiablo.map.NativePresetObjectResolver.Kind.ORDINARY,
          com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ANIMATED_CONTAINER,
          true));
      assertEquals(2, h.factory.items.size());
      assertEquals(Act2HoradricStaffQuest.HORADRIC_CUBE, h.factory.items.get(0).code);

      h.events.dispatch(ObjectInteractionEvent.obtain(firstId, chest, 999, 39,
          com.riiablo.map.NativePresetObjectResolver.Kind.ORDINARY,
          com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.ANIMATED_CONTAINER,
          false));
      assertEquals(2, h.factory.items.size(), "opened chest must not duplicate quest items");
    } finally {
      h.dispose();
    }
  }

  @Test
  void CainScrollMessageConsumesOnlyTheScrollAndUpdatesA2Q2Record() {
    Harness h = new Harness();
    try {
      CharData data = character("CainMessage");
      add(data, Act2HoradricStaffQuest.HORADRIC_SCROLL);
      int player = h.createPlayer(data, D2LevelIds.LEVEL_LUTGHOLEIN);
      int cain = h.createNpc(MonsterType.DECKARDCAIN_ACT2);
      h.process();
      h.events.dispatch(NpcQuestMessageEvent.obtain(
          player, cain, Act2HoradricStaffQuest.MESSAGE_SCROLL));
      assertFalse(data.getItems().containsItemCode(Act2HoradricStaffQuest.HORADRIC_SCROLL));
      assertTrue(NativeQuestRecord.has(record(data), NativeQuestRecord.LEFT_TOWN));
    } finally {
      h.dispose();
    }
  }

  private static CharData character(String name) {
    return CharData.obtain().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
  }

  private static void add(CharData data, String code) {
    Item item = new Item();
    item.code = code;
    data.getItems().getItems().add(item);
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD];
  }

  private static Map.Zone zone(int levelId) {
    Levels.Entry level = new Levels.Entry();
    level.Id = levelId;
    Map.Zone zone = new Map.Zone();
    zone.level = level;
    return zone;
  }

  private static final class Harness {
    final EventSystem events = new EventSystem();
    final PartyManager parties = new PartyManager();
    final RecordingFactory factory = new RecordingFactory();
    final World world = new World(new WorldConfigurationBuilder()
        .with(events, new Act2QuestSystem(), new Act2HoradricStaffDropSystem(),
            new BookGenerator(), factory)
        .build()
        .register("partyManager", parties)
        .register("factory", factory)
        .register("map", new Map(0, 0)));

    int createPlayer(CharData data, int levelId) {
      int id = world.create();
      world.getMapper(Player.class).create(id).data = data;
      world.getMapper(MapWrapper.class).create(id).zone = zone(levelId);
      return id;
    }

    int createChest(int levelId) {
      int id = world.create();
      world.getMapper(MapWrapper.class).create(id).zone = zone(levelId);
      world.getMapper(Position.class).create(id).position.set(10f, 10f);
      return id;
    }

    int createNpc(int type) {
      com.riiablo.codec.excel.MonStats.Entry stats =
          new com.riiablo.codec.excel.MonStats.Entry();
      stats.hcIdx = type;
      int id = world.create();
      world.getMapper(com.riiablo.engine.server.component.Monster.class)
          .create(id).monstats = stats;
      return id;
    }

    void process() { world.process(); }
    void dispose() { world.dispose(); }
  }

  private static final class BookGenerator extends ItemGenerator {
    @Override public Item generate(String code) {
      Item item = new Item();
      item.code = code;
      return item;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    final List<Item> items = new ArrayList<>();
    @Override public int createItem(Item item, float x, float y) {
      items.add(item);
      return world.create();
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
