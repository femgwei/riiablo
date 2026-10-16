package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MonsterRewardState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMemberSyncSystem;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.Map;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Verifies authoritative XP, level-up and loot wiring from DeathEvent. */
class DeathRewardIntegrationTest extends RiiabloTest {
  @Test
  void monsterDeathAwardsExperienceLevelsUpDropsLootAndIsIdempotent() {
    MathUtils.random.setSeed(0xD34D1234L);
    EventSystem events = new EventSystem();
    PartyManager parties = new PartyManager();
    ExperienceManager experience = new ExperienceManager(parties);
    RewardFactory factory = new RewardFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, experience, new DeathRewardSystem(parties), new ItemGenerator(), factory)
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
      // Deliberately disagree with the entity stats below. Native death
      // consumers must use STAT_LEVEL/STAT_EXPERIENCE, not recompute from this
      // row and thereby lose spawn-time modifiers.
      boss.Level = new int[] {50, 50, 50};
      boss.Exp = new int[] {999, 999, 999};
      boss.boss = true;
      int monster = world.create();
      world.getMapper(Monster.class).create(monster).set(boss, new MonStats2.Entry());
      world.getMapper(Position.class).create(monster).position.set(20, 30);
      Attributes monsterAttrs = Attributes.obtainStandard();
      monsterAttrs.base().put(Stat.level, 1);
      monsterAttrs.base().put(Stat.experience, 600);
      monsterAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(monster).attrs = monsterAttrs;
      int unownedKiller = world.create();

      System.out.println("[DEATH_REWARD_CHAIN] phase=death killer=" + player
          + " victim=" + monster + " expBefore=0");
      // Environmental objects can report a lethal event without a player
      // owner. That event must not claim the monster's reward slot; the later
      // authoritative player event still owns XP and loot.
      events.dispatch(DeathEvent.obtain(unownedKiller, monster));
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
      assertEquals(5, data.getStats().base().getValue(Stat.statpts, 0),
          "level 1 -> 2 must grant the native five unspent stat points");
      assertEquals(5, data.getStats().aggregate().getValue(Stat.statpts, 0));
      assertTrue(hpAfter >= 60f && hpAfter < 100f,
          "level-up life must use fixed-point units, not become 600+");
      assertTrue(itemsAfter >= 4, "boss reward must create its guaranteed item drops");
      MonsterRewardState rewardState = world.getMapper(MonsterRewardState.class).get(monster);
      assertTrue(rewardState.hasSnapshot(), "death must capture immutable reward context");
      assertEquals(player, rewardState.snapshotOwnerId());
      assertEquals(1, rewardState.snapshotMonsterLevel());
      assertEquals(600, rewardState.snapshotExperience());

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

  @ParameterizedTest
  @EnumSource(value = SimulationMode.class, names = {"LOCAL", "DEDICATED_SERVER"})
  void explicitPartyBindingsKeepRewardsIsolatedAndObserveLiveMembership(SimulationMode mode) {
    try (PartyWorld joined = new PartyWorld(mode, null);
         PartyWorld solo = new PartyWorld(mode, null)) {
      assertEquals(joined.owner, solo.owner, "entity ids deliberately overlap across worlds");
      assertEquals(joined.member, solo.member);
      joined.join(joined.parties);
      joined.world.process();
      solo.world.process();

      int firstVictim = joined.kill();
      assertEquals(67L, joined.experience(joined.owner));
      assertEquals(67L, joined.experience(joined.member));
      assertArrayEquals(new int[] {joined.owner, joined.member}, joined.rewards(firstVictim).snapshotLootPlayers());
      assertArrayEquals(new int[] {joined.owner, joined.member}, joined.rewards(firstVictim).snapshotEligiblePlayers());
      assertEquals(0L, solo.experience(solo.owner));

      int soloVictim = solo.kill();
      assertEquals(100L, solo.experience(solo.owner));
      assertEquals(0L, solo.experience(solo.member));
      assertArrayEquals(new int[] {solo.owner}, solo.rewards(soloVictim).snapshotLootPlayers());
      assertEquals(67L, joined.experience(joined.owner));

      int drops = joined.factory.itemsCreated;
      joined.events.dispatch(DeathEvent.obtain(joined.owner, firstVictim));
      assertEquals(67L, joined.experience(joined.owner));
      assertEquals(drops, joined.factory.itemsCreated, "duplicate death remains idempotent");

      joined.parties.removePlayer(joined.member);
      int nextVictim = joined.kill();
      assertEquals(167L, joined.experience(joined.owner));
      assertEquals(67L, joined.experience(joined.member));
      assertArrayEquals(new int[] {joined.owner}, joined.rewards(nextVictim).snapshotLootPlayers());
    }
  }

  @Test
  void namedAliasCannotReplaceExplicitRewardAndSnapshotDependencies() {
    PartyManager wrongAlias = new PartyManager();
    try (PartyWorld fixture = new PartyWorld(SimulationMode.DEDICATED_SERVER, wrongAlias)) {
      fixture.join(fixture.parties);
      fixture.join(wrongAlias);
      wrongAlias.updateMember(fixture.owner, 42, 10, 10, 5, 5, 2, 0, 0, true);
      fixture.world.process();
      assertEquals(1, fixture.parties.getPartyForPlayer(fixture.owner).getMember(fixture.owner).level);
      assertEquals(42, wrongAlias.getPartyForPlayer(fixture.owner).getMember(fixture.owner).level);
      // Keep the unrelated alias's owner solo. Using it would award 100/0,
      // rather than the explicit manager's native two-member 67/67 split.
      wrongAlias.removePlayer(fixture.member);
      int victim = fixture.kill();
      assertEquals(67L, fixture.experience(fixture.owner));
      assertEquals(67L, fixture.experience(fixture.member));
      assertArrayEquals(new int[] {fixture.owner, fixture.member}, fixture.rewards(victim).snapshotLootPlayers());
      assertTrue(fixture.factory.itemsCreated >= 4, "boss fixture must materialize rewards");
      for (int i = 0; i < fixture.factory.itemIds.size; i++) {
        com.riiablo.engine.server.component.Item drop = fixture.world
            .getMapper(com.riiablo.engine.server.component.Item.class).get(fixture.factory.itemIds.get(i));
        assertEquals(fixture.owner, drop.dropOwnerId);
        assertEquals(fixture.parties.getPartyId(fixture.owner), drop.dropPartyId);
      }
    }
  }

  /** Real reward systems with MPQ-backed stat/item tables, but no rendered or TCP client. */
  private static final class PartyWorld implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final EventSystem events = new EventSystem();
    final RewardFactory factory = new RewardFactory();
    final Map map = new Map(0, 0);
    final Map.Zone zone = new Map.Zone();
    final World world;
    final int owner;
    final int member;

    PartyWorld(SimulationMode mode, PartyManager alias) {
      SimulationSession session = new SimulationSession(mode, map, new EnvironmentCycle(), parties);
      com.artemis.WorldConfiguration config = session.registerServices(new WorldConfigurationBuilder()
          .with(events, new DeathRewardSystem(session.partyManager()),
              new ExperienceManager(session.partyManager()), new ItemGenerator(), factory,
              new PartyMemberSyncSystem(session.partyManager())).build()).register("factory", factory);
      if (alias != null) config.register("partyManager", alias);
      world = new World(config);
      zone.level = new Levels.Entry();
      zone.level.Id = 2;
      owner = player();
      member = player();
    }

    int player() {
      int entity = world.create();
      CharData data = characterAtLevelOne();
      world.getMapper(Player.class).create(entity).data = data;
      world.getMapper(AttributesWrapper.class).create(entity).attrs = data.getStats();
      world.getMapper(MapWrapper.class).create(entity).set(map, zone);
      world.getMapper(Position.class).create(entity).position.set(10, 10);
      return entity;
    }

    void join(PartyManager manager) {
      assertTrue(manager.sendInvitation(owner, member));
      assertTrue(manager.acceptInvitation(member));
    }

    int kill() {
      MonStats.Entry stats = new MonStats.Entry();
      stats.Id = "session_reward_boss";
      stats.boss = true;
      int victim = world.create();
      world.getMapper(Monster.class).create(victim).set(stats, new MonStats2.Entry());
      world.getMapper(MapWrapper.class).create(victim).set(map, zone);
      world.getMapper(Position.class).create(victim).position.set(10, 10);
      Attributes attributes = Attributes.obtainStandard();
      attributes.base().put(Stat.level, 1);
      attributes.base().put(Stat.experience, 100);
      attributes.reset();
      world.getMapper(AttributesWrapper.class).create(victim).attrs = attributes;
      world.process();
      events.dispatch(DeathEvent.obtain(owner, victim));
      return victim;
    }

    long experience(int entity) {
      return world.getMapper(Player.class).get(entity).data.getStats().aggregate().getValue(Stat.experience, 0L);
    }

    MonsterRewardState rewards(int victim) {
      return world.getMapper(MonsterRewardState.class).get(victim);
    }

    @Override public void close() {
      world.dispose();
      for (int i = 0; i < factory.itemIds.size; i++) GroundDropOwnership.clear(factory.itemIds.get(i));
    }
  }

  private static final class RewardFactory extends EntityFactory {
    int itemsCreated;
    final IntArray itemIds = new IntArray();

    @Override public int createItem(Item item, float x, float y) {
      itemsCreated++;
      int id = world.create();
      itemIds.add(id);
      world.getMapper(com.riiablo.engine.server.component.Item.class).create(id).item = item;
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
