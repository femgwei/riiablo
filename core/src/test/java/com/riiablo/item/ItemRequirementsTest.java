package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.save.CharData;

class ItemRequirementsTest extends RiiabloTest {
  @Test
  void reportsEachMissingRequirement() {
    Item item = new Item();
    item.reset();
    item.base = new ItemEntry();
    item.base.levelreq = 20;
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().put(Stat.item_levelreq, 20);
    item.attrs.base().put(Stat.reqstr, 50);
    item.attrs.base().put(Stat.reqdex, 30);
    item.attrs.reset();

    CharData character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "ReqHero", Riiablo.AMAZON);
    character.level = 10;
    character.getStats().aggregate().put(Stat.level, 10);
    character.getStats().aggregate().put(Stat.strength, 40);
    character.getStats().aggregate().put(Stat.dexterity, 30);

    ItemRequirements.Result result = ItemRequirements.check(item, character);
    assertFalse(result.levelMet);
    assertFalse(result.strengthMet);
    assertTrue(result.dexterityMet);
    assertFalse(result.usable());
  }

  @Test
  void checksBaseClassRestriction() {
    Item item = new Item();
    item.reset();
    item.base = new ItemEntry();
    item.attrs = Attributes.obtainStandard();
    item.attrs.reset();
    item.typeEntry = new ItemTypes.Entry();
    item.typeEntry.Class = "ama";

    CharData character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "ReqHero", Riiablo.SORCERESS);
    ItemRequirements.Result result = ItemRequirements.check(item, character);
    assertFalse(result.classMet);
    assertFalse(result.usable());
  }
}
