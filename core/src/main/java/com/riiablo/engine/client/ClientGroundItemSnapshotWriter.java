package com.riiablo.engine.client;

import com.riiablo.io.ByteInput;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;

/** Validates bytes before replacing either the item or its pickup window. */
final class ClientGroundItemSnapshotWriter {
  private ClientGroundItemSnapshotWriter() {}
  static Item decode(AuthoritativeGroundItemState.Snapshot snapshot, ItemReader reader, Item previous) {
    Item item = snapshot.hasItemData() ? reader.readItemStrict(ByteInput.wrap(snapshot.itemData())) : previous;
    if (item == null) throw new IllegalArgumentException("Ground snapshot has no item");
    item.id = snapshot.serverEntityId;
    return item;
  }
  static void apply(com.riiablo.engine.server.component.Item local, Item item,
      AuthoritativeGroundItemState.Snapshot snapshot) {
    local.item = item;
    local.dropOwnerId = snapshot.ownerId; local.dropOwnerUntilMillis = snapshot.ownerUntilMillis;
    local.dropPartyId = snapshot.partyId; local.dropPartyUntilMillis = snapshot.partyUntilMillis;
    local.partyShareGold = snapshot.partyShareGold;
  }
}
