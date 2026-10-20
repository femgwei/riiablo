package com.riiablo.engine.server.combat;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.Type;
import com.riiablo.save.ItemData;
import com.riiablo.engine.server.state.StateList;

/** Concrete-hand source for ordinary attacks, never for precomputed skill profiles.
 * Item aggregate endpoints already contain item-local ED (whole item damage stats).
 * Do not use the character's merged weapon endpoints or apply local ED twice.
 */
public final class WeaponAttackSource {
  public final int minimum, maximum, attributePercent;
  public final boolean equipped, blunt;
  public final boolean hireling, rogue;
  private final Attributes unit;
  private final ItemData items;
  private final Item weapon;

  public WeaponAttackSource(Attributes unit, ItemData items, Item weapon,
      boolean twoHanded, boolean throwing) {
    this(unit, items, weapon, twoHanded, throwing, false, false);
  }

  public WeaponAttackSource(Attributes unit, ItemData items, Item weapon,
      boolean twoHanded, boolean throwing, boolean hireling, boolean rogue) {
    this.unit = unit;
    this.items = items;
    this.weapon = weapon;
    this.hireling = hireling;
    this.rogue = rogue;
    equipped = weapon != null && weapon.base instanceof Weapons.Entry;
    blunt = equipped && weapon.type != null && weapon.type.is(Type.BLUN);
    short min = throwing ? Stat.item_throw_mindamage
        : twoHanded ? Stat.secondary_mindamage : Stat.mindamage;
    short max = throwing ? Stat.item_throw_maxdamage
        : twoHanded ? Stat.secondary_maxdamage : Stat.maxdamage;
    int sourceMin = endpoint(min, Stat.mindamage);
    int sourceMax = endpoint(max, Stat.maxdamage);
    // MonsterUnique.cpp sub_6FC6CF10: non-Rogue hireling mode stat-list.
    if (hireling && !rogue && sourceMax > 0) {
      sourceMin = Math.max(0, sourceMin + unit.base().getValue(Stat.secondary_mindamage, 0) - 1);
      sourceMax = Math.max(0, sourceMax + unit.base().getValue(Stat.secondary_maxdamage, 0) - 1);
    }
    minimum = sourceMin;
    maximum = sourceMax;
    Weapons.Entry row = equipped ? (Weapons.Entry) weapon.base : null;
    attributePercent = equipped
        ? row.StrBonus * unit.getValue(Stat.strength, 0) / 100
            + row.DexBonus * unit.getValue(Stat.dexterity, 0) / 100
        : unit.getValue(Stat.strength, 0);
  }

  /** Runtime ownership adapter shared by melee and ordinary ranged creation. */
  public static WeaponAttackSource forUnit(com.artemis.World world, int id,
      boolean throwing, boolean left) {
    com.riiablo.engine.server.component.AttributesWrapper attrs = world.getMapper(
        com.riiablo.engine.server.component.AttributesWrapper.class).get(id);
    if (attrs == null || attrs.attrs == null) return null;
    com.riiablo.engine.server.component.Player player = world.getMapper(
        com.riiablo.engine.server.component.Player.class).get(id);
    com.riiablo.engine.server.component.Mercenary merc = world.getMapper(
        com.riiablo.engine.server.component.Mercenary.class).get(id);
    if (player == null && merc == null) return null;
    com.riiablo.save.CharData data = player == null ? null : player.data;
    if (merc != null) {
      com.riiablo.engine.server.component.Player owner = world.getMapper(
          com.riiablo.engine.server.component.Player.class).get(merc.ownerId);
      data = owner == null ? null : owner.data;
    }
    ItemData items = data == null ? null : merc == null ? data.getItems() : data.getMerc().getItems();
    Item weapon = null;
    if (items != null) {
      weapon = activeWeapon(items, left ? com.riiablo.item.BodyLoc.LARM : com.riiablo.item.BodyLoc.RARM);
      if (weapon == null) weapon = activeWeapon(items,
          left ? com.riiablo.item.BodyLoc.RARM : com.riiablo.item.BodyLoc.LARM);
    }
    boolean twoHanded = weapon != null && ((Weapons.Entry) weapon.base)._2handed;
    // A Barbarian can use a 1-or-2-handed sword in one hand when another arm is occupied.
    if (twoHanded && ((Weapons.Entry) weapon.base)._1or2handed && player != null
        && data != null && data.charClass == com.riiablo.Riiablo.BARBARIAN) {
      Item other = items.getEquipped(weapon == items.getEquipped(com.riiablo.item.BodyLoc.RARM)
          ? com.riiablo.item.BodyLoc.LARM : com.riiablo.item.BodyLoc.RARM);
      // INVENTORY_GetWieldType tests occupied body slots, even a broken item.
      if (other != null) twoHanded = false;
    }
    return new WeaponAttackSource(attrs.attrs, items, weapon, twoHanded, throwing,
        merc != null, merc != null && merc.mercType == com.riiablo.engine.server.pet.MercenaryManager.MERC_TYPE_ROGUE);
  }

  private static Item activeWeapon(ItemData items, com.riiablo.item.BodyLoc hand) {
    Item item = items.getEquipped(hand);
    return item != null && item.base instanceof Weapons.Entry && items.isActive(item) ? item : null;
  }

  public StateList.WeaponMasteryBonus mastery(StateList states, boolean throwing) {
    return states == null || weapon == null ? null
        : states.getWeaponMastery(weapon, throwing, new StateList.WeaponMasteryBonus());
  }

  private int endpoint(short stat, short unarmed) {
    if (items == null) return unit.getValue(stat, 0);
    int value = unit.base().getValue(stat, 0);
    if (equipped) value += itemValue(weapon, stat);
    for (Item item : items.getItems()) {
      if (!contributes(item) || isWeapon(item)) continue;
      // Off-weapon +min/+max contributes to all weapon damage modes.
      value += offWeaponFlat(item, unarmed);
    }
    return value;
  }

  /** Unit base plus active concrete weapon and non-weapon equipment/charm stats.
   * Endpoint ED on weapons was consumed locally; only non-weapon ED is global.
   */
  public int stat(short stat) {
    if (items == null) return unit.getValue(stat, 0);
    int value = unit.base().getValue(stat, 0);
    boolean endpointPercent = stat == Stat.item_mindamage_percent
        || stat == Stat.item_maxdamage_percent;
    for (Item item : items.getItems()) {
      if (!contributes(item)) continue;
      if (isWeapon(item) && (item != weapon || endpointPercent)) continue;
      value += itemValue(item, stat);
    }
    return value;
  }

  private boolean contributes(Item item) {
    return item != null && item.type != null && (item.location == Location.EQUIPPED
        && items.isActive(item) && items.getEquipped(item.bodyLoc) == item || item.location == Location.STORED
        && item.storeLoc == StoreLoc.INVENTORY && item.type.is(Type.CHAR));
  }

  private static boolean isWeapon(Item item) {
    return item.base instanceof Weapons.Entry;
  }

  private int offWeaponFlat(Item item, short stat) {
    // Host armor records contain shield bash endpoints. Native regular weapon
    // damage does not inherit those, nor armor-local multiplication of +min/max
    // by ED (the host updater treats its synthetic zero endpoints as weapon stats).
    int value = rawFlat(item, stat);
    // Read each original op=2/4 modifier once. The host updater may leave a
    // consumed modifier in both aggregate and remaining; neither is a unique
    // source list, and its endpoint may already include armor ED.
    value += rawScaledFlat(item, stat);
    return value;
  }

  private int rawScaledFlat(Item item, short stat) {
    int value = scaledFlat(item.attrs.base(), stat);
    for (int i = 0; i < item.attrs.list().numLists(); i++) {
      if ((item.aggFlags & (1 << i)) != 0) value += scaledFlat(item.attrs.list(i), stat);
    }
    for (Item socket : item.sockets) value += rawScaledFlat(socket, stat);
    return value;
  }

  private static int rawFlat(Item item, short stat) {
    int value = item.base instanceof com.riiablo.codec.excel.Armor.Entry
        ? 0 : item.attrs.base().getValue(stat, 0);
    for (int i = 0; i < item.attrs.list().numLists(); i++) {
      if ((item.aggFlags & (1 << i)) != 0) value += item.attrs.list(i).getValue(stat, 0);
    }
    for (Item socket : item.sockets) value += rawFlat(socket, stat);
    return value;
  }

  private int scaledFlat(com.riiablo.attributes.StatListRef modifiers, short stat) {
    int value = 0;
    for (StatRef modifier : modifiers) {
      com.riiablo.codec.excel.ItemStatCost.Entry entry = modifier.entry();
      if (entry.op != 2 && entry.op != 4) continue;
      String name = stat == Stat.mindamage ? "mindamage" : "maxdamage";
      for (String target : entry.op_stat) if (name.equals(target)) {
        int base = entry.op_param > 0 ? unit.getValue(modifier.table().index(entry.op_base), 1) : 1;
        value += (int) (((long) modifier.asInt() * base) >> entry.op_param);
        break;
      }
    }
    return value;
  }

  private static int itemValue(Item item, short stat) {
    if (item == null || item.attrs == null) return 0;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    // A consumed stat can survive in remaining's backing slice. If it was
    // aggregated, that is the authoritative complete value, not another share.
    if (ref != null) return ref.asInt();
    ref = item.attrs.remaining().get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
