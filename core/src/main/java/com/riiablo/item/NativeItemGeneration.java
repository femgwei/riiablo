package com.riiablo.item;

import com.badlogic.gdx.utils.Array;
import com.riiablo.CharacterClass;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Weapons;

/** Data-only D2Game item initialization and trait rules. */
public final class NativeItemGeneration {
  private NativeItemGeneration() {}

  public interface RandomSource {
    int nextInt(int bound);
  }

  /** Mirrors ITEMS_ComputeCraftedMagicAffixLevel for non-crafted drops. */
  public static int affixLevel(int itemLevel, int qualityLevel, int magicLevel) {
    int qlvl = Math.max(0, qualityLevel);
    int ilvl = Math.max(Math.max(1, itemLevel), qlvl);
    int result;
    if (magicLevel > 0) {
      result = magicLevel + ilvl;
    } else {
      int halfQlvl = qlvl / 2;
      int distance = 99 - halfQlvl;
      result = ilvl >= distance ? 2 * ilvl - halfQlvl - distance : ilvl - halfQlvl;
    }
    return Math.max(1, Math.min(99, result));
  }

  /** ITEMS_GetMaxSockets plus the difficulty cap applied by sub_6FC4D6B0. */
  public static int maxSockets(Item item, int itemLevel, int difficulty) {
    if (item == null || item.base == null || item.typeEntry == null
        || item.base.gemsockets <= 0 || item.base.stackable) return 0;
    int[] table = item.typeEntry.MaxSock;
    int tier = itemLevel > 40 ? 2 : itemLevel > 25 ? 1 : 0;
    int typeMax = table == null || table.length == 0 ? 0
        : table[Math.min(tier, table.length - 1)];
    int difficultyMax = difficulty <= 0 ? 3 : difficulty == 1 ? 4 : 6;
    return Math.max(0, Math.min(Math.min(item.base.gemsockets, typeMax),
        Math.min(6, difficultyMax)));
  }

  public static void initializeBaseStats(Item item, RandomSource random) {
    if (item == null || item.base == null || random == null) return;
    if (item.base instanceof Armor.Entry) {
      Armor.Entry armor = (Armor.Entry) item.base;
      item.attrs.base().put(Stat.armorclass,
          between(random, Math.min(armor.minac, armor.maxac), Math.max(armor.minac, armor.maxac)));
      initializeDurability(item, armor.durability, random);
    } else if (item.base instanceof Weapons.Entry) {
      initializeDurability(item, ((Weapons.Entry) item.base).durability, random);
    }

    if (item.base.stackable) {
      int min = Math.max(1, item.base.minstack);
      int max = Math.max(min, item.base.maxstack);
      // Native stack rolls use [min,max), except a degenerate one-value range.
      int quantity = max == min ? min : min + random.nextInt(max - min);
      item.attrs.base().put(Stat.quantity, quantity);
    }
  }

  /**
   * Completes the base fields required by native vendor stock.
   *
   * <p>Vendor items are created through the lightweight {@link ItemGenerator#generate(ItemEntry)}
   * path, which deliberately only attaches the base record. Armor still needs
   * its rolled defense and all durable equipment must be offered fully repaired.
   * Existing generated values are preserved so magic stock is not rerolled.</p>
   */
  public static void normalizeVendorBaseStats(Item item, RandomSource random) {
    if (item == null || item.base == null || random == null) return;
    if (item.base instanceof Armor.Entry
        && item.attrs.base().get(Stat.armorclass) == null) {
      Armor.Entry armor = (Armor.Entry) item.base;
      item.attrs.base().put(Stat.armorclass,
          between(random, Math.min(armor.minac, armor.maxac),
              Math.max(armor.minac, armor.maxac)));
    }

    int baseDurability = 0;
    if (item.base instanceof Armor.Entry) {
      baseDurability = ((Armor.Entry) item.base).durability;
    } else if (item.base instanceof Weapons.Entry) {
      baseDurability = ((Weapons.Entry) item.base).durability;
    } else {
      return;
    }

    StatRef max = item.attrs.base().get(Stat.maxdurability);
    int maxDurability = max == null
        ? (item.base.nodurability ? 0 : Math.max(0, Math.min(255, baseDurability)))
        : Math.max(0, Math.min(255, max.asInt()));
    item.attrs.base().put(Stat.maxdurability, maxDurability);
    if (maxDurability > 0) {
      item.attrs.base().put(Stat.durability, maxDurability);
    }
  }

  public static boolean rollSockets(Item item, Quality quality, int itemLevel,
      int difficulty, int startSeed, RandomSource random) {
    if (item == null || quality == null
        || quality != Quality.NORMAL && quality != Quality.HIGH
        || random == null) return false;
    int max = maxSockets(item, itemLevel, difficulty);
    if (max <= 0 || random.nextInt(100) >= 33) return false;
    int sockets = Math.floorMod(startSeed, max) + 1;
    item.flags |= Item.ITEMFLAG_SOCKETED;
    item.attrs.base().put(Stat.item_numsockets, sockets);
    item.sockets = new Array<>(sockets);
    return true;
  }

  public static boolean rollEthereal(Item item, Quality quality, RandomSource random) {
    if (!canBeEthereal(item, quality) || random.nextInt(100) >= 5) return false;
    applyEthereal(item);
    return true;
  }

  /**
   * Rolls the class-specific single-skill bonuses assigned by
   * {@code D2Game::sub_6FC52410/sub_6FC52650}.
   *
   * <p>The primary {@code ItemTypes.txt} row selects the player class through
   * {@code StaffMods}. The native 1.10 path then rolls zero to three distinct
   * skills from five-skill level tiers and stores them as
   * {@link Stat#item_singleskill} entries in the ordinary item property list.</p>
   *
   * @param itemDropLevel native forced-drop bonus; ordinary monster drops use zero
   * @return number of skill entries assigned
   */
  public static int rollStaffMods(Item item, int itemLevel, int itemDropLevel,
      Skills skills, RandomSource random) {
    if (item == null || item.typeEntry == null || item.attrs == null
        || skills == null || random == null || !canRollStaffMods(item.quality)) return 0;

    String staffMods = item.typeEntry.StaffMods;
    if (staffMods == null || staffMods.isEmpty()) return 0;
    int classId = Skills.getClassId(staffMods);
    if (classId < 0) return 0;
    CharacterClass characterClass = CharacterClass.get(classId);
    int firstSkill = characterClass.firstSpell;
    int skillCount = characterClass.lastSpell - firstSkill;
    if (skillCount <= 0) return 0;

    int count = staffModCount(random.nextInt(100), itemDropLevel);
    if (count == 0) return 0;
    boolean inferior = item.quality == Quality.LOW;
    int baseTier = staffModBaseTier(itemLevel);
    int[] selected = {-1, -1, -1};
    StatListRef properties = item.attrs.list().numLists() == 0
        ? item.attrs.buildList() : item.attrs.list(0);
    int assigned = 0;

    for (int slot = 0; slot < count; slot++) {
      int tier = staffModTier(baseTier, random.nextInt(100), inferior);
      int tierFirstSkill = firstSkill + 5 * (tier - 1);
      int skillId = -1;
      // D2Game makes at most six attempts to avoid an incompatible or
      // duplicate skill in the selected five-skill tier.
      for (int attempt = 0; attempt < 6; attempt++) {
        int candidate = tierFirstSkill + random.nextInt(5);
        if (candidate < firstSkill || candidate >= firstSkill + skillCount
            || contains(selected, candidate)) continue;
        Skills.Entry skill = skills.get(candidate);
        if (!supportsStaffMod(item, skill)) continue;
        skillId = candidate;
        break;
      }
      if (skillId < 0) continue;

      selected[slot] = skillId;
      int value = inferior ? 1 : staffModValue(random.nextInt(100), itemDropLevel);
      properties.putEncoded(Stat.item_singleskill, skillId, value);
      assigned++;
    }

    return assigned;
  }

  static boolean canRollStaffMods(Quality quality) {
    return quality == Quality.LOW || quality == Quality.NORMAL || quality == Quality.HIGH
        || quality == Quality.MAGIC || quality == Quality.RARE || quality == Quality.CRAFTED;
  }

  static int staffModCount(int roll, int itemDropLevel) {
    int adjusted = Math.max(0, itemDropLevel) + Math.floorMod(roll, 100);
    if (adjusted > 90) return 3;
    if (adjusted > 70) return 2;
    if (adjusted > 30 || itemDropLevel != 0) return 1;
    return 0;
  }

  static int staffModBaseTier(int itemLevel) {
    if (itemLevel > 36) return 5;
    if (itemLevel > 24) return 4;
    if (itemLevel > 18) return 3;
    if (itemLevel > 11) return 2;
    return 1;
  }

  static int staffModTier(int baseTier, int roll, boolean inferior) {
    int value = Math.floorMod(roll, 100);
    int tier = value > 80 ? baseTier + 1
        : value > 30 ? baseTier
        : value > 10 ? baseTier - 1 : baseTier - 2;
    tier = Math.max(1, tier);
    return inferior && tier >= 4 ? 4 : tier;
  }

  static int staffModValue(int roll, int itemDropLevel) {
    int adjusted = Math.max(0, itemDropLevel) / 2 + Math.floorMod(roll, 100);
    if (adjusted >= 90) return 3;
    if (adjusted >= 60) return 2;
    return 1;
  }

  private static boolean supportsStaffMod(Item item, Skills.Entry skill) {
    if (skill == null) return false;
    if (skill.itypea == null || skill.itypea.length == 0
        || skill.itypea[0] == null || skill.itypea[0].isEmpty()) return true;
    return item.typeEntry.is(skill.itypea[0]);
  }

  private static boolean contains(int[] values, int value) {
    for (int candidate : values) if (candidate == value) return true;
    return false;
  }

  public static boolean canBeEthereal(Item item, Quality quality) {
    if (item == null || item.base == null || quality == null
        || item.base.nodurability || item.base.quest > 0
        || quality == Quality.LOW || quality == Quality.SET) return false;
    StatRef max = item.attrs.base().get(Stat.maxdurability);
    return (item.base instanceof Armor.Entry || item.base instanceof Weapons.Entry)
        && max != null && max.asInt() > 0;
  }

  public static void applyEthereal(Item item) {
    item.flags |= Item.ITEMFLAG_ETHEREAL;
    if (item.base instanceof Weapons.Entry) {
      scaleBase(item, Stat.mindamage);
      scaleBase(item, Stat.maxdamage);
      scaleBase(item, Stat.secondary_mindamage);
      scaleBase(item, Stat.secondary_maxdamage);
      scaleBase(item, Stat.item_throw_mindamage);
      scaleBase(item, Stat.item_throw_maxdamage);
    } else {
      scaleBase(item, Stat.armorclass);
    }
    StatRef max = item.attrs.base().get(Stat.maxdurability);
    if (max != null && max.asInt() > 0) {
      int etherealMax = max.asInt() / 2 + 1;
      item.attrs.base().put(Stat.maxdurability, etherealMax);
      item.attrs.base().put(Stat.durability, etherealMax);
    }
  }

  /**
   * Applies the native PropertyFunc23 guard before etherealizing an item.
   * Cube outputs and other forced-property callers must not scale an item a
   * second time when the source is already ethereal or has no durability.
   *
   * @return true when the item was changed
   */
  public static boolean applyEtherealIfNeeded(Item item) {
    if (item == null || item.isEthereal() || item.base == null
        || item.base.nodurability) return false;
    StatRef max = item.attrs.base().get(Stat.maxdurability);
    if (max == null || max.asInt() <= 0) return false;
    applyEthereal(item);
    return true;
  }

  private static void initializeDurability(Item item, int durability, RandomSource random) {
    int max = item.base.nodurability ? 0 : Math.max(0, Math.min(255, durability));
    item.attrs.base().put(Stat.maxdurability, max);
    if (max <= 0) return;
    int half = max >> 1;
    int current = half <= 0 ? max : half + random.nextInt(half);
    item.attrs.base().put(Stat.durability, Math.max(1, Math.min(255, current)));
  }

  private static int between(RandomSource random, int min, int max) {
    return max <= min ? min : min + random.nextInt(max - min + 1);
  }

  private static void scaleBase(Item item, short stat) {
    StatRef value = item.attrs.base().get(stat);
    if (value != null) item.attrs.base().put(stat, 3 * value.asInt() / 2);
  }
}
