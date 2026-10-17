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
import com.riiablo.engine.server.NativeTargetNodes;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.ServerMonsterCorpseSystem;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native death/real restore/Revive entry points; terrain/path are test doubles. */
class BaalCrabSummonTargetLifecycleIntegrationTest extends RiiabloTest {
  @Test
  void retainedWallComponentCannotHideAnExplicitNewConversionMembership() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        int id = f.summon(30);
        SummonedPet pet = f.pet(id);
        pet.detachNativeTargetNode();
        pet.set(f.player, "bonewall", 78, 1, true, 100).boneWall = true;
        long wall = pet.nativeTargetOrder();
        long quota = pet.spawnOrder;
        NativeTargetNode node = NativeTargetNodes.get(f.world, id);
        assertTrue(node.isNode9());
        node.detach();
        f.states(id).addState(StateId.CONVERSION, 100, 1, f.player);
        assertTrue(f.states(id).nativeConversionOrder() > wall);
        assertEquals(0, pet.nativeTargetOrder());
        f.world.process();
        assertEquals(id, f.select(), "actual membership, not residual wall metadata, chooses the group");
        assertEquals(2, f.ai.lastTargetCount());
        assertEquals(quota, pet.spawnOrder);
        f.states(id).removeState(StateId.CONVERSION);
        assertTrue(pet.boneWall);
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount());
      }
    }
  }

  @Test
  void actualSummonFactorySharesItsAllocationNodeWithStateCallbacksBeforeFirstTick() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        when(f.zone.findFreeCoordinates(any(Vector2.class), anyInt(), anyInt(), anyBoolean(), any(Vector2.class)))
            .thenReturn(true);
        int id = f.factory.createSummonedPet(f.player, Riiablo.files.monstats.get("skeleton1"),
            "skeleton", 70, 1, 8, false, 0, 30, 10);
        assertTrue(id >= 0);
        f.stats(id, 1000);
        long order = f.pet(id).nativeTargetOrder();
        assertTrue(order > 0);
        f.states(id).addState(StateId.CONFUSE, 1, 1, f.player);
        assertEquals(0, f.states(id).nativeNode9Order());
        assertEquals(order, NativeTargetNodes.get(f.world, id).order());
        f.states(id).update(); // callback before any subscription/world tick
        assertEquals(0, f.pet(id).nativeTargetOrder());
        f.world.process();
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount());
      }
    }
  }

  @Test
  void sharedSummonNodeRejectsCurseBucketButItsCallbackUnlinksForBodyAndClone() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      for (int curse : new int[] {StateId.ATTRACT, StateId.CONFUSE}) {
        try (Fixture f = new Fixture(boss)) {
          int id = f.summon(30);
          int newer = f.summon(30);
          NativeTargetNode node = NativeTargetNodes.get(f.world, id);
          long order = node.order();
          long quota = f.pet(id).spawnOrder;
          f.states(id).addState(curse, 100, 1, f.player);
          f.world.process();
          assertEquals(order, node.order());
          assertEquals(0, f.states(id).nativeNode9Order());
          assertEquals(newer, f.select(), "rejected insertion cannot promote or hide the old child");
          assertEquals(3, f.ai.lastTargetCount());
          f.states(id).removeState(curse);
          assertEquals(0, f.pet(id).nativeTargetOrder());
          f.select();
          assertEquals(2, f.ai.lastTargetCount(), "residual summon metadata cannot resurrect a node");
          assertEquals(quota, f.pet(id).spawnOrder);
          f.states(id).addState(StateId.CONVERSION, 100, 1, f.player);
          f.select();
          assertEquals(3, f.ai.lastTargetCount());
          assertEquals(0, f.pet(id).nativeTargetOrder(), "new state membership is not old pet membership");
          assertTrue(f.states(id).nativeConversionOrder() > order);
        }
      }
    }
  }

  @Test
  void stateNodeCannotBeStolenByMercenaryAndExplicitMigrationDoesNotReviveOldBucket() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        f.world.getMapper(Position.class).get(f.player).position.set(40, 10);
        when(f.map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(true);
        int id = f.monster(14);
        StateList states = f.states(id);
        states.addState(StateId.ATTRACT, 100, 1, f.player);
        long curse = states.nativeNode9Order();
        NativeTargetNode node = NativeTargetNodes.get(f.world, id);
        Mercenary merc = f.world.getMapper(Mercenary.class).create(id)
            .bindNativeTargetNode(node).set(f.player, 0, 10, 4, 5);
        f.world.process();
        assertEquals(0, merc.nativeTargetOrder());
        assertEquals(curse, states.nativeNode9Order());
        assertEquals(id, f.select());
        assertEquals(1, f.ai.lastTargetCount(), "node9 must not also count as a hireling child");
        node.detach();
        merc.insertNativeTargetNode();
        assertEquals(id, f.select());
        assertEquals(2, f.ai.lastTargetCount());
        assertEquals(0, states.nativeNode9Order());
        states.removeState(StateId.ATTRACT);
        assertEquals(0, merc.nativeTargetOrder());
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount());
      }
    }
  }

  @Test
  void retainedCurseDoesNotHideNewConversionAndItsRemovalUnlinksCurrentNode() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        int id = f.monster(30);
        Monster monster = f.world.getMapper(Monster.class).get(id);
        StateList states = f.states(id);
        states.addState(StateId.ATTRACT, 100, 1, f.player);
        states.detachNativeTargetNode();
        monster.converted = true;
        monster.conversionOwnerId = f.player;
        states.addState(StateId.CONVERSION, 100, 1, f.player);
        f.world.process();
        assertTrue(states.hasState(StateId.ATTRACT));
        assertEquals(id, f.select());
        assertEquals(2, f.ai.lastTargetCount(), "membership, not retained curse flags, determines the bucket");
        states.removeState(StateId.ATTRACT);
        assertTrue(states.hasState(StateId.CONVERSION));
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount(), "curse callback unlinks the unit's current node");
        states.addState(StateId.CONVERSION, 200, 1, f.player);
        f.select();
        assertEquals(1, f.ai.lastTargetCount(), "refresh must not rejoin after callback removal");
        states.removeState(StateId.CONVERSION);
        states.addState(StateId.CONVERSION, 100, 1, f.player);
        assertEquals(id, f.select());
        assertEquals(2, f.ai.lastTargetCount());
      }
    }
  }

  @Test
  void secondStateCannotMoveConversionNodeAndPartialRemovalDoesNotRestoreIt() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        int id = f.monster(30);
        Monster monster = f.world.getMapper(Monster.class).get(id);
        monster.converted = true;
        monster.conversionOwnerId = f.player;
        StateList states = f.states(id);
        states.addState(StateId.CONVERSION, 100, 1, f.player);
        long order = states.nativeConversionOrder();
        states.addStateLayer(StateId.CONFUSE, 100, 1, f.player, 81);
        states.addStateLayer(StateId.CONFUSE, 200, 1, f.player + 1, 81);
        f.world.process();
        assertEquals(0, states.nativeNode9Order());
        assertEquals(order, states.nativeConversionOrder());
        assertEquals(id, f.select());
        assertEquals(2, f.ai.lastTargetCount());
        states.removeStateLayer(StateId.CONFUSE, f.player, 81);
        assertTrue(states.hasState(StateId.CONFUSE));
        assertEquals(0, states.nativeNode9Order());
        assertEquals(0, states.nativeConversionOrder());
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount());
      }
    }
  }

  @Test
  void ordinaryFactoryResurrectionDoesNotReinsertOldSummonOrChangePetQuotaOrder() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        int id = f.summon(30);
        SummonedPet pet = f.pet(id);
        long quota = pet.spawnOrder;
        f.world.process();
        f.die(id); // no Baal selection observes the corpse/death modes
        assertEquals(0, pet.nativeTargetOrder());
        assertTrue(f.factory.resurrectMonster(id, f.player));
        f.world.process();
        assertEquals(f.player, f.select());
        assertEquals(1, f.ai.lastTargetCount());
        assertEquals(f.player, pet.ownerId);
        assertEquals(quota, pet.spawnOrder, "target detachment must not reinitialize pet quota/lifetime");
        pet.set(f.player, "skeleton", 70, 1, false, 0);
        f.select();
        assertEquals(2, f.ai.lastTargetCount(), "explicit new summon initialization can insert again");
      }
    }
  }

  @Test
  void convertedStateSurvivalAndRefreshDoNotUndoDeathModeUnlinking() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      for (byte mode : new byte[] {Engine.Monster.MODE_DT, Engine.Monster.MODE_DD}) {
        try (Fixture f = new Fixture(boss)) {
          int id = f.monster(30);
          Monster monster = f.world.getMapper(Monster.class).get(id);
          monster.converted = true;
          monster.conversionOwnerId = f.player;
          StateList states = f.states(id);
          states.addState(StateId.CONVERSION, 100, 1, f.player);
          f.world.process();
          f.modeRoundTrip(id, mode);
          assertTrue(monster.converted);
          assertTrue(states.hasState(StateId.CONVERSION));
          assertEquals(0, states.nativeConversionOrder());
          assertEquals(f.player, f.select());
          assertEquals(1, f.ai.lastTargetCount());
          states.addState(StateId.CONVERSION, 200, 1, f.player);
          f.select();
          assertEquals(1, f.ai.lastTargetCount(), "retained state refresh is not a new insertion");
          states.removeState(StateId.CONVERSION);
          states.addState(StateId.CONVERSION, 200, 1, f.player);
          f.select();
          assertEquals(2, f.ai.lastTargetCount());
        }
      }
    }
  }

  @Test
  void deadWallAndRetainedCursesNeedNewNodeInsertionBeforeNode9Fallback() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      for (int kind : new int[] {-1, StateId.ATTRACT, StateId.CONFUSE}) {
        try (Fixture f = new Fixture(boss)) {
          f.world.getMapper(Position.class).get(f.player).position.set(40, 10);
          when(f.map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
              .thenReturn(true);
          int id = kind == -1 ? f.summon(14) : f.monster(14);
          if (kind == -1) {
            f.pet(id).detachNativeTargetNode();
            f.pet(id).set(f.player, "bonewall", 78, 1, true, 100).boneWall = true;
          }
          else f.states(id).addState(kind, 100, 1, f.player);
          // A high-threat stand-in isolates membership. Native bone-wall rows
          // have threat <=1 and must still score zero with the real table.
          MonStats.Entry threat = new MonStats.Entry();
          threat.threat = 2;
          threat.killable = true;
          f.world.getMapper(Monster.class).get(id).monstats = threat;
          f.world.process();
          assertTrue(f.ai.isValidSpecialEnemyTargetWithoutAiScope(id), boss + "/" + kind);
          assertFalse(f.ai.isInNativeMeleeRange(f.player), "primary must permit node9 fallback");
          assertEquals(id, f.select(), boss + "/" + kind);
          f.modeRoundTrip(id, Engine.Monster.MODE_DT);
          assertEquals(f.player, f.select());
          assertEquals(1, f.ai.lastTargetCount());
          if (kind == -1) {
            assertTrue(f.pet(id).boneWall);
            assertEquals(0, f.pet(id).nativeTargetOrder());
            f.pet(id).set(f.player, "bonewall", 78, 1, true, 100).boneWall = true;
          } else {
            assertTrue(f.states(id).hasState(kind));
            f.states(id).addState(kind, 200, 1, f.player);
            assertEquals(f.player, f.select());
            f.states(id).removeState(kind);
            f.states(id).addState(kind, 200, 1, f.player);
          }
          assertEquals(id, f.select(), "new insertion, unlike retained flags, restores the candidate");
        }
      }
    }
  }

  @Test
  void realNecromancerReviveExplicitlyInsertsRetainedCorpseAsNewestChild() {
    for (String boss : new String[] {"baalcrab", "baalclone"}) {
      try (Fixture f = new Fixture(boss)) {
        MonStats.Entry row = reviveableRow();
        int id = f.factory.createMonster(row.hcIdx, 30, 10);
        f.stats(id, 1000);
        f.world.process();
        f.die(id);
        int older = f.summon(30);
        f.world.process();
        long olderOrder = f.pet(older).nativeTargetOrder();
        f.events.dispatch(SkillDoEvent.obtain(f.player, SkillId.REVIVE, id,
            new Vector2(30, 10), 58, 0));
        assertFalse(f.world.getMapper(Corpse.class).has(id));
        assertNotNull(f.pet(id));
        assertTrue(f.pet(id).nativeTargetOrder() > olderOrder);
        assertEquals(f.player, f.pet(id).ownerId);
        assertTrue(f.world.getMapper(AIWrapper.class).get(id).ai instanceof NecroPet);
        f.stats(id, 1000);
        f.stats(older, 1000);
        f.world.process();
        assertEquals(f.ai.targetScore(older), f.ai.targetScore(id));
        assertEquals(id, f.select());
        assertEquals(3, f.ai.lastTargetCount());
        long joined = f.pet(id).nativeTargetOrder();
        f.events.dispatch(SkillDoEvent.obtain(f.player, SkillId.REVIVE, id,
            new Vector2(30, 10), 58, 0));
        assertEquals(joined, f.pet(id).nativeTargetOrder(), "rejected duplicate Revive cannot promote the child");
      }
    }
  }

  private static MonStats.Entry reviveableRow() {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (row.killable && !row.npc && !row.boss && row.threat > 1
          && Riiablo.files.monstats2.get(row.MonStatsEx) != null
          && Riiablo.files.monstats2.get(row.MonStatsEx).revive) return row;
    }
    throw new AssertionError("1.10f must contain a reviveable scored monster");
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final EventSystem events = new EventSystem();
    final ServerEntityFactory factory = new ServerEntityFactory();
    final Map map = mock(Map.class);
    final Map.Zone zone = mock(Map.Zone.class);
    final World world;
    final int player;
    final BaalCrab ai;

    Fixture(String boss) {
      when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      world = new World(new WorldConfigurationBuilder()
          .with(events, new ItemManager(), new ObjectInteractor(), new WarpInteractor(),
              new ItemInteractor(), new Pathfinder(), new CofManager(), factory,
              new ServerMonsterCorpseSystem(), new NativePlayerTargetSlots(), new ServerSkillSystem(true))
          .build().register("factory", factory).register("map", map)
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
      Riiablo.engine = world;
      player = factory.createPlayer("SummonNode", Riiablo.NECROMANCER, 20, 10);
      world.getMapper(Player.class).get(player).data.setSkillLevel(SkillId.REVIVE, 5);
      stats(player, 0);
      int bossId = factory.createMonster(Riiablo.files.monstats.get(boss).hcIdx, 10, 10);
      ai = (BaalCrab) world.getMapper(AIWrapper.class).get(bossId).ai;
      world.process();
    }

    int monster(int x) {
      int id = factory.createMonster(Riiablo.files.monstats.get("skeleton1").hcIdx, x, 10);
      stats(id, 1000);
      return id;
    }
    int summon(int x) {
      int id = monster(x);
      world.getMapper(SummonedPet.class).create(id)
          .bindNativeTargetNode(NativeTargetNodes.get(world, id))
          .set(player, "skeleton", 70, 1, false, 0);
      world.getMapper(NativeUnitFlags.class).get(id).reset().set(NativeUnitFlags.PLAYER_SUMMON);
      return id;
    }
    SummonedPet pet(int id) { return world.getMapper(SummonedPet.class).get(id); }
    StateList states(int id) { return world.getMapper(UnitStates.class).get(id).stateList; }
    int select() { return ai.findNearestTargetWithAidist(new float[] {Float.MAX_VALUE}); }
    void stats(int id, int damage) {
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.level, 10);
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.maxdamage, damage);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    }
    void modeRoundTrip(int id, byte mode) {
      events.dispatch(ModeChangeEvent.obtain(id, mode));
      world.getMapper(Corpse.class).remove(id);
      world.getMapper(Sequence.class).remove(id);
      world.getMapper(CofReference.class).get(id).mode = Engine.Monster.MODE_NU;
      events.dispatch(ModeChangeEvent.obtain(id, Engine.Monster.MODE_NU));
      // This fixture revives the unit without the factory. Restore its combat
      // flags explicitly; target-node membership must remain detached.
      world.getMapper(NativeUnitFlags.class).get(id).set(NativeUnitFlags.MONSTER_TARGET);
      world.process();
    }
    void die(int id) {
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(id).attrs;
      attrs.base().put(Stat.hitpoints, 0f);
      attrs.reset();
      events.dispatch(DeathEvent.obtain(player, id));
      world.getMapper(CofReference.class).get(id).mode = Engine.Monster.MODE_DD;
      events.dispatch(ModeChangeEvent.obtain(id, Engine.Monster.MODE_DD));
      world.process();
    }
    @Override public void close() { world.dispose(); Riiablo.engine = previous; }
  }
}
