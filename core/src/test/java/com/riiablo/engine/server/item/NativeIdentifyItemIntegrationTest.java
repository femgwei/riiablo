package com.riiablo.engine.server.item;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.TxtParser;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.Quality;
import com.riiablo.item.StoreLoc;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real 1.10f rows plus production inventory/authority, without a GL window. */
class NativeIdentifyItemIntegrationTest extends RiiabloTest {
  @Test
  void nativeIdentifyRowsUsePSpellOneAndCursorZero() throws Exception {
    assertEquals(1, Riiablo.files.misc.get("isc").pSpell);
    assertEquals(1, Riiablo.files.misc.get("ibk").pSpell);
    boolean found = false;
    try (TxtParser books = TxtParser.loadFromFile(
        Riiablo.mpqs.resolve("data\\global\\excel\\books.txt"))) {
      int scrollSkill = books.getColumnId("scrollskill");
      int icon = books.getColumnId("spellicon");
      assertTrue(scrollSkill >= 0 && icon >= 0);
      while (books.nextLine() != null) {
        if ("Scroll of Identify".equalsIgnoreCase(books.getString(scrollSkill))) {
          assertEquals(0, books.getInt(icon));
          found = true;
        }
      }
    }
    assertTrue(found, "Native Books.txt identify row must be present");
  }

  @Test
  void targetingDoesNotConsumeAndSuccessfulIdentificationRevealsExistingRolls() {
    CharData character = character();
    Item scroll = stored(character, "isc", 100);
    Item target = equipment(character, 101);
    int oldAffix = target.qualityId;
    String hiddenName = target.getNameString();

    assertTrue(character.getItems().canUseIdentifyItem(scroll));
    assertTrue(character.getItems().contains(scroll));
    assertFalse(target.isIdentified());
    assertTrue(character.getItems().identifyItem(scroll, target));

    assertTrue(target.isIdentified());
    assertFalse(character.getItems().contains(scroll));
    assertEquals(oldAffix, target.qualityId);
    assertNotEquals(hiddenName, target.getNameString(), "Cached base name must be invalidated");
  }

  @Test
  void lastTomeChargeLeavesAnEmptyBookAndCannotIdentifyAgain() {
    CharData character = character();
    Item tome = stored(character, "ibk", 102);
    quantity(tome, 1);
    Item first = equipment(character, 103);
    Item second = equipment(character, 104);

    assertTrue(character.getItems().identifyItem(tome, first));
    assertTrue(character.getItems().contains(tome));
    assertEquals(0, tome.attrs.base().get(Stat.quantity).asInt());
    assertFalse(character.getItems().canUseIdentifyItem(tome));
    assertFalse(character.getItems().identifyItem(tome, second));
    assertFalse(second.isIdentified());
  }

  @Test
  void alreadyIdentifiedOrForeignTargetNeverConsumesScroll() {
    CharData character = character();
    Item scroll = stored(character, "isc", 105);
    Item target = equipment(character, 106);
    target.flags |= Item.ITEMFLAG_IDENTIFIED;
    assertFalse(character.getItems().identifyItem(scroll, target));
    Item foreign = equipment(character(), 107);
    assertFalse(character.getItems().identifyItem(scroll, foreign));
    assertFalse(character.getItems().identifyItem(scroll, scroll));
    assertTrue(character.getItems().contains(scroll));
    assertFalse(foreign.isIdentified());
  }

  @Test
  void stashCubeAndEquippedModesAreValidButGroundAndVendorItemsAreNot() {
    CharData character = character();
    Item tome = stored(character, "ibk", 108);
    quantity(tome, 5);
    Item target = equipment(character, 109);
    for (StoreLoc store : new StoreLoc[] {StoreLoc.INVENTORY, StoreLoc.STASH, StoreLoc.CUBE}) {
      target.storeLoc = store;
      assertTrue(character.getItems().canIdentifyItem(tome, target));
    }
    target.location = Location.EQUIPPED;
    assertTrue(character.getItems().canIdentifyItem(tome, target));
    target.location = Location.GROUND;
    assertFalse(character.getItems().canIdentifyItem(tome, target));
    target.location = Location.STORED;
    target.storeLoc = StoreLoc.INVENTORY;
    target.flags2 |= Item.ITEMFLAG2_INSTORE;
    assertFalse(character.getItems().identifyItem(tome, target));
    assertEquals(5, tome.attrs.base().get(Stat.quantity).asInt());
  }

  @Test
  void sourceMustBeOwnedInInventoryAndCursorMustBeEmpty() {
    CharData character = character();
    Item source = stored(character, "ibk", 110);
    quantity(source, 2);
    Item target = equipment(character, 111);
    source.storeLoc = StoreLoc.STASH;
    assertFalse(character.getItems().identifyItem(source, target));
    source.storeLoc = StoreLoc.INVENTORY;
    character.groundToCursor(new ItemGenerator().generate("hp1"));
    assertFalse(character.getItems().identifyItem(source, target));
    assertFalse(target.isIdentified());
    assertEquals(2, source.attrs.base().get(Stat.quantity).asInt());
  }

  @Test
  void authorityUsesStableTargetIdAndRejectsDuplicateAndStaleRequests() {
    CharData character = character();
    Item tome = stored(character, "ibk", -112);
    quantity(tome, 3);
    Item target = equipment(character, 113);
    target.id = -113;
    Item next = equipment(character, 114);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    assertFalse(service.apply(1, character, identify(0, tome.id, 0)).success);
    assertFalse(target.isIdentified());
    assertTrue(service.apply(1, character, identify(0, tome.id, target.id)).success);
    assertEquals(1, service.revision(1));
    assertEquals(2, tome.attrs.base().get(Stat.quantity).asInt());
    assertEquals(ItemMoveFailure.STALE_INVENTORY,
        service.apply(1, character, identify(0, tome.id, next.id)).failure);
    assertFalse(service.apply(1, character, identify(1, tome.id, target.id)).success);
    assertEquals(1, service.revision(1));
    assertEquals(2, tome.attrs.base().get(Stat.quantity).asInt());
    assertFalse(next.isIdentified());
  }

  private static CharData character() {
    return CharData.obtain().clear().set(Riiablo.NORMAL, false, "IdentifyHero", Riiablo.AMAZON);
  }

  private static Item stored(CharData character, String code, int id) {
    Item item = new ItemGenerator().generate(code);
    item.id = id;
    assertTrue(character.getItems().addToInventory(item));
    return item;
  }

  private static Item equipment(CharData character, int id) {
    Item item = new ItemGenerator().generateLootItem("cap", 20, Quality.MAGIC, id, Riiablo.NORMAL);
    item.id = id;
    assertFalse(item.isIdentified());
    assertTrue(character.getItems().addToInventory(item));
    return item;
  }

  private static void quantity(Item item, int count) {
    item.attrs.base().put(Stat.quantity, count);
    item.attrs.reset();
  }

  private static ItemMoveIntent identify(long revision, int sourceId, int targetId) {
    return new ItemMoveIntent(1, revision, ItemMoveOperation.IDENTIFY_ITEM,
        sourceId, -1, -1, targetId, -1, -1, false);
  }
}
