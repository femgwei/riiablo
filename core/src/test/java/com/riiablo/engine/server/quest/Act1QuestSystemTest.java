package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class Act1QuestSystemTest extends RiiabloTest {
  @Test
  void keepsPlayerAndDifficultyRecordsIndependent() {
    Harness harness = new Harness();
    try {
      CharData first = character("First", Riiablo.NORMAL);
      CharData second = character("Second", Riiablo.NORMAL);
      int firstId = harness.createPlayer(first);
      harness.createPlayer(second);
      int akara = harness.createAkara();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          firstId, akara, Act1DenOfEvilQuest.MESSAGE_INIT));

      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));
      assertEquals(0, Short.toUnsignedInt(record(second)));

      first.diff = Riiablo.NIGHTMARE;
      assertEquals(0, Short.toUnsignedInt(record(first)));
      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          firstId, akara, Act1DenOfEvilQuest.MESSAGE_INIT));
      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));

      first.diff = Riiablo.NORMAL;
      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void completesOnlyAfterEveryCurrentDenMonsterIsDead() {
    Harness harness = new Harness();
    try {
      CharData data = character("DenHero", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      int first = harness.createDenMonster(10f);
      int second = harness.createDenMonster(10f);
      harness.process();

      harness.setLife(first, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, first));
      assertFalse(NativeQuestRecord.has(record(data), NativeQuestRecord.PRIMARY_GOAL_DONE));

      // Fallen Shaman resurrection removes Corpse and restores life. The
      // resurrected unit must be counted again instead of staying in a dead-ID set.
      harness.setLife(first, 10f);
      harness.world.getMapper(Corpse.class).remove(first);
      assertEquals(2, harness.quests.countLivingMonsters(D2LevelIds.LEVEL_DENOFEVIL));

      harness.setLife(second, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, second));
      assertFalse(NativeQuestRecord.has(record(data), NativeQuestRecord.PRIMARY_GOAL_DONE));

      harness.setLife(first, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, first));
      short completed = record(data);
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.COMPLETED_NOW));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void grantsAkaraSkillPointOnlyOnce() {
    Harness harness = new Harness();
    try {
      CharData data = character("RewardHero", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] =
          Act1DenOfEvilQuest.completeObjective((short) 0);
      int player = harness.createPlayer(data);
      int akara = harness.createAkara();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, akara, Act1DenOfEvilQuest.MESSAGE_SUCCESS));
      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, akara, Act1DenOfEvilQuest.MESSAGE_SUCCESS));

      short claimed = record(data);
      assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
      assertEquals(1, data.getStats().base().getValue(Stat.newskills, 0));
      assertEquals(1, data.getStats().aggregate().getValue(Stat.newskills, 0));
    } finally {
      harness.dispose();
    }
  }

  private static CharData character(String name, int difficulty) {
    CharData data = CharData.obtain().set(difficulty, false, name, Riiablo.AMAZON);
    data.getStats().base().put(Stat.newskills, 0);
    data.getStats().reset();
    return data;
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
  }

  private static final class Harness {
    final EventSystem events = new EventSystem();
    final Act1QuestSystem quests = new Act1QuestSystem();
    final World world = new World(new WorldConfigurationBuilder()
        .with(events, quests)
        .build());

    int createPlayer(CharData data) {
      int entityId = world.create();
      world.getMapper(Player.class).create(entityId).data = data;
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = data.getStats();
      return entityId;
    }

    int createAkara() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.AKARA;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    int createDenMonster(float life) {
      Levels.Entry level = new Levels.Entry();
      level.Id = D2LevelIds.LEVEL_DENOFEVIL;
      Map.Zone zone = new Map.Zone();
      zone.level = level;

      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId);
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, life);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = attrs;
      return entityId;
    }

    void setLife(int entityId, float life) {
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(entityId).attrs;
      attrs.get(Stat.hitpoints).set(life);
    }

    void process() {
      world.process();
    }

    void dispose() {
      world.dispose();
    }
  }
}
