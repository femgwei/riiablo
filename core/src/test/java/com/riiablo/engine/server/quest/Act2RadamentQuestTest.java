package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
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

class Act2RadamentQuestTest extends RiiabloTest {
  @Test
  void nativeRecordTransitionsKeepSkillBookSeparateFromAtmaReward() {
    short record = 0;
    record = Act2RadamentQuest.start(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertEquals(Act2RadamentQuest.MESSAGE_EARLY,
        Act2RadamentQuest.selectAtmaMessage(record));

    record = Act2RadamentQuest.leaveTown(record);
    record = Act2RadamentQuest.enterSewers(record);
    assertEquals(Act2RadamentQuest.MESSAGE_SEWERS,
        Act2RadamentQuest.selectAtmaMessage(record));

    record = Act2RadamentQuest.completeObjective(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1));
    assertTrue(Act2RadamentQuest.needsSkillBook(record, false));
    assertFalse(Act2RadamentQuest.needsSkillBook(record, true));
    assertEquals(Act2RadamentQuest.MESSAGE_REWARD,
        Act2RadamentQuest.selectAtmaMessage(record));

    short claimed = Act2RadamentQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.CUSTOM1),
        "Atma must not consume or emulate the separately dropped skill book");
    assertEquals(-1, Act2RadamentQuest.selectAtmaMessage(claimed));
  }

  @Test
  void zoneAndAtmaMessagesAdvanceOnlyTheAct2Record() {
    Harness h = new Harness();
    try {
      CharData data = character("SewerHero");
      int player = h.createPlayer(data, D2LevelIds.LEVEL_LUTGHOLEIN);
      int atma = h.createNpc(MonsterType.ATMA);
      h.process();

      h.events.dispatch(NpcQuestMessageEvent.obtain(
          player, atma, Act2RadamentQuest.MESSAGE_INIT));
      assertTrue(NativeQuestRecord.has(record(data), NativeQuestRecord.STARTED));
      assertEquals(0, Short.toUnsignedInt(data.getQuests(Riiablo.ACT1)[1]));

      Map.Zone sewers = zone(D2LevelIds.LEVEL_SEWERSLVL1ACT2);
      h.setLevel(player, D2LevelIds.LEVEL_SEWERSLVL1ACT2);
      h.events.dispatch(ZoneChangeEvent.obtain(player, sewers));
      assertTrue(NativeQuestRecord.has(record(data), NativeQuestRecord.LEFT_TOWN));
      assertTrue(NativeQuestRecord.has(record(data), NativeQuestRecord.ENTERED_AREA));
    } finally {
      h.dispose();
    }
  }

  @Test
  void radamentCreditsLevelAndAct2PartyAndDropsOneBookPerCreditedPlayer() {
    Harness h = new Harness();
    try {
      CharData nearbyData = character("Nearby");
      CharData townPartyData = character("TownParty");
      CharData unrelatedData = character("Unrelated");
      int nearby = h.createPlayer(nearbyData, D2LevelIds.LEVEL_SEWERSLVL3ACT2);
      int townParty = h.createPlayer(townPartyData, D2LevelIds.LEVEL_LUTGHOLEIN);
      h.createPlayer(unrelatedData, D2LevelIds.LEVEL_ROCKYWASTE);
      assertTrue(h.parties.sendInvitation(nearby, townParty));
      assertTrue(h.parties.acceptInvitation(townParty));
      int radament = h.createRadament(D2LevelIds.LEVEL_SEWERSLVL3ACT2, true);
      h.process();

      h.events.dispatch(DeathEvent.obtain(nearby, radament));

      assertTrue(NativeQuestRecord.has(record(nearbyData),
          NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(record(townPartyData),
          NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(record(unrelatedData),
          NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(record(unrelatedData),
          NativeQuestRecord.COMPLETED_NOW));
      assertEquals(2, h.factory.items.size());
      assertEquals(Act2RadamentQuest.SKILL_BOOK_CODE, h.factory.items.get(0).code);

      h.events.dispatch(DeathEvent.obtain(nearby, radament));
      assertEquals(2, h.factory.items.size(), "duplicate death must not duplicate books");

      int atma = h.createNpc(MonsterType.ATMA);
      h.events.dispatch(NpcQuestMessageEvent.obtain(
          townParty, atma, Act2RadamentQuest.MESSAGE_REWARD));
      assertTrue(NativeQuestRecord.has(record(townPartyData),
          NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(record(townPartyData),
          NativeQuestRecord.REWARD_PENDING));
    } finally {
      h.dispose();
    }
  }

  @Test
  void ignoresRadamentIdentityOutsideNativeLevel() {
    Harness h = new Harness();
    try {
      CharData data = character("WrongLevel");
      int player = h.createPlayer(data, D2LevelIds.LEVEL_SEWERSLVL2ACT2);
      int radament = h.createRadament(D2LevelIds.LEVEL_SEWERSLVL2ACT2, true);
      h.process();
      h.events.dispatch(DeathEvent.obtain(player, radament));
      assertEquals(0, Short.toUnsignedInt(record(data)));
      assertEquals(0, h.factory.items.size());
    } finally {
      h.dispose();
    }
  }

  @Test
  void networkValidatorAcceptsOnlyCurrentAtmaBranch() {
    CharData data = character("Validator");
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.ATMA, data, Act2RadamentQuest.MESSAGE_INIT));
    assertFalse(Act2QuestMessageValidator.isAllowed(
        MonsterType.ATMA, data, Act2RadamentQuest.MESSAGE_REWARD));
    data.getQuests(Riiablo.ACT2)[Act2RadamentQuest.RECORD] =
        Act2RadamentQuest.completeObjective((short) 0);
    assertTrue(Act2QuestMessageValidator.isAllowed(
        MonsterType.ATMA, data, Act2RadamentQuest.MESSAGE_REWARD));
  }

  private static CharData character(String name) {
    return CharData.obtain().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2RadamentQuest.RECORD];
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
    final World world;

    Harness() {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(events, new Act2QuestSystem(), new BookGenerator(), factory);
      world = new World(builder.build()
          .register("partyManager", parties)
          .register("factory", factory)
          .register("map", new Map(0, 0)));
    }

    int createPlayer(CharData data, int levelId) {
      int id = world.create();
      world.getMapper(Player.class).create(id).data = data;
      world.getMapper(MapWrapper.class).create(id).zone = zone(levelId);
      return id;
    }

    void setLevel(int entityId, int levelId) {
      world.getMapper(MapWrapper.class).get(entityId).zone = zone(levelId);
    }

    int createNpc(int type) {
      MonStats.Entry stats = new MonStats.Entry();
      stats.hcIdx = type;
      stats.npc = true;
      stats.interact = true;
      int id = world.create();
      world.getMapper(Monster.class).create(id).monstats = stats;
      return id;
    }

    int createRadament(int levelId, boolean superUnique) {
      MonStats.Entry stats = new MonStats.Entry();
      stats.hcIdx = MonsterType.RADAMENT;
      int id = world.create();
      world.getMapper(Monster.class).create(id).monstats = stats;
      world.getMapper(MapWrapper.class).create(id).zone = zone(levelId);
      world.getMapper(Position.class).create(id).position.set(20f, 30f);
      if (superUnique) {
        world.getMapper(SuperUnique.class).create(id).id =
            D2SuperUniques.SUPERUNIQUE_RADAMENT;
      }
      return id;
    }

    void process() { world.process(); }
    void dispose() { world.dispose(); }
  }

  private static final class BookGenerator extends ItemGenerator {
    @Override
    public Item generate(String code) {
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
