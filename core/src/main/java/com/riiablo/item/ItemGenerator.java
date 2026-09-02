package com.riiablo.item;

import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
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
import com.riiablo.codec.excel.UniqueItems;
import com.riiablo.codec.excel.SetItems;
import com.riiablo.codec.excel.Excel;
import com.riiablo.attributes.PropertiesGenerator;
import com.riiablo.engine.server.NativeRng;

public class ItemGenerator extends PassiveSystem {
  private static final String TAG = "ItemGenerator";

  private static final boolean DEBUG = true;

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

  /** Creates a drop with native quality metadata and persisted affix data. */
  public Item generateLootItem(String code, int itemLevel, Quality requested) {
    int seed = Riiablo.gameSeed ^ code.hashCode() ^ (itemLevel * 0x45D9F3B);
    return generateLootItem(code, itemLevel, requested, seed, Riiablo.NORMAL);
  }

  /** Authoritative seeded item creation used by server drops. */
  public Item generateLootItem(String code, int itemLevel, Quality requested,
      int seed, int difficulty) {
    NativeRng rng = new NativeRng(seed);
    if (requested == Quality.MAGIC || requested == Quality.RARE) {
      Item item = generateQuestReward(code, itemLevel, requested, seed, rng);
      applyNativeTraits(item, requested, itemLevel, difficulty, seed, rng);
      item.attrs.reset();
      return item;
    }
    Item item = generate(code);
    item.id = seed;
    item.version = Item.VERSION_110;
    item.ilvl = (byte) Math.max(1, Math.min(99, itemLevel));
    item.flags |= Item.ITEMFLAG_IDENTIFIED;
    item.quality = requested == null ? Quality.NORMAL : requested;
    NativeItemGeneration.initializeBaseStats(item, rng::nextInt);
    PropertiesGenerator properties = properties(rng);
    if (item.quality == Quality.UNIQUE) {
      UniqueItems.Entry entry = findUnique(code, itemLevel, rng);
      if (entry == null) return generateLootItem(code, itemLevel, Quality.MAGIC,
          seed, difficulty);
      item.qualityId = Riiablo.files.UniqueItems.index(entry.index);
      item.qualityData = entry;
      addUniqueProperties(item, entry, properties);
    } else if (item.quality == Quality.SET) {
      SetItems.Entry entry = findSet(code, itemLevel, rng);
      if (entry == null) return generateLootItem(code, itemLevel, Quality.MAGIC,
          seed, difficulty);
      item.qualityId = Riiablo.files.SetItems.index(entry.index);
      item.qualityData = entry;
      addSetProperties(item, entry, properties);
    }
    // ItemWriter always emits a serialized magic/set/unique property list for
    // non-compact standard items.  Keep one list even for NORMAL, LOW and
    // HIGH drops; an absent list produces a malformed D2S item stream.
    if ((item.flags & Item.ITEMFLAG_COMPACT) == 0 && item.attrs.list().numLists() == 0) {
      item.attrs.buildList();
    }
    applyNativeTraits(item, item.quality, itemLevel, difficulty, seed, rng);
    item.attrs.reset();
    return item;
  }

  private static UniqueItems.Entry findUnique(String code, int itemLevel, NativeRng rng) {
    if (Riiablo.files.UniqueItems == null) return null;
    Array<UniqueItems.Entry> candidates = new Array<>();
    int totalRarity = 0;
    for (UniqueItems.Entry entry : Riiablo.files.UniqueItems) {
      if (entry.enabled && code.equals(entry.code) && (entry.lvl <= 0 || entry.lvl <= itemLevel)) {
        candidates.add(entry);
        totalRarity += Math.max(1, entry.rarity);
      }
    }
    if (candidates.size == 0) return null;
    int roll = rng.nextInt(Math.max(1, totalRarity));
    for (UniqueItems.Entry entry : candidates) {
      roll -= Math.max(1, entry.rarity);
      if (roll < 0) return entry;
    }
    return candidates.peek();
  }

  private static SetItems.Entry findSet(String code, int itemLevel, NativeRng rng) {
    if (Riiablo.files.SetItems == null) return null;
    Array<SetItems.Entry> candidates = new Array<>();
    int totalRarity = 0;
    for (SetItems.Entry entry : Riiablo.files.SetItems) {
      String itemCode = entry._item != null && !entry._item.isEmpty() ? entry._item : entry.item;
      if (code.equals(itemCode) && (entry.lvl <= 0 || entry.lvl <= itemLevel)) {
        candidates.add(entry);
        totalRarity += Math.max(1, entry.rarity);
      }
    }
    if (candidates.size == 0) return null;
    int roll = rng.nextInt(Math.max(1, totalRarity));
    for (SetItems.Entry entry : candidates) {
      roll -= Math.max(1, entry.rarity);
      if (roll < 0) return entry;
    }
    return candidates.peek();
  }

  private static void addUniqueProperties(
      Item item, UniqueItems.Entry e, PropertiesGenerator properties) {
    StatListRef list = item.attrs.buildList();
    properties.add(list,
        new String[] {e.prop1,e.prop2,e.prop3,e.prop4,e.prop5,e.prop6,e.prop7,e.prop8,e.prop9,e.prop10,e.prop11,e.prop12},
        new int[] {e.par1,e.par2,e.par3,e.par4,e.par5,e.par6,e.par7,e.par8,e.par9,e.par10,e.par11,e.par12},
        new int[] {e.min1,e.min2,e.min3,e.min4,e.min5,e.min6,e.min7,e.min8,e.min9,e.min10,e.min11,e.min12},
        new int[] {e.max1,e.max2,e.max3,e.max4,e.max5,e.max6,e.max7,e.max8,e.max9,e.max10,e.max11,e.max12});
  }

  private static void addSetProperties(
      Item item, SetItems.Entry e, PropertiesGenerator properties) {
    StatListRef list = item.attrs.buildList();
    properties.add(list,
        new String[] {e.prop1,e.prop2,e.prop3,e.prop4,e.prop5,e.prop6,e.prop7,e.prop8,e.prop9},
        new int[] {e.par1,e.par2,e.par3,e.par4,e.par5,e.par6,e.par7,e.par8,e.par9},
        new int[] {e.min1,e.min2,e.min3,e.min4,e.min5,e.min6,e.min7,e.min8,e.min9},
        new int[] {e.max1,e.max2,e.max3,e.max4,e.max5,e.max6,e.max7,e.max8,e.max9});
  }

  /** Creates a native quest reward with valid persisted magic/rare metadata. */
  public Item generateQuestReward(String code, int itemLevel, Quality quality, int id) {
    NativeRng rng = new NativeRng(id == 0
        ? Riiablo.gameSeed ^ code.hashCode() ^ (itemLevel * 0x45D9F3B) : id);
    return generateQuestReward(code, itemLevel, quality, id, rng);
  }

  private Item generateQuestReward(String code, int itemLevel, Quality quality,
      int id, NativeRng rng) {
    Item item = generate(code);
    item.id = id;
    item.version = Item.VERSION_110;
    item.ilvl = (byte) Math.max(1, Math.min(127, itemLevel));
    item.quality = quality;
    item.flags |= Item.ITEMFLAG_IDENTIFIED;
    NativeItemGeneration.initializeBaseStats(item, rng::nextInt);
    // Every non-compact standard item serializes the magic property list,
    // including rare rewards whose generated affix list is currently empty.
    StatListRef magic = item.attrs.buildList();
    PropertiesGenerator properties = properties(rng);

    if (quality == Quality.MAGIC) {
      int prefix = findMagicAffix(Riiablo.files.MagicPrefix, item, itemLevel, rng);
      int suffix = findMagicAffix(Riiablo.files.MagicSuffix, item, itemLevel, rng);
      item.qualityId = prefix | (suffix << Item.MAGIC_AFFIX_SIZE);
      addMagicAffix(magic, Riiablo.files.MagicPrefix.get(prefix), properties);
      addMagicAffix(magic, Riiablo.files.MagicSuffix.get(suffix), properties);
    } else if (quality == Quality.RARE) {
      int rarePrefix = findRareAffix(Riiablo.files.RarePrefix, item, rng);
      int rareSuffix = findRareAffix(Riiablo.files.RareSuffix, item, rng);
      item.qualityId = rarePrefix | (rareSuffix << Item.RARE_AFFIX_SIZE);
      // RarePrefix/RareSuffix supply only the generated rare name. The six
      // persisted RareQualityData slots reference MagicPrefix/MagicSuffix;
      // storing rare-name ids there corrupts the property stream on reload.
      int magicPrefix = findMagicAffix(Riiablo.files.MagicPrefix, item, itemLevel, rng);
      int magicSuffix = findMagicAffix(Riiablo.files.MagicSuffix, item, itemLevel, rng);
      item.qualityData = new RareQualityData(magicPrefix, magicSuffix);
      addMagicAffix(magic, Riiablo.files.MagicPrefix.get(magicPrefix), properties);
      addMagicAffix(magic, Riiablo.files.MagicSuffix.get(magicSuffix), properties);
    }
    item.attrs.reset();
    return item;
  }

  /** Creates Charsi's rare replacement while preserving native item traits. */
  public Item generateImbuedItem(Item source, int playerLevel) {
    if (source == null || source.base == null) throw new NullPointerException("source");
    int itemLevel = Math.max(1, Math.min(99, playerLevel > 5 ? playerLevel + 4 : playerLevel));
    Item output = generateQuestReward(source.code, itemLevel, Quality.RARE, source.id);
    output.flags |= source.flags & (Item.ITEMFLAG_ETHEREAL | Item.ITEMFLAG_INSCRIBED);
    output.inscription = source.inscription;
    copyBaseStat(source, output, Stat.armorclass);
    copyBaseStat(source, output, Stat.maxdurability);
    repairDurability(source, output);
    copyBaseStat(source, output, Stat.quantity);
    output.attrs.reset();
    return output;
  }

  private static void copyBaseStat(Item source, Item destination, short stat) {
    StatRef value = source.attrs.base().get(stat);
    if (value != null) {
      destination.attrs.base().putEncoded(stat, value.encodedParams(), value.encodedValues());
    }
  }

  private static void repairDurability(Item source, Item destination) {
    StatRef maxDurability = source.attrs.base().get(Stat.maxdurability);
    if (maxDurability != null && maxDurability.asInt() > 0) {
      destination.attrs.base().put(Stat.durability, maxDurability.asInt());
    }
  }

  private static int findMagicAffix(
      MagicPrefix entries, Item item, int itemLevel, NativeRng rng) {
    Array<MagicAffix> candidates = new Array<>();
    int totalFrequency = 0;
    int affixLevel = NativeItemGeneration.affixLevel(itemLevel, item.base.level, 0);
    for (MagicAffix affix : entries) {
      if (!eligible(affix, item, affixLevel)) continue;
      MagicPrefix.Entry e = (MagicPrefix.Entry) affix;
      if (!supports(e.itype1, e.itype2, e.itype3, e.itype4, e.itype5, e.itype6, e.itype7, item)) continue;
      if (excludes(item, e.etype1, e.etype2, e.etype3, e.etype4, e.etype5)) continue;
      candidates.add(affix);
      totalFrequency += Math.max(0, affix.frequency);
    }
    return selectMagicAffix(entries, candidates, totalFrequency, rng,
        "prefix", item, itemLevel);
  }

  private static int findMagicAffix(
      MagicSuffix entries, Item item, int itemLevel, NativeRng rng) {
    Array<MagicAffix> candidates = new Array<>();
    int totalFrequency = 0;
    int affixLevel = NativeItemGeneration.affixLevel(itemLevel, item.base.level, 0);
    for (MagicAffix affix : entries) {
      if (!eligible(affix, item, affixLevel)) continue;
      MagicSuffix.Entry e = (MagicSuffix.Entry) affix;
      if (!supports(e.itype1, e.itype2, e.itype3, e.itype4, e.itype5, e.itype6, e.itype7, item)) continue;
      if (excludes(item, e.etype1, e.etype2, e.etype3)) continue;
      candidates.add(affix);
      totalFrequency += Math.max(0, affix.frequency);
    }
    return selectMagicAffix(entries, candidates, totalFrequency, rng,
        "suffix", item, itemLevel);
  }

  private static boolean eligible(MagicAffix affix, Item item, int affixLevel) {
    if (affix == null || !affix.spawnable || affix.frequency <= 0
        || affix.level > affixLevel
        || affix.maxlevel > 0 && affixLevel > affix.maxlevel) return false;
    if (affix.version >= 100 && item.version != Item.VERSION_110 && item.version < 100) return false;
    if ((item.quality == Quality.RARE || item.quality == Quality.CRAFTED) && !affix.rare) {
      return false;
    }
    if (affix.classspecific != null && !affix.classspecific.isEmpty()
        && item.typeEntry != null && item.typeEntry.Class != null
        && !item.typeEntry.Class.isEmpty()
        && !affix.classspecific.equalsIgnoreCase(item.typeEntry.Class)) return false;
    return true;
  }

  private static int selectMagicAffix(
      Excel<? extends MagicAffix> entries, Array<MagicAffix> candidates,
      int totalFrequency, NativeRng rng, String kind, Item item, int itemLevel) {
    if (candidates.size == 0 || totalFrequency <= 0) {
      throw new IllegalStateException("No valid " + kind + " for " + item.code
          + " at ilvl " + itemLevel);
    }
    int roll = rng.nextInt(totalFrequency + 1);
    MagicAffix selected = candidates.peek();
    for (MagicAffix affix : candidates) {
      selected = affix;
      roll -= Math.max(0, affix.frequency);
      if (roll < 0) break;
    }
    return entries.index(selected.name);
  }

  private static boolean excludes(Item item, String... types) {
    if (item == null || item.typeEntry == null || types == null) return false;
    for (String type : types) {
      if (type != null && !type.isEmpty() && item.typeEntry.is(type)) return true;
    }
    return false;
  }

  private static int findRareAffix(RarePrefix entries, Item item, NativeRng rng) {
    Array<RarePrefix.Entry> candidates = new Array<>();
    for (RarePrefix.Entry affix : entries) {
      if (affix.version >= 100 && item.version != Item.VERSION_110 && item.version < 100) continue;
      if (!supports(affix.itype1, affix.itype2, affix.itype3, affix.itype4,
          affix.itype5, affix.itype6, affix.itype7, item)) continue;
      if (excludes(item, affix.etype1, affix.etype2, affix.etype3,
          affix.etype4)) continue;
      candidates.add(affix);
    }
    if (candidates.size == 0) throw new IllegalStateException(
        "No valid rare prefix for " + item.code);
    return entries.index(candidates.get(rng.nextInt(candidates.size)).name);
  }

  private static int findRareAffix(RareSuffix entries, Item item, NativeRng rng) {
    Array<RareSuffix.Entry> candidates = new Array<>();
    for (RareSuffix.Entry affix : entries) {
      if (affix.version >= 100 && item.version != Item.VERSION_110 && item.version < 100) continue;
      if (!supports(affix.itype1, affix.itype2, affix.itype3, affix.itype4,
          affix.itype5, affix.itype6, affix.itype7, item)) continue;
      if (excludes(item, affix.etype1, affix.etype2, affix.etype3,
          affix.etype4)) continue;
      candidates.add(affix);
    }
    if (candidates.size == 0) throw new IllegalStateException(
        "No valid rare suffix for " + item.code);
    return entries.index(candidates.get(rng.nextInt(candidates.size)).name);
  }

  private static boolean supports(String a, String b, String c, String d, String e, String f, String g, Item item) {
    String[] types = {a, b, c, d, e, f, g};
    for (String type : types) if (type != null && !type.isEmpty() && item.typeEntry.is(type)) return true;
    return false;
  }

  private static void addMagicAffix(
      StatListRef magic, MagicAffix affix, PropertiesGenerator properties) {
    if (!(affix instanceof MagicPrefix.Entry) && !(affix instanceof MagicSuffix.Entry)) return;
    if (affix instanceof MagicPrefix.Entry) {
      MagicPrefix.Entry e = (MagicPrefix.Entry) affix;
      properties.add(magic,
          new String[] {e.mod1code, e.mod2code, e.mod3code},
          new int[] {e.mod1param, e.mod2param, e.mod3param},
          new int[] {e.mod1min, e.mod2min, e.mod3min},
          new int[] {e.mod1max, e.mod2max, e.mod3max});
    } else {
      MagicSuffix.Entry e = (MagicSuffix.Entry) affix;
      properties.add(magic,
          new String[] {e.mod1code, e.mod2code, e.mod3code},
          new int[] {e.mod1param, e.mod2param, e.mod3param},
          new int[] {e.mod1min, e.mod2min, e.mod3min},
          new int[] {e.mod1max, e.mod2max, e.mod3max});
    }
  }

  private static PropertiesGenerator properties(NativeRng rng) {
    return new PropertiesGenerator((min, max) -> {
      int lower = Math.min(min, max);
      int upper = Math.max(min, max);
      return upper <= lower ? lower : lower + rng.nextInt(upper - lower + 1);
    });
  }

  private static void applyNativeTraits(Item item, Quality quality, int itemLevel,
      int difficulty, int seed, NativeRng rng) {
    NativeItemGeneration.rollEthereal(item, quality, rng::nextInt);
    NativeItemGeneration.rollSockets(item, quality, itemLevel, difficulty,
        seed, rng::nextInt);
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

  public Item generate(Armor.Entry base) {
    Item item = new Item();
    item.reset();
    item.location = Location.STORED; // FIXME: should allow null?
    if (base.compactsave) item.flags |= Item.ITEMFLAG_COMPACT;

    item.setBase(base);

    return item;
  }

  public Item generate(Weapons.Entry base) {
    Item item = new Item();
    item.reset();
    item.location = Location.STORED; // FIXME: should allow null?
    if (base.compactsave) item.flags |= Item.ITEMFLAG_COMPACT;

    item.setBase(base);

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
