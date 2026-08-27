package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.save.CharData;
import com.riiablo.item.VendorPricing;
import com.riiablo.engine.server.item.GroundDropOwnership;

public class ServerItemManager extends ItemManager {
  private static final String TAG = "ServerItemManager";

  @Override
  public void groundToCursor(int entityId, int dst) {
    com.riiablo.item.Item ground = mItem.get(dst).item;
    if (ground != null && "gld".equalsIgnoreCase(ground.code)) {
      if (!GroundDropOwnership.canPickup(dst, entityId)) return;
      int amount = ground.attrs == null || ground.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null
          ? 0 : ground.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt();
      if (amount > 0) VendorPricing.grantGold(mPlayer.get(entityId).data, amount);
      world.delete(dst);
      com.riiablo.logger.LogManager.getLogger(ServerItemManager.class).info(
          "[GOLD_PICKUP] player={} entity={} amount={}", entityId, dst, amount);
      return;
    }
    super.groundToCursor(entityId, dst);
    GroundDropOwnership.clear(dst);
    world.delete(dst);
  }

  @Override
  public void cursorToGround(int entityId) {
    CharData charData = mPlayer.get(entityId).data;
    com.riiablo.item.Item item = charData.getItems().getCursor();
    super.cursorToGround(entityId);

    Vector2 position = mPosition.get(entityId).position;
    int droppedEntity = factory.createItem(item, position);
    if (droppedEntity >= 0 && mItem.has(droppedEntity)) {
      com.riiablo.engine.server.component.Item dropped = mItem.get(droppedEntity);
      dropped.dropOwnerId = entityId;
      dropped.dropOwnerUntilMillis = System.currentTimeMillis() + 10_000L;
      GroundDropOwnership.register(droppedEntity, entityId, 10_000L);
    }
  }
}
