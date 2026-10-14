package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ServerEntityFactoryPlayerTargetSlotsTest extends RiiabloTest {
  @Test
  void realPlayerFactoryRegistersBeforeReturningAndSubscriptionDoesNotDuplicate() {
    World previous = Riiablo.engine;
    ServerEntityFactory factory = new ServerEntityFactory();
    NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ItemManager(), new ObjectInteractor(),
            new WarpInteractor(), new ItemInteractor(), factory, slots)
        .build().register("factory", factory).register("map", mock(Map.class)));
    Riiablo.engine = world;
    try {
      int first = factory.createPlayer("NativeSlotsA", Riiablo.NECROMANCER, 10, 10);
      int second = factory.createPlayer("NativeSlotsB", Riiablo.NECROMANCER, 20, 10);
      assertEquals(first, slots.playerAtSlot(0), "allocation, not the next ECS tick, registers the head");
      assertEquals(second, slots.playerAtSlot(1));
      world.process();
      assertEquals(first, slots.playerAtSlot(0));
      assertEquals(second, slots.playerAtSlot(1));
      assertEquals(com.riiablo.engine.Engine.INVALID_ENTITY, slots.playerAtSlot(2));
    } finally {
      world.dispose();
      Riiablo.engine = previous;
    }
  }
}
