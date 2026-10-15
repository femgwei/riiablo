package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Shared membership mechanics, including intentionally mixed test components;
 * not a claim that native skill eligibility permits every such combination. */
class NativeTargetNodeTest {
  @Test
  void allComponentInsertionOrdersKeepExactlyTheFirstMembership() {
    int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
    for (int stateId : new int[] {StateId.ATTRACT, StateId.CONFUSE, StateId.CONVERSION}) {
      for (int[] permutation : permutations) {
        try (Fixture f = new Fixture()) {
          long order = 0;
          for (int member : permutation) {
            if (member == 0) f.pet.set(f.owner, "skeleton", 70, 1, false, 100);
            if (member == 1) f.merc.set(f.owner, 0, 10, 3, 4);
            if (member == 2) f.states.addState(stateId, 100, 1, f.owner);
            if (order == 0) order = f.node.order();
            assertEquals(order, f.node.order(), "duplicate insertion cannot reorder");
          }
          int first = permutation[0];
          assertEquals(first == 0 ? order : 0, f.pet.nativeTargetOrder());
          assertEquals(first == 1 ? order : 0, f.merc.nativeTargetOrder());
          assertEquals(first == 2 && stateId != StateId.CONVERSION ? order : 0, f.states.nativeNode9Order());
          assertEquals(first == 2 && stateId == StateId.CONVERSION ? order : 0, f.states.nativeConversionOrder());
          assertEquals(first == 2 && stateId != StateId.CONVERSION ? -1 : f.owner, f.node.ownerId());
        }
      }
    }
  }

  @Test
  void rejectedStateRemovalUnlinksCurrentSummonAndDoesNotRestoreItsComponent() {
    for (int stateId : new int[] {StateId.ATTRACT, StateId.CONFUSE, StateId.CONVERSION}) {
      try (Fixture f = new Fixture()) {
        f.pet.set(f.owner, "skeleton", 70, 5, false, 120);
        long quota = f.pet.spawnOrder;
        f.pet.shotsFired = 3;
        f.states.addStateLayer(stateId, 100, 1, f.owner, 81);
        f.states.addStateLayer(stateId, 100, 1, f.owner + 10, 81);
        assertTrue(f.states.removeStateLayer(stateId, f.owner, 81));
        assertEquals(0, f.node.order());
        assertEquals(0, f.pet.nativeTargetOrder());
        assertTrue(f.states.hasState(stateId));
        f.states.addStateLayer(stateId, 200, 2, f.owner + 10, 81);
        assertEquals(0, f.node.order(), "retained layer refresh cannot rejoin");
        assertEquals(quota, f.pet.spawnOrder);
        assertEquals(3, f.pet.shotsFired);
        assertEquals(120, f.pet.durationFrames);
        f.states.removeState(stateId);
        f.states.addState(stateId, 100, 1, f.owner);
        assertTrue(f.node.order() > 0, "a genuinely new event can migrate to the state bucket");
        assertEquals(0, f.pet.nativeTargetOrder());
      }
    }
  }

  @Test
  void mercenaryReinsertionIsExplicitAndDoesNotResetAi() {
    try (Fixture f = new Fixture()) {
      f.merc.set(f.owner, 0, 10, 42, 4);
      f.merc.aiRngState = 123;
      f.merc.targetId = 77;
      f.merc.setSkill(0, 10, 5);
      long first = f.merc.nativeTargetOrder();
      f.merc.insertNativeTargetNode();
      assertEquals(first, f.merc.nativeTargetOrder());
      f.states.addState(StateId.ATTRACT, 100, 1, f.owner);
      f.states.removeState(StateId.ATTRACT);
      assertEquals(0, f.merc.nativeTargetOrder());
      f.merc.insertNativeTargetNode();
      assertTrue(f.merc.nativeTargetOrder() > first);
      assertEquals(123, f.merc.aiRngState);
      assertEquals(77, f.merc.targetId);
      assertEquals(5, f.merc.skillLevels[0]);
    }
  }

  @Test
  void explicitDetachCanMigrateAndOldStateCallbackUnlinksTheNewBucket() {
    try (Fixture f = new Fixture()) {
      f.states.addState(StateId.CONFUSE, 100, 1, f.owner);
      long curse = f.node.order();
      f.merc.set(f.owner, 0, 10, 1, 4);
      assertEquals(curse, f.node.order());
      f.node.detach();
      f.merc.insertNativeTargetNode();
      assertTrue(f.merc.nativeTargetOrder() > curse);
      assertFalse(f.node.isNode9());
      f.states.removeState(StateId.CONFUSE);
      assertEquals(0, f.merc.nativeTargetOrder());
      assertEquals(0, f.node.order());
    }
  }

  @Test
  void ordinaryStateClearAndComponentDiscardAreNotTargetsLast() {
    try (Fixture f = new Fixture()) {
      f.pet.set(f.owner, "skeleton", 70, 1, false, 0);
      long order = f.node.order();
      f.states.addState(StateId.POISON, 100, 1, f.owner);
      f.states.clearAll();
      assertEquals(order, f.node.order(), "only native target-state callbacks unlink");
      f.states.discardAll();
      assertEquals(order, f.node.order(), "pool/snapshot teardown is not gameplay removal");
    }
  }

  @Test
  void playerHeadReleaseFreesEvenChildNotYetInSubscriptionAndDoesNotQueueIt() {
    try (Fixture f = new Fixture()) {
      f.pet.set(f.owner, "skeleton", 70, 1, false, 0);
      long old = f.node.order();
      f.world.getMapper(Player.class).remove(f.owner);
      f.world.process();
      assertEquals(0, f.node.order());
      assertFalse(f.node.insert(NativeTargetNode.SUMMON, f.owner), "no head, no insertion");
      f.world.getMapper(Player.class).create(f.owner);
      f.world.process();
      assertEquals(0, f.node.order());
      assertTrue(f.node.insert(NativeTargetNode.SUMMON, f.owner));
      assertTrue(f.node.order() > old);
    }
  }

  @Test
  void metadataOwnerChangesCannotStealAnExistingPlayerGroup() {
    try (Fixture f = new Fixture()) {
      f.pet.set(f.owner, "skeleton", 70, 1, false, 0);
      long order = f.node.order();
      f.pet.ownerId = 500;
      f.merc.set(500, 0, 10, 1, 4);
      assertEquals(f.owner, f.node.ownerId());
      assertEquals(order, f.node.order());
    }
  }

  @Test
  void separateWorldsAndRecycledUnitIdsCannotShareMembershipOrCallbackReferences() {
    try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
      assertEquals(first.id, second.id);
      first.states.addState(StateId.ATTRACT, 100, 1, first.owner);
      second.pet.set(second.owner, "skeleton", 70, 1, false, 0);
      long secondOrder = second.node.order();
      first.world.process();
      first.world.delete(first.id);
      first.world.process();
      int reused = first.world.create();
      assertEquals(first.id, reused);
      NativeTargetNode replacement = NativeTargetNodes.get(first.world, reused);
      assertNotSame(first.node, replacement);
      replacement.insert(NativeTargetNode.WALL, first.owner);
      first.states.removeState(StateId.ATTRACT); // old allocation's callback
      assertTrue(replacement.isNode9());
      assertEquals(secondOrder, second.node.order());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().with(new NativePlayerTargetSlots()).build());
    final int owner = world.create();
    final int id;
    final NativeTargetNode node;
    final SummonedPet pet;
    final Mercenary merc;
    final StateList states;

    Fixture() {
      world.getMapper(Player.class).create(owner);
      world.getSystem(NativePlayerTargetSlots.class).registerPlayer(owner);
      id = world.create();
      node = NativeTargetNodes.get(world, id);
      pet = world.getMapper(SummonedPet.class).create(id).bindNativeTargetNode(node);
      merc = world.getMapper(Mercenary.class).create(id).bindNativeTargetNode(node);
      states = world.getMapper(UnitStates.class).create(id).init(world, id).stateList;
    }

    @Override public void close() { world.dispose(); }
  }
}
