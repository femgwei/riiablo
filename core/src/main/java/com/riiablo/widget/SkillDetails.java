package com.riiablo.widget;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.SkillFormula;
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

  public void refresh() {
    if (skillId < 0 || Riiablo.files == null || Riiablo.files.skills == null
        || Riiablo.files.skilldesc == null) return;
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return;
    SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
    if (desc == null) return;
    int level = chargedSkill != null
        ? Math.max(1, chargedSkill.param0())
        : Riiablo.charData != null ? Math.max(1, Riiablo.charData.getSkill(skillId)) : 1;
    if (level == displayedLevel) return;
    displayedLevel = level;

    clearChildren();
    final float spacing = 2f;
    final BitmapFont font = Riiablo.fonts.font16;
    setBackground(PaletteIndexedColorDrawable.MODAL_FONT16);
    add(new Label(Riiablo.string.lookup(desc.str_name), font, Riiablo.colors.green))
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
    add(new Label(Riiablo.string.lookup("StrSkill2") + level, font))
        .center().space(spacing).row();
    for (int i = 0; i < desc.descline.length && desc.descline[i] > 0; i++) {
      String line = formatLine(desc.descline[i], desc.desctexta[i], desc.desctextb[i],
          desc.desccalca[i], skill, level, desc.str_mana);
      if (line != null) add(new Label(line, font)).center().space(spacing).row();
    }
    pack();
  }

  private static String formatLine(int type, String textA, String textB, String calc,
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
      case 12: return a + value + lookup("StrSkill16");
      case 13: return lookup("StrSkill42") + value;
      case 19: return b + a + (value * 2f / 3f) + lookup("StrSkill26");
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
}
