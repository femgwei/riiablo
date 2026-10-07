package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class CharacterPanelStatsTest {
  @Test
  void attackRatingUsesNativePlayerFormula() {
    assertEquals(95, CharacterPanel.calculateAttackRating(25, 0, 5));
    assertEquals(65, CharacterPanel.calculateAttackRating(20, 0, 0));
    int equippedBase = CharacterPanel.calculateAttackRating(236, 29, 5);
    assertEquals(1179, equippedBase);
    assertEquals(1591,
        CharacterPanel.calculateSkillAttackRating(equippedBase, 35, 0));
    assertEquals(2688,
        CharacterPanel.calculateSkillAttackRating(equippedBase, 35, 93));
  }

  @Test
  void damageRangeAppliesWeaponAttributeScaling() {
    assertArrayEquals(new int[] {1, 6},
        CharacterPanel.calculateDamageRange(1, 5, 20, 25, 0, 80, 0));
    assertArrayEquals(new int[] {2, 7},
        CharacterPanel.calculateDamageRange(2, 6, 20, 0, 100, 0, 0));
  }

  @Test
  void damageRangeUsesNativeFlatPercentAndSourceDamageOrder() {
    assertArrayEquals(new int[] {6, 13}, CharacterPanel.calculateDamageRange(
        4, 9, 100, 0, 100, 0,
        20, 1, 10, 30, 25, 64));
  }

  @Test
  void attackRatingRowsFollowSelectedSkillTypeAndBonus() {
    Skills.Entry normal = new Skills.Entry();
    normal.Id = 0;
    assertTrue(CharacterPanel.skillUsesAttackRating(normal));
    assertEquals(100, CharacterPanel.calculateSkillAttackRating(100, normal, 1));
    assertTrue(CharacterPanel.isNormalAttack(normal));

    Skills.Entry weaponSkill = new Skills.Entry();
    weaponSkill.Id = 101;
    weaponSkill.SrcDam = 128;
    weaponSkill.ToHit = 20;
    weaponSkill.LevToHit = 5;
    assertTrue(CharacterPanel.skillUsesAttackRating(weaponSkill));
    assertEquals(130, CharacterPanel.calculateSkillAttackRating(100, weaponSkill, 3));
    assertEquals(150,
        CharacterPanel.calculateSkillAttackRating(100, 20, 30));

    Skills.Entry spell = new Skills.Entry();
    spell.Id = 200;
    assertFalse(CharacterPanel.skillUsesAttackRating(spell));
    assertEquals(0, CharacterPanel.calculateSkillAttackRating(100, spell, 10));

    Skills.Entry alwaysHit = new Skills.Entry();
    alwaysHit.Id = 201;
    alwaysHit.SrcDam = 128;
    alwaysHit.ResultFlags = 1;
    assertFalse(CharacterPanel.skillUsesAttackRating(alwaysHit));
  }

  @Test
  void nativeAttackRatingPlaceholderOnlyContainsSpecialSkillName() {
    String nativeFormat = "%s\nAttack Rating (AR)";
    assertEquals("Multiple Shot\nAttack Rating (AR)",
        CharacterPanel.formatCombatLabel(nativeFormat, "Multiple Shot", true));
    assertEquals("Attack Rating (AR)",
        CharacterPanel.formatCombatLabel(nativeFormat, "", false));
  }


  @Test
  void damageRowsKeepWeaponAndSkillElementalRangesSeparate() {
    assertArrayEquals(new int[] {3, 13},
        CharacterPanel.calculateDamageRange(1, 4, 100, 236, 0, 100, 0));
    assertEquals("4-19\n106-123", CharacterPanel.formatDamageRange(
        new int[] {4, 19}, new int[] {106, 123}));
    assertEquals("4-19", CharacterPanel.formatDamageRange(
        new int[] {4, 19}, new int[] {0, 0}));
    assertEquals("106-123", CharacterPanel.formatDamageRange(
        new int[] {0, 0}, new int[] {106, 123}));
  }
}
