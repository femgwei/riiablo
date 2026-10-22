package com.riiablo.engine.client;

import com.badlogic.gdx.utils.Array;
import com.riiablo.io.ByteInput;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import java.util.HashSet;
import java.util.Set;

/** Decode a complete transaction before touching ownership or its revision. */
final class ClientInventorySnapshotWriter {
  private ClientInventorySnapshotWriter() {}

  static Prepared prepare(AuthoritativeInventoryState.Snapshot snapshot, ItemReader reader) {
    Array<Item> items = new Array<>(false, snapshot.entries.size(), Item.class);
    Set<Integer> ids = new HashSet<>();
    Set<BodyLoc> equipped = new HashSet<>();
    boolean cursor = false;
    for (AuthoritativeInventoryState.Entry entry : snapshot.entries) {
      if (entry.merc) throw new IllegalArgumentException("Mercenary entry in player inventory snapshot");
      Item item = reader.readItemStrict(ByteInput.wrap(entry.itemData()));
      item.id = entry.itemId;
      item.location = Location.valueOf(entry.location);
      item.storeLoc = StoreLoc.valueOf(entry.storeLoc);
      item.bodyLoc = BodyLoc.valueOf(entry.bodyLoc);
      item.gridX = (byte) entry.x; item.gridY = (byte) entry.y;
      if (!ids.add(item.id) || item.location == null || item.storeLoc == null || item.bodyLoc == null)
        throw new IllegalArgumentException("Invalid inventory snapshot entry " + item.id);
      if (item.location == Location.CURSOR) {
        if (cursor) throw new IllegalArgumentException("Multiple cursor items");
        cursor = true;
      }
      if (item.location == Location.EQUIPPED
          && (item.bodyLoc == BodyLoc.NONE || !equipped.add(item.bodyLoc)))
        throw new IllegalArgumentException("Duplicate equipment slot");
      items.add(item);
    }
    Item ground = snapshot.hasGroundItem()
        ? reader.readItemStrict(ByteInput.wrap(snapshot.groundItemData())) : null;
    if (ground != null) {
      if (!Float.isFinite(snapshot.groundX) || !Float.isFinite(snapshot.groundY))
        throw new IllegalArgumentException("Invalid ground position");
      ground.id = snapshot.groundEntityId;
    }
    return new Prepared(items, ground);
  }

  static final class Prepared {
    final Array<Item> items;
    final Item ground;
    private Prepared(Array<Item> items, Item ground) { this.items = items; this.ground = ground; }
    void apply(CharData character) { character.getItems().replaceFromAuthoritativeSnapshot(items); }
  }
}
