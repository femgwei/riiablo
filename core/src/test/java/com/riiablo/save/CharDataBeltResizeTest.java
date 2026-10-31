package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.portal.TownPortalRegistry;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.client.ClientItemContextPort;
import com.riiablo.engine.client.ClientItemManager;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import org.junit.jupiter.api.Test;

class CharDataBeltResizeTest extends RiiabloTest {
  @Test
  void removingBeltKeepsBottomRowAndPacksUpperRowsWithoutReplacingItems() {
    CharData character = character();
    Item belt = equipBelt(character);
    List<Item> potions = fillBelt(character);

    assertTrue(character.bodyToCursor(BodyLoc.BELT, false, item -> {
      fail("Empty inventory must be used before the ground");
      return false;
    }));

    assertEquals(1, character.getItems().getBeltRows());
    assertSame(belt, character.getItems().getCursor());
    assertEquals(17, character.getItems().getItems().size);
    for (int i = 0; i < potions.size(); i++) {
      Item potion = potions.get(i);
      assertTrue(character.getItems().contains(potion));
      assertEquals(i < 4 ? Location.BELT : Location.STORED, potion.location);
      if (i < 4) {
        assertEquals(i, potion.gridX);
        assertEquals(0, potion.gridY);
      } else {
        assertEquals(StoreLoc.INVENTORY, potion.storeLoc);
      }
    }
    assertEquals(4, character.getItems().getLocation(Location.BELT).size);
    assertEquals(12, character.getItems().getStore(StoreLoc.INVENTORY).size);
  }

  @Test
  void oneInventorySpaceIsUsedBeforeDroppingRemainingExactItemsInSlotOrder() {
    CharData character = character();
    Item belt = equipBelt(character);
    List<Item> potions = fillBelt(character);
    fillInventory(character, 39);
    List<Item> dropped = new ArrayList<>();

    assertTrue(character.bodyToCursor(BodyLoc.BELT, false, item -> {
      assertFalse(character.getItems().contains(item));
      item.location = Location.GROUND;
      dropped.add(item);
      return true;
    }));

    assertSame(belt, character.getItems().getCursor());
    assertEquals(11, dropped.size());
    assertEquals(40, character.getItems().getStore(StoreLoc.INVENTORY).size);
    assertEquals(4, character.getItems().getLocation(Location.BELT).size);
    assertEquals(56, character.getItems().getItems().size + dropped.size());
    assertEquals(Location.STORED, potions.get(4).location);
    for (int i = 0; i < dropped.size(); i++) {
      assertSame(potions.get(i + 5), dropped.get(i));
      assertEquals(Location.GROUND, dropped.get(i).location);
      assertFalse(character.getItems().contains(dropped.get(i)));
    }
  }

  @Test
  void smallerBeltKeepsItsValidRowsAndCursorSurvivesRemovalOfEarlierIndices() {
    CharData character = character();
    Item oldBelt = equipBelt(character);
    List<Item> potions = fillBelt(character);
    fillInventory(character, 40);
    Item replacement = item("lbl", 700);
    character.groundToCursor(replacement);
    List<Item> dropped = new ArrayList<>();

    assertTrue(character.swapBodyItem(BodyLoc.BELT, false, item -> {
      item.location = Location.GROUND;
      dropped.add(item);
      return true;
    }));

    assertEquals(2, character.getItems().getBeltRows());
    assertSame(oldBelt, character.getItems().getCursor());
    assertSame(replacement, character.getItems().getSlot(BodyLoc.BELT));
    assertEquals(8, dropped.size());
    assertEquals(8, character.getItems().getLocation(Location.BELT).size);
    for (int i = 0; i < 8; i++) assertEquals(Location.BELT, potions.get(i).location);
    assertEquals(58, character.getItems().getItems().size + dropped.size());
  }

  @Test
  void cursorToOccupiedBeltSlotUsesTheSameShrinkRule() {
    CharData character = character();
    Item oldBelt = equipBelt(character);
    fillBelt(character);
    fillInventory(character, 40);
    Item replacement = item("lbl", 703);
    character.groundToCursor(replacement);
    List<Item> dropped = new ArrayList<>();

    assertTrue(character.cursorToBody(BodyLoc.BELT, false, item -> {
      item.location = Location.GROUND;
      dropped.add(item);
      return true;
    }));

    assertSame(oldBelt, character.getItems().getCursor());
    assertSame(replacement, character.getItems().getSlot(BodyLoc.BELT));
    assertEquals(8, dropped.size());
    assertEquals(8, character.getItems().getLocation(Location.BELT).size);
  }

  @Test
  void sameCapacitySwapDoesNotEvacuateAnyPotion() {
    CharData character = character();
    Item oldBelt = equipBelt(character);
    fillBelt(character);
    fillInventory(character, 40);
    Item replacement = item("hbl", 701);
    character.groundToCursor(replacement);

    assertTrue(character.swapBodyItem(BodyLoc.BELT, false, item -> {
      fail("Same-capacity swap must not drop any potions");
      return false;
    }));

    assertSame(oldBelt, character.getItems().getCursor());
    assertSame(replacement, character.getItems().getSlot(BodyLoc.BELT));
    assertEquals(16, character.getItems().getLocation(Location.BELT).size);
  }

  @Test
  void failedDropRestoresPotionAndLeavesOldBeltEquipped() {
    CharData character = character();
    Item belt = equipBelt(character);
    List<Item> potions = fillBelt(character);
    fillInventory(character, 40);
    Item replacement = item("lbl", 702);
    character.groundToCursor(replacement);

    assertFalse(character.swapBodyItem(BodyLoc.BELT, false, item -> {
      item.id = 99999;
      item.location = Location.GROUND;
      return false;
    }));

    assertSame(belt, character.getItems().getSlot(BodyLoc.BELT));
    assertSame(replacement, character.getItems().getCursor());
    assertEquals(16, character.getItems().getLocation(Location.BELT).size);
    assertEquals(58, character.getItems().getItems().size);
    assertSame(potions.get(8), character.getItems().getItem(character.getItems().indexOf(potions.get(8))));
    assertEquals(108, potions.get(8).id);
    assertEquals(0, potions.get(8).gridX);
    assertEquals(2, potions.get(8).gridY);
  }

  @Test
  void missingWorldCannotLoseFullInventoryOverflow() {
    CharData character = character();
    Item belt = equipBelt(character);
    fillBelt(character);
    fillInventory(character, 40);

    character.bodyToCursor(BodyLoc.BELT, false);

    assertSame(belt, character.getItems().getSlot(BodyLoc.BELT));
    assertNull(character.getItems().getCursor());
    assertEquals(16, character.getItems().getLocation(Location.BELT).size);
  }

  @Test
  void localManagerRoutesOverflowToItsWorldDropHook() {
    CharData character = character();
    Item belt = equipBelt(character);
    fillBelt(character);
    fillInventory(character, 40);
    ClientItemContextPort context = org.mockito.Mockito.mock(ClientItemContextPort.class);
    org.mockito.Mockito.when(context.charData()).thenReturn(character);
    ClientItemManager manager = new ClientItemManager(context);
    EntityFactory factory = org.mockito.Mockito.mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(manager, factory).build()
        .register("factory", factory).register("townPortalRegistry", new TownPortalRegistry())
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int player = world.create();
      org.mockito.Mockito.when(context.playerId()).thenReturn(player);
      world.getMapper(Position.class).create(player).position.set(20, 30);
      world.process();
      List<Item> dropped = new ArrayList<>();
      java.util.Set<Vector2> positions = new java.util.HashSet<>();
      org.mockito.Mockito.when(factory.createItem(org.mockito.ArgumentMatchers.any(Item.class),
          org.mockito.ArgumentMatchers.any(Vector2.class))).thenAnswer(invocation -> {
        Item item = invocation.getArgument(0);
        Vector2 position = invocation.getArgument(1);
        assertTrue(positions.add(new Vector2(position)), "Bulk drops must reserve distinct subtiles");
        item.location = Location.GROUND;
        dropped.add(item);
        // ECS subscriptions do not yet see these new entities in this tick.
        return world.create();
      });

      manager.bodyToCursor(BodyLoc.BELT, false);

      assertSame(belt, character.getItems().getCursor());
      assertEquals(12, dropped.size());
      assertEquals(12, positions.size());
      assertEquals(1, character.getItems().getBeltRows());
    } finally {
      world.dispose();
    }
  }

  private static CharData character() {
    return CharData.obtain().set(Riiablo.NORMAL, false, "BeltResizeHero", Riiablo.AMAZON);
  }

  private static Item item(String code, int id) {
    Item item = new ItemGenerator().generate(code);
    item.id = id;
    return item;
  }

  private static Item equipBelt(CharData character) {
    Item belt = item("hbl", 99);
    character.getItems().add(belt);
    character.getItems().equipItem(BodyLoc.BELT, belt);
    return belt;
  }

  private static List<Item> fillBelt(CharData character) {
    List<Item> potions = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      Item potion = item("hp1", 100 + i);
      character.groundToCursor(potion);
      character.cursorToBelt(i % 4, i / 4);
      assertEquals(Location.BELT, potion.location);
      potions.add(potion);
    }
    return potions;
  }

  private static void fillInventory(CharData character, int count) {
    for (int i = 0; i < count; i++)
      assertTrue(character.getItems().addToInventory(item("hp1", 300 + i)));
  }
}
