package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Verifies authoritative XP, level-up and loot wiring from DeathEvent. */
class DeathRewardIntegrationTest extends RiiabloTest {
  @Test
  void monsterDeathAwardsExperienceLevelsUpDropsLootAndIsIdempotent() {
    MathUtils.random.setSeed(0xD34D1234L);
    EventSystem events = new EventSystem();
    ExperienceManager experience = new ExperienceManager();
    RewardFactory factory = new RewardFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, experience, new DeathRewardSystem(), new ItemGenerator(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      CharData data = characterAtLevelOne();
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();

      MonStats.Entry boss = new MonStats.Entry();
      boss.Id = "headless_reward_boss";
      boss.Level = new int[] {1, 1, 1};
      boss.Exp = new int[] {600, 600, 600};
      boss.boss = true;
      int monster = world.create();
      world.getMapper(Monster.class).create(monster).set(boss, new MonStats2.Entry());
      world.getMapper(Position.class).create(monster).position.set(20, 30);

      System.out.println("[DEATH_REWARD_CHAIN] phase=death killer=" + player
          + " victim=" + monster + " expBefore=0");
      events.dispatch(DeathEvent.obtain(player, monster));

      long experienceAfter = data.getStats().aggregate().getValue(Stat.experience, 0L);
      int levelAfter = data.getStats().aggregate().getValue(Stat.level, 0);
      float hpAfter = data.getStats().aggregate().getValue(Stat.hitpoints, 0f);
      int itemsAfter = factory.itemsCreated;
      assertEquals(600L, experienceAfter);
      assertEquals(2, levelAfter);
      assertEquals(2, data.level & 0xFF);
      assertEquals(1, data.getStats().base().getValue(Stat.newskills, 0),
          "level 1 -> 2 must grant one unspent skill point");
      assertEquals(1, data.getStats().aggregate().getValue(Stat.newskills, 0));
      assertTrue(hpAfter >= 60f && hpAfter < 100f,
          "level-up life must use fixed-point units, not become 600+");
      assertTrue(itemsAfter >= 4, "boss reward must create its guaranteed item drops");

      // Both melee and missile systems may observe a lethal result. Neither
      // progression nor loot may be granted twice for one victim entity.
      events.dispatch(DeathEvent.obtain(player, monster));
      assertEquals(experienceAfter,
          data.getStats().aggregate().getValue(Stat.experience, 0L));
      assertEquals(itemsAfter, factory.itemsCreated);
      System.out.println("[DEATH_REWARD_CHAIN] phase=summary experience=0->"
          + experienceAfter + " level=1->" + levelAfter + " hp=" + hpAfter
          + " items=" + itemsAfter + " duplicateIgnored=true");
    } finally {
      world.dispose();
    }
  }

  private static CharData characterAtLevelOne() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "RewardHero", Riiablo.AMAZON);
    data.getStats().base().put(Stat.level, 1);
    data.getStats().base().put(Stat.experience, 0);
    data.getStats().base().put(Stat.hitpoints, 60f);
    data.getStats().base().put(Stat.maxhp, 60f);
    data.getStats().base().put(Stat.mana, 20f);
    data.getStats().base().put(Stat.maxmana, 20f);
    data.getStats().base().put(Stat.stamina, 40f);
    data.getStats().base().put(Stat.maxstamina, 40f);
    data.getStats().reset();
    return data;
  }

  private static final class RewardFactory extends EntityFactory {
    int itemsCreated;

    @Override public int createItem(Item item, float x, float y) {
      itemsCreated++;
      int id = world.create();
      System.out.println("[DEATH_REWARD_CHAIN] phase=loot entity=" + id
          + " code=" + item.code + " quality=" + item.quality
          + " position=(" + x + "," + y + ")");
      return id;
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
