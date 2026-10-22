package com.riiablo.server.d2gs;

import static org.junit.Assert.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.engine.server.component.Player;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.save.CharData;
import java.lang.reflect.Method;
import org.junit.Test;

/** Exercises the production full-inventory transport without sockets or MPQ data. */
public class InventorySnapshotTransportTest {
  @Test public void publishesCommittedInventoryThenEmptyBaselineWithoutReplayingRules() throws Exception {
    D2GS server = new D2GS(new FileHandle("."), 0, 0);
    server.world = new World(new WorldConfigurationBuilder().build());
    try {
      int entity = server.world.create();
      CharData data = CharData.obtain();
      server.world.getMapper(Player.class).create(entity).data = data;
      server.player.put(3, entity);
      Item item = new Item();
      item.id = 701; item.code = "hp1"; item.flags = Item.ITEMFLAG_COMPACT;
      item.location = Location.STORED; item.storeLoc = StoreLoc.INVENTORY;
      item.bodyLoc = BodyLoc.NONE; item.gridX = 2; item.gridY = 1;
      data.getItems().getItems().add(item);

      publish(server, true);
      Packet packet = server.outPackets.poll();
      assertNotNull(packet); assertEquals(1 << 3, packet.id);
      assertEquals(D2GSData.ItemMoveResult, packet.data.dataType());
      ItemMoveResult result = (ItemMoveResult) packet.data.data(new ItemMoveResult());
      assertEquals(0, result.requestId()); assertEquals(1, result.revision());
      assertEquals(1, result.snapshotLength()); assertEquals(-1, result.groundEntityId());
      assertEquals(701, result.snapshot(0).itemId());
      assertEquals(2, result.snapshot(0).x()); assertEquals(1, result.snapshot(0).y());
      assertEquals('J', result.snapshot(0).itemData(0));
      assertEquals('M', result.snapshot(0).itemData(1));
      assertSame(item, data.getItems().getItems().first());

      data.getItems().getItems().clear();
      publish(server, false);
      result = (ItemMoveResult) server.outPackets.remove().data.data(new ItemMoveResult());
      assertEquals(1, result.revision()); assertEquals(0, result.snapshotLength());
      assertEquals(1, server.authoritativeItems.revision(entity));
    } finally { server.world.dispose(); }
  }

  @Test public void missingPlayerDoesNotPublishOrAdvanceInventory() throws Exception {
    D2GS server = new D2GS(new FileHandle("."), 0, 0);
    publish(server, true);
    assertTrue(server.outPackets.isEmpty());
  }

  private static void publish(D2GS server, boolean mutated) throws Exception {
    Method method = D2GS.class.getDeclaredMethod("publishInventorySnapshot", int.class, boolean.class);
    method.setAccessible(true); method.invoke(server, 3, mutated);
  }
}
