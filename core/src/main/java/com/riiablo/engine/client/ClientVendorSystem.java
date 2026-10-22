package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.utils.Array;
import com.riiablo.codec.excel.Npc;
import com.riiablo.engine.server.npc.NpcRepairService;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.VendorGenerator;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.IntSupplier;

/** Local vendor write owner. UI submits stable ids and consumes committed results. */
@Wire(failOnNull = false)
public final class ClientVendorSystem extends BaseSystem {
  private final Supplier<CharData> character;
  private final IntSupplier player;
  private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
  protected ClientNetworkReceiver receiver;
  protected ClientUnitLifecycleSystem lifecycle;

  public ClientVendorSystem(Supplier<CharData> character) { this(character, () -> -1); }
  public ClientVendorSystem(Supplier<CharData> character, IntSupplier player) {
    this.character = character; this.player = player;
  }
  private boolean enqueue(BooleanSupplier valid, Consumer<CharData> action) {
    CharData captured = character.get();
    if (captured == null || receiver != null) return false;
    pending.add(() -> {
      if (receiver == null && character.get() == captured && valid.getAsBoolean()
          && (lifecycle == null || !lifecycle.isPlayerDead(player.getAsInt()))) action.accept(captured);
    });
    return true;
  }

  public boolean repair(int id, Npc.Entry pricing, BooleanSupplier valid,
      Consumer<NpcRepairService.Result> result) {
    return enqueue(valid, data -> {
      Item item = data.getItems().findItemById(id);
      if (item != null) result.accept(NpcRepairService.repairItem(
          data, pricing, data.getItems().indexOf(item), id));
    });
  }
  public boolean repairAll(Npc.Entry pricing, BooleanSupplier valid,
      Consumer<NpcRepairService.Result> result) {
    return enqueue(valid, data -> result.accept(NpcRepairService.repairAll(data, pricing)));
  }
  public boolean sell(int id, Npc.Entry pricing, Array<Item> stock, boolean addToStock,
      BooleanSupplier valid, Consumer<Item> result) {
    return enqueue(valid, data -> {
      Item item = data.getItems().findItemById(id);
      if (item == null || !VendorPricing.sell(data, data.getItems().indexOf(item), pricing, data.diff)) return;
      if (stock != null) {
        item.flags2 |= Item.ITEMFLAG2_INSTORE;
        item.location = Location.STORED; item.storeLoc = StoreLoc.NONE;
        item.gridX = 0; item.gridY = 0; item.vendorPrice = -1;
        if (addToStock && !VendorPricing.isQuiver(item) && !stock.contains(item, true)) stock.insert(0, item);
      }
      result.accept(item);
    });
  }
  public boolean buy(int id, Npc.Entry pricing, Array<Item> stock, boolean gamble,
      boolean toCursor, VendorGenerator generator, BooleanSupplier valid, Consumer<Item> result) {
    return enqueue(valid, data -> {
      if (stock == null) return;
      Item item = null;
      for (Item candidate : stock) if (candidate.id == id) { item = candidate; break; }
      if (item == null) return;
      boolean bought = gamble
          ? (toCursor ? VendorPricing.gambleToCursor(data, item) : VendorPricing.gamble(data, item))
          : (toCursor ? VendorPricing.buyToCursor(data, item, pricing) : VendorPricing.buy(data, item, pricing));
      if (!bought) return;
      int index = stock.indexOf(item, true);
      Item replacement = !gamble && VendorPricing.isInfiniteStockItem(item) && generator != null
          ? generator.restock(item, data.diff) : null;
      if (replacement != null && index >= 0) stock.set(index, replacement);
      else stock.removeValue(item, true);
      result.accept(item);
    });
  }
  @Override protected void processSystem() {
    for (int count = pending.size(); count > 0; count--) pending.removeFirst().run();
  }
  @Override protected void dispose() { pending.clear(); }
}
