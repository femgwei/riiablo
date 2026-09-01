package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.engine.server.party.PartyGoldShareService;
import com.riiablo.save.CharData;

class GoldPickupServiceTest extends RiiabloTest {
  @Test
  void goldIsCreditedInsteadOfPlacedOnCursor() {
    CharData character = CharData.obtain()
        .set(Riiablo.NORMAL, false, "GoldHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 10);
    character.getStats().aggregate().put(Stat.gold, 10);

    Item gold = new Item();
    gold.id = 42;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 25);
    ItemMoveIntent intent = new ItemMoveIntent(1, 0, ItemMoveOperation.GROUND_TO_CURSOR,
        42, 99, -1, -1, -1, -1, false);

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.pickup(7, character, intent, gold);
    assertTrue(result.success);
    assertEquals(ItemMoveFailure.NONE, result.failure);
    assertEquals(35, character.getStats().get(Stat.gold).asInt());
    assertNull(character.getItems().getCursor());
    assertEquals(1, service.revision(7));
  }

  @Test
  void pickupRespectsCarriedLimitAndLeavesRemainderOnGround() {
    CharData character = CharData.obtain()
        .set(Riiablo.NORMAL, false, "CapHero", Riiablo.AMAZON);
    character.level = 1; // Native cap is level * 10,000, bounded by VendorPricing.MAX_GOLD.
    character.getStats().base().put(Stat.gold, 9_990);
    character.getStats().aggregate().put(Stat.gold, 9_990);

    Item gold = new Item();
    gold.id = 43;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 25);
    ItemMoveIntent intent = new ItemMoveIntent(2, 0, ItemMoveOperation.GROUND_TO_CURSOR,
        43, 100, -1, -1, -1, -1, false);

    AuthoritativeItemMoveService.Outcome result =
        new AuthoritativeItemMoveService().pickup(8, character, intent, gold);
    assertTrue(result.success);
    assertEquals(10_000, character.getStats().get(Stat.gold).asInt());
    assertEquals(15, result.groundQuantityRemaining);
    assertFalse(result.consumeGroundEntity);
    assertEquals(15, gold.attrs.base().get(Stat.quantity).asInt());
  }

  @Test
  void onlyOnePlayerCanConsumeTheSameGroundEntity() {
    CharData owner = CharData.obtain()
        .set(Riiablo.NORMAL, false, "Owner", Riiablo.AMAZON);
    CharData other = CharData.obtain()
        .set(Riiablo.NORMAL, false, "Other", Riiablo.AMAZON);
    other.getStats().base().put(Stat.gold, 0);
    other.getStats().aggregate().put(Stat.gold, 0);
    Item gold = new Item();
    gold.id = 44;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 5);
    GroundDropOwnership.register(901, 11, 10_000L);

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome denied = service.pickup(12, other,
        new ItemMoveIntent(3, 0, ItemMoveOperation.GROUND_TO_CURSOR, 44, 901,
            -1, -1, -1, -1, false), gold);
    assertFalse(denied.success);
    assertEquals(ItemMoveFailure.GROUND_ITEM_NOT_OWNED, denied.failure);

    AuthoritativeItemMoveService.Outcome picked = service.pickup(11, owner,
        new ItemMoveIntent(4, 0, ItemMoveOperation.GROUND_TO_CURSOR, 44, 901,
            -1, -1, -1, -1, false), gold);
    assertTrue(picked.success);
    AuthoritativeItemMoveService.Outcome duplicate = service.pickup(12, other,
        new ItemMoveIntent(5, 0, ItemMoveOperation.GROUND_TO_CURSOR, 44, 901,
            -1, -1, -1, -1, false), gold);
    assertFalse(duplicate.success);
    assertEquals(ItemMoveFailure.GROUND_ITEM_NOT_OWNED, duplicate.failure);
    assertEquals(5, owner.getStats().get(Stat.gold).asInt());
    assertEquals(0, other.getStats().get(Stat.gold).asInt());
  }

  @Test
  void fullWalletRejectsPickupWithoutConsumingPile() {
    CharData character = CharData.obtain()
        .set(Riiablo.NORMAL, false, "FullWallet", Riiablo.AMAZON);
    character.level = 1;
    character.getStats().base().put(Stat.gold, 10_000);
    character.getStats().aggregate().put(Stat.gold, 10_000);
    Item gold = new Item();
    gold.id = 45;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 20);

    AuthoritativeItemMoveService.Outcome result = new AuthoritativeItemMoveService().pickup(
        13, character, new ItemMoveIntent(6, 0, ItemMoveOperation.GROUND_TO_CURSOR,
            45, 902, -1, -1, -1, -1, false), gold);
    assertFalse(result.success);
    assertEquals(ItemMoveFailure.GOLD_LIMIT_REACHED, result.failure);
    assertFalse(result.consumeGroundEntity);
    assertEquals(20, gold.attrs.base().get(Stat.quantity).asInt());
    assertEquals(0, result.revision);
  }

  @Test
  void expiredOwnershipAllowsAnotherPlayerToClaim() {
    GroundDropOwnership.register(903, 21, 0L);
    assertTrue(GroundDropOwnership.claim(903, 22));
  }

  @Test
  void partyWindowAllowsTeammateBeforePublicWindow() {
    GroundDropOwnership.register(904, 31, 7, 0L, 10_000L);
    assertFalse(GroundDropOwnership.isPartyShareGold(904));
    assertFalse(GroundDropOwnership.claim(904, 32, 8));
    assertTrue(GroundDropOwnership.claim(904, 33, 7));
  }

  @Test
  void sharedPickupAdvancesEveryCreditedWalletRevision() {
    CharData picker = CharData.obtain().set(Riiablo.NORMAL, false, "Picker", Riiablo.AMAZON);
    CharData teammate = CharData.obtain().set(Riiablo.NORMAL, false, "Teammate", Riiablo.AMAZON);
    Item gold = new Item();
    gold.id = 46;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 100);
    GroundDropOwnership.register(905, 41, 9, 0L, 10_000L, true);
    Array<PartyGoldShareService.Recipient> recipients = new Array<>();
    recipients.add(new PartyGoldShareService.Recipient(41, picker, true));
    recipients.add(new PartyGoldShareService.Recipient(42, teammate, true));

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.pickupSharedGold(
        41, 9, picker,
        new ItemMoveIntent(7, 0, ItemMoveOperation.GROUND_TO_CURSOR,
            46, 905, -1, -1, -1, -1, false), gold, recipients);
    assertTrue(result.success);
    assertTrue(result.consumeGroundEntity);
    assertEquals(50, picker.getStats().get(Stat.gold).asInt());
    assertEquals(50, teammate.getStats().get(Stat.gold).asInt());
    assertEquals(1, service.revision(41));
    assertEquals(1, service.revision(42));
  }

  @Test
  void sharedPickupLeavesPileWhenEveryWalletIsFull() {
    CharData picker = CharData.obtain().set(Riiablo.NORMAL, false, "FullPicker", Riiablo.AMAZON);
    CharData teammate = CharData.obtain().set(Riiablo.NORMAL, false, "FullMate", Riiablo.AMAZON);
    picker.getStats().base().put(Stat.gold, 10_000);
    picker.getStats().aggregate().put(Stat.gold, 10_000);
    teammate.getStats().base().put(Stat.gold, 10_000);
    teammate.getStats().aggregate().put(Stat.gold, 10_000);
    Item gold = new Item();
    gold.id = 47;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 20);
    GroundDropOwnership.register(906, 51, 10, 0L, 10_000L, true);
    Array<PartyGoldShareService.Recipient> recipients = new Array<>();
    recipients.add(new PartyGoldShareService.Recipient(51, picker, true));
    recipients.add(new PartyGoldShareService.Recipient(52, teammate, true));

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.pickupSharedGold(
        51, 10, picker,
        new ItemMoveIntent(8, 0, ItemMoveOperation.GROUND_TO_CURSOR,
            47, 906, -1, -1, -1, -1, false), gold, recipients);
    assertFalse(result.success);
    assertEquals(ItemMoveFailure.GOLD_LIMIT_REACHED, result.failure);
    assertFalse(result.consumeGroundEntity);
    assertEquals(20, result.groundQuantityRemaining);
    assertEquals(0, service.revision(51));
    assertEquals(0, service.revision(52));
  }
}
