package com.d2moo.testutil;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class Slot8FactoryTest extends RiiabloTest {
  @Test void realNpcBarbAllocationRegistersIndependentSlot8Membership() {
    ServerEntityFactory factory = new ServerEntityFactory();
    World previous = Riiablo.engine;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new com.riiablo.engine.server.CofManager(),
            new com.riiablo.engine.server.Pathfinder(), new com.riiablo.engine.server.ItemManager(),
            new com.riiablo.engine.server.ObjectInteractor(), new com.riiablo.engine.server.WarpInteractor(),
            new com.riiablo.engine.server.ItemInteractor(), factory, new NativePlayerTargetSlots()).build()
        .register("factory", factory).register("map", mock(Map.class))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    Riiablo.engine = world;
    try {
      int checked = 0;
      for (MonStats.Entry row : Riiablo.files.monstats) {
        if (!"NpcBarb".equals(row.AI)) continue;
        int id = factory.createMonster(Riiablo.files.monstats.index(row.Id), 10, 10);
        assertTrue(id >= 0, row.Id);
        NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(id);
        assertNotNull(node, row.Id);
        assertTrue(node.isNode8(), row.Id);
        assertFalse(node.isNode9(), row.Id);
        assertEquals(-1, node.ownerId(), row.Id);
        checked++;
      }
      assertTrue(checked > 0, "retail NpcBarb rows must be exercised");
    } finally { world.dispose(); Riiablo.engine = previous; }
  }
}
