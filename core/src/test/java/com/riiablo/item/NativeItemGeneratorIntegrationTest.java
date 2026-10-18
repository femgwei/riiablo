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
  void charmDropsCannotBecomeAffixlessWhiteItemsAndPersistTheirGraphic() {
    ItemGenerator generator = new ItemGenerator();
    for (String code : new String[] {"cm1", "cm2", "cm3"}) {
      Set<Integer> pictures = new HashSet<>();
      for (Quality requested : new Quality[] {null, Quality.LOW, Quality.NORMAL, Quality.HIGH, Quality.MAGIC}) {
        for (int seed = 1; seed <= 12; seed++) {
          Item charm = generator.generateLootItem(code, 2, requested, seed, 0);
          assertEquals(Quality.MAGIC, charm.quality, code);
          assertFalse(charm.isIdentified());
          assertTrue(charm.qualityId != 0);
          assertFalse(charm.attrs.list(0).isEmpty());
          assertTrue(charm.pictureId >= 0 && charm.pictureId < charm.typeEntry.VarInvGfx);
          pictures.add((int) charm.pictureId);
          assertEquals(charm.typeEntry.InvGfx[charm.pictureId], charm.getInvFileName());
          assertFalse(charm.base.invfile.equals(charm.getInvFileName()));
          io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
          try {
            new ItemWriter().writeItem(charm, com.riiablo.io.ByteOutput.wrap(buffer));
            Item restored = new ItemReader().readItem(com.riiablo.io.ByteInput.wrap(buffer));
            assertEquals(charm.quality, restored.quality);
            assertEquals(charm.qualityId, restored.qualityId);
            assertEquals(charm.pictureId, restored.pictureId);
            assertEquals(charm.getInvFileName(), restored.getInvFileName());
            assertFalse(restored.attrs.list(0).isEmpty());
          } finally { buffer.release(); }
        }
      }
      assertTrue(pictures.size() > 1, "inventory graphics must actually be rolled");
    }
  }

  @Test
  void oldAffixlessCharmUsesValidIconWithoutSilentlyRerollingItsStats() {
    Item charm = new ItemGenerator().generate("cm1");
    charm.id = 1318;
    charm.ilvl = 2;
    charm.quality = Quality.NORMAL;
    charm.pictureId = Item.NO_PICTURE_ID;
    assertEquals("invch1", charm.getInvFileName());
    assertEquals(Quality.NORMAL, charm.quality);
    assertEquals(Item.NO_PICTURE_ID, charm.pictureId);
    assertEquals(0, charm.attrs.list().numLists());
    charm.pictureId = 7;
    assertEquals("invch7", charm.getInvFileName());
  }

  @Test
  void explicitItemDataSurvivesGenerationRequirementsAndWireRoundTripWithoutGlobals() {
    com.riiablo.Files files = com.riiablo.Riiablo.files;
    ItemGenerator generator = new ItemGenerator(files);
    Item expected = generator.generateLootItem("cap", 30, Quality.MAGIC, 0x34567812, 1);
    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
    try {
      com.riiablo.Riiablo.files = null;
      Item actual = generator.generateLootItem("cap", 30, Quality.MAGIC, 0x34567812, 1);
      assertEquals(expected.qualityId, actual.qualityId);
      assertEquals(expected.type.getOrdinalString(), actual.type.getOrdinalString());
      assertEquals(ItemRequirements.requiredLevel(expected, com.riiablo.CharacterClass.AMAZON),
          ItemRequirements.requiredLevel(actual, com.riiablo.CharacterClass.AMAZON));
      assertTrue(VendorPricing.buyPrice(actual) >= 0);
      new ItemWriter().writeItem(actual, com.riiablo.io.ByteOutput.wrap(buffer));
      Item restored = new ItemReader(files).readItem(com.riiablo.io.ByteInput.wrap(buffer));
      assertEquals(actual.code, restored.code);
      assertEquals(actual.qualityId, restored.qualityId);
      assertEquals(actual.attrs.base().get(Stat.maxdurability).asInt(),
          restored.attrs.base().get(Stat.maxdurability).asInt());
      assertTrue(restored.files() == files);
      Item gem = generator.generateLootItem("gcv", 20, Quality.NORMAL, 1234, 0);
      assertTrue(gem.attrs.list().numLists() > 0);
    } finally {
      com.riiablo.Riiablo.files = files;
      buffer.release();
    }
  }

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
    assertFalse(item.isIdentified());
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
    assertFalse(first.isIdentified());
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
