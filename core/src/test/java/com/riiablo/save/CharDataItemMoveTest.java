package com.riiablo.save;

import org.junit.jupiter.api.Test;

import com.riiablo.item.BodyLoc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class CharDataItemMoveTest {
  @Test
  void bodyToCursorIgnoresAnEmptyEquipmentSlot() {
    CharData data = new CharData();

    assertDoesNotThrow(() -> data.bodyToCursor(BodyLoc.RARM));
    assertNull(data.getItems().getCursor());
  }

  @Test
  void swapBodyItemIgnoresAnEmptyCursor() {
    CharData data = new CharData();

    assertDoesNotThrow(() -> data.swapBodyItem(BodyLoc.RARM));
    assertNull(data.getItems().getCursor());
  }
}
