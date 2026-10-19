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
    PlayerStatsManager.increaseStaminaForVitality(attrs.base(), attrs.aggregate(), 256);
    assertEquals(18619, attrs.aggregate().get(Stat.stamina).encodedValues());
    assertEquals(18619, attrs.base().get(Stat.stamina).encodedValues());
    assertEquals(24064, attrs.aggregate().get(Stat.maxstamina).encodedValues());
  }

  @Test void zeroGetsOnlyClassGainAndFullStaysCapped() {
    for (int gain : new int[] {256, 320}) {
      Attributes empty = attributes(0, 24320);
      PlayerStatsManager.increaseStaminaForVitality(empty.base(), empty.aggregate(), gain);
      assertEquals(gain, empty.aggregate().get(Stat.stamina).encodedValues());
      Attributes full = attributes(24320, 24320);
      PlayerStatsManager.increaseStaminaForVitality(full.base(), full.aggregate(), gain);
      assertEquals(24320 + gain, full.aggregate().get(Stat.stamina).encodedValues());
    }
  }

  @Test void repeatedAllocationAppliesEachCallbackOnce() {
    Attributes attrs = attributes(18168, 23808);
    for (int i = 0; i < 2; i++) {
      PlayerStatsManager.increaseStaminaForVitality(attrs.base(), attrs.aggregate(), 256);
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
