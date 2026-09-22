package com.riiablo.screen.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.Type;
import com.riiablo.save.ItemData;

/** Native inventory warning state used by the right-side HUD indicator. */
final class InventoryWarning {
  static final int FRAME_GROUPS = 8;
  static final int COLORS_PER_GROUP = 3;

  enum Kind { QUANTITY, DURABILITY }

  static final class Entry {
    final Kind kind;
    final Item item;
    final int current;
    final int threshold;
    final int group;
    final int frame;

    Entry(Kind kind, Item item, int current, int threshold, int group, int frame) {
      this.kind = kind;
      this.item = item;
      this.current = current;
      this.threshold = threshold;
      this.group = group;
      this.frame = frame;
    }
  }

  private static final BodyLoc[] EQUIPMENT_SLOTS = {
      BodyLoc.HEAD, BodyLoc.TORS, BodyLoc.RARM, BodyLoc.LARM,
      BodyLoc.RRIN, BodyLoc.LRIN, BodyLoc.BELT, BodyLoc.FEET, BodyLoc.GLOV
  };

  private InventoryWarning() {}

  static List<Entry> collect(ItemData items) {
    List<Entry> result = new ArrayList<>();
    if (items == null) return result;

    Set<Integer> seen = new HashSet<>();
    for (BodyLoc slot : EQUIPMENT_SLOTS) {
      add(result, seen, items.getEquipped(slot));
    }
    Item ranged = items.getEquippedRangedWeapon();
    add(result, seen, ranged);
    add(result, seen, items.getEquippedAmmo(ranged));
    add(result, seen, items.getEquippedThrowableWeapon());
    return result;
  }

  private static void add(List<Entry> result, Set<Integer> seen, Item item) {
    if (item == null || !seen.add(item.id) || item.base == null || item.attrs == null) return;

    int quantityThreshold = Math.max(0, item.base.qntwarning);
    int quantity = value(item, Stat.quantity, 0);
    if (supportsQuantityWarning(item)
        && quantityThreshold > 0
        && quantity <= quantityThreshold) {
      int group = quantityGroup(item);
      result.add(new Entry(
          Kind.QUANTITY,
          item,
          quantity,
          quantityThreshold,
          group,
          frame(group, quantitySeverity(quantity, quantityThreshold))));
    }

    int durabilityThreshold = Math.max(0, item.base.durwarning);
    int maximumDurability = value(item, Stat.maxdurability, 0);
    int durability = value(item, Stat.durability, maximumDurability);
    if (supportsDurabilityWarning(item)
        && durabilityThreshold > 0
        && maximumDurability > 0
        && durability <= durabilityThreshold) {
      int group = durabilityGroup(item);
      result.add(new Entry(
          Kind.DURABILITY,
          item,
          durability,
          durabilityThreshold,
          group,
          frame(group, durabilitySeverity(durability))));
    }
  }

  private static int value(Item item, short stat, int fallback) {
    StatRef ref = item.attrs.base().get(stat);
    return ref == null ? fallback : Math.max(0, ref.asInt());
  }

  /** Three native color states: gold, orange, then red. */
  static int severity(int current, int threshold, int maximum) {
    if (current <= 0) return 2;
    if (threshold <= 1) return 0;
    if (current * 2 <= threshold) return 1;
    return 0;
  }

  static int frame(int group, int severity) {
    int safeGroup = Math.max(0, Math.min(FRAME_GROUPS - 1, group));
    int safeSeverity = Math.max(0, Math.min(COLORS_PER_GROUP - 1, severity));
    return safeGroup * COLORS_PER_GROUP + safeSeverity;
  }

  /** Native ammunition warning colors: yellow for 1..threshold, red at zero. */
  static int quantitySeverity(int current, int threshold) {
    return current <= 0 ? 2 : 0;
  }

  /** Native durability colors: yellow while low, red only when broken. */
  static int durabilitySeverity(int current) {
    return current <= 0 ? 2 : 0;
  }

  static float rightInsetX(float screenWidth, float iconWidth, float inset) {
    return Math.max(0f, screenWidth)
        - Math.max(0f, inset)
        - Math.max(0f, iconWidth);
  }

  static float screenSlotY(Kind kind, float screenHeight, float iconHeight,
      float centerOffset) {
    float height = Math.max(0f, iconHeight);
    float middle = Math.max(0f, screenHeight) / 2f;
    float offset = Math.max(0f, centerOffset);
    return kind == Kind.QUANTITY
        ? middle + offset
        : middle - offset - height;
  }

  static float slotOffsetY(Kind kind, float iconHeight, float gap) {
    return kind == Kind.QUANTITY
        ? Math.max(0f, iconHeight) + Math.max(0f, gap)
        : 0f;
  }

  /** Quantity warning icon classes: arrows, bolts, potions, javelins/knives, axes. */
  static boolean supportsQuantityWarning(Item item) {
    if (item == null || item.type == null) return false;
    return item.type.is(Type.BOWQ)
        || item.type.is(Type.XBOQ)
        || item.type.is(Type.TPOT)
        || item.type.is(Type.JAVE)
        || item.type.is(Type.TKNI)
        || item.type.is(Type.TAXE);
  }

  /**
   * Stack-based missiles and throwing weapons consume quantity, not
   * durability. Respect nodurability even when a stale/generated durability
   * stat is present on the runtime item.
   */
  static boolean supportsDurabilityWarning(Item item) {
    return item != null
        && item.base != null
        && !item.base.nodurability
        && !supportsQuantityWarning(item);
  }

  private static int quantityGroup(Item item) {
    if (item.type != null) {
      if (item.type.is(Type.BOWQ)) return 0;
      if (item.type.is(Type.XBOQ)) return 1;
      if (item.type.is(Type.TPOT)) return 2;
      if (item.type.is(Type.JAVE) || item.type.is(Type.TKNI)) return 3;
      if (item.type.is(Type.TAXE)) return 4;
    }
    return 3;
  }

  /** Durability warning icon classes: weapon, shield, armor, helmet. */
  static int durabilityGroup(Item item) {
    if (item.bodyLoc != null) {
      switch (item.bodyLoc) {
        case HEAD:
          return 7;
        case TORS:
        case BELT:
        case FEET:
        case GLOV:
          return 6;
        case RARM:
        case LARM:
        case RARM2:
        case LARM2:
          return item.type != null && item.type.is(Type.SHIE) ? 5 : 4;
        default:
          break;
      }
    }
    if (item.type != null) {
      if (item.type.is(Type.SHIE)) return 5;
      if (item.type.is(Type.HELM) || item.type.is(Type.HEAD)
          || item.type.is(Type.PHLM) || item.type.is(Type.PELT)
          || item.type.is(Type.CIRC)) return 7;
      if (item.type.is(Type.ARMO)) return 6;
    }
    // Unknown armor-like slots must never masquerade as a broken weapon.
    return 6;
  }
}
