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
    final int frame;

    Entry(Kind kind, Item item, int current, int threshold, int frame) {
      this.kind = kind;
      this.item = item;
      this.current = current;
      this.threshold = threshold;
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
    if (quantityThreshold > 0 && quantity <= quantityThreshold) {
      result.add(new Entry(
          Kind.QUANTITY,
          item,
          quantity,
          quantityThreshold,
          frame(quantityGroup(item), quantitySeverity(quantity, quantityThreshold))));
    }

    int durabilityThreshold = Math.max(0, item.base.durwarning);
    int maximumDurability = value(item, Stat.maxdurability, 0);
    int durability = value(item, Stat.durability, maximumDurability);
    if (durabilityThreshold > 0 && maximumDurability > 0
        && durability <= durabilityThreshold) {
      result.add(new Entry(
          Kind.DURABILITY,
          item,
          durability,
          durabilityThreshold,
          frame(durabilityGroup(item), severity(durability, durabilityThreshold, maximumDurability))));
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

  static float rightEdgeX(float hudWidth, float warningWidth) {
    return Math.max(0f, hudWidth) - Math.max(0f, warningWidth);
  }

  static float slotOffsetY(Kind kind, float iconHeight, float gap) {
    return kind == Kind.QUANTITY
        ? Math.max(0f, iconHeight) + Math.max(0f, gap)
        : 0f;
  }

  /** Quantity warning icon classes: arrows, bolts, potions, javelins/knives, axes. */
  private static int quantityGroup(Item item) {
    if (item.type != null) {
      if (item.type.is(Type.BOWQ)) return 0;
      if (item.type.is(Type.XBOQ)) return 1;
      if (item.type.is(Type.TPOT)) return 2;
      if (item.type.is(Type.JAVE) || item.type.is(Type.TKNI)
          || item.type.is(Type.SPEA)) return 3;
      if (item.type.is(Type.TAXE)) return 4;
    }
    return 3;
  }

  /** Durability warning icon classes: weapon, shield, armor, helmet. */
  private static int durabilityGroup(Item item) {
    if (item.type != null) {
      if (item.type.is(Type.SHIE)) return 5;
      if (item.type.is(Type.HELM) || item.type.is(Type.HEAD)
          || item.type.is(Type.PHLM) || item.type.is(Type.PELT)
          || item.type.is(Type.CIRC)) return 7;
      if (item.type.is(Type.ARMO)) return 6;
    }
    return 4;
  }
}
