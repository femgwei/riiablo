package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;

class NativeGemShrineServiceTest {
  @Test
  void followsNativeGemTierAndDoesNotUpgradePerfectGems() {
    assertEquals("gfr", NativeGemShrineService.betterCode("gcr"));
    assertEquals("gpr", NativeGemShrineService.betterCode("glr"));
    assertEquals("skz", NativeGemShrineService.betterCode("skl"));
    assertEquals(null, NativeGemShrineService.betterCode("gpr"));
    assertTrue(NativeGemShrineService.isGemCode("gpr"));
    assertFalse(NativeGemShrineService.isGemCode("hp1"));
  }

  @Test
  void chippedFallbackIsStableAndCoversAllNativeGemFamilies() {
    assertEquals("gcw", NativeGemShrineService.chooseChippedCode(0));
    assertEquals("gcv", NativeGemShrineService.chooseChippedCode(5));
    assertEquals("gcw", NativeGemShrineService.chooseChippedCode(6));
    assertEquals(6, NativeGemShrineService.chippedCodes().length);
  }

  @Test
  void externalInventoryMutationAdvancesRevisionExactlyOnce() {
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    assertEquals(0L, service.revision(42));
    assertEquals(1L, service.markExternalMutation(42));
    assertEquals(1L, service.revision(42));
    assertEquals(2L, service.markExternalMutation(42));
  }

  @Test
  void emptyInventoryDropsOneChippedGemWithoutMutatingInventory() {
    CharData character = CharData.obtain();
    ItemGenerator generator = new ItemGenerator() {
      @Override public Item generate(String code) {
        Item item = new Item();
        item.reset();
        item.code = code;
        return item;
      }
    };
    final Item[] dropped = new Item[1];
    NativeGemShrineService.Result result = NativeGemShrineService.apply(
        character.getItems(), generator, item -> { dropped[0] = item; return 17; },
        id -> { throw new AssertionError("fallback drop must not roll back"); }, 5, 27);
    assertEquals(NativeGemShrineService.Outcome.DROPPED_CHIPPED, result.outcome);
    assertEquals("gcv", result.outputCode);
    assertEquals(17, result.groundEntityId);
    assertEquals("gcv", dropped[0].code);
    assertEquals(27, dropped[0].ilvl & 0xFF);
    assertEquals(0, character.getItems().getItems().size);
  }

  @Test
  void skipsPerfectGemAndConsumesFirstLaterUpgradeableGemOnlyAfterDropSucceeds() {
    CharData character = CharData.obtain();
    Item perfect = inventoryGem("gpr");
    Item flawed = inventoryGem("gfr");
    character.getItems().getItems().add(perfect);
    character.getItems().getItems().add(flawed);
    character.getItems().getStore(StoreLoc.INVENTORY).add(0);
    character.getItems().getStore(StoreLoc.INVENTORY).add(1);
    ItemGenerator generator = stubGenerator();

    NativeGemShrineService.Result result = NativeGemShrineService.apply(
        character.getItems(), generator, item -> 23, id -> {}, 0, 11);

    assertEquals(NativeGemShrineService.Outcome.UPGRADED, result.outcome);
    assertEquals("gfr", result.sourceCode);
    assertEquals("gsr", result.outputCode);
    assertEquals(1, character.getItems().getItems().size);
    assertTrue(character.getItems().contains(perfect));
    assertFalse(character.getItems().contains(flawed));
  }

  @Test
  void failedGroundCreationLeavesUpgradeableGemOwned() {
    CharData character = CharData.obtain();
    Item chipped = inventoryGem("gcr");
    character.getItems().getItems().add(chipped);

    NativeGemShrineService.Result result = NativeGemShrineService.apply(
        character.getItems(), stubGenerator(), item -> -1, id -> {}, 0, 9);

    assertEquals(NativeGemShrineService.Outcome.DROP_FAILED, result.outcome);
    assertTrue(character.getItems().contains(chipped));
  }

  private static ItemGenerator stubGenerator() {
    return new ItemGenerator() {
      @Override public Item generate(String code) {
        Item item = new Item();
        item.reset();
        item.code = code;
        return item;
      }
    };
  }

  private static Item inventoryGem(String code) {
    Item item = new Item();
    item.reset();
    item.code = code;
    item.location = Location.STORED;
    item.storeLoc = StoreLoc.INVENTORY;
    return item;
  }
}
