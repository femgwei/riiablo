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
import com.riiablo.codec.excel.MagicAffix;
import com.riiablo.codec.excel.MagicPrefix;
import com.riiablo.codec.excel.MagicSuffix;
import com.riiablo.codec.excel.RarePrefix;
import com.riiablo.codec.excel.RareSuffix;
import com.riiablo.attributes.PropertiesGenerator;
import com.riiablo.attributes.StatListRef;

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

  /** Creates a native quest reward with valid persisted magic/rare metadata. */
  public Item generateQuestReward(String code, int itemLevel, Quality quality, int id) {
    Item item = generate(code);
    item.id = id;
    item.version = Item.VERSION_110;
    item.ilvl = (byte) Math.max(1, Math.min(127, itemLevel));
    item.quality = quality;
    item.flags |= Item.ITEMFLAG_IDENTIFIED;
    // Every non-compact standard item serializes the magic property list,
    // including rare rewards whose generated affix list is currently empty.
    StatListRef magic = item.attrs.buildList();

    if (quality == Quality.MAGIC) {
      int prefix = findMagicAffix(Riiablo.files.MagicPrefix, item, itemLevel);
      int suffix = findMagicAffix(Riiablo.files.MagicSuffix, item, itemLevel);
      item.qualityId = prefix | (suffix << Item.MAGIC_AFFIX_SIZE);
      addMagicAffix(magic, Riiablo.files.MagicPrefix.get(prefix));
      addMagicAffix(magic, Riiablo.files.MagicSuffix.get(suffix));
    } else if (quality == Quality.RARE) {
      int prefix = findRareAffix(Riiablo.files.RarePrefix, item);
      int suffix = findRareAffix(Riiablo.files.RareSuffix, item);
      item.qualityId = prefix | (suffix << Item.RARE_AFFIX_SIZE);
      item.qualityData = new RareQualityData(prefix, suffix);
    }
    item.attrs.reset();
    return item;
  }

  private static int findMagicAffix(MagicPrefix entries, Item item, int itemLevel) {
    for (MagicAffix affix : entries) {
      if (!affix.spawnable || affix.level > itemLevel || (affix.maxlevel > 0 && affix.maxlevel < itemLevel)) continue;
      MagicPrefix.Entry e = (MagicPrefix.Entry) affix;
      if (!supports(e.itype1, e.itype2, e.itype3, e.itype4, e.itype5, e.itype6, e.itype7, item)) continue;
      return entries.index(affix.name);
    }
    throw new IllegalStateException("No valid prefix for " + item.code + " at ilvl " + itemLevel);
  }

  private static int findMagicAffix(MagicSuffix entries, Item item, int itemLevel) {
    for (MagicAffix affix : entries) {
      if (!affix.spawnable || affix.level > itemLevel || (affix.maxlevel > 0 && affix.maxlevel < itemLevel)) continue;
      MagicSuffix.Entry e = (MagicSuffix.Entry) affix;
      if (!supports(e.itype1, e.itype2, e.itype3, e.itype4, e.itype5, e.itype6, e.itype7, item)) continue;
      return entries.index(affix.name);
    }
    throw new IllegalStateException("No valid suffix for " + item.code + " at ilvl " + itemLevel);
  }

  private static int findRareAffix(RarePrefix entries, Item item) {
    for (RarePrefix.Entry affix : entries) {
      if (!supports(affix.itype1, affix.itype2, affix.itype3, affix.itype4,
          affix.itype5, affix.itype6, affix.itype7, item)) continue;
      return entries.index(affix.name);
    }
    throw new IllegalStateException("No valid rare affix for " + item.code);
  }

  private static int findRareAffix(RareSuffix entries, Item item) {
    for (RareSuffix.Entry affix : entries) {
      if (!supports(affix.itype1, affix.itype2, affix.itype3, affix.itype4,
          affix.itype5, affix.itype6, affix.itype7, item)) continue;
      return entries.index(affix.name);
    }
    throw new IllegalStateException("No valid rare affix for " + item.code);
  }

  private static boolean supports(String a, String b, String c, String d, String e, String f, String g, Item item) {
    String[] types = {a, b, c, d, e, f, g};
    for (String type : types) if (type != null && !type.isEmpty() && item.typeEntry.is(type)) return true;
    return false;
  }

  private static void addMagicAffix(StatListRef magic, MagicAffix affix) {
    if (!(affix instanceof MagicPrefix.Entry) && !(affix instanceof MagicSuffix.Entry)) return;
    if (affix instanceof MagicPrefix.Entry) {
      MagicPrefix.Entry e = (MagicPrefix.Entry) affix;
      new PropertiesGenerator().add(magic,
          new String[] {e.mod1code, e.mod2code, e.mod3code},
          new int[] {e.mod1param, e.mod2param, e.mod3param},
          new int[] {e.mod1min, e.mod2min, e.mod3min},
          new int[] {e.mod1max, e.mod2max, e.mod3max});
    } else {
      MagicSuffix.Entry e = (MagicSuffix.Entry) affix;
      new PropertiesGenerator().add(magic,
          new String[] {e.mod1code, e.mod2code, e.mod3code},
          new int[] {e.mod1param, e.mod2param, e.mod3param},
          new int[] {e.mod1min, e.mod2min, e.mod3min},
          new int[] {e.mod1max, e.mod2max, e.mod3max});
    }
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
