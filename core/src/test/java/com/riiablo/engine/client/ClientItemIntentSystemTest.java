package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.item.Item;
import com.riiablo.presentation.ClientGameplayContext;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class ClientItemIntentSystemTest extends RiiabloTest {
  @Test void weaponSetChangesOnlyAtTickAndCannotBeLocallySettledForReplica() {
    CharData data = character();
    ClientItemManager local = owner(data);
    int before = data.getItems().getAlternate();
    assertTrue(local.intents().swapWeapons());
    assertEquals(before, data.getItems().getAlternate());
    local.intents().processSystem();
    assertNotEquals(before, data.getItems().getAlternate());
    NetworkedClientItemManager remote = new NetworkedClientItemManager(context(data));
    assertFalse(remote.intents().swapWeapons());
    remote.intents().processSystem();
    assertNotEquals(before, data.getItems().getAlternate());
  }
  @Test void storedItemIsResolvedByIdentityAfterIndicesShift() {
    CharData data = character();
    Item first = ClientInventoryQuestReceiverTest.item(11), selected = ClientInventoryQuestReceiverTest.item(22);
    data.getItems().addToInventory(first); data.getItems().addToInventory(selected);
    ClientItemManager owner = owner(data);
    ClientItemIntentSystem intents = owner.intents();
    intents.storeToCursor(1);
    assertNull(data.getItems().getCursor());
    data.getItems().removeOwnedItem(first);
    intents.processSystem();
    assertSame(selected, data.getItems().getCursor());
  }

  @Test void removedTargetDoesNotSelectAnotherItemAtTheSameIndex() {
    CharData data = character();
    Item selected = ClientInventoryQuestReceiverTest.item(11), other = ClientInventoryQuestReceiverTest.item(22);
    data.getItems().addToInventory(selected); data.getItems().addToInventory(other);
    ClientItemManager owner = owner(data);
    owner.intents().storeToCursor(0);
    data.getItems().removeOwnedItem(selected);
    owner.intents().processSystem();
    assertNull(data.getItems().getCursor());
    assertTrue(data.getItems().contains(other));
  }

  @Test void cursorChangeAndDeathAreRevalidatedOnTick() {
    CharData data = character();
    ClientItemManager owner = owner(data);
    ClientItemIntentSystem intents = owner.intents();
    intents.cursorToGround();
    data.getItems().addToCursor(ClientInventoryQuestReceiverTest.item(22));
    intents.processSystem();
    verify(owner, never()).cursorToGround();
    intents.lifecycle = mock(ClientUnitLifecycleSystem.class);
    when(intents.lifecycle.isPlayerDead(1)).thenReturn(true);
    intents.depositGold(10); intents.processSystem();
    verify(owner, never()).depositGold(anyInt());
  }

  @Test void goldRequestIsDeferredAndDisposedIntentsCannotExecute() {
    ClientItemManager owner = owner(character());
    owner.intents().depositGold(10);
    verify(owner, never()).depositGold(anyInt());
    owner.intents().dispose(); owner.intents().processSystem();
    verify(owner, never()).depositGold(anyInt());
    owner.intents().depositGold(10); owner.intents().processSystem();
    verify(owner).depositGold(10);
  }

  @Test void remoteItemIntentDoesNotMutateLocalOwnership() {
    CharData data = character();
    Item item = ClientInventoryQuestReceiverTest.item(22); data.getItems().addToInventory(item);
    ClientGameplayContext context = context(data);
    NetworkedClientItemManager remote = new NetworkedClientItemManager(context);
    remote.socket = mock(com.badlogic.gdx.net.Socket.class);
    try {
      when(remote.socket.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
      remote.intents().storeToCursor(0);
      assertNull(data.getItems().getCursor());
      remote.intents().processSystem();
      assertNull(data.getItems().getCursor());
      assertTrue(data.getItems().contains(item));
    } catch (Exception e) { throw new AssertionError(e); }
  }
  private static CharData character() { return CharData.obtain().clear().set(0, false, "Intent", Riiablo.AMAZON); }
  private static ClientGameplayContext context(CharData data) {
    ClientGameplayContext context = mock(ClientGameplayContext.class);
    when(context.charData()).thenReturn(data); when(context.playerId()).thenReturn(1);
    return context;
  }
  private static ClientItemManager owner(CharData data) { return spy(new ClientItemManager(context(data))); }
}
