package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
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

  private static CharData character() {
    return CharData.obtain().set(Riiablo.NORMAL, false, "MoveHero", Riiablo.AMAZON);
  }

  private static Item item(String code, int id) {
    Item item = new ItemGenerator().generate(code);
    item.id = id;
    return item;
  }

  private static ItemMoveIntent intent(byte operation, int itemId,
                                       int storeLoc, int x, int y) {
    return new ItemMoveIntent(1L, 0L, operation, itemId, -1,
        storeLoc, x, y, -1, false);
  }
}
