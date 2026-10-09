package com.riiablo.engine.server;

import java.util.HashSet;
import java.util.Set;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.save.CharData;
import com.riiablo.item.VendorPricing;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.engine.server.item.GroundDropPosition;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.Party;
import com.artemis.annotations.Wire;

public class ServerItemManager extends ItemManager {
  private static final String TAG = "ServerItemManager";

  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  @Override
  public void groundToCursor(int entityId, int dst) {
    com.riiablo.item.Item ground = mItem.get(dst).item;
    int partyId = partyManager == null ? Party.INVALID_ID : partyManager.getPartyId(entityId);
    if (ground != null && "gld".equalsIgnoreCase(ground.code)) {
      if (!GroundDropOwnership.claim(dst, entityId, partyId)) return;
      int amount = ground.attrs == null || ground.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null
          ? 0 : ground.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt();
      VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(mPlayer.get(entityId).data, amount);
      if (grant.remaining > 0 && ground.attrs != null) {
        ground.attrs.base().put(com.riiablo.attributes.Stat.quantity, grant.remaining);
        ground.attrs.aggregate().put(com.riiablo.attributes.Stat.quantity, grant.remaining);
        GroundDropOwnership.release(dst);
      } else if (grant.credited > 0) {
        world.delete(dst);
        GroundDropOwnership.clear(dst);
      } else {
        GroundDropOwnership.release(dst);
      }
      com.riiablo.logger.LogManager.getLogger(ServerItemManager.class).info(
          "[GOLD_PICKUP] player={} entity={} amount={} credited={} remaining={}",
          entityId, dst, amount, grant.credited, grant.remaining);
      return;
    }
    if (!GroundDropOwnership.claim(dst, entityId, partyId)) return;
    try {
      CharData character = mPlayer.get(entityId).data;
      boolean stored = character != null
          && character.getItems().addGroundPickup(ground, character);
      if (stored) {
        if (ground.code != null) {
          event.dispatch(com.riiablo.engine.server.event.QuestItemPickedUpEvent.obtain(
              entityId, dst, ground.code));
        }
        GroundDropOwnership.clear(dst);
        world.delete(dst);
      } else {
        GroundDropOwnership.release(dst);
      }
    } catch (RuntimeException | Error t) {
      GroundDropOwnership.release(dst);
      throw t;
    }
  }

  @Override
  public void cursorToGround(int entityId) {
    CharData charData = mPlayer.get(entityId).data;
    com.riiablo.item.Item item = charData.getItems().getCursor();
    super.cursorToGround(entityId);

    Vector2 position = freeGroundPosition(entityId, mPosition.get(entityId).position);
    int droppedEntity = factory.createItem(item, position);
    // The ground component keeps the same Item instance that was removed
    // from the cursor.  Replace its inventory id with the authoritative ECS
    // entity id before broadcasting or serializing the drop.
    if (droppedEntity >= 0 && item != null) item.id = droppedEntity;
    if (droppedEntity >= 0 && mItem.has(droppedEntity)) {
      com.riiablo.engine.server.component.Item dropped = mItem.get(droppedEntity);
      GroundDropOwnership.applyMetadata(dropped, entityId, -1,
          10_000L, 0L, false);
      GroundDropOwnership.register(droppedEntity, entityId, 10_000L);
    }
  }

  private Vector2 freeGroundPosition(int playerId, Vector2 origin) {
    Set<Long> occupied = new HashSet<>();
    MapWrapper playerMap = world.getMapper(MapWrapper.class).get(playerId);
    if (playerMap != null && playerMap.map != null) {
      IntBag entities = world.getAspectSubscriptionManager().get(
          Aspect.all(Item.class, Position.class, MapWrapper.class)).getEntities();
      ComponentMapper<Item> items = world.getMapper(Item.class);
      ComponentMapper<Position> positions = world.getMapper(Position.class);
      ComponentMapper<MapWrapper> maps = world.getMapper(MapWrapper.class);
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
}
