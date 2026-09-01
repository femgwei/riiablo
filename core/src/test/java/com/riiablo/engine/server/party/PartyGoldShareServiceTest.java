package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class PartyGoldShareServiceTest extends RiiabloTest {
  private static CharData character(String name, int level, int gold) {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
    data.level = (byte) level;
    data.getStats().base().put(Stat.gold, gold);
    data.getStats().aggregate().put(Stat.gold, gold);
    return data;
  }

  private static Item pile(int amount) {
    Item item = new Item();
    item.code = "gld";
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().put(Stat.quantity, amount);
    return item;
  }

  private static Array<PartyGoldShareService.Recipient> recipients(CharData... data) {
    Array<PartyGoldShareService.Recipient> result = new Array<>();
    for (int i = 0; i < data.length; i++) {
      result.add(new PartyGoldShareService.Recipient(i + 1, data[i], true));
    }
    return result;
  }

  @Test void dividesEvenlyAndRemainderGoesToPicker() {
    CharData picker = character("picker", 1, 0);
    CharData teammate = character("teammate", 1, 0);
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(
        pile(101), 1, recipients(picker, teammate));
    assertEquals(51, picker.getStats().get(Stat.gold).asInt());
    assertEquals(50, teammate.getStats().get(Stat.gold).asInt());
    assertEquals(0, result.remaining);
  }

  @Test void capacityShortfallRemainsOnGround() {
    CharData picker = character("picker", 1, 9_995);
    CharData teammate = character("teammate", 1, 10_000);
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(
        pile(20), 1, recipients(picker, teammate));
    assertEquals(10_000, picker.getStats().get(Stat.gold).asInt());
    assertEquals(10_000, teammate.getStats().get(Stat.gold).asInt());
    assertEquals(15, result.remaining);
  }

  @Test void threeMembersUseNativeIntegerDivision() {
    CharData picker = character("picker", 1, 0);
    CharData second = character("second", 1, 0);
    CharData third = character("third", 1, 0);
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(
        pile(100), 1, recipients(picker, second, third));
    assertEquals(34, picker.getStats().get(Stat.gold).asInt());
    assertEquals(33, second.getStats().get(Stat.gold).asInt());
    assertEquals(33, third.getStats().get(Stat.gold).asInt());
    assertEquals(0, result.remaining);
  }

  @Test void ineligibleCrossLevelMemberDoesNotParticipate() {
    CharData picker = character("picker", 1, 0);
    CharData teammate = character("teammate", 1, 0);
    CharData farAway = character("farAway", 1, 0);
    Array<PartyGoldShareService.Recipient> list = recipients(picker, teammate);
    list.add(new PartyGoldShareService.Recipient(3, farAway, false));
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(pile(100), 1, list);
    assertEquals(50, picker.getStats().get(Stat.gold).asInt());
    assertEquals(50, teammate.getStats().get(Stat.gold).asInt());
    assertEquals(0, farAway.getStats().get(Stat.gold).asInt());
    assertEquals(0, result.remaining);
  }

  @Test void teammateCapacityReturnsShareToPicker() {
    CharData picker = character("picker", 1, 0);
    CharData teammate = character("teammate", 1, 9_995);
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(
        pile(20), 1, recipients(picker, teammate));
    assertEquals(15, picker.getStats().get(Stat.gold).asInt());
    assertEquals(10_000, teammate.getStats().get(Stat.gold).asInt());
    assertEquals(0, result.remaining);
  }

  @Test void pickerMustBeEligibleForAuthoritativeDistribution() {
    CharData picker = character("picker", 1, 0);
    CharData teammate = character("teammate", 1, 0);
    Array<PartyGoldShareService.Recipient> list = new Array<>();
    list.add(new PartyGoldShareService.Recipient(1, picker, false));
    list.add(new PartyGoldShareService.Recipient(2, teammate, true));
    PartyGoldShareService.Result result = PartyGoldShareService.distribute(
        pile(10), 1, list);
    assertEquals(10, result.remaining);
    assertEquals(0, picker.getStats().get(Stat.gold).asInt());
  }
}
