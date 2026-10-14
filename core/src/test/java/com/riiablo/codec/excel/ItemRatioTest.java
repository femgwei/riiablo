package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ItemRatioTest {
  private static class Table extends ItemRatio {
    Entry add(int id, int version, boolean uber, boolean specific) {
      Entry row = new Entry();
      row.Version = version;
      row.Uber = uber;
      row.ClassSpecific = specific;
      put(id, row);
      return row;
    }
  }

  @Test
  void newestCompatibleVersionAndLastSourceRowWin() {
    Table table = new Table();
    ItemRatio.Entry expansion = table.add(4, 1, false, false);
    ItemRatio.Entry classic = table.add(0, 0, false, false);
    table.add(2, 101, false, false);
    ItemEntry base = new ItemEntry();
    assertSame(classic, table.get(base, null, 0));
    assertSame(expansion, table.get(base, null, 100));
    ItemRatio.Entry last = table.add(9, 1, false, false);
    assertSame(last, table.get(base, null, 100));
    assertNull(table.get(null, null, 100));
  }

  @Test
  void uberMeansUpgradedNonQuestEquipmentNotQuestFlag() {
    Table table = new Table();
    ItemRatio.Entry normal = table.add(0, 1, false, false);
    ItemRatio.Entry upgraded = table.add(1, 1, true, false);
    Weapons.Entry weapon = new Weapons.Entry();
    weapon.code = "9ax";
    weapon.ubercode = "9ax";
    assertSame(upgraded, table.get(weapon, null, 100));
    weapon.quest = 1;
    assertSame(normal, table.get(weapon, null, 100));
    weapon.quest = 0;
    weapon.type = "mpot";
    assertSame(normal, table.get(weapon, null, 100));
    Armor.Entry armor = new Armor.Entry();
    armor.code = armor.ultracode = "uar";
    assertSame(upgraded, table.get(armor, null, 100));
    ItemEntry misc = new ItemEntry();
    misc.code = misc.ubercode = "abc";
    assertSame(normal, table.get(misc, null, 100));
  }

  @Test
  void classSpecificIsIndependentOfEquipmentTier() {
    Table table = new Table();
    ItemRatio.Entry specific = table.add(0, 1, true, true);
    Weapons.Entry weapon = new Weapons.Entry();
    weapon.code = weapon.ultracode = "7ar";
    ItemTypes.Entry type = new ItemTypes.Entry();
    type.Class = "ama";
    assertSame(specific, table.get(weapon, type, 100));
    type.Class = "";
    assertNull(table.get(weapon, type, 100));
  }
}
