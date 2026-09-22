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
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.quest.QuestWarp;
import com.riiablo.item.ItemGenerator;
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
  protected ComponentMapper<MapWrapper> mMapWrapper;

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
    if (prefersCursorPickup(item)) {
      if (Riiablo.charData == null || Riiablo.charData.getItems().getCursor() != null) {
        log.info("[GROUND_PICKUP] phase=reject mode=local entity={} item={} reason=cursor_occupied",
            entityId, item.id);
        return;
      }
      Riiablo.charData.groundToCursor(item);
      world.delete(entityId);
      log.info("[GROUND_PICKUP] phase=cursor mode=local entity={} item={} code={}",
          entityId, item.id, item.code);
      return;
    }
    boolean stored = Riiablo.charData != null
        && Riiablo.charData.getItems().addGroundPickup(item, Riiablo.charData);
    if (!stored) {
      boolean replayed = replayGroundDrop(entityId);
      log.info("[GROUND_PICKUP] phase=reject mode=local entity={} item={} reason=no_space",
          entityId, item.id);
      log.info("[GROUND_DROP] phase=bounce mode=local entity={} item={} replayed={}",
          entityId, item.id, replayed);
      return;
    }
    world.delete(entityId);
    playPickupSound();
    log.info("[GROUND_PICKUP] phase=stored mode=local entity={} item={} location={} store={}",
        entityId, item.id, item.location, item.storeLoc);
  }

  protected boolean replayGroundDrop(int entityId) {
    ItemEffectManager effects = world.getSystem(ItemEffectManager.class);
    return effects != null && effects.replayDrop(entityId);
  }

  /** Native D2 uses one generic sound when an item is taken by the player. */
  protected static void playPickupSound() {
    if (Riiablo.audio != null) Riiablo.audio.play("item_pickup", true);
  }

  protected boolean prefersCursorPickup(com.riiablo.item.Item item) {
    boolean inventoryVisible = Riiablo.game != null
        && Riiablo.game.inventoryPanel != null
        && Riiablo.game.inventoryPanel.isVisible();
    return prefersCursorPickup(item, inventoryVisible);
  }

  static boolean prefersCursorPickup(com.riiablo.item.Item item, boolean inventoryVisible) {
    return inventoryVisible && item != null && !"gld".equalsIgnoreCase(item.code);
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

  @Override
  public void useInventoryItem(com.riiablo.item.Item item) {
    if (item == null || item.code == null || Riiablo.charData == null
        || Riiablo.game == null || Riiablo.game.player < 0) return;
    if (!"tsc".equalsIgnoreCase(item.code) && !"tbk".equalsIgnoreCase(item.code)) return;
    MapWrapper wrapper = mMapWrapper == null ? null : mMapWrapper.get(Riiablo.game.player);
    Position playerPosition = mPosition.get(Riiablo.game.player);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.isTown() || playerPosition == null) return;

    Vector2 portalPosition = new Vector2(playerPosition.position);
    if (!wrapper.zone.findFreeCoordinates(portalPosition, 1, 16, true, portalPosition)) return;
    int visual = factory.createStaticObjectByClassId(
        com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL,
        portalPosition.x, portalPosition.y);
    int destinationTown = townLevelForAct(wrapper.zone.level == null ? 1 : wrapper.zone.level.Act);
    int warp = factory.createWarp(wrapper.zone, QuestWarp.encode(destinationTown),
        portalPosition.x, portalPosition.y);
    if (warp < 0) {
      if (visual >= 0) world.delete(visual);
      return;
    }
    if (!Riiablo.charData.getItems().consumeStoredItem(item)) {
      world.delete(warp);
      if (visual >= 0) world.delete(visual);
      return;
    }
    wrapper.zone.addWarp(warp);
    if (Riiablo.audio != null) Riiablo.audio.play(item.getUseSound(), true);
  }

  private static int townLevelForAct(int act) {
    switch (act) {
      case 2: return 40;  // Lut Gholein
      case 3: return 75;  // Kurast Docks
      case 4: return 103; // Pandemonium Fortress
      case 5: return 109; // Harrogath
      default: return 1;  // Rogue Encampment
    }
  }

  @Override
  public void dropGold(int amount) {
    if (Riiablo.charData == null || amount <= 0) return;
    StatRef carriedRef = Riiablo.charData.getStats().get(Stat.gold);
    if (carriedRef == null || amount > carriedRef.asInt()) return;
    if (Riiablo.game == null || Riiablo.game.player < 0) return;
    Position position = mPosition.get(Riiablo.game.player);
    ItemGenerator generator = world.getSystem(ItemGenerator.class);
    com.riiablo.item.Item gold = generator == null ? null : generator.generate("gld");
    if (position == null || gold == null) return;
    gold.quality = com.riiablo.item.Quality.NORMAL;
    gold.flags |= com.riiablo.item.Item.ITEMFLAG_IDENTIFIED;
    gold.attrs.base().put(Stat.quantity, amount);
    gold.attrs.aggregate().put(Stat.quantity, amount);
    int entityId = factory.createItem(gold, position.position.x, position.position.y);
    if (entityId >= 0) {
      VendorPricing.dropCarriedGold(Riiablo.charData, amount);
    }
  }

  @Override
  public void depositGold(int amount) {
    VendorPricing.depositGold(Riiablo.charData, amount);
  }

  @Override
  public void withdrawGold(int amount) {
    VendorPricing.withdrawGold(Riiablo.charData, amount);
  }
}
