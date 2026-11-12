package com.riiablo.engine.server.skill;

import com.riiablo.Files;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.AttributesUpdater;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListFlags;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonEquip;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.ItemData;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import java.util.ArrayList;
import java.util.List;

/** SKILLS_EquipMonsterFromTable, evaluated once when a summon is created. */
public final class SummonEquipment {
  private SummonEquipment() {}

  public static List<Item> generate(Files files, ItemGenerator generator, String monster,
      int skillLevel, int itemLevel, int difficulty, ItemData ownerItems,
      NativeUnitRandom random) {
    List<Item> equipment = new ArrayList<>();
    if (files.MonEquip == null) return equipment;
    boolean started = false;
    boolean[] occupied = new boolean[11];
    // Excel's backing IntMap iteration order is not TXT order. Native tiers
    // rely on descending rows and the first item that fills each body slot.
    for (int rowIndex = 0; rowIndex < files.MonEquip.size(); rowIndex++) {
      MonEquip.Entry row = files.MonEquip.get(rowIndex);
      if (row == null) continue;
      if (!monster.equalsIgnoreCase(row.monster)) {
        if (started) break;
        continue;
      }
      if (!started && row.level > skillLevel) continue;
      started = true;
      int count = 0;
      while (count < 3 && row.loc != null && count < row.loc.length
          && bodyLoc(row.loc[count]) != BodyLoc.NONE) count++;
      if (count == 0) continue;
      int index = random.nextInt(count);
      BodyLoc loc = bodyLoc(row.loc[index]);
      if (occupied[loc.ordinal()]) continue;
      String code = row.item[index] == null ? "" : row.item[index].trim();
      if (code.isEmpty()) {
        Item source = ownerItems == null ? null : ownerItems.getEquipped(loc);
        if (source == null) continue;
        code = source.code;
      }
      int quality = row.mod[index];
      if (quality < 0 || quality >= 8) quality = 0;
      Item item = generator.generateLootItem(code, Math.max(1, Math.min(99, itemLevel)),
          Quality.valueOf(quality), (int) random.nextUnsigned(), difficulty);
      if (item == null) continue;
      item.identify();
      item.bodyLoc = loc;
      occupied[loc.ordinal()] = true;
      equipment.add(item);
    }
    return equipment;
  }

  /** Summon gear cannot be swapped/dropped. Store its resolved contributions
   * in the durable base snapshot, which is also used after checkpoint restore. */
  public static Attributes apply(Attributes original, List<Item> equipment) {
    if (original == null || equipment.isEmpty()) return original;
    Attributes pet = Attributes.obtainLarge(original.base().table());
    pet.base().setAll(original.base());
    pet.reset();
    AttributesUpdater updater = new AttributesUpdater();
    Item primaryWeapon = null;
    for (Item item : equipment) {
      if (item.base instanceof Weapons.Entry
          && (primaryWeapon == null || item.bodyLoc == BodyLoc.RARM)) primaryWeapon = item;
    }
    for (Item item : equipment) {
      item.aggFlags |= StatListFlags.FLAG_MAGIC | StatListFlags.FLAG_RUNE;
      item.update(updater, pet, null, new com.badlogic.gdx.utils.IntIntMap());
      // Item-local enhanced damage/defense has already been resolved by the
      // item updater. Only its remaining global bonuses propagate to the pet.
      for (StatRef stat : item.attrs.remaining()) {
        StatRef old = pet.base().get(stat.id(), stat.encodedParams());
        int value = stat.encodedValues();
        if (old != null) {
          // Skill/chance stats carry encoded parameters. StatListRef.add's
          // scalar fast path rejects them even when their keys match.
          value = stat.entry().Encode <= 2
              ? old.encodedValues() + value : Math.max(old.encodedValues(), value);
        }
        pet.base().putEncoded(stat.id(), stat.encodedParams(), value);
      }
      copy(pet, item, Stat.armorclass, Stat.armorclass);
      if (item == primaryWeapon) {
        boolean twoHanded = ((Weapons.Entry) item.base)._2handed;
        copy(pet, item, twoHanded ? Stat.secondary_mindamage : Stat.mindamage, Stat.mindamage);
        copy(pet, item, twoHanded ? Stat.secondary_maxdamage : Stat.maxdamage, Stat.maxdamage);
      }
      pet.reset();
    }
    if (primaryWeapon != null) {
      Weapons.Entry weapon = (Weapons.Entry) primaryWeapon.base;
      int bonus = pet.getValue(Stat.strength, 0) * weapon.StrBonus / 100
          + pet.getValue(Stat.dexterity, 0) * weapon.DexBonus / 100
          - pet.getValue(Stat.strength, 0);
      // Monster melee already includes raw strength. Replace that default
      // with this weapon's StrBonus/DexBonus rather than adding it twice.
      pet.base().add(Stat.damagepercent, bonus);
      pet.reset();
    }
    return pet;
  }

  private static void copy(Attributes pet, Item item, short from, short to) {
    StatRef value = item.attrs.get(from, StatRef.obtain());
    if (value != null) pet.base().add(to, value.asInt());
  }

  private static BodyLoc bodyLoc(String code) {
    if (code == null || code.isEmpty()) return BodyLoc.NONE;
    switch (code.toLowerCase(java.util.Locale.ROOT)) {
      case "head": return BodyLoc.HEAD;
      case "neck": return BodyLoc.NECK;
      case "tors": return BodyLoc.TORS;
      case "rarm": return BodyLoc.RARM;
      case "larm": return BodyLoc.LARM;
      case "rrin": return BodyLoc.RRIN;
      case "lrin": return BodyLoc.LRIN;
      case "belt": return BodyLoc.BELT;
      case "feet": return BodyLoc.FEET;
      case "glov": return BodyLoc.GLOV;
      default: return BodyLoc.NONE;
    }
  }
}
