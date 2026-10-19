package com.riiablo.attributes;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaMaximumTest extends RiiabloTest {
  @Test void levelUpKeepsPercentageMaximumAndSubsequentStateRefreshDoesNotCompoundIt() {
    com.riiablo.save.CharData data = com.riiablo.save.CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "StaminaLevel", Riiablo.BARBARIAN);
    Attributes attrs = data.getStats();
    attrs.base().put(Stat.maxstamina, 100f);
    attrs.base().put(Stat.stamina, 40f);
    attrs.reset();
    attrs.staminaMaximum().applyStatePercent(attrs, 50);
    long nextLevel = ExperienceTable.getInstance().getExperienceForNextLevel(1, Riiablo.BARBARIAN);
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new ExperienceManager(new com.riiablo.engine.server.party.PartyManager())).build());
    try { world.getSystem(ExperienceManager.class).addExperienceForPlayer(data, 1, nextLevel); }
    finally { world.dispose(); }
    int raw = 100 * 256 + (CharacterClass.BARBARIAN.entry().StaminaPerLevel << 6);
    assertPool(attrs, raw + raw / 2, raw + raw / 2);
    attrs.staminaMaximum().applyStatePercent(attrs, 50);
    assertPool(attrs, raw + raw / 2, raw + raw / 2);
  }
  @Test void equipmentVitalityAddsUnscaledClassGainWithoutCountingBaseVitality() {
    Attributes attrs = attributes(100 * 256, 50 * 256);
    attrs.base().put(Stat.vitality, 50);
    attrs.reset();
    Attributes gear = Attributes.obtainStandard();
    gear.base().put(Stat.vitality, 4);
    gear.base().put(Stat.skill_staminapercent, 50);
    int maximum = 150 * 256 + 4 * (CharacterClass.BARBARIAN.entry().StaminaPerVitality << 6);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).add(gear.base()).apply();
    assertPool(attrs, maximum, maximum / 2);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).add(gear.base()).apply();
    assertPool(attrs, maximum, maximum / 2);
  }

  @Test void differentNativePercentStatsTruncateSeparately() {
    Attributes attrs = attributes(257, 257);
    attrs.staminaMaximum().applyStatePercent(attrs, 1, 1);
    assertPool(attrs, 261, 261);
  }

  @Test void equipmentPerLevelStaminaIsNotAmplifiedByPercent() {
    Attributes attrs = attributes(100 * 256, 50 * 256);
    attrs.base().put(Stat.level, 10);
    attrs.reset();
    Attributes gear = Attributes.obtainStandard();
    gear.base().put(Stat.item_stamina_perlevel, 32);
    gear.base().put(Stat.skill_staminapercent, 50);
    com.riiablo.codec.excel.ItemStatCost.Entry row = Riiablo.files.ItemStatCost.get(Stat.item_stamina_perlevel);
    assertEquals(2, row.op);
    assertEquals("maxstamina", row.op_stat[0]);
    int bonus = gear.base().get(Stat.item_stamina_perlevel).encodedValues() * 10 >> row.op_param;
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).add(gear.base()).apply();
    assertPool(attrs, 150 * 256 + bonus, (150 * 256 + bonus) / 2);
  }
  @Test void actualEquipmentOpsAndStatePercentUseOneUnmodifiedMaximum() {
    for (short id : new short[] {Stat.skill_staminapercent, Stat.skill_passive_staminapercent}) {
      assertEquals(1, Riiablo.files.ItemStatCost.get(id).op);
      assertEquals("maxstamina", Riiablo.files.ItemStatCost.get(id).op_stat[0]);
    }
    Attributes attrs = attributes(100 * 256, 40 * 256);
    Attributes gear = Attributes.obtainStandard();
    gear.base().put(Stat.skill_staminapercent, 50);
    gear.base().put(Stat.skill_passive_staminapercent, 25);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).add(gear.base()).apply();
    assertPool(attrs, 175 * 256, 70 * 256);
    attrs.staminaMaximum().applyStatePercent(attrs, 25);
    assertPool(attrs, 200 * 256, 80 * 256);
    attrs.staminaMaximum().increaseForVitality(attrs, 256);
    int afterGain = (int) (80.0 / 200 * (202 * 256)) + 256;
    assertPool(attrs, 202 * 256, afterGain);
    for (int i = 0; i < 3; i++) {
      attrs.staminaMaximum().applyStatePercent(attrs, 25);
      assertPool(attrs, 202 * 256, afterGain);
    }
    // Rebuilding from saved/base must retain the live depleted pool, not refill it.
    attrs.aggregate().putEncoded(Stat.stamina, 20 * 256);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).add(gear.base()).apply();
    assertPool(attrs, 202 * 256, 20 * 256);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).apply();
    int withoutGear = 101 * 256 + 101 * 256 * 25 / 100;
    int current = StaminaMaximum.rescale(20 * 256, 202 * 256, withoutGear);
    assertPool(attrs, withoutGear, current);
    attrs.staminaMaximum().applyStatePercent(attrs, 0);
    assertPool(attrs, 101 * 256, StaminaMaximum.rescale(current, withoutGear, 101 * 256));
    assertEquals(101 * 256, attrs.base().get(Stat.maxstamina).encodedValues());
  }

  @Test void stateMaximumChangesPreserveZeroAndRescaleBothDirectionsWithoutCompounding() {
    Attributes attrs = attributes(100 * 256, 40 * 256);
    attrs.staminaMaximum().applyStatePercent(attrs, 50);
    assertPool(attrs, 150 * 256, 60 * 256);
    attrs.staminaMaximum().applyStatePercent(attrs, 50);
    assertPool(attrs, 150 * 256, 60 * 256);
    attrs.staminaMaximum().applyStatePercent(attrs, 0);
    assertPool(attrs, 100 * 256, 40 * 256);
    attrs.aggregate().putEncoded(Stat.stamina, 0);
    attrs.staminaMaximum().applyStatePercent(attrs, 100);
    assertPool(attrs, 200 * 256, 0);
    new AttributesUpdater().update(attrs, CharacterClass.BARBARIAN.entry()).apply();
    assertPool(attrs, 200 * 256, 0);
    attrs.staminaMaximum().increaseForVitality(attrs, 256);
    assertPool(attrs, 202 * 256, 256);
  }

  @Test void nativeCallbackUsesOnePointDenominatorAndCapsInvalidCurrent() {
    assertEquals(1, StaminaMaximum.rescale(1, 128, 256));
    assertEquals(512, StaminaMaximum.rescale(300, 256, 512));
    assertEquals(0, StaminaMaximum.rescale(100, 256, 0));
  }

  private static Attributes attributes(int maximum, int current) {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().putEncoded(Stat.maxstamina, maximum);
    attrs.base().putEncoded(Stat.stamina, current);
    attrs.reset();
    return attrs;
  }
  private static void assertPool(Attributes attrs, int maximum, int current) {
    assertEquals(maximum, attrs.get(Stat.maxstamina).encodedValues());
    assertEquals(current, attrs.get(Stat.stamina).encodedValues());
  }
}
