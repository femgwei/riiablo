package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.combat.CombatSystem;
import org.junit.jupiter.api.Test;

/** Verifies the real MonStats/MonLvl contract used by native ranged attacks. */
class MonsterAttackProfileIntegrationTest extends RiiabloTest {
  @Test
  void quillRatSecondaryAttackUsesLevelScaledDamageAndToHit() {
    MonStats.Entry quillRat = Riiablo.files.monstats.get("quillrat1");
    assertNotNull(quillRat);
    int level = quillRat.Level[0];
    MonsterStatsCalculator.MonsterStatsInit scaled =
        new MonsterStatsCalculator.MonsterStatsInit();

    assertTrue(MonsterStatsCalculator.calculateMonsterStatsByLevel(
        quillRat.hcIdx, 1, 0, level, (short) 0x10, scaled));
    assertTrue(scaled.A2MinD >= 0 && scaled.A2MaxD >= scaled.A2MinD);
    assertTrue(scaled.A2MaxD < quillRat.A2MaxD[0],
        "MonStats A2 is a ratio and must not be used as final damage");
    assertTrue(scaled.TH < quillRat.A2TH[0],
        "MonStats A2TH is a ratio and must not be used as final attack rating");
    System.out.println("[MONSTER_DAMAGE_PROFILE] monster=" + quillRat.Id
        + " rawA2=" + quillRat.A2MinD[0] + ".." + quillRat.A2MaxD[0]
        + " scaledA2=" + scaled.A2MinD + ".." + scaled.A2MaxD
        + " rawAr=" + quillRat.A2TH[0] + " scaledAr=" + scaled.TH);
  }

  @Test
  void zeroDamageSecondaryProfileDoesNotFallBackToPrimaryDamage() {
    Attributes attacker = attributes(20, 8, 8, 10);
    Attributes defender = attributes(60, 0, 0, 0);
    MathUtils.random.setSeed(0xA2D4A6EL);

    CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, false, true, true, 0, 0, 10);

    assertTrue(result.hit);
    assertEquals(1, result.physicalDamage,
        "native zero profile is clamped to one, not replaced by A1 8 damage");
  }

  private static Attributes attributes(int hp, int minDamage, int maxDamage, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }
}
