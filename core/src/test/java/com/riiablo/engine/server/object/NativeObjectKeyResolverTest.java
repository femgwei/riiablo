package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

class NativeObjectKeyResolverTest extends RiiabloTest {
  @Test
  void unlockedObjectDoesNotInspectInventory() {
    assertEquals(NativeObjectKeyResolver.Result.NOT_LOCKED,
        NativeObjectKeyResolver.unlock(3, null));
  }

  @Test
  void assassinBypassesLockedChestWithoutKey() {
    CharData assassin = character((byte) Riiablo.ASSASSIN);
    assertEquals(NativeObjectKeyResolver.Result.ASSASSIN_BYPASS,
        NativeObjectKeyResolver.unlock(0x83, assassin));
    assertTrue(assassin.getItems().getItems().isEmpty());
  }

  @Test
  void normalClassRequiresAndConsumesInventoryPageKey() {
    CharData amazon = character((byte) Riiablo.AMAZON);
    int[] removals = {0};
    amazon.getItems().addStoreListener(new ItemData.StoreListener() {
      @Override
      public void onAdded(ItemData items, StoreLoc storeLoc, Item item) {}

      @Override
      public void onRemoved(ItemData items, StoreLoc storeLoc, Item item) {
        removals[0]++;
      }
    });
    assertEquals(NativeObjectKeyResolver.Result.MISSING_KEY,
        NativeObjectKeyResolver.unlock(0x83, amazon));

    Item key = storedKey(2, StoreLoc.INVENTORY);
    amazon.getItems().add(key);
    assertEquals(NativeObjectKeyResolver.Result.KEY_CONSUMED,
        NativeObjectKeyResolver.unlock(0x83, amazon));
    assertEquals(1, key.attrs.base().get(Stat.quantity).asInt());
    assertEquals(1, amazon.getItems().getItems().size);
    assertEquals(0, removals[0]);

    assertEquals(NativeObjectKeyResolver.Result.KEY_CONSUMED,
        NativeObjectKeyResolver.unlock(0x83, amazon));
    assertTrue(amazon.getItems().getItems().isEmpty());
    assertEquals(1, removals[0]);
  }

  @Test
  void keyOutsideInventoryPageIsNotConsumed() {
    CharData amazon = character((byte) Riiablo.AMAZON);
    Item stashKey = storedKey(1, StoreLoc.STASH);
    amazon.getItems().add(stashKey);

    assertEquals(NativeObjectKeyResolver.Result.MISSING_KEY,
        NativeObjectKeyResolver.unlock(0x83, amazon));
    assertFalse(amazon.getItems().getItems().isEmpty());
  }

  private static CharData character(byte characterClass) {
    return CharData.obtain(Riiablo.NORMAL, false, "key-test", characterClass);
  }

  private static Item storedKey(int quantity, StoreLoc storeLoc) {
    Item key = new ItemGenerator().generate("key");
    key.location = Location.STORED;
    key.storeLoc = storeLoc;
    key.attrs.base().put(Stat.quantity, quantity);
    key.attrs.reset();
    return key;
  }
}
