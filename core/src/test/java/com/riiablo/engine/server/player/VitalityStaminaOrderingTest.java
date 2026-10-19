package com.riiablo.engine.server.player;

import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.CharacterClass;
import com.riiablo.save.CharData;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VitalityStaminaOrderingTest extends RiiabloTest {
  @Test void allocationWithEquipmentAndOwnedPassiveSurvivesActualStateTicksAndItemRefresh() {
    com.riiablo.engine.EntityFactory factory = org.mockito.Mockito.mock(com.riiablo.engine.EntityFactory.class);
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(),
            new com.riiablo.engine.server.StateUpdater(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      CharData data = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "StaminaPassive", Riiablo.BARBARIAN);
      data.setSkillLevel(141, 2);
      Attributes attrs = data.getStats();
      attrs.base().put(Stat.maxstamina, 100f);
      attrs.base().put(Stat.stamina, 40f);
      attrs.base().put(Stat.statpts, 1);
      attrs.reset();
      Attributes gear = Attributes.obtainStandard();
      gear.base().put(Stat.skill_staminapercent, 50);
      new com.riiablo.attributes.AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry())
          .add(gear.base()).apply();
      int player = world.create();
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(player).data = data;
      world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class).create(player).attrs = attrs;
      world.getMapper(com.riiablo.engine.server.component.UnitStates.class).create(player).init(player);
      world.process();
      assertEquals(195 * 256, attrs.get(Stat.maxstamina).encodedValues());
      int before = attrs.get(Stat.stamina).encodedValues();
      int gain = CharacterClass.BARBARIAN.entry().StaminaPerVitality << 6;
      int raw = 100 * 256 + gain;
      int maximum = raw + raw * 95 / 100;
      int current = com.riiablo.attributes.StaminaMaximum.rescale(before, 195 * 256, maximum) + gain;
      assertEquals(PlayerStatsManager.RESULT_SUCCESS,
          new PlayerStatsManager().spendStatPoint(data, PlayerStatsManager.STAT_TYPE_VITALITY));
      for (int i = 0; i < 3; i++) world.process();
      assertEquals(maximum, attrs.get(Stat.maxstamina).encodedValues());
      assertEquals(current, attrs.get(Stat.stamina).encodedValues());
      // No real gear was installed, so ItemData removes the synthetic equipment
      // contribution while retaining the owned 45% passive.
      data.getItems().updateStats();
      int withoutGear = raw + raw * 45 / 100;
      world.process();
      assertEquals(withoutGear, attrs.get(Stat.maxstamina).encodedValues());
      assertEquals(com.riiablo.attributes.StaminaMaximum.rescale(current, maximum, withoutGear),
          attrs.get(Stat.stamina).encodedValues());
    } finally { world.dispose(); }
  }
  @Test void publicAllocationUsesNativeClassGainAndLiveCurrentValue() {
    for (CharacterClass characterClass : CharacterClass.values()) {
      CharData data = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "Vitality", (byte) characterClass.id);
      data.getStats().base().put(Stat.statpts, 1);
      data.getStats().aggregate().put(Stat.statpts, 1);
      data.getStats().base().putEncoded(Stat.maxstamina, 23808);
      data.getStats().base().putEncoded(Stat.stamina, 23808);
      data.getStats().aggregate().putEncoded(Stat.maxstamina, 23808);
      data.getStats().aggregate().putEncoded(Stat.stamina, 18168);
      int gain = characterClass.entry().StaminaPerVitality << 6;
      int expected = (int) (18168.0 / 23808 * (23808 + gain)) + gain;
      assertEquals(PlayerStatsManager.RESULT_SUCCESS,
          new PlayerStatsManager().spendStatPoint(data, PlayerStatsManager.STAT_TYPE_VITALITY));
      assertEquals(expected, data.getStats().aggregate().get(Stat.stamina).encodedValues());
      assertEquals(expected, data.getStats().base().get(Stat.stamina).encodedValues());
      assertEquals(0, data.getAvailableStatPoints());
    }
  }
  @Test void partialLiveStaminaRescalesBeforeGainAndReplacesStaleSavedValue() {
    Attributes attrs = attributes(23808, 23808);
    attrs.aggregate().putEncoded(Stat.stamina, 18168);
    attrs.staminaMaximum().increaseForVitality(attrs, 256);
    assertEquals(18619, attrs.aggregate().get(Stat.stamina).encodedValues());
    assertEquals(18619, attrs.base().get(Stat.stamina).encodedValues());
    assertEquals(24064, attrs.aggregate().get(Stat.maxstamina).encodedValues());
  }

  @Test void zeroGetsOnlyClassGainAndFullStaysCapped() {
    for (int gain : new int[] {256, 320}) {
      Attributes empty = attributes(0, 24320);
      empty.staminaMaximum().increaseForVitality(empty, gain);
      assertEquals(gain, empty.aggregate().get(Stat.stamina).encodedValues());
      Attributes full = attributes(24320, 24320);
      full.staminaMaximum().increaseForVitality(full, gain);
      assertEquals(24320 + gain, full.aggregate().get(Stat.stamina).encodedValues());
    }
  }

  @Test void repeatedAllocationAppliesEachCallbackOnce() {
    Attributes attrs = attributes(18168, 23808);
    for (int i = 0; i < 2; i++) {
      attrs.staminaMaximum().increaseForVitality(attrs, 256);
    }
    assertEquals(19073, attrs.aggregate().get(Stat.stamina).encodedValues());
    assertEquals(24320, attrs.base().get(Stat.maxstamina).encodedValues());
  }

  private static Attributes attributes(int current, int maximum) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().putEncoded(Stat.stamina, current);
    attrs.base().putEncoded(Stat.maxstamina, maximum);
    attrs.reset();
    return attrs;
  }
}
