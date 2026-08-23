package com.riiablo.item;

import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.Weapons;

public class ItemGenerator extends PassiveSystem {
  private static final String TAG = "ItemGenerator";

  private static final boolean DEBUG = true;

  private static final float SOCKETED_CHANCE = 1 / 3f;
  private static final float ETHEREAL_CHANCE = 1 / 20f;

//  public Item generate(TreasureClass tc) {
//    return null;
//  }

  public Item generate(String code) {
    ItemEntry type = ItemUtils.getBase(code);
    return generate(type);
  }

  public Item generate(ItemEntry base) {
    Gdx.app.debug(TAG, String.format("Generating %s (%s)", base.code, base.name));
    if (base instanceof Armor.Entry) {
      return generate((Armor.Entry) base);
    } else if (base instanceof Weapons.Entry) {
      return generate((Weapons.Entry) base);
    } else if (base instanceof Misc.Entry) {
      return generate((Misc.Entry) base);
    }

    throw new AssertionError();
  }

  /**
   * Creates a normal, identified item using the fields required by the native
   * 1.10+ D2S format. This mirrors the item initialization performed by
   * D2Game's {@code PLAYER_CreateStartItem}.
   *
   * @param startSkill skill id granted by the first starting item, or -1
   */
  public Item generateStartItem(String code, int id, int startSkill) {
    Item item = generate(code);
    item.flags |= Item.ITEMFLAG_IDENTIFIED | Item.ITEMFLAG_BEGINNER;
    item.version = Item.VERSION_110;
    item.id = id;
    item.ilvl = 1;
    item.quality = Quality.NORMAL;

    if (item.base.stackable) {
      item.attrs.base().put(Stat.quantity, Math.max(1, item.base.maxstack));
    }

    if (item.base instanceof Armor.Entry) {
      Armor.Entry armor = item.getBase();
      int minAc = Math.min(armor.minac, armor.maxac);
      int maxAc = Math.max(armor.minac, armor.maxac);
      int armorClass = minAc;
      if (maxAc > minAc) {
        armorClass += Math.floorMod(id, maxAc - minAc + 1);
      }
      item.attrs.base().put(Stat.armorclass, armorClass);
      initializeDurability(item, armor.durability);
    } else if (item.base instanceof Weapons.Entry) {
      Weapons.Entry weapon = item.getBase();
      initializeDurability(item, weapon.durability);
    }

    if ((item.flags & Item.ITEMFLAG_COMPACT) == 0) {
      // A standard item always serializes a magic list. An empty list is still
      // required so ItemWriter emits the 0x1ff end marker expected by D2.
      StatListRef magic = item.attrs.buildList();
      if (startSkill >= 0) {
        magic.putEncoded(Stat.item_singleskill, startSkill, 1);
      }
    }

    item.attrs.reset();
    return item;
  }

  private static void initializeDurability(Item item, int durability) {
    int maxDurability = item.base.nodurability ? 0 : Math.max(0, durability);
    item.attrs.base().put(Stat.maxdurability, maxDurability);
    if (maxDurability > 0) {
      item.attrs.base().put(Stat.durability, maxDurability);
    }
  }

  private static void socket(Item item) {
    // TODO: include difficulty
    if (item.base.gemsockets > 0 && MathUtils.randomBoolean(SOCKETED_CHANCE)) {
      Gdx.app.debug(TAG, "Item is socketed");
      item.flags |= Item.ITEMFLAG_SOCKETED;
      int diff = Riiablo.NORMAL;
      int maxSockets = Math.min(item.base.gemsockets, item.typeEntry.MaxSock[diff]);
      int numSockets = MathUtils.random(1, maxSockets);
      Gdx.app.debug(TAG, "Setting sockets to: " + numSockets);
      item.attrs.base().put(Stat.item_numsockets, numSockets);
      item.sockets = new Array<>(numSockets);
    }
  }

  private static void ethereal(Item item) {
    if (!item.base.nodurability && MathUtils.randomBoolean(ETHEREAL_CHANCE)) {
      Gdx.app.debug(TAG, "Item is ethereal");
      item.flags |= Item.ITEMFLAG_ETHEREAL;
    }
  }

  private static void durability(Item item) {
    if (item.base.nodurability) {
      item.attrs.base().put(Stat.maxdurability, 0);
    } else {
      // TODO: assign random int up to item.base.durability
    }
  }

  public Item generate(Armor.Entry base) {
    Item item = new Item();
    item.reset();
    item.location = Location.STORED; // FIXME: should allow null?
    if (base.compactsave) item.flags |= Item.ITEMFLAG_COMPACT;

    item.setBase(base);

//    socket(item);
//    ethereal(item);
//    durability(item);

    return item;
  }

  public Item generate(Weapons.Entry base) {
    Item item = new Item();
    item.reset();
    item.location = Location.STORED; // FIXME: should allow null?
    if (base.compactsave) item.flags |= Item.ITEMFLAG_COMPACT;

    item.setBase(base);

//    socket(item);
//    ethereal(item);
//    durability(item);

    // Set quantity for stackable weapons (javelins, throwing knives, throwing axes)
    if (base.stackable) {
      int quantity = base.spawnstack;
      if (quantity <= 0) {
        // Default quantity if spawnstack is 0 or invalid
        quantity = 80; // Default stack size for throwing weapons
      }
      item.attrs.base().put(Stat.quantity, quantity);
    }

    return item;
  }

  public Item generate(final Misc.Entry base) {
    Item item = new Item();
    item.reset();
    item.location = Location.STORED; // FIXME: should allow null?
    if (base.compactsave) item.flags |= Item.ITEMFLAG_COMPACT;

    if (base.code.equalsIgnoreCase("ear")) {
      item.setEar(0, 0, "null"); // TODO: creating an 'ear' requires the following params
      throw new IllegalArgumentException("No support for 'ear'");
    } else {
      item.setBase(base);
    }

    // Set quantity for stackable misc items
    if (base.stackable) {
      int quantity = base.spawnstack;
      if (quantity <= 0) {
        // Default quantity if spawnstack is 0 or invalid
        quantity = 1; // Default for misc stackable items
      }
      item.attrs.base().put(Stat.quantity, quantity);
    }

    return item;
  }
}
