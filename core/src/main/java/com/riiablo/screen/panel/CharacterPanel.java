package com.riiablo.screen.panel;

import java.text.NumberFormat;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Cvars;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceTable;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.CharStats;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.loader.DC6Loader;
import com.riiablo.skill.SkillCodes;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;
import com.riiablo.widget.StatLabel;
import com.riiablo.engine.server.player.PlayerStatsManager;

public class CharacterPanel extends WidgetGroup implements Disposable {

  final AssetDescriptor<DC6> invcharDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\invchar6.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion invchar;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  final AssetDescriptor<DC6> levelButtonDescriptor =
      new AssetDescriptor<>("data\\global\\ui\\PANEL\\level.DC6", DC6.class);
  Button btnExit;
  private Label statPoints;
  private Label levelValue;
  private Label experienceValue;
  private Label nextLevelValue;
  private final Label[] damageValues = new Label[2];
  private final Label[] attackRatingValues = new Label[2];
  private final Label[] damageNames = new Label[2];
  private final Label[] attackRatingNames = new Label[2];
  private final Button[] statButtons = new Button[4];
  private Button.ButtonStyle statButtonStyle;

  public CharacterPanel() {
    Riiablo.assets.load(invcharDescriptor);
    Riiablo.assets.finishLoadingAsset(invcharDescriptor);
    invchar = Riiablo.assets.get(invcharDescriptor).getTexture(0);
    setSize(invchar.getRegionWidth(), invchar.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    Riiablo.assets.load(levelButtonDescriptor);
    Riiablo.assets.finishLoadingAsset(levelButtonDescriptor);
    DC6 levelButton = Riiablo.assets.get(levelButtonDescriptor);
    statButtonStyle = new Button.ButtonStyle(
        new TextureRegionDrawable(levelButton.getTexture(0)),
        new TextureRegionDrawable(levelButton.getTexture(1)));
    statButtonStyle.disabled = new TextureRegionDrawable(levelButton.getTexture(2));

    btnExit = new Button(new Button.ButtonStyle() {{
      Riiablo.assets.load(buysellbtnDescriptor);
      Riiablo.assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(128, 11);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        setVisible(false);
      }
    });
    addActor(btnExit);

    // The stat-point balance belongs to the lower-left "Stat Points" row.
    // It used to be attached to the top "Next Level" box, which made the
    // number appear as an extra value after the next-level experience.
    Label statPointsLabel = new Label(4075, Riiablo.fonts.ReallyTheLastSucker);
    statPointsLabel.setPosition(11, getHeight() - 373);
    statPointsLabel.setSize(108, 16);
    statPointsLabel.setAlignment(Align.center);
    addActor(statPointsLabel);

    statPoints = new Label("0", Riiablo.fonts.font16, Riiablo.colors.gold);
    statPoints.setAutoSize(false);
    statPoints.setPosition(120, getHeight() - 373);
    statPoints.setSize(36, 16);
    statPoints.setAlignment(Align.center);
    addActor(statPoints);

    Label name = new Label(Riiablo.charData.name, Riiablo.fonts.font16);
    name.setPosition(12, getHeight() - 24);
    name.setSize(168, 13);
    name.setAlignment(Align.center);
    addActor(name);

    Label levelLabel = new Label(4057, Riiablo.fonts.ReallyTheLastSucker);
    levelLabel.setPosition(12, getHeight() - 48);
    levelLabel.setSize(42, 14);
    levelLabel.setAlignment(Align.center);
    addActor(levelLabel);
    levelValue = new Label("0", Riiablo.fonts.font16);
    configureTopValue(levelValue, 12, 42);

    Label expLabel = new Label(4058, Riiablo.fonts.ReallyTheLastSucker);
    expLabel.setPosition(66, getHeight() - 48);
    expLabel.setSize(114, 14);
    expLabel.setAlignment(Align.center);
    addActor(expLabel);
    experienceValue = new Label("0", Riiablo.fonts.font16);
    configureTopValue(experienceValue, 66, 114);

    Label clazz = new Label(Riiablo.charData.classId.name, Riiablo.fonts.font16);
    clazz.setPosition(194, getHeight() - 24);
    clazz.setSize(114, 13);
    clazz.setAlignment(Align.center);
    addActor(clazz);

    Label nextLevelLabel = new Label(4059, Riiablo.fonts.ReallyTheLastSucker);
    nextLevelLabel.setPosition(194, getHeight() - 48);
    nextLevelLabel.setSize(114, 14);
    nextLevelLabel.setAlignment(Align.center);
    addActor(nextLevelLabel);
    nextLevelValue = new Label("0", Riiablo.fonts.font16);
    configureTopValue(nextLevelValue, 194, 114);

    Label strLabel = new Label(4060, Riiablo.fonts.ReallyTheLastSucker);
    strLabel.setPosition(11, getHeight() - 100);
    strLabel.setSize(63, 16);
    strLabel.setAlignment(Align.center);
    addActor(strLabel);

    Label str = createPrimaryStatLabel(Stat.strength);
    str.setPosition(78, getHeight() - 100);
    str.setSize(36, 16);
    addActor(str);
    addStatButton(PlayerStatsManager.STAT_TYPE_STRENGTH, 137, getHeight() - 92);

    addCombatRow(4061, getHeight() - 100, damageValues, damageNames, 0, false);
    addCombatRow(4061, getHeight() - 124, damageValues, damageNames, 1, true);

    Label dexLabel = new Label(4062, Riiablo.fonts.ReallyTheLastSucker);
    dexLabel.setPosition(11, getHeight() - 162);
    dexLabel.setSize(63, 16);
    dexLabel.setAlignment(Align.center);
    addActor(dexLabel);

    Label dex = createPrimaryStatLabel(Stat.dexterity);
    dex.setPosition(78, getHeight() - 162);
    dex.setSize(36, 16);
    addActor(dex);
    addStatButton(PlayerStatsManager.STAT_TYPE_DEXTERITY, 137, getHeight() - 154);

    addCombatRow(4063, getHeight() - 162, attackRatingValues, attackRatingNames, 0, false);
    addCombatRow(4063, getHeight() - 186, attackRatingValues, attackRatingNames, 1, false);

    Label defenseLabel = Label.i18n("strchrdef", Riiablo.fonts.ReallyTheLastSucker);
    defenseLabel.setPosition(165, getHeight() - 210);
    defenseLabel.setSize(108, 16);
    defenseLabel.setAlignment(Align.center);
    addActor(defenseLabel);

    Label armorclass = createStatLabel(Stat.armorclass);
    armorclass.setPosition(272, getHeight() - 210);
    armorclass.setSize(40, 16);
    addActor(armorclass);

    Label vitLabel = new Label(4066, Riiablo.fonts.ReallyTheLastSucker);
    vitLabel.setPosition(11, getHeight() - 248);
    vitLabel.setSize(63, 16);
    vitLabel.setAlignment(Align.center);
    addActor(vitLabel);

    Label vit = createPrimaryStatLabel(Stat.vitality);
    vit.setPosition(78, getHeight() - 248);
    vit.setSize(36, 16);
    addActor(vit);
    addStatButton(PlayerStatsManager.STAT_TYPE_VITALITY, 137, getHeight() - 240);

    Label staminaLabel = Label.i18n("strchrstm", Riiablo.fonts.ReallyTheLastSucker);
    staminaLabel.setPosition(165, getHeight() - 248);
    staminaLabel.setSize(63, 16);
    staminaLabel.setAlignment(Align.center);
    addActor(staminaLabel);

    Label stamina = createStatLabel(Stat.stamina);
    stamina.setPosition(235, getHeight() - 248);
    stamina.setSize(36, 16);
    addActor(stamina);

    Label maxstamina = createStatLabel(Stat.maxstamina);
    maxstamina.setPosition(275, getHeight() - 248);
    maxstamina.setSize(36, 16);
    addActor(maxstamina);

    Label lifeLabel = Label.i18n("strchrlif", Riiablo.fonts.ReallyTheLastSucker);
    lifeLabel.setPosition(165, getHeight() - 272);
    lifeLabel.setSize(63, 16);
    lifeLabel.setAlignment(Align.center);
    addActor(lifeLabel);

    Label hitpoints = createStatLabel(Stat.hitpoints);
    hitpoints.setPosition(235, getHeight() - 272);
    hitpoints.setSize(36, 16);
    addActor(hitpoints);

    Label maxhp = createStatLabel(Stat.maxhp);
    maxhp.setPosition(275, getHeight() - 272);
    maxhp.setSize(36, 16);
    addActor(maxhp);

    Label eneLabel = new Label(4069, Riiablo.fonts.ReallyTheLastSucker);
    eneLabel.setPosition(11, getHeight() - 310);
    eneLabel.setSize(63, 16);
    eneLabel.setAlignment(Align.center);
    addActor(eneLabel);

    Label ene = createPrimaryStatLabel(Stat.energy);
    ene.setPosition(78, getHeight() - 310);
    ene.setSize(36, 16);
    addActor(ene);
    addStatButton(PlayerStatsManager.STAT_TYPE_ENERGY, 137, getHeight() - 302);

    Label manaLabel = Label.i18n("strchrman", Riiablo.fonts.ReallyTheLastSucker);
    manaLabel.setPosition(165, getHeight() - 310);
    manaLabel.setSize(63, 16);
    manaLabel.setAlignment(Align.center);
    addActor(manaLabel);

    Label mana = createStatLabel(Stat.mana);
    mana.setPosition(235, getHeight() - 310);
    mana.setSize(36, 16);
    addActor(mana);

    Label maxmana = createStatLabel(Stat.maxmana);
    maxmana.setPosition(275, getHeight() - 310);
    maxmana.setSize(36, 16);
    addActor(maxmana);

    Label fireResLabel = new Label(4071, Riiablo.fonts.ReallyTheLastSucker);
    fireResLabel.setPosition(175, getHeight() - 349);
    fireResLabel.setSize(94, 16);
    fireResLabel.setAlignment(Align.center);
    addActor(fireResLabel);

    Label fireRes = createStatLabel(Stat.fireresist, StatLabel.Colorizer.RESISTANCE);
    fireRes.setPosition(273, getHeight() - 349);
    fireRes.setSize(36, 16);
    addActor(fireRes);

    Label coldResLabel = new Label(4072, Riiablo.fonts.ReallyTheLastSucker);
    coldResLabel.setPosition(175, getHeight() - 373);
    coldResLabel.setSize(94, 16);
    coldResLabel.setAlignment(Align.center);
    addActor(coldResLabel);

    Label coldRes = createStatLabel(Stat.coldresist, StatLabel.Colorizer.RESISTANCE);
    coldRes.setPosition(273, getHeight() - 373);
    coldRes.setSize(36, 16);
    addActor(coldRes);

    Label lightningResLabel = new Label(4073, Riiablo.fonts.ReallyTheLastSucker);
    lightningResLabel.setPosition(175, getHeight() - 397);
    lightningResLabel.setSize(94, 16);
    lightningResLabel.setAlignment(Align.center);
    addActor(lightningResLabel);

    Label lightningRes = createStatLabel(Stat.lightresist, StatLabel.Colorizer.RESISTANCE);
    lightningRes.setPosition(273, getHeight() - 397);
    lightningRes.setSize(36, 16);
    addActor(lightningRes);

    Label poisonResLabel = new Label(4074, Riiablo.fonts.ReallyTheLastSucker);
    poisonResLabel.setPosition(175, getHeight() - 421);
    poisonResLabel.setSize(94, 16);
    poisonResLabel.setAlignment(Align.center);
    addActor(poisonResLabel);

    Label poisonRes = createStatLabel(Stat.poisonresist, StatLabel.Colorizer.RESISTANCE);
    poisonRes.setPosition(273, getHeight() - 421);
    poisonRes.setSize(36, 16);
    addActor(poisonRes);

    //setDebug(true, true);
  }

  private Label createStatLabel(short statId) {
    return createStatLabel(statId, StatLabel.Colorizer.DEFAULT);
  }

  private Label createPrimaryStatLabel(short statId) {
    return createStatLabel(statId, StatLabel.Colorizer.BASE_DIFFERENCE);
  }

  private Label createStatLabel(short statId, StatLabel.Colorizer colorizer) {
    Attributes attrs = Riiablo.charData.getStats();
    return new StatLabel(attrs, statId, colorizer);
  }

  private void configureTopValue(Label label, float x, float width) {
    label.setAutoSize(false);
    // Keep the value in a fixed lower row. Table.growY() made the value's
    // baseline depend on the current string/font metrics, which was visible
    // as the experience and next-level numbers sitting too low.
    label.setPosition(x, getHeight() - 65);
    label.setSize(width, 16);
    label.setAlignment(Align.center);
    addActor(label);
  }

  private void addCombatRow(int labelId, float y, Label[] values, Label[] names,
      int index, boolean skillQualified) {
    Label name = new Label(labelId, Riiablo.fonts.ReallyTheLastSucker);
    name.setAutoSize(false);
    name.setPosition(165, y);
    name.setSize(108, 16);
    name.setAlignment(Align.center);
    if (skillQualified) name.setText(combatLabel(labelId, selectedSkillName(), true));
    else name.setText(combatLabel(labelId, selectedSkillName(), false));
    names[index] = name;
    addActor(name);

    Label value = values[index] = new Label("0", Riiablo.fonts.font16);
    value.setAutoSize(false);
    value.setPosition(272, y);
    value.setSize(40, 16);
    value.setAlignment(Align.center);
    addActor(value);
  }

  /**
   * Character-panel labels 4061/4063 are native format strings. The first
   * row is the plain stat name, while the second row includes the currently
   * selected left-button skill. Do not render the native %s placeholder when
   * the plain row is requested.
   */
  private static String combatLabel(int labelId, String skillName, boolean skillQualified) {
    String format = Riiablo.string.lookup(labelId);
    if (format == null) return skillQualified ? skillName : "";
    String placeholder = format.contains("%s1") ? "%s1" : "%s";
    if (format.contains(placeholder)) {
      // In the native Chinese table 4063 is deliberately just "%s". The
      // client supplies the localized "Attack Rating" caption at runtime;
      // using the raw string leaves a visible percent marker in Riiablo.
      if (labelId == 4063) {
        String attackRating = localizedAttackRating();
        String replacement = skillQualified && skillName != null && !skillName.isEmpty()
            ? skillName + localizedSeparator() + attackRating : attackRating;
        return format.replace(placeholder, replacement);
      }
      if (skillQualified) return format.replace(placeholder, skillName);
      return format.replace(placeholder, "").trim();
    }
    return skillQualified ? skillName + localizedSeparator() + format : format;
  }

  private static String localizedAttackRating() {
    // 4240 is the native "Attack" caption and 3480 is the native
    // "Accuracy/Rating" caption. Combining them keeps this localized for
    // both the Chinese and English string tables.
    return Riiablo.string.lookup(4240) + localizedSeparator()
        + Riiablo.string.lookup(3480);
  }

  private static String localizedSeparator() {
    return Riiablo.language == com.riiablo.D2Language.CHINESE ? "" : " ";
  }

  private Skills.Entry selectedSkill(int button) {
    if (Riiablo.charData == null || Riiablo.files == null) return null;
    return Riiablo.files.skills.get(Riiablo.charData.getAction(button));
  }

  private String selectedSkillName() {
    return selectedSkillName(Input.Buttons.LEFT);
  }

  private String selectedSkillName(int button) {
    Skills.Entry skill = selectedSkill(button);
    if (skill == null) return "";
    SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
    if (desc == null || desc.str_name == null || desc.str_name.isEmpty()) return skill.skill;
    String name = Riiablo.string.lookup(desc.str_name);
    return name == null || name.startsWith("ERROR:") ? skill.skill : name;
  }

  static boolean skillUsesAttackRating(Skills.Entry skill) {
    if (skill == null || skill.passive || skill.aura) return false;
    // Native ResultFlags bit 0 marks an always-hit packet. Such skills do not
    // have a meaningful attack-rating value even when they carry weapon
    // source damage (Guided Arrow is the common example).
    if ((skill.ResultFlags & 1) != 0) return false;
    switch (skill.Id) {
      case SkillCodes.attack:
      case SkillCodes.kick:
      case SkillCodes.throw_:
      case SkillCodes.left_hand_throw:
      case SkillCodes.left_hand_swing:
        return true;
      default:
        // Weapon attacks either scale source damage or expose the native
        // ToHit/LevToHit modifier. Pure spells have none of these fields.
        return skill.SrcDam > 0 || skill.ToHit != 0 || skill.LevToHit != 0;
    }
  }

  static boolean isNormalAttack(Skills.Entry skill) {
    return skill != null
        && (skill.Id == SkillCodes.attack || skill.Id == SkillCodes.left_hand_swing);
  }

  static int calculateSkillAttackRating(int baseAttackRating, Skills.Entry skill, int level) {
    if (!skillUsesAttackRating(skill)) return 0;
    int bonus = skill.ToHit + Math.max(0, level - 1) * skill.LevToHit;
    return Math.max(0, baseAttackRating + baseAttackRating * bonus / 100);
  }

  private void addStatButton(final int statType, float centerX, float centerY) {
    Button button = new Button(statButtonStyle);
    button.setPosition(
        centerX - button.getWidth() / 2f,
        centerY - button.getHeight() / 2f);
    button.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (Riiablo.game == null) return;
        boolean accepted = Riiablo.game.spendStatPoint(statType);
        com.riiablo.logger.LogManager.getLogger("CharacterPanel").info(
            "[STAT_POINT_UI] phase=request stat={} accepted={}", statType, accepted);
        // Single-player mutations are immediate. Multiplayer waits for the
        // authoritative result/snapshot before changing the visible balance.
        updateStatPointControls();
      }
    });
    statButtons[statType] = button;
    addActor(button);
  }

  private void updateStatPointControls() {
    int available = Riiablo.charData == null ? 0 : Riiablo.charData.getAvailableStatPoints();
    if (statPoints != null) statPoints.setText(Integer.toString(available));
    if (levelValue != null) {
      levelValue.setText(Integer.toString(
          Riiablo.charData.getStats().get(Stat.level).asInt()));
    }
    if (experienceValue != null) {
      setNumber(experienceValue, statLong(Stat.experience));
    }
    if (nextLevelValue != null) {
      int level = statInt(Stat.level);
      long threshold = level >= ExperienceTable.MAX_LEVEL
          ? statLong(Stat.experience)
          : ExperienceTable.getInstance().getExperienceForNextLevel(
              level, Riiablo.charData.classId.id);
      setNumber(nextLevelValue, threshold);
    }

    int[] damage = displayedPhysicalDamage();
    Color damageColor = displayedDamageColor();
    String damageText = damage[0] + "-" + damage[1];
    for (Label label : damageValues) {
      if (label == null) continue;
      setCompactText(label, damageText);
      label.setColor(damageColor);
    }

    String skillName = selectedSkillName();
    if (damageNames[0] != null) {
      damageNames[0].setText(combatLabel(4061, skillName, false));
    }
    if (damageNames[1] != null) {
      damageNames[1].setText(combatLabel(4061, skillName, true));
    }
    int baseAttackRating = displayedAttackRating();
    int[] buttons = {Input.Buttons.LEFT, Input.Buttons.RIGHT};
    for (int i = 0; i < buttons.length; i++) {
      Skills.Entry skill = selectedSkill(buttons[i]);
      Label name = attackRatingNames[i];
      Label value = attackRatingValues[i];
      if (!skillUsesAttackRating(skill)) {
        if (name != null) name.setText("");
        if (value != null) setCompactText(value, "");
        continue;
      }

      if (name != null) {
        name.setText(isNormalAttack(skill)
            ? combatLabel(4063, "", false)
            : combatLabel(4063, selectedSkillName(buttons[i]), true));
      }
      if (value != null) {
        int skillLevel = Math.max(1, Riiablo.charData.getSkill(skill.Id));
        setNumber(value, calculateSkillAttackRating(baseAttackRating, skill, skillLevel));
      }
    }
    for (Button button : statButtons) {
      if (button != null) button.setVisible(isVisible() && available > 0);
    }
  }

  private int statInt(short stat) {
    StatRef value = Riiablo.charData.getStats().get(stat, StatRef.obtain());
    return value == null ? 0 : value.asInt();
  }

  private long statLong(short stat) {
    StatRef value = Riiablo.charData.getStats().get(stat, StatRef.obtain());
    return value == null ? 0L : value.asLong();
  }

  private void setNumber(Label label, long value) {
    String text = NumberFormat.getInstance(Cvars.Client.Locale.get()).format(value);
    setCompactText(label, text);
  }

  private void setCompactText(Label label, String text) {
    BitmapFont font = getFont(text.length());
    if (label.getStyle().font != font) {
      label.getStyle().font = font;
      label.setStyle(label.getStyle());
    }
    label.setText(text);
  }

  int displayedAttackRating() {
    int dexterity = statInt(Stat.dexterity);
    int toHit = statInt(Stat.tohit);
    CharStats.Entry charStats = Riiablo.charData.classId.entry();
    int classFactor = charStats == null ? 0 : charStats.ToHitFactor;
    return calculateAttackRating(dexterity, toHit, classFactor);
  }

  static int calculateAttackRating(int dexterity, int toHit, int classFactor) {
    return Math.max(0, toHit + 5 * (dexterity - 7) + classFactor);
  }

  int[] displayedPhysicalDamage() {
    int minimum = statInt(Stat.mindamage);
    int maximum = statInt(Stat.maxdamage);
    int secondaryMinimum = statInt(Stat.secondary_mindamage);
    int secondaryMaximum = statInt(Stat.secondary_maxdamage);
    if (secondaryMinimum > 0 && secondaryMaximum > 0) {
      minimum = secondaryMinimum;
      maximum = secondaryMaximum;
    }
    if (minimum <= 0 && maximum <= 0) {
      minimum = 1;
      maximum = 2;
    }
    maximum = Math.max(minimum, maximum);

    int strengthBonus = 100;
    int dexterityBonus = 0;
    Item weapon = activeWeapon();
    if (weapon != null && weapon.base instanceof Weapons.Entry) {
      Weapons.Entry record = (Weapons.Entry) weapon.base;
      strengthBonus = record.StrBonus;
      dexterityBonus = record.DexBonus;
    }
    return calculateDamageRange(minimum, maximum, statInt(Stat.strength),
        statInt(Stat.dexterity), strengthBonus, dexterityBonus,
        statInt(Stat.damagepercent));
  }

  static int[] calculateDamageRange(int minimum, int maximum, int strength, int dexterity,
      int strengthBonus, int dexterityBonus, int damagePercent) {
    int bonus = damagePercent
        + strengthBonus * strength / 100
        + dexterityBonus * dexterity / 100;
    bonus = Math.max(-90, bonus);
    return new int[] {
        Math.max(0, minimum + minimum * bonus / 100),
        Math.max(0, maximum + maximum * bonus / 100)
    };
  }

  private Item activeWeapon() {
    Item right = Riiablo.charData.getItems().getEquipped(BodyLoc.RARM);
    if (right != null && right.base instanceof Weapons.Entry) return right;
    Item left = Riiablo.charData.getItems().getEquipped(BodyLoc.LARM);
    return left != null && left.base instanceof Weapons.Entry ? left : null;
  }

  private Color displayedDamageColor() {
    Attributes attrs = Riiablo.charData.getStats();
    if (hasPositiveDamage(attrs, Stat.poisonmindam, Stat.poisonmaxdam)) {
      return Riiablo.colors.green;
    }
    if (hasPositiveDamage(attrs, Stat.firemindam, Stat.firemaxdam)) {
      return Riiablo.colors.red;
    }
    if (hasPositiveDamage(attrs, Stat.coldmindam, Stat.coldmaxdam)) {
      return Riiablo.colors.blue;
    }
    if (hasPositiveDamage(attrs, Stat.lightmindam, Stat.lightmaxdam)) {
      return Riiablo.colors.yellow;
    }
    return Riiablo.colors.white;
  }

  private static boolean hasPositiveDamage(Attributes attrs, short minimum, short maximum) {
    return statValue(attrs.aggregate(), minimum) > 0
        || statValue(attrs.aggregate(), maximum) > 0
        || statValue(attrs.remaining(), minimum) > 0
        || statValue(attrs.remaining(), maximum) > 0;
  }

  private static int statValue(com.riiablo.attributes.StatListRef stats, short stat) {
    StatRef value = stats.get(stat, StatRef.obtain());
    return value == null ? 0 : value.asInt();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    updateStatPointControls();
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    // Refresh synchronously when the panel is opened. Waiting for the next
    // stage tick made newly awarded points appear to be missing.
    updateStatPointControls();
  }

  private BitmapFont getFont(int length) {
    if (length > 6) {
      return Riiablo.fonts.ReallyTheLastSucker;
    } else if (length > 3) {
      return Riiablo.fonts.font8;
    } else {
      return Riiablo.fonts.font16;
    }
  }

  private Color getColor(boolean mod, StatRef stat, int value) {
    if (value < 0) {
      return Riiablo.colors.red;
    }

    if (!stat.entry().maxstat.isEmpty()) {
      short id = Stat.index(stat.entry().maxstat);
      StatRef maxstat = Riiablo.charData.getStats().get(id);
      if (maxstat != null && value >= maxstat.asInt()) {
        return Riiablo.colors.gold;
      }
    }

    if (mod && Riiablo.charData.getStats().get(stat.id()).modified()) {
      return Riiablo.colors.blue;
    }

    return Riiablo.colors.white;
  }

  @Override
  public void dispose() {
    Riiablo.assets.unload(invcharDescriptor.fileName);
    Riiablo.assets.unload(buysellbtnDescriptor.fileName);
    Riiablo.assets.unload(levelButtonDescriptor.fileName);
    for (Button button : statButtons) {
      if (button != null) button.dispose();
    }
  }

  @Override
  public void draw(Batch batch, float a) {
    batch.draw(invchar, getX(), getY());
    super.draw(batch, a);
  }
}
