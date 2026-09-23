package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.ItemReader;
import com.riiablo.item.ItemWriter;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class AuthoritativeItemMoveServiceTest extends RiiabloTest {
  @Test
  void stableItemIdNeverFallsBackToInventoryIndex() {
    CharData character = character();
    Item owned = item("cap", 77);
    assertTrue(character.getItems().addToInventory(owned));

    byte failure = ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.STORE_TO_CURSOR, 0, -1, -1, -1));

    assertEquals(ItemMoveFailure.ITEM_NOT_OWNED, failure);
    assertEquals(Location.STORED, owned.location);
  }

  @Test
  void cubeAndStashUseTheirNativeGridDimensions() {
    CharData character = character();
    character.groundToCursor(item("hp1", 78));

    assertEquals(ItemMoveFailure.NONE, ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.CURSOR_TO_STORE, -1, StoreLoc.CUBE.ordinal(), 2, 3)));
    assertEquals(ItemMoveFailure.INVALID_POSITION, ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.CURSOR_TO_STORE, -1, StoreLoc.CUBE.ordinal(), 3, 3)));
    assertEquals(ItemMoveFailure.NONE, ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.CURSOR_TO_STORE, -1, StoreLoc.STASH.ordinal(), 5, 7)));
    assertEquals(ItemMoveFailure.INVALID_POSITION, ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.CURSOR_TO_STORE, -1, StoreLoc.STASH.ordinal(), 6, 7)));
  }

  @Test
  void storeSwapIgnoresOnlyTheSelectedTargetRectangle() {
    CharData character = character();
    Item target = item("cap", 79);
    Item replacement = item("hp1", 80);
    assertTrue(character.getItems().addToInventory(target));
    character.groundToCursor(replacement);
    ItemMoveIntent swap = intent(ItemMoveOperation.SWAP_STORE_ITEM, target.id,
        StoreLoc.INVENTORY.ordinal(), target.gridX, target.gridY);

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.apply(5, character, swap);

    assertTrue(result.success);
    assertEquals(1L, result.revision);
    assertSame(target, character.getItems().getCursor());
    assertEquals(Location.STORED, replacement.location);
    assertEquals(StoreLoc.INVENTORY, replacement.storeLoc);
  }

  @Test
  void failedGroundCreationRestoresCursorAndRevision() {
    CharData character = character();
    Item cursor = item("hp1", 81);
    character.groundToCursor(cursor);
    ItemMoveIntent drop = intent(ItemMoveOperation.CURSOR_TO_GROUND,
        -1, -1, -1, -1);

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.drop(
        6, character, drop, ignored -> false);

    assertFalse(result.success);
    assertEquals(ItemMoveFailure.MUTATION_FAILED, result.failure);
    assertEquals(0L, result.revision);
    assertSame(cursor, character.getItems().getCursor());
    assertTrue(character.getItems().contains(cursor));
  }

  @Test
  void failedPickupNeverConsumesGroundEntity() {
    CharData character = character();
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    ItemMoveIntent missing = new ItemMoveIntent(2L, 0L,
        ItemMoveOperation.GROUND_TO_CURSOR, 999, 9001,
        -1, -1, -1, -1, false);

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        7, character, missing, null);

    assertFalse(result.success);
    assertFalse(result.consumeGroundEntity);
    assertEquals(ItemMoveFailure.GROUND_ITEM_NOT_FOUND, result.failure);
    assertEquals(0L, result.revision);
  }

  @Test
  void groundPotionPrefersFirstFreeBeltSlot() {
    CharData character = character();
    Item potion = item("hp1", 82);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        8, character, pickupIntent(0L, potion), potion);

    assertTrue(result.success);
    assertTrue(result.consumeGroundEntity);
    assertEquals(1L, result.revision);
    assertEquals(Location.BELT, potion.location);
    assertEquals(StoreLoc.NONE, potion.storeLoc);
    assertEquals(0, potion.gridX);
    assertEquals(0, potion.gridY);
  }

  @Test
  void groundPotionUsesNextFreeBeltSlotThenFallsBackToInventory() {
    CharData character = character();
    equipBelt(character, "hbl", 99);
    for (int i = 0; i < 16; i++) {
      assertTrue(character.getItems().addPotionToBelt(item("hp1", 100 + i)));
    }
    Item potion = item("hp1", 200);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        9, character, pickupIntent(0L, potion), potion);

    assertTrue(result.success);
    assertEquals(Location.STORED, potion.location);
    assertEquals(StoreLoc.INVENTORY, potion.storeLoc);
  }

  @Test
  void groundPotionFillsMatchingFamilyColumnBeforeStartingAnotherQuickSlot() {
    CharData character = character();
    equipBelt(character, "hbl", 201);
    Item minorHealing = item("hp1", 202);
    assertTrue(character.getItems().addPotionToBelt(minorHealing));
    Item greaterHealing = item("hp5", 203);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome healingResult = service.pickup(
        11, character, pickupIntent(0L, greaterHealing), greaterHealing);

    assertTrue(healingResult.success);
    assertEquals(Location.BELT, greaterHealing.location);
    assertEquals(0, greaterHealing.gridX);
    assertEquals(1, greaterHealing.gridY);

    Item mana = item("mp1", 204);
    AuthoritativeItemMoveService.Outcome manaResult = service.pickup(
        11, character, pickupIntent(1L, mana), mana);

    assertTrue(manaResult.success);
    assertEquals(Location.BELT, mana.location);
    assertEquals(1, mana.gridX);
    assertEquals(0, mana.gridY);
  }

  @Test
  void fullMatchingPotionColumnStartsAtFirstEmptyQuickSlot() {
    CharData character = character();
    equipBelt(character, "hbl", 209);
    for (int i = 0; i < 4; i++) {
      Item potion = item(i == 0 ? "hp1" : "hp5", 210 + i);
      assertTrue(character.getItems().addPotionToBelt(potion));
      assertEquals(0, potion.gridX);
      assertEquals(i, potion.gridY);
    }
    Item next = item("hp2", 220);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        12, character, pickupIntent(0L, next), next);

    assertTrue(result.success);
    assertEquals(Location.BELT, next.location);
    assertEquals(1, next.gridX);
    assertEquals(0, next.gridY);
  }

  @Test
  void groundEquipmentAutoEquipsEmptyBodySlot() {
    CharData character = character();
    Item armor = item("cap", 201);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        10, character, pickupIntent(0L, armor), armor);

    assertTrue(result.success);
    assertEquals(Location.EQUIPPED, armor.location);
    assertEquals(BodyLoc.HEAD, armor.bodyLoc);
    assertSame(armor, character.getItems().getSlot(BodyLoc.HEAD));
  }

  @Test
  void inventoryOpenGroundPickupLeavesItemOnCursor() {
    CharData character = character();
    Item armor = item("cap", 202);
    ItemMoveIntent manualPickup = new ItemMoveIntent(1L, 0L,
        ItemMoveOperation.GROUND_TO_CURSOR, armor.id, 9000 + armor.id,
        -1, -1, -1, -1, false, true);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        10, character, manualPickup, armor);

    assertTrue(result.success);
    assertTrue(result.consumeGroundEntity);
    assertSame(armor, character.getItems().getCursor());
    assertEquals(Location.CURSOR, armor.location);
  }

  @Test
  void characterWithoutBeltHasOnlyFourQuickSlots() {
    CharData character = character();
    assertEquals(1, character.getItems().getBeltRows());
    for (int i = 0; i < 4; i++) {
      assertTrue(character.getItems().addPotionToBelt(item("hp1", 230 + i)));
    }
    assertFalse(character.getItems().addPotionToBelt(item("hp1", 240)));
  }

  @Test
  void inventoryPotionCanMoveDirectlyToBeltWithoutUsingCursor() {
    CharData character = character();
    Item potion = item("hp1", 83);
    assertTrue(character.getItems().addToInventory(potion));
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    ItemMoveIntent intent = new ItemMoveIntent(0L, 0L, ItemMoveOperation.STORE_TO_BELT,
        potion.id, -1, -1, 0, 0, -1, false);

    AuthoritativeItemMoveService.Outcome result = service.apply(8, character, intent);

    assertTrue(result.success);
    assertEquals(Location.BELT, potion.location);
    assertEquals(StoreLoc.NONE, potion.storeLoc);
    assertEquals(0, potion.gridX);
    assertEquals(0, potion.gridY);
    assertEquals(null, character.getItems().getCursor());
  }

  @Test
  void equippingBeltKeepsExistingPotionInBottomQuickSlot() {
    CharData character = character();
    Item potion = item("hp1", 244);
    assertTrue(character.getItems().addPotionToBelt(potion));
    assertEquals(0, potion.gridY);

    equipBelt(character, "mbl", 245);

    assertEquals(3, character.getItems().getBeltRows());
    assertEquals(0, potion.gridY);
    assertSame(potion, character.getItems().getBeltPotion(potion.gridX));
  }

  @Test
  void manualBeltMoveRejectsRowsNotProvidedByEquippedBelt() {
    CharData character = character();
    equipBelt(character, "mbl", 245); // Native medium belt: three rows.
    Item potion = item("hp1", 246);
    character.groundToCursor(potion);

    assertEquals(ItemMoveFailure.INVALID_BELT_SLOT, ItemMoveValidator.validate(character,
        intent(ItemMoveOperation.CURSOR_TO_BELT, -1, StoreLoc.NONE.ordinal(), 0, 3)));
    assertSame(potion, character.getItems().getCursor());
    assertFalse(character.getItems().canStoreInBelt(potion, 0, 3));
    assertTrue(character.getItems().canStoreInBelt(potion, 0, 2));
  }

  @Test
  void useBeltItemConsumesBottomPotionAndShiftsColumnDown() {
    CharData character = character();
    equipBelt(character, "hbl", 250);
    Item first = item("hp1", 251);
    Item second = item("hp2", 252);
    assertTrue(character.getItems().addPotionToBelt(first));
    assertTrue(character.getItems().addPotionToBelt(second));
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.apply(13, character,
        intent(ItemMoveOperation.USE_BELT_ITEM, first.id, -1, 0, -1));

    assertTrue(result.success);
    assertFalse(character.getItems().contains(first));
    assertSame(second, character.getItems().getBeltPotion(0));
    assertEquals(0, second.gridY);
  }

  @Test
  void townPortalScrollSurvivesPickupAndSnapshotEncoding() {
    CharData character = character();
    Item scroll = item("tsc", 260);
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome result = service.pickup(
        14, character, pickupIntent(0L, scroll), scroll);

    assertTrue(result.success);
    assertEquals(Location.STORED, scroll.location);
    assertEquals(StoreLoc.INVENTORY, scroll.storeLoc);
    ByteOutput encoded = ByteOutput.wrap(Unpooled.buffer());
    new ItemWriter().writeItem(scroll, encoded);
    Item decoded = new ItemReader().readItem(ByteInput.wrap(encoded.buffer()));

    assertNotNull(decoded);
    assertEquals("tsc", decoded.code);
  }

  @Test
  void inventoryTownPortalConsumesScrollOnlyAfterWorldEffectSucceeds() {
    CharData character = character();
    Item scroll = item("tsc", 261);
    assertTrue(character.getItems().addToInventory(scroll));
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();

    AuthoritativeItemMoveService.Outcome rejected = service.useInventoryItem(15, character,
        intent(ItemMoveOperation.USE_INVENTORY_ITEM, scroll.id, -1, -1, -1), () -> false);
    assertFalse(rejected.success);
    assertTrue(character.getItems().contains(scroll));
    assertEquals(0L, service.revision(15));

    AuthoritativeItemMoveService.Outcome used = service.useInventoryItem(15, character,
        intent(ItemMoveOperation.USE_INVENTORY_ITEM, scroll.id, -1, -1, -1), () -> true);
    assertTrue(used.success);
    assertFalse(character.getItems().contains(scroll));
    assertEquals(1L, service.revision(15));
  }

  @Test
  void inventoryTownPortalRejectsNonPortalMiscItems() {
    CharData character = character();
    Item potion = item("hp1", 262);
    assertTrue(character.getItems().addToInventory(potion));
    ItemMoveIntent use = intent(ItemMoveOperation.USE_INVENTORY_ITEM, potion.id, -1, -1, -1);
    assertEquals(ItemMoveFailure.INVALID_ITEM, ItemMoveValidator.validate(character, use));
  }

  private static CharData character() {
    return CharData.obtain().set(Riiablo.NORMAL, false, "MoveHero", Riiablo.AMAZON);
  }

  private static Item item(String code, int id) {
    Item item = new ItemGenerator().generate(code);
    item.id = id;
    return item;
  }

  private static void equipBelt(CharData character, String code, int id) {
    Item belt = item(code, id);
    character.getItems().add(belt);
    character.getItems().equipItem(BodyLoc.BELT, belt);
  }

  private static ItemMoveIntent intent(byte operation, int itemId,
                                       int storeLoc, int x, int y) {
    return new ItemMoveIntent(1L, 0L, operation, itemId, -1,
        storeLoc, x, y, -1, false);
  }

  private static ItemMoveIntent pickupIntent(long revision, Item item) {
    return new ItemMoveIntent(1L, revision, ItemMoveOperation.GROUND_TO_CURSOR,
        item.id, 9000 + item.id, -1, -1, -1, -1, false);
  }
}
