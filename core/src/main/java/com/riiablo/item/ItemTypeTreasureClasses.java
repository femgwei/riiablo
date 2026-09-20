package com.riiablo.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.Weapons;

/** Runtime item-type treasure classes such as {@code armo3} and {@code weap3}. */
public final class ItemTypeTreasureClasses {
  private static final int MIN_LEVEL = 3;
  private static final int MAX_LEVEL = 96;
  private static final int LEVEL_STEP = 3;
  private static final String MISSILE_POTION = "tpot";

  private static final class Candidate {
    final String code;
    final int probability;

    Candidate(String code, int probability) {
      this.code = code;
      this.probability = probability;
    }
  }

  private static final class ItemClass {
    final List<Candidate> candidates = new ArrayList<>();
    int totalProbability;

    void add(String code, int probability) {
      probability = Math.max(1, probability);
      candidates.add(new Candidate(code, probability));
      totalProbability += probability;
    }

    String select(TreasureClassResolver.RandomSource random) {
      if (totalProbability <= 0) return null;
      int roll = random.nextInt(totalProbability);
      if (roll < 0 || roll >= totalProbability) {
        throw new IllegalArgumentException(
            "random source returned " + roll + " for bound " + totalProbability);
      }
      for (Candidate candidate : candidates) {
        roll -= candidate.probability;
        if (roll < 0) return candidate.code;
      }
      return null;
    }
  }

  private final ItemTypes itemTypes;
  private final Map<String, ItemClass> classes = new HashMap<>();

  public ItemTypeTreasureClasses(ItemTypes itemTypes, Weapons weapons,
      Armor armor, Misc misc) {
    if (itemTypes == null) throw new NullPointerException("itemTypes");
    this.itemTypes = itemTypes;
    for (int typeId = 0; typeId < itemTypes.size(); typeId++) {
      ItemTypes.Entry type = itemTypes.get(typeId);
      if (type == null || type.TreasureClass == 0 || type.Code == null
          || type.Code.isEmpty()) continue;
      for (int level = MIN_LEVEL; level <= MAX_LEVEL; level += LEVEL_STEP) {
        ItemClass itemClass = new ItemClass();
        addItems(itemClass, type, level, weapons);
        addItems(itemClass, type, level, armor);
        addItems(itemClass, type, level, misc);
        classes.put(type.Code + level, itemClass);
      }
    }
  }

  public boolean contains(String name) {
    return classes.containsKey(name);
  }

  public String select(String name, TreasureClassResolver.RandomSource random) {
    ItemClass itemClass = classes.get(name);
    return itemClass == null ? null : itemClass.select(random);
  }

  private <T extends ItemEntry> void addItems(ItemClass itemClass, ItemTypes.Entry target,
      int level, Excel<T> items) {
    if (items == null) return;
    for (int itemId = 0; itemId < items.size(); itemId++) {
      T item = items.get(itemId);
      if (item == null || !item.spawnable || item.quest != 0
          || item.level <= level - LEVEL_STEP || item.level > level
          || !isItemType(item, target.Code)) continue;
      if (!MISSILE_POTION.equals(target.Code) && isItemType(item, MISSILE_POTION)) continue;

      ItemTypes.Entry primaryType = itemTypes.get(item.type);
      int probability = primaryType == null ? 1 : primaryType.Rarity;
      itemClass.add(item.code, probability);
    }
  }

  private boolean isItemType(ItemEntry item, String target) {
    return isItemType(item.type, target, 0) || isItemType(item.type2, target, 0);
  }

  private boolean isItemType(String code, String target, int depth) {
    if (code == null || code.isEmpty() || target == null || target.isEmpty()
        || depth >= TreasureClassResolver.NATIVE_MAX_DEPTH) return false;
    if (target.equals(code)) return true;
    ItemTypes.Entry type = itemTypes.get(code);
    if (type == null || type.Equiv == null) return false;
    for (String equivalent : type.Equiv) {
      if (equivalent == null || equivalent.isEmpty()) continue;
      if (isItemType(equivalent, target, depth + 1)) return true;
    }
    return false;
  }
}
