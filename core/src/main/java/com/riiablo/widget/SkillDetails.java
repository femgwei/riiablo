package com.riiablo.widget;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
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
    for (int i = 0; i < desc.descline.length && desc.descline[i] > 0; i++) {
      String line = formatLine(desc.descline[i], desc.desctexta[i], desc.desctextb[i],
          desc.desccalca[i], skill, level, desc.str_mana);
      if (line != null) add(new Label(line, font)).center().space(spacing).row();
    }
    pack();
  }

  /** Formats one SkillDesc row, including the native missile-backed rows used
   * by Immolation Arrow (continuous fire, duration, and explosion damage). */
  public static String formatLine(int type, String textA, String textB, String calc,
      Skills.Entry skill, int level, String manaText) {
    String a = lookup(textA);
    String b = lookup(textB);
    int value = SkillFormula.evaluate(calc, skill, level);
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
      case 12: return a + SkillFormula.durationSeconds(value) + lookup("StrSkill16");
      case 13: return lookup("StrSkill42") + value;
      case 19: return b + a + (value * 2f / 3f) + lookup("StrSkill26");
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
      case 67: return a + ": +" + value + b;
      default: return null;
    }
  }

  private static String lookup(String key) {
    return key == null || key.isEmpty() ? "" : Riiablo.string.lookup(key);
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
