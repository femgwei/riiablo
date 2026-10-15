package com.riiablo.engine.server.ai;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.ServerMonsterCorpseSystem;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real factory/NPC restore and native tables; terrain/path are test doubles. */
class BaalCrabMercenaryResurrectionIntegrationTest extends RiiabloTest {
  @Test
  void deathUnlinksPersistentlyRatherThanOnlyWhileDeadModeIsVisible() {
    try (Fixture f = new Fixture("baalcrab")) {
      f.die(); // no Baal scan is needed to remember the unlink
      assertEquals(0, f.merc().nativeTargetOrder());
      f.world.getMapper(Corpse.class).remove(f.hireling);
      f.world.getMapper(Sequence.class).remove(f.hireling);
      f.world.getMapper(CofReference.class).get(f.hireling).mode = Engine.Monster.MODE_NU;
      f.stats(f.hireling);
      f.world.process();
      assertEquals(f.player, f.select());
      assertEquals(1, f.ai.lastTargetCount(), "raw mode/life restoration is not native insertion");
    }
  }

  @Test
  void retainedNpcResurrectionReinsertsAfterNewerSummonWithoutReinitializingHireling() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        long firstOrder = f.merc().nativeTargetOrder();
        f.die();
        int summon = f.summon();
        long summonOrder = f.world.getMapper(SummonedPet.class).get(summon).nativeTargetOrder();
        assertTrue(summonOrder > firstOrder);
        f.merc().setSkill(0, 456, 7);
        f.merc().aiRngState = 12345;
        int seed = f.merc().seed;
        assertTrue(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
        assertTrue(f.merc().nativeTargetOrder() > summonOrder);
        assertEquals(seed, f.merc().seed);
        assertEquals(456, f.merc().skills[0]);
        assertEquals(7, f.merc().skillLevels[0]);
        assertEquals(12345, f.merc().aiRngState);
        assertFalse(f.world.getMapper(AIWrapper.class).has(f.hireling));
        assertFalse(f.world.getMapper(Interactable.class).has(f.hireling));
        f.stats(f.hireling);
        f.world.process();
        assertEquals(f.ai.targetScore(summon), f.ai.targetScore(f.hireling));
        assertEquals(f.hireling, f.select(), "resurrected hireling is the newest equal-score child");
        assertEquals(3, f.ai.lastTargetCount());
        long restored = f.merc().nativeTargetOrder();
        assertFalse(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
        assertEquals(restored, f.merc().nativeTargetOrder(), "duplicate callback cannot reorder a live child");
      }
    }
  }

  @Test
  void explicitNpcRestoreCanJoinReplacementHeadButOldInsertionCannot() {
    try (Fixture f = new Fixture("baalcrab")) {
      f.die();
      f.world.getMapper(Player.class).remove(f.player);
      f.world.process();
      f.world.getMapper(Player.class).create(f.player);
      f.world.process();
      long newHead = f.slots.registrationOrder(f.player);
      assertFalse(f.slots.acceptsChildInsertion(f.player, f.merc().nativeTargetOrder()));
      assertTrue(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
      assertTrue(f.merc().nativeTargetOrder() > newHead);
      f.stats(f.hireling);
      f.world.process();
      assertEquals(f.hireling, f.select());
      assertEquals(2, f.ai.lastTargetCount());
    }
  }

  @Test
  void successfulRestoreWithoutRegisteredHeadDoesNotQueueLaterInsertion() {
    try (Fixture f = new Fixture("baalcrab")) {
      f.die();
      for (int i = 1; i < NativePlayerTargetSlots.SLOT_COUNT; i++) f.playerHead();
      f.world.getMapper(Player.class).remove(f.player);
      f.world.process();
      int occupying = f.playerHead();
      f.world.getMapper(Player.class).create(f.player);
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(f.player));
      assertTrue(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
      assertEquals(0, f.merc().nativeTargetOrder(), "restoring life succeeds even if UpdateSummonAI cannot join");
      f.world.delete(occupying);
      f.world.process();
      f.slots.registerPlayer(f.player);
      assertEquals(0, f.slots.slotOf(f.player));
      assertFalse(f.slots.acceptsChildInsertion(f.player, f.merc().nativeTargetOrder()));
    }
  }

  @Test
  void rejectedRestoreLeavesOtherChildOrderAndDetachedHirelingUntouched() {
    try (Fixture f = new Fixture("baalcrab")) {
      f.die();
      int summon = f.summon();
      long order = f.world.getMapper(SummonedPet.class).get(summon).nativeTargetOrder();
      int other = f.playerHead();
      assertFalse(f.rewards.resurrectMercenaryEntity(f.hireling, other));
      f.world.getMapper(Corpse.class).get(f.hireling).usable = false;
      assertFalse(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
      f.world.getMapper(Corpse.class).get(f.hireling).usable = true;
      when(f.map.flags(any(Vector2.class))).thenReturn(DT1.Tile.FLAG_BLOCK_WALK);
      assertFalse(f.rewards.resurrectMercenaryEntity(f.hireling, f.player));
      assertEquals(0, f.merc().nativeTargetOrder());
      assertEquals(order, f.world.getMapper(SummonedPet.class).get(summon).nativeTargetOrder());
      assertEquals(summon, f.select());
    }
  }

  @Test
  void paidNpcTransactionRejoinsOnlyAfterAffordableSuccessfulRestore() {
    try (Fixture f = new Fixture("baalcrab")) {
      f.die();
      Attributes owner = f.world.getMapper(Player.class).get(f.player).data.getStats();
      owner.base().put(Stat.gold, 0);
      owner.reset();
      assertFalse(f.rewards.resurrectMercenary(f.player));
      assertEquals(0, f.merc().nativeTargetOrder());
      int cost = f.rewards.resurrectionCost(f.player);
      assertTrue(cost > 0);
      owner.base().put(Stat.gold, cost + 100);
      owner.reset();
      assertTrue(f.rewards.resurrectMercenary(f.player));
      assertEquals(f.hireling, f.rewards.mercenaryEntityId(f.player), "retained entity, not a replacement hire");
      assertTrue(f.slots.acceptsChildInsertion(f.player, f.merc().nativeTargetOrder()));
      assertEquals(100, f.rewards.getPlayerGold(f.player));
      f.world.process();
      f.select();
      assertEquals(2, f.ai.lastTargetCount());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final EventSystem events = new EventSystem();
    final ServerEntityFactory factory = new ServerEntityFactory();
    final NativeMercenaryRewardSystem rewards = new NativeMercenaryRewardSystem();
    final NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    final Map map = mock(Map.class);
    final Map.Zone zone = mock(Map.Zone.class);
    final World world;
    final int player, hireling;
    final BaalCrab ai;

    Fixture(String boss) {
      when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      world = new World(new WorldConfigurationBuilder()
          .with(events, new ItemManager(), new ObjectInteractor(), new WarpInteractor(),
              new ItemInteractor(), new Pathfinder(), new CofManager(), factory,
              new ServerMonsterCorpseSystem(), slots, rewards)
          .build().register("factory", factory).register("map", map));
      Riiablo.engine = world;
      player = factory.createPlayer("TargetRejoin", Riiablo.NECROMANCER, 20, 10);
      initializePlayerStats();
      assertTrue(rewards.grantFreeRogue(player));
      hireling = rewards.mercenaryEntityId(player);
      assertTrue(hireling >= 0);
      stats(hireling);
      int baal = factory.createMonster(Riiablo.files.monstats.get(boss).hcIdx, 10, 10);
      ai = (BaalCrab) world.getMapper(AIWrapper.class).get(baal).ai;
      world.process();
    }

    private void initializePlayerStats() {
      Attributes attrs = world.getMapper(Player.class).get(player).data.getStats();
      attrs.base().put(Stat.level, 10);
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.maxdamage, 0);
      attrs.reset();
    }

    void stats(int id) {
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.maxdamage, 1000);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    }

    Mercenary merc() { return world.getMapper(Mercenary.class).get(hireling); }
    int select() { return ai.findNearestTargetWithAidist(new float[] {Float.MAX_VALUE}); }
    int playerHead() {
      int id = world.create();
      world.getMapper(Player.class).create(id);
      world.process();
      return id;
    }

    int summon() {
      int id = factory.createMonster(Riiablo.files.monstats.get("skeleton1").hcIdx, 30, 10);
      MonStats.Entry threat = new MonStats.Entry();
      threat.threat = 2; // isolate score arithmetic, never mutate the shared table
      world.getMapper(Monster.class).get(id).monstats = threat;
      world.getMapper(SummonedPet.class).create(id)
          .bindNativeTargetNode(com.riiablo.engine.server.NativeTargetNodes.get(world, id))
          .set(player, "skeleton", 70, 1, false, 0);
      stats(id);
      world.process();
      return id;
    }

    void die() {
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(hireling).attrs;
      attrs.base().put(Stat.hitpoints, 0f);
      attrs.reset();
      events.dispatch(DeathEvent.obtain(player, hireling));
      world.getMapper(CofReference.class).get(hireling).mode = Engine.Monster.MODE_DD;
      events.dispatch(ModeChangeEvent.obtain(hireling, Engine.Monster.MODE_DD));
      world.process();
    }

    @Override public void close() { world.dispose(); Riiablo.engine = previous; }
  }
}
