package com.riiablo.widget;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.graphics.PaletteIndexedColorDrawable;

/** Compact native-style tooltip shared by HUD and quick-skill buttons. */
public final class SkillDetails extends Table {
  private int skillId;
  private StatRef chargedSkill;
  private int displayedLevel = Integer.MIN_VALUE;

  public SkillDetails(int skillId, StatRef chargedSkill) {
    setSkill(skillId, chargedSkill);
  }

  public void setSkill(int skillId, StatRef chargedSkill) {
    this.skillId = skillId;
    this.chargedSkill = chargedSkill;
    displayedLevel = Integer.MIN_VALUE;
    refresh();
  }

  /**
   * Returns the local player's effective skill level, including temporary
   * state bonuses such as the Skill Shrine's +2 all skills.  CharData holds
   * the permanent/equipment skill table while UnitStates carries timed native
   * modifiers, so using CharData alone leaves the skill book and HUD stale
   * during a shrine effect.
   */
  public static int effectivePlayerSkillLevel(int skillId) {
    if (Riiablo.charData == null) return 0;
    int level = Math.max(0, Riiablo.charData.getSkill(skillId));
    return Math.max(0, level + activePlayerSkillBonus());
  }

  /** Returns the active temporary all-skills modifier for the local player. */
  public static int activePlayerSkillBonus() {
    if (Riiablo.engine == null || Riiablo.game == null || Riiablo.game.player < 0) return 0;
    try {
      com.artemis.ComponentMapper<UnitStates> mapper =
          Riiablo.engine.getMapper(UnitStates.class);
      if (mapper == null || !mapper.has(Riiablo.game.player)) return 0;
      UnitStates states = mapper.get(Riiablo.game.player);
      return states != null && states.stateList != null
          ? states.stateList.getTotalSkillModifier() : 0;
    } catch (RuntimeException ignored) {
      // The HUD can be queried during world teardown. Treat that as no
      // temporary modifier instead of making tooltip rendering fatal.
      return 0;
    }
  }

  public void refresh() {
    if (skillId < 0 || Riiablo.files == null || Riiablo.files.skills == null
        || Riiablo.files.skilldesc == null) return;
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return;
    SkillDesc.Entry desc = SkillNameResolver.description(skill);
    if (desc == null) return;
    int level = chargedSkill != null
        ? Math.max(1, chargedSkill.param0())
        : Math.max(1, effectivePlayerSkillLevel(skillId));
    if (level == displayedLevel) return;
    displayedLevel = level;

    clearChildren();
    final float spacing = 2f;
    final BitmapFont font = Riiablo.fonts.font16;
    setBackground(PaletteIndexedColorDrawable.MODAL_FONT16);
    add(new Label(SkillNameResolver.name(skill), font, Riiablo.colors.green))
        .center().space(spacing).row();

    String text = Riiablo.string.lookup(desc.str_long);
    String[] lines = StringUtils.split(text, '\n');
    if (lines != null) {
      ArrayUtils.reverse(lines);
      Label description = new Label(StringUtils.join(lines, '\n'), font);
      description.setAlignment(Align.center);
      add(description).center().space(spacing).row();
    }

    add().height(font.getLineHeight()).center().space(spacing).row();
    Color levelColor = chargedSkill == null && activePlayerSkillBonus() != 0
        ? Riiablo.colors.blue : Riiablo.colors.white;
    add(new Label(Riiablo.string.lookup("StrSkill2") + level, font, levelColor))
        .center().space(spacing).row();
    appendLines(desc.dsc2line, desc.dsc2texta, desc.dsc2textb,
        desc.dsc2calca, desc.dsc2calcb, skill, level, desc.str_mana, font, spacing);
    appendLines(desc.descline, desc.desctexta, desc.desctextb,
        desc.desccalca, desc.desccalcb, skill, level, desc.str_mana, font, spacing);
    appendLines(desc.dsc3line, desc.dsc3texta, desc.dsc3textb,
        desc.dsc3calca, desc.dsc3calcb, skill, level, desc.str_mana, font, spacing);
    pack();
  }

  private void appendLines(int[] types, String[] textA, String[] textB,
      String[] calcA, String[] calcB, Skills.Entry skill, int level,
      String manaText, BitmapFont font, float spacing) {
    if (types == null) return;
    for (int i = 0; i < types.length && types[i] > 0; i++) {
      String line = formatLine(types[i], textA[i], textB[i], calcA[i], calcB[i],
          skill, level, manaText);
      if (line != null && !line.isEmpty()) {
        add(new Label(line, font)).center().space(spacing).row();
      }
    }
  }

  /** Formats one SkillDesc row, including the native missile-backed rows used
   * by Immolation Arrow (continuous fire, duration, and explosion damage). */
  public static String formatLine(int type, String textA, String textB, String calc,
      Skills.Entry skill, int level, String manaText) {
    return formatLine(type, textA, textB, calc, "", skill, level, manaText);
  }

  /** Formats a SkillDesc row using both native calculation operands. */
  public static String formatLine(int type, String textA, String textB,
      String calcA, String calcB, Skills.Entry skill, int level, String manaText) {
    String a = lookup(textA);
    String b = lookup(textB);
    int value = evaluate(calcA, skill, level);
    int valueB = evaluate(calcB, skill, level);
    switch (type) {
      case 1:
        float mana = NativeSkillResolver.manaCost(skill, level);
        String manaValue = mana == Math.rint(mana)
            ? Integer.toString((int) mana)
            : String.format(java.util.Locale.ROOT, "%.1f", mana);
        return lookup(manaText) + manaValue;
      case 2: return a + "+" + value + b;
      case 3: return a + value + b;
      case 4: return a + "+" + value;
      case 5: return a + value;
      case 6: return "+" + value + a;
      case 7: return value + a;
      case 8: return a + b;
      case 9: return a + b + lookup("StrSkill4") + "+" + value;
      case 10:
        return formatElementalDamageLine(skill, level);
      case 11:
        return formatWeaponDamageLine(skill, level);
      case 12: return a + SkillFormula.durationSeconds(value) + lookup("StrSkill16");
      case 13: return lookup("StrSkill42") + value;
      case 14:
        return formatElementalDamageLine(skill, level);
      case 19:
        return b + a + String.format(java.util.Locale.ROOT, "%.1f", value * 2f / 3f)
            + lookup("StrSkill26");
      case 22:
        return formatMissileDamageLine(skill, level, lookup("StrSkill35"));
      case 23:
        return formatMissileDurationLine(skill, level, a);
      case 24:
        return formatMissileDamageLine(skill, level,
            a.isEmpty() ? lookup("StrSkill83") : a);
      case 28: return lookup("StrSkill18") + "1" + lookup("StrSkill36");
      case 40: return String.format(a, b);
      case 63: return a + ": +" + value + "% " + b;
      case 66: return String.format(a, value);
      case 67: return a + ": +" + value + b;
      case 73:
        return formatWeaponPercentLine(a, value, valueB);
      default: return null;
    }
  }

  private static int evaluate(String expression, Skills.Entry skill, int level) {
    return SkillFormula.evaluate(expression, skill, level,
        SkillDetails::hardSkillLevel, SkillNameResolver::entry);
  }

  private static int hardSkillLevel(String internalName) {
    Skills.Entry referenced = SkillNameResolver.entry(internalName);
    return referenced == null || Riiablo.charData == null
        ? 0 : Math.max(0, Riiablo.charData.getSkill(referenced.Id));
  }

  private static String lookup(String key) {
    return key == null || key.isEmpty() ? "" : Riiablo.string.lookup(key);
  }

  private static String formatElementalDamageLine(Skills.Entry skill, int level) {
    if (skill == null || skill.EType == null || skill.EType.isEmpty()) return null;
    long minFixed = skillElementalDamageFixed(skill, level, true);
    long maxFixed = Math.max(minFixed, skillElementalDamageFixed(skill, level, false));
    String label = elementalDamageLabel(skill.EType);
    if (isPoison(skill.EType)) {
      int frames = Math.max(1,
          skill.ELen + missileDamageBonus(Math.max(1, level), skill.ELevLen));
      long min = minFixed * frames / 256L;
      long max = Math.max(min, maxFixed * frames / 256L);
      return label + min + "-" + max + " over "
          + SkillFormula.durationSeconds(frames) + lookup("StrSkill16");
    }
    long min = minFixed / 256L;
    long max = Math.max(min, maxFixed / 256L);
    return label + min + "-" + max;
  }

  private static long skillElementalDamageFixed(
      Skills.Entry skill, int level, boolean minimum) {
    level = Math.max(1, level);
    int base = minimum ? skill.EMin : skill.EMax;
    int[] perLevel = minimum ? skill.EMinLev : skill.EMaxLev;
    long damage = Math.max(0L, (long) base + missileDamageBonus(level, perLevel));
    damage <<= Math.max(0, Math.min(30, skill.HitShift));
    // Keep this in native 8.8 units. The server applies the same hard-point
    // synergy before writing a missile damage snapshot.
    boolean applySynergy = !minimum || damage > 256L
        || (perLevel != null && perLevel.length > 0 && perLevel[0] != 0);
    if (applySynergy) {
      int synergy = Math.max(0, evaluate(skill.EDmgSymPerCalc, skill, level));
      damage += damage * synergy / 100L;
    }
    return damage;
  }

  private static boolean isPoison(String element) {
    return "pois".equalsIgnoreCase(element) || "poison".equalsIgnoreCase(element);
  }

  private static String elementalDamageLabel(String element) {
    if ("fire".equalsIgnoreCase(element)) return "Fire Damage: ";
    if ("ltng".equalsIgnoreCase(element)
        || "lightning".equalsIgnoreCase(element)) return "Lightning Damage: ";
    if ("cold".equalsIgnoreCase(element) || "freeze".equalsIgnoreCase(element)
        || "frze".equalsIgnoreCase(element)) return "Cold Damage: ";
    if (isPoison(element)) return "Poison Damage: ";
    if ("mag".equalsIgnoreCase(element) || "magic".equalsIgnoreCase(element)) {
      return "Magic Damage: ";
    }
    return "Damage: ";
  }

  private static String formatWeaponDamageLine(Skills.Entry skill, int level) {
    if (skill == null || skill.SrcDam <= 0) return null;
    int[] weapon = currentWeaponDamage();
    if (weapon != null) {
      int min = weapon[0] * skill.SrcDam / 128
          + shiftedSkillDamage(skill.MinDam, skill.MinLevDam, level, skill.HitShift);
      int max = weapon[1] * skill.SrcDam / 128
          + shiftedSkillDamage(skill.MaxDam, skill.MaxLevDam, level, skill.HitShift);
      return "Weapon Damage: " + Math.max(0, min) + "-" + Math.max(min, max);
    }
    int percent = Math.max(0, Math.round(skill.SrcDam * 100f / 128f));
    return "Weapon Damage: " + percent + "%";
  }

  private static int shiftedSkillDamage(int base, int[] perLevel, int level, int hitShift) {
    long value = Math.max(0L,
        (long) base + missileDamageBonus(Math.max(1, level), perLevel));
    int shift = hitShift - 8;
    if (shift > 0) value <<= Math.min(shift, 30);
    else if (shift < 0) value >>= Math.min(-shift, 30);
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private static int[] currentWeaponDamage() {
    if (Riiablo.engine == null || Riiablo.game == null || Riiablo.game.player < 0) return null;
    try {
      com.artemis.ComponentMapper<AttributesWrapper> mapper =
          Riiablo.engine.getMapper(AttributesWrapper.class);
      if (mapper == null || !mapper.has(Riiablo.game.player)) return null;
      Attributes attrs = mapper.get(Riiablo.game.player).attrs;
      if (attrs == null) return null;
      int min = statValue(attrs, Stat.mindamage);
      int max = statValue(attrs, Stat.maxdamage);
      int throwMax = statValue(attrs, Stat.item_throw_maxdamage);
      if (throwMax > 0) {
        min = statValue(attrs, Stat.item_throw_mindamage);
        max = throwMax;
      }
      return max > 0 ? new int[] {Math.max(0, min), Math.max(min, max)} : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static int statValue(Attributes attrs, short stat) {
    StatRef value = attrs.get(stat);
    return value == null ? 0 : value.asInt();
  }

  private static String formatWeaponPercentLine(String label, int numerator, int denominator) {
    if (denominator <= 0) return null;
    String prefix = label == null || label.isEmpty() ? "Weapon Damage: " : label;
    return prefix + Math.max(0, Math.round(numerator * 100f / denominator)) + "%";
  }

  private static Missiles.Entry descriptionMissile(Skills.Entry skill) {
    if (skill == null || Riiablo.files == null || Riiablo.files.skilldesc == null
        || Riiablo.files.Missiles == null) return null;
    SkillDesc.Entry desc = SkillNameResolver.description(skill);
    if (desc == null || desc.descmissile1 == null || desc.descmissile1.isEmpty()) return null;
    return Riiablo.files.Missiles.get(desc.descmissile1);
  }

  private static String formatMissileDamageLine(Skills.Entry skill, int level, String label) {
    Missiles.Entry missile = descriptionMissile(skill);
    if (missile == null || missile.EType == null || missile.EType.isEmpty()) return null;
    int min = missile.EMin + missileDamageBonus(level, missile.MinELev);
    int max = missile.Emax + missileDamageBonus(level, missile.MaxELev);
    min = Math.max(0, min);
    max = Math.max(min, max);
    return label + min + "-" + max;
  }

  private static String formatMissileDurationLine(Skills.Entry skill, int level, String label) {
    Missiles.Entry missile = descriptionMissile(skill);
    if (missile == null) return null;
    int frames = missile.Range + Math.max(0, level - 1) * missile.LevRange;
    String prefix = label.isEmpty() ? lookup("StrSkill82") : label;
    return prefix + SkillFormula.durationSeconds(frames) + lookup("StrSkill16");
  }

  private static int missileDamageBonus(int level, int[] perLevel) {
    if (level <= 1 || perLevel == null || perLevel.length == 0) return 0;
    int l1 = perLevel.length > 0 ? perLevel[0] : 0;
    int l2 = perLevel.length > 1 ? perLevel[1] : 0;
    int l3 = perLevel.length > 2 ? perLevel[2] : 0;
    int l4 = perLevel.length > 3 ? perLevel[3] : 0;
    int l5 = perLevel.length > 4 ? perLevel[4] : 0;
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }
}
