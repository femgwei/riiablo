package com.riiablo.engine.client;

import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/** Applies received wallet/ammo values without evaluating a spend or pickup. */
final class ClientPlayerItemSnapshotWriter {
  private ClientPlayerItemSnapshotWriter() {}
  static void apply(CharData character, AuthoritativePlayerItemState.Snapshot snapshot) {
    if (snapshot.walletPresent) VendorPricing.setGoldSnapshot(character,
        (int) Math.min(Integer.MAX_VALUE, snapshot.gold),
        (int) Math.min(Integer.MAX_VALUE, snapshot.goldBank));
    ItemData items = character.getItems();
    if (snapshot.ammoPresent) {
      Item ammo = items.findItemById(snapshot.ammoItemId);
      if (ammo == null) ammo = items.getEquippedRangedAmmo();
      if (ammo == null) ammo = items.getEquippedThrowableWeapon();
      if (ammo != null && ammo.attrs != null) {
        ammo.attrs.base().put(Stat.quantity, snapshot.ammoQuantity);
        ammo.attrs.aggregate().put(Stat.quantity, snapshot.ammoQuantity);
      }
    } else {
      Item stale = items.getEquippedRangedAmmo();
      if (stale != null && VendorPricing.isQuiver(stale)) items.removeOwnedItem(stale);
    }
  }
}
