package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** Native Skills.txt and States.txt-facing contract for Druid forms. */
class NativeDruidShapeDataTest extends RiiabloTest {
  @Test
  void druidConstantsFollowNativeInterleavedSkillRows() {
    assertSkill(SkillId.RAVEN, "Raven");
    assertSkill(SkillId.POISON_CREEPER, "Plague Poppy");
    assertSkill(SkillId.WEREWOLF, "Wearwolf");
    assertSkill(SkillId.LYCANTHROPY, "Shape Shifting");
    assertSkill(SkillId.FIRESTORM, "Firestorm");
    assertSkill(SkillId.OAK_SAGE, "Oak Sage");
    assertSkill(SkillId.SUMMON_SPIRIT_WOLF, "Summon Spirit Wolf");
    assertSkill(SkillId.WEREBEAR, "Wearbear");
    assertSkill(SkillId.MOLTEN_BOULDER, "Molten Boulder");
    assertSkill(SkillId.ARCTIC_BLAST, "Arctic Blast");
    assertSkill(SkillId.CARRION_VINE, "Cycle of Life");
    assertSkill(SkillId.FERAL_RAGE, "Feral Rage");
    assertSkill(SkillId.MAUL, "Maul");
    assertSkill(SkillId.FISSURE, "Eruption");
    assertSkill(SkillId.CYCLONE_ARMOR, "Cyclone Armor");
    assertSkill(SkillId.HEART_OF_WOLVERINE, "Heart of Wolverine");
    assertSkill(SkillId.SUMMON_DIRE_WOLF, "Summon Fenris");
    assertSkill(SkillId.RABIES, "Rabies");
    assertSkill(SkillId.FIRE_CLAWS, "Fire Claws");
    assertSkill(SkillId.TWISTER, "Twister");
    assertSkill(SkillId.SOLAR_CREEPER, "Vines");
    assertSkill(SkillId.HUNGER, "Hunger");
    assertSkill(SkillId.SHOCK_WAVE, "Shock Wave");
    assertSkill(SkillId.VOLCANO, "Volcano");
    assertSkill(SkillId.TORNADO, "Tornado");
    assertSkill(SkillId.SPIRIT_OF_BARBS, "Spirit of Barbs");
    assertSkill(SkillId.SUMMON_GRIZZLY, "Summon Grizzly");
    assertSkill(SkillId.FURY, "Fury");
    assertSkill(SkillId.ARMAGEDDON, "Armageddon");
    assertSkill(SkillId.HURRICANE, "Hurricane");
  }

  @Test
  void werewolfAndWerebearUseNativeSrvDo116AndAuraStats() {
    Skills.Entry wolf = skill(SkillId.WEREWOLF);
    assertEquals(116, wolf.srvdofunc);
    assertEquals("wolf", wolf.aurastate);
    assertEquals("1000+skill('Shape Shifting'.ln12)", wolf.auralencalc);
    assertEquals("skill_staminapercent", wolf.aurastat[0]);
    assertEquals("attackrate", wolf.aurastat[1]);
    assertEquals("item_tohit_percent", wolf.aurastat[2]);
    assertEquals("item_maxhp_percent", wolf.aurastat[3]);
    assertEquals("dm34", wolf.aurastatcalc[1]);
    assertEquals("toht", wolf.aurastatcalc[2]);
    assertEquals(StateId.WOLF, DruidSkills.getShapeStateId(wolf));
    assertEquals(wolf.ToHit + wolf.LevToHit,
        DruidSkills.calculateWerewolfAttackRatingBonus(2));
    assertEquals(SkillFormula.evaluate("dm34", wolf, 2),
        DruidSkills.getWerewolfIasBonus(2));

    Skills.Entry bear = skill(SkillId.WEREBEAR);
    assertEquals(116, bear.srvdofunc);
    assertEquals("bear", bear.aurastate);
    assertEquals("damagepercent", bear.aurastat[0]);
    assertEquals("skill_armor_percent", bear.aurastat[1]);
    assertEquals("item_maxhp_percent", bear.aurastat[2]);
    assertEquals(StateId.BEAR, DruidSkills.getShapeStateId(bear));
    assertEquals(SkillFormula.evaluate("ln12", bear, 3),
        DruidSkills.calculateWerebearDamageBonus(3));
    assertEquals(SkillFormula.evaluate("ln34", bear, 3),
        DruidSkills.calculateWerebearDefenseBonus(3));
    Skills.Entry lycanthropy = skill(SkillId.LYCANTHROPY);
    assertEquals(SkillFormula.evaluate("ln34", lycanthropy, 3),
        DruidSkills.calculateLycanthropyLifeBonus(3));
  }

  @Test
  void nativeReferencedLnAndToHitOperandsAreEvaluated() {
    Skills.Entry wolf = skill(SkillId.WEREWOLF);
    Skills.Entry lycanthropy = skill(SkillId.LYCANTHROPY);
    assertEquals(wolf.ToHit, SkillFormula.evaluate("toht", wolf, 1));
    assertEquals(wolf.ToHit + wolf.LevToHit, SkillFormula.evaluate("toht", wolf, 2));
    assertEquals(lycanthropy.Param[0], SkillFormula.evaluate(
        "skill('Shape Shifting'.ln12)", wolf, 1,
        name -> 1, name -> Riiablo.files.skills.get(name)));
    assertEquals(lycanthropy.Param[0] + lycanthropy.Param[1], SkillFormula.evaluate(
        "skill('Shape Shifting'.ln12)", wolf, 1,
        name -> 2, name -> Riiablo.files.skills.get(name)));
    assertEquals(0, SkillFormula.evaluate(
        "skill('Shape Shifting'.ln12)", wolf, 1,
        name -> 0, name -> Riiablo.files.skills.get(name)));
  }

  @Test
  void transformGfxClassesResolveToNativeMonsterTokens() {
    assertEquals("40", Riiablo.files.monstats.get(430).Code);
    assertEquals("TG", Riiablo.files.monstats.get(431).Code);
    assertTrue(Riiablo.mpqs.contains("data\\global\\monsters\\40\\cof\\40NUHTH.cof"));
    assertTrue(Riiablo.mpqs.contains("data\\global\\monsters\\TG\\cof\\TGNUHTH.cof"));
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, Integer.toString(id));
    return skill;
  }

  private static void assertSkill(int id, String name) {
    assertEquals(name, skill(id).skill, Integer.toString(id));
  }
}
