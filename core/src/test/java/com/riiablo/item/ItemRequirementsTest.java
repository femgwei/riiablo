package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.codec.excel.MagicAffix;
import com.riiablo.codec.excel.UniqueItems;
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

  @Test
  void magicAffixRequirementIsIncludedWithoutDoublingBaseRequirement() {
    int prefixId = 0;
    for (MagicAffix affix : Riiablo.files.MagicPrefix) {
      if (affix.levelreq == 3) {
        prefixId = Riiablo.files.MagicPrefix.index(affix.name);
        break;
      }
    }
    assertTrue(prefixId > 0, "test data should contain a level-3 magic prefix");

    Item item = new Item();
    item.reset();
    item.base = new ItemEntry();
    item.base.levelreq = 1;
    item.quality = Quality.MAGIC;
    item.qualityId = prefixId;
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().put(Stat.item_levelreq, 1);
    item.attrs.reset();

    assertEquals(3, ItemRequirements.requiredLevel(
        item, CharacterClass.AMAZON));
  }

  @Test
  void uniqueAndSocketRequirementsUseTheHighestLevel() {
    Item socket = new Item();
    socket.reset();
    socket.base = new ItemEntry();
    socket.base.levelreq = 8;
    socket.attrs = Attributes.obtainStandard();
    socket.attrs.base().put(Stat.item_levelreq, 8);
    socket.attrs.reset();

    Item item = new Item();
    item.reset();
    item.base = new ItemEntry();
    item.base.levelreq = 2;
    item.quality = Quality.UNIQUE;
    UniqueItems.Entry unique = new UniqueItems.Entry();
    unique.lvl_req = 6;
    item.qualityData = unique;
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().put(Stat.item_levelreq, 2);
    item.attrs.reset();
    item.sockets = new Array<>();
    item.sockets.add(socket);

    assertEquals(8, ItemRequirements.requiredLevel(
        item, CharacterClass.AMAZON));
  }

  @Test
  void nonClassSkillAddsSixLevelsForOtherClasses() {
    Item item = new Item();
    item.reset();
    item.base = new ItemEntry();
    item.quality = Quality.MAGIC;
    item.attrs = Attributes.obtainStandard();
    item.attrs.buildList().putEncoded(Stat.item_nonclassskill, 54, 1); // Teleport

    assertEquals(18, ItemRequirements.requiredLevel(item, CharacterClass.SORCERESS));
    assertEquals(24, ItemRequirements.requiredLevel(item, CharacterClass.AMAZON));
  }
}
