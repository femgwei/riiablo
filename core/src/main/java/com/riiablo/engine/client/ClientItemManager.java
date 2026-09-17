package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Position;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.VendorPricing;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.ItemController;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public class ClientItemManager extends PassiveSystem implements ItemController {
  private static final String TAG = "ClientItemManager";
  private static final Logger log = LogManager.getLogger(ClientItemManager.class);

  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Position> mPosition;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Override
  public void groundToCursor(int entityId) {
    Item component = mItem.get(entityId);
    com.riiablo.item.Item item = component == null ? null : component.item;
    if (item == null) {
      log.warn("[GROUND_PICKUP] phase=reject mode=local entity={} reason=item_missing", entityId);
      return;
    }
    if ("gld".equalsIgnoreCase(item.code)) {
      int amount = quantity(item);
      log.info("[GROUND_PICKUP] phase=gold_request mode=local entity={} item={} amount={}",
          entityId, item.id, amount);
      VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(Riiablo.charData, amount);
      if (grant.credited <= 0) {
        log.info("[GROUND_PICKUP] phase=reject mode=local entity={} reason={} amount={}",
            entityId, amount <= 0 ? "invalid_quantity" : "gold_limit", amount);
        return;
      }
      if (grant.remaining > 0) {
        item.attrs.base().put(Stat.quantity, grant.remaining);
        item.attrs.aggregate().put(Stat.quantity, grant.remaining);
      } else {
        world.delete(entityId);
      }
      playPickupSound();
      log.info(
          "[GROUND_PICKUP] phase=gold_granted mode=local entity={} credited={} remaining={}",
          entityId, grant.credited, grant.remaining);
      return;
    }
    boolean stored = Riiablo.charData != null
        && Riiablo.charData.getItems().addGroundPickup(item);
    if (!stored) {
      log.info("[GROUND_PICKUP] phase=reject mode=local entity={} item={} reason=no_space",
          entityId, item.id);
      return;
    }
    world.delete(entityId);
    playPickupSound();
    log.info("[GROUND_PICKUP] phase=stored mode=local entity={} item={} location={} store={}",
        entityId, item.id, item.location, item.storeLoc);
  }

  /** Native D2 uses one generic sound when an item is taken by the player. */
  protected static void playPickupSound() {
    if (Riiablo.audio != null) Riiablo.audio.play("item_pickup", true);
  }

  private static int quantity(com.riiablo.item.Item item) {
    if (item == null || item.attrs == null) return 0;
    StatRef quantity = item.attrs.base().get(Stat.quantity);
    return quantity == null ? 0 : Math.max(0, quantity.asInt());
  }

  @Override
  public void cursorToGround() {
    com.riiablo.item.Item item = Riiablo.charData.getItems().getCursor();
    Riiablo.charData.cursorToGround();

    Vector2 position = mPosition.get(Riiablo.game.player).position;
    factory.createItem(item, position);
  }

  @Override
  public void storeToCursor(int i) {
    Riiablo.charData.storeToCursor(i);
  }

  @Override
  public void cursorToStore(StoreLoc storeLoc, int x, int y) {
    Riiablo.charData.cursorToStore(storeLoc, x, y);
  }

  @Override
  public void swapStoreItem(int i, StoreLoc storeLoc, int x, int y) {
    Riiablo.charData.swapStoreItem(i, storeLoc, x, y);
  }

  @Override
  public void bodyToCursor(BodyLoc bodyLoc, boolean merc) {
    Riiablo.charData.bodyToCursor(bodyLoc, merc);
  }

  @Override
  public void cursorToBody(BodyLoc bodyLoc, boolean merc) {
    Riiablo.charData.cursorToBody(bodyLoc, merc);
  }

  @Override
  public void swapBodyItem(BodyLoc bodyLoc, boolean merc) {
    Riiablo.charData.swapBodyItem(bodyLoc, merc);
  }

  @Override
  public void beltToCursor(int i) {
    Riiablo.charData.beltToCursor(i);
  }

  @Override
  public void cursorToBelt(int x, int y) {
    Riiablo.charData.cursorToBelt(x, y);
  }

  @Override
  public void swapBeltItem(int i) {
    Riiablo.charData.swapBeltItem(i);
  }

  @Override
  public void useBeltSlot(int column) {
    if (Riiablo.charData == null) return;
    com.riiablo.item.Item potion = Riiablo.charData.getItems().getBeltPotion(column);
    if (potion != null && Riiablo.charData.useBeltPotion(column)) {
      Riiablo.audio.play(potion.getUseSound(), true);
    }
  }
}
