package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.io.BitInput;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.item.ItemWriter;
import com.riiablo.item.Location;
import com.riiablo.item.Quality;
import com.riiablo.item.StoreLoc;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class BeltSaveRoundTripTest extends RiiabloTest {
  // JM + flags + version + unknown + location + bodyloc, then 4-bit x/y.
  private static final int X_BIT = 16 + 32 + 8 + 2 + 3 + 4;

  @Test
  void writesNativeSlotNumbersWithoutMutatingLiveCoordinates() {
    for (int slot = 0; slot < 16; slot++) {
      Item potion = potion(slot % 4, slot / 4);
      BitInput bits = BitInput.wrap(encode(potion));
      bits.skipBits(X_BIT);
      assertEquals(slot, bits.read7u(4));
      assertEquals(0, bits.read7u(4));
      assertEquals(slot % 4, potion.gridX);
      assertEquals(slot / 4, potion.gridY);
    }
  }

  @Test
  void readsAllSixteenNativeBeltSlotsIntoPositiveRuntimeRows() {
    byte[] template = encode(potion(0, 0));
    for (int slot = 0; slot < 16; slot++) {
      byte[] bytes = template.clone();
      writeNibble(bytes, X_BIT, slot);
      Item restored = new ItemReader().readItemStrict(ByteInput.wrap(bytes));
      assertEquals(Location.BELT, restored.location);
      assertEquals(slot % 4, restored.gridX);
      assertEquals(slot / 4, restored.gridY);
    }
  }

  @Test
  void oldRiiabloTwoDimensionalSavesRetainTheirUpperRowsAndResaveNatively() {
    byte[] template = encode(potion(0, 0));
    for (int slot = 0; slot < 16; slot++) {
      byte[] legacy = template.clone();
      writeNibble(legacy, X_BIT, slot % 4);
      writeNibble(legacy, X_BIT + 4, slot / 4);
      Item restored = new ItemReader().readItemStrict(ByteInput.wrap(legacy));
      assertEquals(slot % 4, restored.gridX);
      assertEquals(slot / 4, restored.gridY);
      BitInput rewritten = BitInput.wrap(encode(restored));
      rewritten.skipBits(X_BIT);
      assertEquals(slot, rewritten.read7u(4));
      assertEquals(0, rewritten.read7u(4));
    }
  }

  @Test
  void inventoryCoordinatesAreNotInterpretedAsBeltSlots() {
    Item item = potion(9, 3);
    item.location = Location.STORED;
    item.storeLoc = StoreLoc.INVENTORY;
    Item restored = new ItemReader().readItemStrict(ByteInput.wrap(encode(item)));
    assertEquals(Location.STORED, restored.location);
    assertEquals(9, restored.gridX);
    assertEquals(3, restored.gridY);
  }

  @Test
  void twoThreeAndFourRowBeltsSurviveRepeatedFullCharacterSaveLoads() {
    String[] belts = {"lbl", "mbl", "hbl"};
    for (int rows = 2; rows <= 4; rows++) {
      CharData character = CharData.obtain().set(Riiablo.NORMAL, false, "BeltReload", Riiablo.AMAZON);
      Item belt = new ItemGenerator().generate(belts[rows - 2]);
      belt.id = 1;
      belt.version = Item.VERSION_110;
      belt.ilvl = 1;
      belt.quality = Quality.NORMAL;
      belt.flags |= Item.ITEMFLAG_IDENTIFIED;
      character.getItems().add(belt);
      character.getItems().equipItem(BodyLoc.BELT, belt);
      for (int slot = 0; slot < rows * 4; slot++) {
        Item potion = potion(slot % 4, slot / 4);
        character.groundToCursor(potion);
        character.cursorToBelt(slot % 4, slot / 4);
      }

      for (int cycle = 0; cycle < 3; cycle++) {
        byte[] saved = new D2SWriter96().writeD2S(D2SWriter96.createD2S(character));
        D2S decoded = D2SReader.INSTANCE.readComplete(saved);
        character = CharData.obtain().load(decoded);
        assertEquals(rows, character.getItems().getBeltRows());
        assertEquals(rows * 4, character.getItems().getLocation(Location.BELT).size);
        boolean[][] occupied = new boolean[rows][4];
        for (Item potion : character.getItems().getItems()) {
          if (potion.location != Location.BELT) continue;
          assertTrue(potion.gridX >= 0 && potion.gridX < 4);
          assertTrue(potion.gridY >= 0 && potion.gridY < rows);
          assertFalse(occupied[potion.gridY][potion.gridX], "Saved potions must not overlap");
          occupied[potion.gridY][potion.gridX] = true;
        }
        for (boolean[] row : occupied) for (boolean present : row) assertTrue(present);
      }
    }
  }

  private static Item potion(int column, int row) {
    Item potion = new ItemGenerator().generate("hp1");
    potion.location = Location.BELT;
    potion.bodyLoc = BodyLoc.NONE;
    potion.storeLoc = StoreLoc.NONE;
    potion.gridX = (byte) column;
    potion.gridY = (byte) row;
    return potion;
  }

  private static byte[] encode(Item item) {
    ByteOutput out = ByteOutput.wrap(Unpooled.buffer());
    try {
      new ItemWriter().writeItem(item, out);
      return ByteBufUtil.getBytes(out.buffer());
    } finally {
      out.buffer().release();
    }
  }

  private static void writeNibble(byte[] bytes, int offset, int value) {
    for (int bit = 0; bit < 4; bit++) {
      int index = (offset + bit) / 8;
      int mask = 1 << ((offset + bit) % 8);
      bytes[index] = (byte) ((bytes[index] & ~mask) | (((value >>> bit) & 1) * mask));
    }
  }
}
