package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemController;
import java.util.ArrayDeque;
import java.util.function.IntConsumer;

/** UI captures item identities; only a simulation tick invokes the local or remote owner. */
@Wire(failOnNull = false)
public final class ClientItemIntentSystem extends BaseSystem implements ItemController {
  private final ClientItemManager owner;
  private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
  protected ClientUnitLifecycleSystem lifecycle;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mGround;
  protected ComponentMapper<com.riiablo.engine.server.component.NativePathIdentity> mIdentity;

  ClientItemIntentSystem(ClientItemManager owner) { this.owner = owner; }
  private CharData character() { return owner.clientContext.charData(); }
  private Item item(int index) {
    CharData data = character();
    return data == null || index < 0 || index >= data.getItems().getItems().size
        ? null : data.getItems().getItems().get(index);
  }
  private Item find(int id) { return character() == null ? null : character().getItems().findItemById(id); }

  private boolean enqueue(Runnable intent, boolean cursorSensitive) {
    CharData captured = character();
    if (captured == null) return false;
    Item cursor = captured.getItems().getCursor();
    boolean hadCursor = cursor != null;
    int cursorId = cursor == null ? 0 : cursor.id;
    pending.add(() -> {
      if (character() != captured) return;
      int player = owner.clientContext.playerId();
      if (lifecycle != null && lifecycle.isPlayerDead(player)) return;
      Item current = captured.getItems().getCursor();
      if (cursorSensitive && (hadCursor != (current != null)
          || current != null && current.id != cursorId)) return;
      intent.run();
    });
    return true;
  }

  private void indexed(int index, IntConsumer intent, boolean cursorSensitive) {
    Item selected = item(index);
    if (selected == null) return;
    int id = selected.id;
    enqueue(() -> {
      Item current = find(id);
      if (current != null) intent.accept(character().getItems().indexOf(current));
    }, cursorSensitive);
  }

  @Override protected void processSystem() {
    for (int count = pending.size(); count > 0; count--) pending.removeFirst().run();
  }
  @Override protected void dispose() { pending.clear(); }

  @Override public void groundToCursor(int entity) {
    if (entity < 0 || mGround == null || !mGround.has(entity) || mGround.get(entity).item == null) return;
    int id = mGround.get(entity).item.id;
    com.riiablo.engine.server.component.NativePathIdentity.Token token =
        mIdentity != null && mIdentity.has(entity) ? mIdentity.get(entity).token() : null;
    enqueue(() -> {
      if (mGround.has(entity) && mGround.get(entity).item != null && mGround.get(entity).item.id == id
          && (token == null || token.isCurrent(world, entity))) owner.groundToCursor(entity);
    }, true);
  }
  /** The legacy protocol has no weapon-set operation; remote replicas cannot settle it. */
  public boolean swapWeapons() {
    return !(owner instanceof NetworkedClientItemManager)
        && enqueue(() -> character().getItems().alternate(), false);
  }
  @Override public void cursorToGround() { enqueue(owner::cursorToGround, true); }
  @Override public void storeToCursor(int i) { indexed(i, owner::storeToCursor, true); }
  @Override public boolean inventoryToBelt(int i) {
    Item item = item(i);
    if (item == null || character().getItems().findFreeBeltSlot(item) < 0) return false;
    indexed(i, owner::inventoryToBelt, false);
    return true; // admission only; the owner revalidates at the tick
  }
  @Override public void cursorToStore(StoreLoc loc, int x, int y) {
    enqueue(() -> owner.cursorToStore(loc, x, y), true);
  }
  @Override public void swapStoreItem(int i, StoreLoc loc, int x, int y) {
    indexed(i, index -> owner.swapStoreItem(index, loc, x, y), true);
  }
  @Override public void bodyToCursor(BodyLoc loc, boolean merc) {
    equipped(loc, merc, () -> owner.bodyToCursor(loc, merc));
  }
  @Override public void cursorToBody(BodyLoc loc, boolean merc) {
    enqueue(() -> owner.cursorToBody(loc, merc), true);
  }
  @Override public void swapBodyItem(BodyLoc loc, boolean merc) {
    equipped(loc, merc, () -> owner.swapBodyItem(loc, merc));
  }
  private void equipped(BodyLoc loc, boolean merc, Runnable intent) {
    if (character() == null || loc == null) return;
    com.riiablo.save.ItemData data = merc ? character().getMerc().getItems() : character().getItems();
    Item selected = data.getEquipped(loc);
    if (selected == null) return;
    int id = selected.id;
    enqueue(() -> {
      Item current = data.getEquipped(loc);
      if (current != null && current.id == id) intent.run();
    }, true);
  }
  @Override public void beltToCursor(int i) { indexed(i, owner::beltToCursor, true); }
  @Override public void cursorToBelt(int x, int y) { enqueue(() -> owner.cursorToBelt(x, y), true); }
  @Override public void swapBeltItem(int i) { indexed(i, owner::swapBeltItem, true); }
  @Override public void useBeltSlot(int column) {
    Item item = character() == null ? null : character().getItems().getBeltPotion(column);
    if (item == null) return;
    int id = item.id;
    enqueue(() -> {
      Item current = character().getItems().getBeltPotion(column);
      if (current != null && current.id == id) owner.useBeltSlot(column);
    }, false);
  }
  @Override public boolean useCursorPotionOnMercenary() {
    Item cursor = character() == null ? null : character().getItems().getCursor();
    return cursor != null && com.riiablo.engine.server.MercenaryPotionSystem.isHealingPotion(cursor)
        && enqueue(owner::useCursorPotionOnMercenary, true);
  }
  @Override public void useInventoryItem(Item item) {
    if (item == null) return;
    int id = item.id;
    enqueue(() -> { Item current = find(id); if (current != null) owner.useInventoryItem(current); }, false);
  }
  @Override public boolean identifyItem(Item source, Item target) {
    if (character() == null || !character().getItems().canIdentifyItem(source, target)) return false;
    int sourceId = source.id, targetId = target.id;
    return enqueue(() -> owner.identifyItem(find(sourceId), find(targetId)), false);
  }
  @Override public void dropGold(int amount) { if (amount > 0) enqueue(() -> owner.dropGold(amount), false); }
  @Override public void depositGold(int amount) { if (amount > 0) enqueue(() -> owner.depositGold(amount), false); }
  @Override public void withdrawGold(int amount) { if (amount > 0) enqueue(() -> owner.withdrawGold(amount), false); }
}
