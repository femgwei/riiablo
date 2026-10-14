package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.DeathRewardSystem;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.quest.Act5BaalQuest;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Real server factory, 1.10f tables and reward consumers; terrain/path are stubs. */
class BaalCrabCloneAiIntegrationTest extends RiiabloTest {
  @Test
  void realCloneRowUsesDedicatedAiAndIsNotTheQuestBaal() {
    MonStats.Entry row = Riiablo.files.monstats.get("baalclone");
    assertNotNull(row);
    assertEquals("BaalCrabClone", row.AI);
    assertTrue(AI.findAI(42, row.AI) instanceof BaalCrabClone);
    assertFalse(Act5BaalQuest.isBaalMonster(row.hcIdx, row.Id));
    for (int action = 0; action < 16; action++) {
      assertEquals(action == 7 || action == 9 || action == 14 || action == 15 ? 2 : action,
          BaalCrabClone.remapAction(action));
    }
    assertEquals(20, BaalCrab.cloneWeight(0));
    assertEquals(10, BaalCrab.cloneWeight(1));
    assertEquals(0, BaalCrab.cloneWeight(2));
    assertEquals(0, BaalCrab.cloneWeight(3));
    Monster pooled = new Monster().set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    pooled.baalLinkedOwnerId = 123;
    pooled.baalClonesCreated = 2;
    pooled.set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    assertEquals(-1, pooled.baalLinkedOwnerId);
    assertEquals(0, pooled.baalClonesCreated);
  }

  @Test
  void spawnCopiesEncodedThirdLifeAndLinksBothOwnersWithoutChangingBaal() {
    try (Fixture f = new Fixture()) {
      f.ai.execute(15, f.player);
      int clone = f.cloneId();
      assertTrue(clone >= 0);
      assertEquals("baalclone", f.monster(clone).monstats.Id.toLowerCase());
      assertEquals(f.baal, f.monster(clone).minionOwnerId);
      assertEquals(f.baal, f.monster(clone).baalLinkedOwnerId);
      assertEquals(f.baal, f.monster(f.baal).minionOwnerId);
      assertEquals(1, f.monster(f.baal).baalClonesCreated);
      assertEquals(25601 / 3, f.attrs(clone).get(Stat.maxhp).encodedValues());
      assertEquals(17777 / 3, f.attrs(clone).get(Stat.hitpoints).encodedValues());
      assertEquals(0, f.attrs(clone).getValue(Stat.hpregen, -1));
      assertEquals(25601, f.attrs(f.baal).get(Stat.maxhp).encodedValues());
      assertEquals(17777, f.attrs(f.baal).get(Stat.hitpoints).encodedValues());
      NativeUnitFlags flags = f.world.getMapper(NativeUnitFlags.class).get(clone);
      assertTrue(flags.has(NativeUnitFlags.BAAL_CLONE));
      assertTrue(flags.has(NativeUnitFlags.MONSTER_TARGET), "reward policy must preserve target bits");
      assertFalse(f.world.getMapper(NativeUnitFlags.class).get(f.baal)
          .has(NativeUnitFlags.BAAL_CLONE));
    }
  }

  @Test
  void spawnUsesFourNativeDrawsAndTargetRelativeIntegerOffsets() {
    try (Fixture f = new Fixture()) {
      Riiablo.gameSeed = 0x12345;
      D2Seed seed = new D2Seed(Riiablo.gameSeed ^ (f.baal * 0x45D9F3B), 666);
      Seed.rollRandomNumber(seed);
      Seed.rollRandomNumber(seed);
      int x = 50 + (int) ((Seed.rollRandomNumber(seed) & 0xFFFFFFFFL) % 24) - 12;
      int y = 40 + (int) ((Seed.rollRandomNumber(seed) & 0xFFFFFFFFL) % 24) - 12;
      long next = Seed.rollRandomNumber(seed) & 0xFFFFFFFFL;
      f.ai.execute(15, f.player);
      assertEquals(new Vector2(x, y), f.position(f.cloneId()));
      assertEquals(next, f.ai.rollAiRandomNumber());
    }
  }

  @Test
  void livingCloneBlocksDuplicatesBeforeAndAfterSubscriptionInsertion() {
    try (Fixture f = new Fixture()) {
      f.ai.execute(15, f.player);
      int first = f.cloneId();
      f.ai.execute(15, f.player);
      assertEquals(first, f.cloneId());
      assertEquals(1, f.monster(f.baal).baalClonesCreated);
      f.world.process();
      f.ai.execute(15, f.player);
      assertEquals(1, f.monster(f.baal).baalClonesCreated);
      f.world.getMapper(Corpse.class).create(first);
      f.ai.execute(15, f.player);
      assertNotEquals(first, f.cloneId());
      assertEquals(2, f.monster(f.baal).baalClonesCreated,
          "death permits a replacement but does not refund the native creation count");
    }
  }

  @Test
  void livingNonCloneMinionAlsoBlocksNativeAction15() {
    try (Fixture f = new Fixture()) {
      int minion = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 42, 40);
      f.monster(minion).minionOwnerId = f.baal;
      f.world.process();
      f.ai.execute(15, f.player);
      assertEquals(-1, f.cloneId());
      assertEquals(0, f.monster(f.baal).baalClonesCreated);
    }
  }

  @Test
  void blockedLandingOrFactoryFailureDoesNotIncrementOrLink() {
    for (boolean failFactory : new boolean[] {false, true}) {
      try (Fixture f = new Fixture()) {
        if (failFactory) f.factory.reject = true;
        else when(f.map.isBlockedForPath(eq(f.zone), anyInt(), anyInt(),
            eq(DT1.Tile.FLAG_BLOCK_WALK), anyInt(), eq(false))).thenReturn(true);
        f.ai.execute(15, f.player);
        assertEquals(-1, f.cloneId());
        assertEquals(-1, f.monster(f.baal).minionOwnerId);
        assertEquals(0, f.monster(f.baal).baalClonesCreated);
        assertFalse(f.world.getMapper(NativeUnitFlags.class).get(f.baal)
            .has(NativeUnitFlags.BAAL_CLONE));
      }
    }
  }

  @Test
  void cloneStartupWaits15FramesThenUsesSharedCombatWithoutNestedSpawning() {
    try (Fixture f = new Fixture()) {
      f.ai.execute(15, f.player);
      int clone = f.cloneId();
      BaalCrabClone ai = f.cloneAi();
      ai.update(14 * Animation.FRAME_DURATION);
      assertEquals("IDLE", ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(clone));
      // No target: Fn140 rolls/discards the no-target action, then idles 15.
      f.position(f.player).set(1000, 1000);
      ai.update(2 * Animation.FRAME_DURATION);
      assertFalse(f.world.getMapper(Casting.class).has(clone));
      for (int action : new int[] {7, 9, 14, 15}) {
        ai.execute(action, f.player);
        assertEquals("CIRCLE", ai.getState());
        assertFalse(f.world.getMapper(Casting.class).has(clone));
        assertEquals(0, f.monster(clone).baalClonesCreated);
      }
      ai.execute(10, f.player);
      assertEquals(SkillCodes.attack, f.world.getMapper(Casting.class).get(clone).skillId);
      assertEquals(Engine.Monster.MODE_A2, f.world.getMapper(Sequence.class).get(clone).mode1);
      f.finish(clone);
      ai.execute(11, f.player);
      assertEquals(BaalCrab.SKILL_NOVA, f.world.getMapper(Casting.class).get(clone).skillId);
    }
  }

  @Test
  void cloneNoTargetWaits15FramesAndNeverCastsTentacle() {
    try (Fixture f = new Fixture()) {
      f.ai.execute(15, f.player);
      int clone = f.cloneId();
      BaalCrabClone ai = f.cloneAi();
      ai.execute(9, Engine.INVALID_ENTITY);
      f.position(f.player).set(1000, 1000);
      Riiablo.gameSeed = 77;
      D2Seed seed = new D2Seed(77 ^ (clone * 0x45D9F3B), 666);
      ai.update(14 * Animation.FRAME_DURATION);
      assertEquals(Seed.rollRandomNumber(seed) & 0xFFFFFFFFL, ai.rollAiRandomNumber(),
          "no decision before the fifteenth frame");
      ai.update(2 * Animation.FRAME_DURATION);
      Seed.rollRandomNumber(seed);
      assertEquals(Seed.rollRandomNumber(seed) & 0xFFFFFFFFL, ai.rollAiRandomNumber());
      assertFalse(f.world.getMapper(Casting.class).has(clone));
      assertEquals("IDLE", ai.getState());
    }
  }

  @Test
  void deadOrMissingOwnerKillsCloneThroughDeathEventExactlyOnce() {
    for (int scenario = 0; scenario < 4; scenario++) {
      try (Fixture f = new Fixture()) {
        f.ai.execute(15, f.player);
        int clone = f.cloneId();
        BaalCrabClone ai = f.cloneAi();
        if (scenario == 0) f.world.getMapper(Corpse.class).create(f.baal);
        else if (scenario == 1) f.world.getMapper(Sequence.class).create(f.baal)
            .sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
        else if (scenario == 2) { f.world.delete(f.baal); f.world.process(); }
        else f.attrs(f.baal).get(Stat.hitpoints).set(0f);
        ai.update(16 * Animation.FRAME_DURATION);
        assertEquals("DEAD", ai.getState());
        assertEquals(0f, f.attrs(clone).getValue(Stat.hitpoints, -1f));
        assertEquals(1, f.probe.deaths);
        assertEquals(clone, f.probe.victim);
        assertEquals(Engine.INVALID_ENTITY, f.probe.killer);
        ai.update(100f);
        assertEquals(1, f.probe.deaths);
      }
    }
  }

  @Test
  void missingStoredOwnerOrDeadMinionOwnerCannotContinuePlayerCombat() {
    for (boolean missingOwner : new boolean[] {true, false}) {
      try (Fixture f = new Fixture()) {
        f.ai.execute(15, f.player);
        int clone = f.cloneId();
        BaalCrabClone ai = f.cloneAi();
        if (missingOwner) f.monster(clone).baalLinkedOwnerId = Engine.INVALID_ENTITY;
        else {
          int other = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 42, 40);
          f.world.getMapper(Corpse.class).create(other);
          f.monster(clone).minionOwnerId = other;
        }
        ai.update(16 * Animation.FRAME_DURATION);
        assertEquals("DEAD", ai.getState());
        assertFalse(f.world.getMapper(Casting.class).has(clone));
        assertEquals(1, f.probe.deaths);
      }
    }
  }

  @Test
  void realDeathConsumersAwardNoExperienceOrLootForClone() {
    try (Fixture f = new Fixture()) {
      f.ai.execute(15, f.player);
      int clone = f.cloneId();
      f.attrs(clone).base().put(Stat.level, 1);
      f.attrs(clone).base().put(Stat.experience, 600);
      f.attrs(clone).reset();
      long before = f.playerData.getStats().getValue(Stat.experience, 0L);
      f.events.dispatch(DeathEvent.obtain(f.player, clone));
      f.events.dispatch(DeathEvent.obtain(f.player, clone));
      assertEquals(before, f.playerData.getStats().getValue(Stat.experience, 0L));
      assertEquals(0, f.factory.items);
      // Positive control: the same authoritative path still rewards the real Baal.
      f.attrs(f.baal).base().put(Stat.level, 1);
      f.attrs(f.baal).base().put(Stat.experience, 600);
      f.attrs(f.baal).reset();
      f.events.dispatch(DeathEvent.obtain(f.player, f.baal));
      assertEquals(before + 600, f.playerData.getStats().getValue(Stat.experience, 0L));
      assertTrue(f.factory.items > 0);
    }
  }

  private static final class Probe extends BaseSystem {
    int deaths, victim, killer;
    @Subscribe public void onDeath(DeathEvent event) {
      deaths++; victim = event.victim; killer = event.killer;
    }
    @Override protected void processSystem() {}
  }

  private static final class Factory extends ServerEntityFactory {
    int items;
    boolean reject;
    @Override public int createMonster(int id, float x, float y) {
      return reject ? Engine.INVALID_ENTITY : super.createMonster(id, x, y);
    }
    @Override public int createItem(Item item, float x, float y) { items++; return world.create(); }
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final int oldSeed = Riiablo.gameSeed;
    final EventSystem events = new EventSystem();
    final Probe probe = new Probe();
    final Factory factory = new Factory();
    final Map map = mock(Map.class);
    final Map.Zone zone = mock(Map.Zone.class);
    final Pathfinder paths = mock(Pathfinder.class);
    final World world;
    final int baal, player;
    final BaalCrab ai;
    final CharData playerData;

    Fixture() {
      when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      when(zone.areRoomsAdjacent(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(true);
      when(paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(true);
      world = new World(new WorldConfigurationBuilder()
          .with(events, probe, new ExperienceManager(), new DeathRewardSystem(), new ItemGenerator(),
              new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
              new Pathfinder(), new CofManager(), factory)
          .build().register("factory", factory).register("map", map));
      Riiablo.engine = world;
      playerData = CharData.obtain().clear().set(Riiablo.NORMAL, false, "CloneTest", Riiablo.AMAZON);
      playerData.getStats().base().put(Stat.level, 1);
      playerData.getStats().base().put(Stat.experience, 0);
      playerData.getStats().reset();
      player = world.create();
      world.getMapper(Player.class).create(player).data = playerData;
      world.getMapper(AttributesWrapper.class).create(player).attrs = playerData.getStats();
      world.getMapper(Position.class).create(player).position.set(50, 40);
      world.getMapper(MapWrapper.class).create(player).set(map, zone);
      baal = factory.createMonster(Riiablo.files.monstats.get("baalcrab").hcIdx, 40, 40);
      ai = (BaalCrab) world.getMapper(AIWrapper.class).get(baal).ai;
      ai.pathfinder = paths;
      attrs(baal).base().putEncoded(Stat.maxhp, 25601);
      attrs(baal).base().putEncoded(Stat.hitpoints, 17777);
      attrs(baal).reset();
      world.process();
    }

    int cloneId() { return monster(baal).baalLinkedOwnerId; }
    Monster monster(int id) { return world.getMapper(Monster.class).get(id); }
    Attributes attrs(int id) { return world.getMapper(AttributesWrapper.class).get(id).attrs; }
    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    BaalCrabClone cloneAi() {
      BaalCrabClone ai = (BaalCrabClone) world.getMapper(AIWrapper.class).get(cloneId()).ai;
      ai.pathfinder = paths;
      world.process();
      return ai;
    }
    void finish(int id) {
      world.getMapper(Casting.class).remove(id);
      world.getMapper(Sequence.class).remove(id);
      world.process();
    }
    @Override public void close() {
      world.dispose(); Riiablo.engine = previous; Riiablo.gameSeed = oldSeed;
    }
  }
}
