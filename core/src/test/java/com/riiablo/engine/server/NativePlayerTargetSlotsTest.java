package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativePlayerTargetSlotsTest {
  @Test
  void newHeadGenerationRejectsOldAndUnknownInsertionsUntilExplicitRejoin() {
    try (Fixture f = new Fixture()) {
      int player = f.player();
      long beforeHead = f.slots.nextOrder();
      f.slots.registerPlayer(player);
      long head = f.slots.registrationOrder(player);
      long child = f.slots.nextOrder();
      assertTrue(head > beforeHead);
      assertFalse(f.slots.acceptsChildInsertion(player, 0));
      assertFalse(f.slots.acceptsChildInsertion(player, beforeHead));
      assertFalse(f.slots.acceptsChildInsertion(player, head));
      assertTrue(f.slots.acceptsChildInsertion(player, child));
      f.world.process();
      f.slots.registerPlayer(player);
      assertEquals(head, f.slots.registrationOrder(player));
      f.world.getMapper(Player.class).remove(player);
      f.world.process();
      assertEquals(0, f.slots.registrationOrder(player));
      assertFalse(f.slots.acceptsChildInsertion(player, child));
      f.world.getMapper(Player.class).create(player);
      f.world.process();
      assertTrue(f.slots.registrationOrder(player) > child);
      assertFalse(f.slots.acceptsChildInsertion(player, child));
      assertTrue(f.slots.acceptsChildInsertion(player, f.slots.nextOrder()));
    }
  }

  @Test
  void explicitAllocationOrderWinsOverEntityAndSubscriptionOrder() {
    try (Fixture f = new Fixture()) {
      int lowerId = f.player();
      int higherId = f.player();
      f.slots.registerPlayer(higherId);
      f.slots.registerPlayer(lowerId);
      f.world.process();
      f.slots.registerPlayer(higherId);
      assertEquals(higherId, f.slots.playerAtSlot(0));
      assertEquals(lowerId, f.slots.playerAtSlot(1));
      assertEquals(0, f.slots.slotOf(higherId));
      assertEquals(1, f.slots.slotOf(lowerId));
    }
  }

  @Test
  void destructionFreesFirstHoleWithoutCompactingSurvivingHeads() {
    try (Fixture f = new Fixture()) {
      int first = f.player();
      int second = f.player();
      int third = f.player();
      f.world.process();
      f.world.delete(second);
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(second));
      assertEquals(Engine.INVALID_ENTITY, f.slots.playerAtSlot(1));
      int newcomer = f.player();
      f.world.process();
      assertEquals(first, f.slots.playerAtSlot(0));
      assertEquals(newcomer, f.slots.playerAtSlot(1));
      assertEquals(third, f.slots.playerAtSlot(2));
    }
  }

  @Test
  void fullSlotsDoNotQueueNinthPlayerOrPromoteItWhenOneLeaves() {
    try (Fixture f = new Fixture()) {
      int first = f.player();
      for (int i = 1; i < NativePlayerTargetSlots.SLOT_COUNT; i++) f.player();
      f.world.process();
      int ninth = f.player();
      f.slots.registerPlayer(ninth); // immediate factory-like attempt while full
      f.world.delete(first);
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(ninth),
          "subscription insertion must not retry an earlier failed factory attempt");
      assertEquals(Engine.INVALID_ENTITY, f.slots.playerAtSlot(0));
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(ninth));
      int newcomer = f.player();
      f.world.process();
      assertEquals(newcomer, f.slots.playerAtSlot(0));
    }
  }

  @Test
  void allocationDestroyedBeforeFirstSubscriptionTickDoesNotLeakASlot() {
    for (boolean destroyEntity : new boolean[] {true, false}) {
      try (Fixture f = new Fixture()) {
        int aborted = f.player();
        f.slots.registerPlayer(aborted);
        if (destroyEntity) f.world.delete(aborted);
        else f.world.getMapper(Player.class).remove(aborted);
        f.world.process();
        assertEquals(Engine.INVALID_ENTITY, f.slots.playerAtSlot(0));
        assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(aborted));
        int replacement = f.player();
        f.world.process();
        assertEquals(replacement, f.slots.playerAtSlot(0));
      }
    }
  }

  @Test
  void deathAndPositionRemovalKeepAnAlreadyRegisteredHead() {
    try (Fixture f = new Fixture()) {
      int player = f.player();
      f.world.getMapper(Position.class).create(player);
      f.world.process();
      long headOrder = f.slots.registrationOrder(player);
      for (byte mode : new byte[] {Engine.Player.MODE_DT, Engine.Player.MODE_DD}) {
        f.world.getMapper(Sequence.class).create(player).sequence(mode, mode);
        f.world.getMapper(Position.class).remove(player);
        f.world.process();
        f.slots.registerPlayer(player);
        assertEquals(player, f.slots.playerAtSlot(0));
        assertEquals(headOrder, f.slots.registrationOrder(player));
      }
    }
  }

  @Test
  void newlyAllocatedDeadUnitIsNotRegisteredOrAutomaticallyRetried() {
    try (Fixture f = new Fixture()) {
      int dead = f.player();
      f.world.getMapper(CofReference.class).create(dead).mode = Engine.Player.MODE_DD;
      int living = f.player();
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(dead));
      assertEquals(living, f.slots.playerAtSlot(0));
      f.world.getMapper(CofReference.class).get(dead).mode = Engine.Player.MODE_TN;
      f.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, f.slots.slotOf(dead));
      f.slots.registerPlayer(dead); // only an explicit native registration can retry
      assertEquals(dead, f.slots.playerAtSlot(1));
    }
  }

  @Test
  void componentRemovalAndRecreationReleaseAndReregisterTheHead() {
    try (Fixture f = new Fixture()) {
      int first = f.player();
      int second = f.player();
      f.world.process();
      f.world.getMapper(Player.class).remove(first);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, f.slots.playerAtSlot(0));
      assertEquals(second, f.slots.playerAtSlot(1));
      f.world.getMapper(Player.class).create(first);
      f.world.process();
      assertEquals(first, f.slots.playerAtSlot(0));
    }
  }

  @Test
  void worldSlotsAreIndependentAndNonPlayersCannotRegister() {
    try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
      int nonPlayer = first.world.create();
      first.slots.registerPlayer(Engine.INVALID_ENTITY);
      first.slots.registerPlayer(nonPlayer);
      int a = first.player();
      int b = second.player();
      first.world.process();
      second.world.process();
      assertEquals(NativePlayerTargetSlots.UNREGISTERED, first.slots.slotOf(nonPlayer));
      assertEquals(a, first.slots.playerAtSlot(0));
      assertEquals(b, second.slots.playerAtSlot(0));
      first.world.delete(a);
      first.world.process();
      assertEquals(b, second.slots.playerAtSlot(0));
      assertEquals(Engine.INVALID_ENTITY, first.slots.playerAtSlot(-1));
      assertEquals(Engine.INVALID_ENTITY, first.slots.playerAtSlot(8));
    }
  }

  private static class Fixture implements AutoCloseable {
    final NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    final World world = new World(new WorldConfigurationBuilder().with(slots).build());

    int player() {
      int id = world.create();
      world.getMapper(Player.class).create(id);
      return id;
    }

    @Override public void close() { world.dispose(); }
  }
}
