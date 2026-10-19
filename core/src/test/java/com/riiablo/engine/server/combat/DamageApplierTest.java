package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;

/** Regression coverage for the channel and monster-immunity compatibility API. */
public class DamageApplierTest extends RiiabloTest {
  @Test
  public void monsterElementalImmunityUsesHundredResistanceThreshold() {
    Attributes monster = Attributes.obtainStandard();
    monster.base().put(Stat.fireresist, 100);
    monster.reset();
    DamageResult result = new DamageResult();
    result.fireDamage = 100;

    new DamageApplier().applyResistancesAndAbsorb(result, monster, false, true);

    assertEquals(100, result.rolled[DamageResult.CHANNEL_FIRE]);
    assertEquals(0, result.mitigated[DamageResult.CHANNEL_FIRE]);
    assertEquals(0, result.totalDamage);
  }

  @Test
  public void playerResistanceStillUsesMaximumResistanceCap() {
    Attributes player = Attributes.obtainStandard();
    player.base().put(Stat.fireresist, 100);
    player.reset();
    DamageResult result = new DamageResult();
    result.fireDamage = 100;

    new DamageApplier().applyResistancesAndAbsorb(result, player, false, false);

    assertEquals(25, result.fireDamage);
    assertEquals(25, result.immediateTotal());
  }

  @Test
  public void poisonIsAChannelButNotImmediateDamage() {
    DamageResult result = new DamageResult();
    result.physicalDamage = 7;
    result.poisonDamage = 11;
    result.captureRolledChannels();
    result.syncMitigatedChannels();

    assertEquals(11, result.rolled[DamageResult.CHANNEL_POISON]);
    assertEquals(11, result.mitigated[DamageResult.CHANNEL_POISON]);
    assertEquals(7, result.immediateTotal());
  }

  @Test
  public void monsterColdLengthUsesDifficultyDivisors() {
    assertEquals(80, CombatSystem.scaleMonsterColdDuration(80, true, 0));
    assertEquals(40, CombatSystem.scaleMonsterColdDuration(80, true, 1));
    assertEquals(20, CombatSystem.scaleMonsterColdDuration(80, true, 2));
    assertEquals(80, CombatSystem.scaleMonsterColdDuration(80, false, 2));
  }

  @Test
  public void elementalPierceIsAppliedAtTheMitigationBoundary() {
    Attributes monster = Attributes.obtainStandard();
    monster.base().put(Stat.fireresist, 50);
    monster.reset();
    DamageResult result = new DamageResult();
    result.fireDamage = 100;
    result.piercePercent = 20;

    new DamageApplier().applyResistancesAndAbsorb(result, monster, false, true);

    assertEquals(70, result.fireDamage);
  }

  @Test
  public void physicalFlatReductionPrecedesResistance() {
    Attributes defender = Attributes.obtainStandard();
    defender.base().put(Stat.damageresist, 50);
    defender.base().put(Stat.normal_damage_reduction, 10);
    defender.reset();
    DamageResult result = new DamageResult();
    result.physicalDamage = 10_000;

    new DamageApplier().applyResistancesAndAbsorb(result, defender, false, false);

    assertEquals((10_000 - (10 << 8)) * 50 / 100, result.physicalDamage);
  }

  @Test
  public void poisonPierceAffectsDamageButNotItsDurationRow() {
    Attributes defender = Attributes.obtainStandard();
    defender.base().put(Stat.poisonresist, 50);
    defender.base().put(Stat.item_poisonlengthresist, 50);
    defender.reset();
    DamageResult result = new DamageResult();
    result.poisonDamage = 100;
    result.poisonDuration = 100;
    result.piercePercent = 20;

    new DamageApplier().applyResistancesAndAbsorb(result, defender, false, false);

    assertEquals(70, result.poisonDamage);
    assertEquals(50, result.poisonDuration);
  }
}
