package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ClientVendorSystemTest extends RiiabloTest {
  @Test void deathBeforeTickRejectsQueuedSale() {
    CharData data = CharData.obtain().clear().set(0, false, "Vendor", Riiablo.AMAZON);
    Item item = ClientInventoryQuestReceiverTest.item(12); data.getItems().addToInventory(item);
    ClientVendorSystem system = new ClientVendorSystem(() -> data, () -> 1);
    system.lifecycle = mock(ClientUnitLifecycleSystem.class);
    system.sell(12, null, null, false, () -> true, sold -> fail("dead player cannot sell"));
    when(system.lifecycle.isPlayerDead(1)).thenReturn(true);
    system.processSystem();
    assertTrue(data.getItems().contains(item));
  }
  @Test void closedSessionCannotSellQueuedItem() {
    CharData data = CharData.obtain().clear().set(0, false, "Vendor", Riiablo.AMAZON);
    Item item = ClientInventoryQuestReceiverTest.item(12); data.getItems().addToInventory(item);
    ClientVendorSystem system = new ClientVendorSystem(() -> data);
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      AtomicBoolean open = new AtomicBoolean(true), completed = new AtomicBoolean();
      assertTrue(system.sell(12, null, null, false, open::get, sold -> completed.set(true)));
      assertTrue(data.getItems().contains(item));
      open.set(false); world.process();
      assertTrue(data.getItems().contains(item)); assertFalse(completed.get());
    } finally { world.dispose(); }
  }
  @Test void remoteWorldCannotExecuteLocalVendorRules() {
    CharData data = mock(CharData.class);
    ClientVendorSystem system = new ClientVendorSystem(() -> data);
    system.receiver = mock(ClientNetworkReceiver.class);
    assertFalse(system.repairAll(null, () -> true, result -> fail("local result")));
    verifyNoInteractions(data);
  }
}
