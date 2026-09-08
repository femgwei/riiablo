package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.StatListReader;
import com.riiablo.codec.excel.SetItems;
import com.riiablo.codec.excel.UniqueItems;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemUtils;
import com.riiablo.item.Quality;
import com.riiablo.item.RareQualityData;
import org.junit.jupiter.api.Test;

/** Verifies that generated 1.10f quality metadata survives a D2S round-trip. */
class D2SItemQualityRoundTripTest extends RiiabloTest {
  @Test
  void magicAndRareAffixesRoundTrip() {
    ItemGenerator generator = new ItemGenerator();
    assertQualityRoundTrip(generator.generateLootItem("cap", 35, Quality.MAGIC,
        0x13572468, Riiablo.NORMAL));
    assertQualityRoundTrip(generator.generateLootItem("cap", 35, Quality.RARE,
        0x24681357, Riiablo.NORMAL));
  }

  @Test
  void setAndUniqueMetadataRoundTripWhenNativeTablesContainCandidates() {
    SetItems.Entry set = firstEnabledSet();
    UniqueItems.Entry unique = firstEnabledUnique();
    assumeTrue(set != null || unique != null, "1.10f set/unique tables are unavailable");
    ItemGenerator generator = new ItemGenerator();
    if (set != null) {
      String code = set._item != null && !set._item.isEmpty() ? set._item : set.item;
      Item item = generator.generateLootItem(code, Math.max(30, set.lvl), Quality.SET,
          0x11223344, Riiablo.NORMAL);
      // A missing matching base causes the native generator to fall back to magic.
      if (item.quality == Quality.SET) assertQualityRoundTrip(item);
    }
    if (unique != null) {
      Item item = generator.generateLootItem(unique.code, Math.max(30, unique.lvl), Quality.UNIQUE,
          0x55667788, Riiablo.NORMAL);
      if (item.quality == Quality.UNIQUE) assertQualityRoundTrip(item);
    }
  }

  @Test
  void runewordAndExtendedItemFlagsRoundTrip() {
    String weaponCode = firstWeaponCode();
    assumeTrue(weaponCode != null, "1.10f weapon table is unavailable");
    Item item = new ItemGenerator().generateLootItem(weaponCode, 40, Quality.NORMAL,
        0x7A6B5C4D, Riiablo.NORMAL);
    item.flags |= Item.ITEMFLAG_RUNEWORD | Item.ITEMFLAG_ETHEREAL | Item.ITEMFLAG_INSCRIBED;
    item.runewordData = 0x1234;
    item.inscription = "RunewordHero";

    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, true, "RunewordHero", Riiablo.AMAZON);
    D2S encoded = D2SWriter96.createD2S(character);
    encoded.items.items = new Array<>();
    encoded.items.items.add(item);
    byte[] bytes = new D2SWriter96().writeD2S(encoded);
    D2S decoded = D2SReader.INSTANCE.readComplete(bytes);
    Item restored = decoded.items.items.first();

    assertEquals(item.flags, restored.flags);
    assertEquals(item.runewordData, restored.runewordData);
    assertEquals(item.inscription, restored.inscription);
  }

  private static void assertQualityRoundTrip(Item item) {
    assertNotNull(item);
    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, true, "QualityHero", Riiablo.AMAZON);
    D2S encoded = D2SWriter96.createD2S(character);
    encoded.items.items = new Array<>();
    encoded.items.items.add(item);

    byte[] bytes = new D2SWriter96().writeD2S(encoded);
    D2S decoded = D2SReader.INSTANCE.readComplete(bytes,
        new StatListReader(), new com.riiablo.item.ItemReader());
    Item restored = decoded.items.items.first();
    assertEquals(item.code, restored.code);
    assertEquals(item.quality, restored.quality);
    assertEquals(item.qualityId, restored.qualityId);
    assertEquals(item.flags, restored.flags);
    assertEquals(item.id, restored.id);
    if (item.quality == Quality.RARE || item.quality == Quality.CRAFTED) {
      assertNotNull(restored.qualityData);
      RareQualityData expected = (RareQualityData) item.qualityData;
      RareQualityData actual = (RareQualityData) restored.qualityData;
      assertArrayEquals(expected.prefixes, actual.prefixes);
      assertArrayEquals(expected.suffixes, actual.suffixes);
    } else if (item.quality == Quality.SET || item.quality == Quality.UNIQUE) {
      assertNotNull(restored.qualityData);
    }
  }

  private static SetItems.Entry firstEnabledSet() {
    if (Riiablo.files == null || Riiablo.files.SetItems == null) return null;
    for (SetItems.Entry entry : Riiablo.files.SetItems) {
      String code = entry._item != null && !entry._item.isEmpty() ? entry._item : entry.item;
      if (isBaseCode(code)) return entry;
    }
    return null;
  }

  private static UniqueItems.Entry firstEnabledUnique() {
    if (Riiablo.files == null || Riiablo.files.UniqueItems == null) return null;
    for (UniqueItems.Entry entry : Riiablo.files.UniqueItems) {
      if (entry.enabled && isBaseCode(entry.code)) return entry;
    }
    return null;
  }

  private static String firstWeaponCode() {
    if (Riiablo.files == null || Riiablo.files.weapons == null) return null;
    for (com.riiablo.codec.excel.Weapons.Entry entry : Riiablo.files.weapons) {
      if (isBaseCode(entry.code)) return entry.code;
    }
    return null;
  }

  private static boolean isBaseCode(String code) {
    if (code == null || code.isEmpty()) return false;
    try {
      ItemUtils.getBase(code);
      return true;
    } catch (RuntimeException ignored) {
      // Some 1.10f rows expose a display name in the item column rather than
      // the four-character base code; those rows cannot be generated here.
      return false;
    }
  }
}
