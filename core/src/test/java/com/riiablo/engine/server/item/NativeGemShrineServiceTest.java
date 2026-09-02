package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
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
    assertEquals("gcr", NativeGemShrineService.chooseChippedCode(0));
    assertEquals("skc", NativeGemShrineService.chooseChippedCode(6));
    assertEquals("gcr", NativeGemShrineService.chooseChippedCode(7));
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
        id -> { throw new AssertionError("fallback drop must not roll back"); }, 6);
    assertEquals(NativeGemShrineService.Outcome.DROPPED_CHIPPED, result.outcome);
    assertEquals("skc", result.outputCode);
    assertEquals(17, result.groundEntityId);
    assertEquals("skc", dropped[0].code);
    assertEquals(0, character.getItems().getItems().size);
  }
}
