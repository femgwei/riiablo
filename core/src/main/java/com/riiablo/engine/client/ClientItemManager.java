package com.riiablo.engine.client;

import java.util.HashSet;
import java.util.Set;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.quest.QuestWarp;
import com.riiablo.engine.server.item.GroundDropPosition;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.engine.server.portal.TownPortalRegistry;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.VendorPricing;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.ItemController;

import net.mostlyoriginal.api.system.core.PassiveSystem;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import com.riiablo.engine.client.event.ClientItemPickupEvent;
import com.riiablo.engine.client.event.ClientItemDropReplayEvent;

public class ClientItemManager extends PassiveSystem implements ItemController {
  private static final String TAG = "ClientItemManager";
  private static final Logger log = LogManager.getLogger(ClientItemManager.class);

  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "townPortalRegistry")
  protected TownPortalRegistry townPortalRegistry;

  protected final ClientItemContextPort clientContext;
  @com.artemis.annotations.SkipWire
  private ClientItemIntentSystem intents;

  public ClientItemIntentSystem intents() {
    if (intents == null) intents = new ClientItemIntentSystem(this);
    return intents;
  }

  /** Legacy construction is retained for isolated fixtures; GameScreen supplies a context. */
  public ClientItemManager() {
    this(ClientItemContextPort.fromGlobals());
  }

  public ClientItemManager(ClientItemContextPort clientContext) {
    this.clientContext = java.util.Objects.requireNonNull(clientContext, "clientContext");
  }

  /** Read-only context access for the presentation-owned client host recipe. */
  public ClientItemContextPort clientContext() {
    return clientContext;
  }

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
      VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(clientContext.charData(), amount);
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
      if (clientContext.charData() == null || clientContext.charData().getItems().getCursor() != null) {
        log.info("[GROUND_PICKUP] phase=reject mode=local entity={} item={} reason=cursor_occupied",
            entityId, item.id);
        return;
      }
      clientContext.charData().groundToCursor(item);
      world.delete(entityId);
      dispatchQuestPickup(item, entityId);
      log.info("[GROUND_PICKUP] phase=cursor mode=local entity={} item={} code={}",
          entityId, item.id, item.code);
      return;
    }
    boolean stored = clientContext.charData() != null
        && clientContext.charData().getItems().addGroundPickup(item, clientContext.charData());
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
    dispatchQuestPickup(item, entityId);
    log.info("[GROUND_PICKUP] phase=stored mode=local entity={} item={} location={} store={}",
        entityId, item.id, item.location, item.storeLoc);
  }

  protected boolean replayGroundDrop(int entityId) {
    EventSystem events = world.getSystem(EventSystem.class);
    if (events == null) return false;
    events.dispatch(ClientItemDropReplayEvent.obtain(entityId));
    return true;
  }

  /** Quest pickup is a consequence of a successful local mutation, never of UI intent capture. */
  protected void dispatchQuestPickup(com.riiablo.item.Item item, int entityId) {
    if (item == null || item.code == null) return;
    EventSystem events = world.getSystem(EventSystem.class);
    int player = clientContext.playerId();
    if (events != null && player >= 0) {
      events.dispatch(QuestItemPickedUpEvent.obtain(player, entityId, item.code));
    }
  }

  /** Native D2 uses one generic sound when an item is taken by the player. */
  public void playPickupSound() { dispatchSound("item_pickup"); }

  /** Publishes a one-shot item presentation sound without resolving an audio device here. */
  protected void dispatchSound(String sound) {
    if (sound == null || sound.isEmpty() || world == null) return;
    EventSystem events = world.getSystem(EventSystem.class);
    int source = clientContext.playerId();
    if (events != null && source >= 0) events.dispatch(SoundEvent.obtain(source, sound, true));
  }

  /** Handles a committed network pickup after the receiver's simulation phase. */
  @Subscribe
  public void onNetworkPickup(ClientItemPickupEvent event) {
    if (event != null) playPickupSound();
  }

  protected boolean prefersCursorPickup(com.riiablo.item.Item item) {
    return prefersCursorPickup(item, clientContext.inventoryVisible());
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
    if (clientContext.charData() == null) return;
    int player = clientContext.playerId();
    if (player < 0 || mPosition.get(player) == null) return;
    com.riiablo.item.Item item = clientContext.charData().getItems().getCursor();
    clientContext.charData().cursorToGround();

    Vector2 position = freeGroundPosition(player, mPosition.get(player).position);
    factory.createItem(item, position);
  }

  private Vector2 freeGroundPosition(int playerId, Vector2 origin) {
    Set<Long> occupied = new HashSet<>();
    MapWrapper playerMap = mMapWrapper.has(playerId) ? mMapWrapper.get(playerId) : null;
    if (playerMap != null && playerMap.map != null) {
      IntBag entities = world.getAspectSubscriptionManager().get(
          Aspect.all(Item.class, Position.class, MapWrapper.class)).getEntities();
      ComponentMapper<Item> items = mItem;
      ComponentMapper<Position> positions = mPosition;
      ComponentMapper<MapWrapper> maps = mMapWrapper;
      for (int i = 0; i < entities.size(); i++) {
        int id = entities.get(i);
        Item item = items.get(id);
        Position position = positions.get(id);
        MapWrapper map = maps.get(id);
        if (item == null || item.item == null || position == null || map == null
            || map.map != playerMap.map) continue;
        occupied.add(GroundDropPosition.key(Math.round(position.position.x),
            Math.round(position.position.y)));
      }
    }
    return GroundDropPosition.findFree(origin.x, origin.y, occupied, 8, new Vector2());
  }

  @Override
  public void storeToCursor(int i) {
    if (clientContext.charData() != null) clientContext.charData().storeToCursor(i);
  }

  @Override
  public boolean inventoryToBelt(int i) {
    if (clientContext.charData() == null || i < 0 || i >= clientContext.charData().getItems().getItems().size)
      return false;
    com.riiablo.item.Item item = clientContext.charData().getItems().getItems().get(i);
    int slot = clientContext.charData().getItems().findFreeBeltSlot(item);
    return slot >= 0 && clientContext.charData().getItems().movePotionToBelt(
        item, slot & 3, slot >>> 2);
  }

  @Override
  public void cursorToStore(StoreLoc storeLoc, int x, int y) {
    if (clientContext.charData() != null) clientContext.charData().cursorToStore(storeLoc, x, y);
  }

  @Override
  public void swapStoreItem(int i, StoreLoc storeLoc, int x, int y) {
    if (clientContext.charData() != null) clientContext.charData().swapStoreItem(i, storeLoc, x, y);
  }

  @Override
  public void bodyToCursor(BodyLoc bodyLoc, boolean merc) {
    if (clientContext.charData() != null) clientContext.charData().bodyToCursor(bodyLoc, merc);
  }

  @Override
  public void cursorToBody(BodyLoc bodyLoc, boolean merc) {
    if (clientContext.charData() != null) clientContext.charData().cursorToBody(bodyLoc, merc);
  }

  @Override
  public void swapBodyItem(BodyLoc bodyLoc, boolean merc) {
    if (clientContext.charData() != null) clientContext.charData().swapBodyItem(bodyLoc, merc);
  }

  @Override
  public void beltToCursor(int i) {
    if (clientContext.charData() != null) clientContext.charData().beltToCursor(i);
  }

  @Override
  public void cursorToBelt(int x, int y) {
    if (clientContext.charData() != null) clientContext.charData().cursorToBelt(x, y);
  }

  @Override
  public void swapBeltItem(int i) {
    if (clientContext.charData() != null) clientContext.charData().swapBeltItem(i);
  }

  @Override
  public void useBeltSlot(int column) {
    if (clientContext.charData() == null) return;
    com.riiablo.item.Item potion = clientContext.charData().getItems().getBeltPotion(column);
    if (potion != null && clientContext.charData().useBeltPotion(column)) {
      dispatchSound(potion.getUseSound());
    }
  }

  @Override
  public boolean useCursorPotionOnMercenary() {
    if (clientContext.charData() == null || !clientContext.isGameActive()) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=game_state_missing");
      return false;
    }
    com.riiablo.item.Item potion = clientContext.charData().getItems().getCursor();
    if (potion == null) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=empty_cursor");
      return false;
    }
    final int itemId = potion.id;
    final String itemCode = potion.code;
    if (!com.riiablo.engine.server.MercenaryPotionSystem.isHealingPotion(potion)) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=not_healing_potion item={} code={} type={} location={}",
          itemId, itemCode, potion.type, potion.location);
      return false;
    }
    com.riiablo.engine.server.MercenaryPotionSystem service =
        world.getSystem(com.riiablo.engine.server.MercenaryPotionSystem.class);
    if (service == null) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=service_missing item={} code={}",
          itemId, itemCode);
      return false;
    }
    int player = clientContext.playerId();
    if (player < 0 || !service.useOnMercenary(player, potion)) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=service_rejected item={} code={} player={}",
          itemId, itemCode, player);
      return false;
    }
    if (!clientContext.charData().consumeCursorPotion(potion)) {
      log.info("[MERC_POTION] phase=rejected mode=local reason=consume_failed item={} code={}",
          itemId, itemCode);
      return false;
    }
    dispatchSound(potion.getUseSound());
    log.info("[MERC_POTION] phase=used mode=local item={} code={} player={}", itemId, itemCode,
        player);
    return true;
  }

  @Override
  public void useInventoryItem(com.riiablo.item.Item item) {
    if ("isc".equalsIgnoreCase(item == null ? null : item.code)
        || "ibk".equalsIgnoreCase(item == null ? null : item.code)) {
      if (clientContext.cursor() != null) clientContext.cursor().beginIdentify(item, intents());
      return;
    }
    if (item == null || item.code == null || clientContext.charData() == null
        || !clientContext.isGameActive() || clientContext.playerId() < 0) return;
    if (item.type != null && item.type.is(com.riiablo.item.Type.POTI)) {
      if (clientContext.charData().useInventoryPotion(item)) dispatchSound(item.getUseSound());
      return;
    }
    if (!"tsc".equalsIgnoreCase(item.code) && !"tbk".equalsIgnoreCase(item.code)) return;
    int player = clientContext.playerId();
    MapWrapper wrapper = mMapWrapper == null ? null : mMapWrapper.get(player);
    Position playerPosition = mPosition.get(player);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.isTown() || playerPosition == null) return;

    Vector2 portalPosition = new Vector2(playerPosition.position);
    if (!wrapper.zone.findFreeCoordinates(portalPosition, 1, 16, true, portalPosition)) return;
    int sourceLevel = wrapper.zone.level == null ? -1 : wrapper.zone.level.Id;
    int destinationTown = TownPortalRegistry.townLevelForAct(
        wrapper.zone.level == null ? -1 : wrapper.zone.level.Act);
    if (destinationTown < 0) return;
    if (clientContext.files() == null || clientContext.files().Levels == null) return;
    Map.Zone townZone = sourceLevel <= 0 || clientContext.files().Levels.get(destinationTown) == null
        || wrapper.map == null ? null
        : wrapper.map.findZone(clientContext.files().Levels.get(destinationTown));
    Vector2 returnPosition = TownPortalRegistry.preferredTownPosition(townZone, world);
    if (returnPosition == null
        || townPortalRegistry == null
        || !townPortalRegistry.findFreeTownPosition(townZone, returnPosition, 1, 64,
            returnPosition)) return;
    int visual = factory.createStaticObjectByClassId(
        com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL,
        portalPosition.x, portalPosition.y);
    int warp = factory.createWarp(wrapper.zone, QuestWarp.encode(destinationTown),
        portalPosition.x, portalPosition.y);
    int returnVisual = factory.createStaticObjectByClassId(
        com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL,
        returnPosition.x, returnPosition.y);
    int returnWarp = factory.createWarp(townZone, QuestWarp.encode(sourceLevel),
        returnPosition.x, returnPosition.y);
    if (warp < 0 || returnWarp < 0 || visual < 0 || returnVisual < 0) {
      if (visual >= 0) world.delete(visual);
      if (returnVisual >= 0) world.delete(returnVisual);
      if (returnWarp >= 0) world.delete(returnWarp);
      return;
    }
    if (!clientContext.charData().getItems().consumeStoredItem(item)) {
      world.delete(warp);
      if (visual >= 0) world.delete(visual);
      world.delete(returnWarp);
      if (returnVisual >= 0) world.delete(returnVisual);
      return;
    }
    townPortalRegistry.replace(player, world, wrapper.zone, portalPosition,
        visual, warp, townZone, returnPosition, returnVisual, returnWarp);
    dispatchSound("player_townportal_cast");
  }

  @Override
  public boolean identifyItem(com.riiablo.item.Item source, com.riiablo.item.Item target) {
    if (clientContext.charData() == null
        || !clientContext.charData().getItems().identifyItem(source, target)) return false;
    dispatchSound("cursor_identify_item");
    return true;
  }

  @Override
  public void dropGold(int amount) {
    if (clientContext.charData() == null || amount <= 0) return;
    StatRef carriedRef = clientContext.charData().getStats().get(Stat.gold);
    if (carriedRef == null || amount > carriedRef.asInt()) return;
    int player = clientContext.playerId();
    if (player < 0) return;
    Position position = mPosition.get(player);
    ItemGenerator generator = world.getSystem(ItemGenerator.class);
    com.riiablo.item.Item gold = generator == null ? null : generator.generate("gld");
    if (position == null || gold == null) return;
    gold.quality = com.riiablo.item.Quality.NORMAL;
    gold.flags |= com.riiablo.item.Item.ITEMFLAG_IDENTIFIED;
    gold.attrs.base().put(Stat.quantity, amount);
    gold.attrs.aggregate().put(Stat.quantity, amount);
    Vector2 dropPosition = freeGroundPosition(player, position.position);
    int entityId = factory.createItem(gold, dropPosition);
    if (entityId >= 0) {
      VendorPricing.dropCarriedGold(clientContext.charData(), amount);
    }
  }

  @Override
  public void depositGold(int amount) {
    if (clientContext.charData() != null) VendorPricing.depositGold(clientContext.charData(), amount);
  }

  @Override
  public void withdrawGold(int amount) {
    if (clientContext.charData() != null) VendorPricing.withdrawGold(clientContext.charData(), amount);
  }
}

