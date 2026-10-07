package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

/** Real Excel/MPQ half of the native item-generation test gate. */
class NativeItemGeneratorIntegrationTest extends RiiabloTest {
  @Test
  void fixedSeedProducesStableBaseStatsAndTraits() {
    ItemGenerator generator = new ItemGenerator();
    Item first = generator.generateLootItem("cap", 20, Quality.NORMAL, 0x12345678, 0);
    Item second = generator.generateLootItem("cap", 20, Quality.NORMAL, 0x12345678, 0);

    assertEquals(first.flags, second.flags);
    assertEquals(first.attrs.base().get(Stat.armorclass).asInt(),
        second.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(first.attrs.base().get(Stat.durability).asInt(),
        second.attrs.base().get(Stat.durability).asInt());
    assertEquals(first.attrs.base().get(Stat.maxdurability).asInt(),
        second.attrs.base().get(Stat.maxdurability).asInt());
    assertTrue(first.attrs.base().get(Stat.durability).asInt() > 0);
  }

  @Test
  void magicDropUsesEligiblePersistedAffixes() {
    Item item = new ItemGenerator().generateLootItem(
        "cap", 30, Quality.MAGIC, 0x34567812, 1);
    assertEquals(Quality.MAGIC, item.quality);
    assertTrue(item.qualityId != 0);
    assertNotNull(item.attrs.list());
    assertTrue(item.attrs.list().numLists() > 0);
  }

  @Test
  void rareNameAndMagicAffixesAreStableForItemSeed() {
    ItemGenerator generator = new ItemGenerator();
    Item first = generator.generateLootItem("cap", 35, Quality.RARE, 0x45678123, 1);
    Item second = generator.generateLootItem("cap", 35, Quality.RARE, 0x45678123, 1);
    assertEquals(first.qualityId, second.qualityId);
    assertTrue(first.qualityData instanceof RareQualityData);
    RareQualityData firstRare = (RareQualityData) first.qualityData;
    RareQualityData secondRare = (RareQualityData) second.qualityData;
    assertArrayEquals(firstRare.prefixes, secondRare.prefixes);
    assertArrayEquals(firstRare.suffixes, secondRare.suffixes);
  }

  @Test
  void eagleOrbReceivesNativeSorceressStaffMods() {
    ItemGenerator generator = new ItemGenerator();
    Item orb = null;
    Set<Integer> skillIds = new HashSet<>();
    for (int seed = 1; seed <= 128 && skillIds.isEmpty(); seed++) {
      Item candidate = generator.generateLootItem("ob1", 12, Quality.NORMAL, seed, 0);
      for (StatRef stat : candidate.attrs.list(0)) {
        if (stat.id() != Stat.item_singleskill) continue;
        assertTrue(skillIds.add(stat.encodedParams()), "staffmods must be distinct");
        assertTrue(stat.asInt() >= 1 && stat.asInt() <= 3);
      }
      if (!skillIds.isEmpty()) orb = candidate;
    }

    assertNotNull(orb, "a StaffMods=sor Eagle Orb must be able to roll staffmods");
    assertEquals("sor", orb.typeEntry.StaffMods);
    for (int skillId : skillIds) {
      Skills.Entry skill = com.riiablo.Riiablo.files.skills.get(skillId);
      assertNotNull(skill);
      assertEquals("sor", skill.charclass);
    }
  }

  @Test
  void ordinarySwordNeverReceivesStaffMods() {
    Item sword = new ItemGenerator().generateLootItem(
        "ssd", 12, Quality.NORMAL, 7, 0);
    assertTrue(sword.typeEntry.StaffMods == null || sword.typeEntry.StaffMods.isEmpty());
    assertFalse(sword.attrs.list(0).containsAny(Stat.item_singleskill));
  }
}
