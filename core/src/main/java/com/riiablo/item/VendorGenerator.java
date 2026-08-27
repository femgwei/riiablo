package com.riiablo.item;

import java.lang.reflect.Field;

import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.ItemEntry;

public class VendorGenerator extends PassiveSystem {
  private static final int FIRST_VENDOR_ITEM_ID = 0x60000000;

  protected ItemGenerator generator;
  private int nextItemId = FIRST_VENDOR_ITEM_ID;

  public Array<Item> generate(String vendor) throws Exception {
    Array<Item> items = new Array<>(true, 64, Item.class);
    generate(vendor, items, Riiablo.files.armor);
    generate(vendor, items, Riiablo.files.weapons);
    generate(vendor, items, Riiablo.files.misc);
    return items;
  }

  /** Generates a compact native-style gambling page for Gheed. */
  public Array<Item> generateGamble() {
    Array<ItemEntry> candidates = new Array<>(false, 128, ItemEntry.class);
    collectGambleCandidates(candidates, Riiablo.files.armor);
    collectGambleCandidates(candidates, Riiablo.files.weapons);
    collectGambleCandidates(candidates, Riiablo.files.misc);
    Array<Item> items = new Array<>(true, 10, Item.class);
    int count = Math.min(10, candidates.size);
    for (int i = 0; i < count; i++) {
      ItemEntry base = candidates.get(MathUtils.random(candidates.size - 1));
      int roll = MathUtils.random(99);
      Item item;
      if (roll < 5) {
        try {
          item = generator.generateQuestReward(base.code, Math.max(1, base.level), Quality.RARE, nextId());
        } catch (RuntimeException ignored) {
          item = createNormal(base);
        }
      } else if (roll < 35) {
        item = createMagic(base, Math.max(1, base.level));
      } else {
        item = createNormal(base);
      }
      item.flags2 |= Item.ITEMFLAG2_INSTORE;
      items.add(item);
    }
    return items;
  }

  private static void collectGambleCandidates(Array<ItemEntry> candidates,
      Excel<? extends ItemEntry> excel) {
    for (ItemEntry base : excel) {
      if (base == null || base.code == null || base.code.isEmpty()
          || base.quest != 0 || base.invwidth <= 0 || base.invheight <= 0
          || base.level <= 0) continue;
      candidates.add(base);
    }
  }

  public void generate(String vendor, Array<Item> items, Excel<? extends ItemEntry> excel) throws Exception {
    Class<? extends ItemEntry> entryClass = excel.getEntryClass();
    Field field = entryClass.getField(vendor);
    for (ItemEntry base : excel) {
      int[] vendorData = (int[]) field.get(base);
      if (vendorData[1] > 0) {
        int count = base.PermStoreItem ? 1 : MathUtils.random(vendorData[0], vendorData[1]);
        for (int i = 0; i < count; i++) {
          Item item = createNormal(base);
          items.add(item);
        }
      }
      if (vendorData[3] > 0 && vendorData[4] != 0xFF) {
        int count = base.PermStoreItem ? 1 : MathUtils.random(vendorData[2], vendorData[3]);
        for (int i = 0; i < count; i++) {
          Item item = createMagic(base, vendorData[4]);
          items.add(item);
        }
      }
    }
  }

  private Item createNormal(ItemEntry base) {
    Item item = generator.generate(base);
    item.id = nextId();
    item.version = Item.VERSION_110;
    item.ilvl = (byte) Math.max(1, Math.min(99, base.level));
    item.quality = Quality.NORMAL;
    item.flags |= Item.ITEMFLAG_IDENTIFIED;
    item.flags2 |= Item.ITEMFLAG2_INSTORE;
    item.load();
    return item;
  }

  private Item createMagic(ItemEntry base, int magicLevel) {
    int id = nextId();
    try {
      Item item = generator.generateQuestReward(
          base.code, Math.max(base.level, magicLevel), Quality.MAGIC, id);
      item.flags2 |= Item.ITEMFLAG2_INSTORE;
      item.load();
      return item;
    } catch (RuntimeException ignored) {
      // Some vendor-table rows have no valid affix at their configured level.
      // Native D2 falls back to a usable stock entry instead of aborting the
      // entire NPC inventory.
      Item item = createNormal(base);
      item.id = id;
      return item;
    }
  }

  private synchronized int nextId() {
    return nextItemId++;
  }
}
