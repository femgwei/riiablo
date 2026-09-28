package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.item.Item;
import org.junit.jupiter.api.Test;

/** D2MOO ToHit/Calc/SrcDam boundary checks for Amazon weapon skills. */
class NativeAmazonCombatFormulaTest extends RiiabloTest {
  @Test
  void attackRatingUsesNativeToHitAndLevelGrowthForPlayersAndMonsters() {
    Skills.Entry jab = Riiablo.files.skills.get("Jab");
    Attributes attacker = attributes(1000, 0);
    int factor1 = SkillFormula.evaluate("toht", jab, 1);
    int factor2 = SkillFormula.evaluate("toht", jab, 2);

    assertEquals(1000 * (100 + factor1) / 100,
        AmazonSkills.getAttackRating(jab, 1, attacker, true));
    assertEquals(1000 * (100 + factor2) / 100,
        AmazonSkills.getAttackRating(jab, 2, attacker, true));
    assertEquals(1000 + factor1,
        AmazonSkills.getAttackRating(jab, 1, attacker, false));
    assertEquals(1000 + factor2,
        AmazonSkills.getAttackRating(jab, 2, attacker, false));
  }

  @Test
  void weaponDamageKeepsNativeMinimumAndMaximumPacketInvariants() {
    Attributes attacker = attributes(0, 0);
    Item empty = weapon("hax", 0, 0);

    int[] damage = AmazonSkills.calculateWeaponDamage(null, 1, attacker, empty, null);

    assertEquals(1, damage[0], "native empty packet minimum is one damage");
    assertEquals(2, damage[1], "native empty packet maximum is minimum plus one");
  }

  @Test
  void negativeDamagePercentClampsToNativeMinusNinety() {
    Skills.Entry jab = Riiablo.files.skills.get("Jab");
    Attributes attacker = attributes(0, -200);
    Item weapon = weapon("hax", 10, 10);

    int[] damage = AmazonSkills.calculateWeaponDamage(jab, 1, attacker, weapon, null);

    // Jab contributes +8%, but native damage percent is clamped at -90%:
    // 10..11 becomes 1..1 after the fixed-point truncation.
    assertEquals(1, damage[0]);
    assertEquals(1, damage[1]);
  }

  @Test
  void sourceDamageScalesTheCompleteWeaponPacket() {
    Skills.Entry jab = Riiablo.files.skills.get("Jab");
    int originalSrcDam = jab.SrcDam;
    try {
      jab.SrcDam = 64;
      Attributes attacker = attributes(0, 0);
      Item weapon = weapon("hax", 10, 20);

      int[] damage = AmazonSkills.calculateWeaponDamage(jab, 1, attacker, weapon, null);

      // Jab's native 1.10f Calc1 is -15%; after that fixed-point modifier,
      // the 10..21 packet is scaled by SrcDam=64 to 4..8.
      assertEquals(4, damage[0]);
      assertEquals(8, damage[1]);
    } finally {
      jab.SrcDam = originalSrcDam;
    }
  }

  private static Attributes attributes(int toHit, int damagePercent) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.damagepercent, damagePercent);
    attrs.base().put(Stat.strength, 0);
    attrs.base().put(Stat.dexterity, 0);
    attrs.base().put(Stat.level, 1);
    attrs.reset();
    return attrs;
  }

  private static Item weapon(String code, int minDamage, int maxDamage) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().put(Stat.mindamage, minDamage);
    item.attrs.base().put(Stat.maxdamage, maxDamage);
    item.attrs.reset();
    return item;
  }
}
