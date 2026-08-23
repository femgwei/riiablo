package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC;
import com.riiablo.graphics.BlendMode;
import com.riiablo.key.MappedKey;
import com.riiablo.item.Item;
import com.riiablo.save.ItemData;
import com.riiablo.skill.SkillCodes;

public class HotkeyButton extends Button {
  MappedKey mapping;
  Label hotkey;
  Label charges;
  int skillId;
  StatRef chargedSkill;
  private boolean lastDisabled;
  private boolean disabledInitialized;

  public HotkeyButton(final DC dc, final int index, int skillId) {
    this(dc, index, skillId, null);
  }

  public HotkeyButton(final DC dc, final int index, int skillId, StatRef chargedSkill) {
    super(new ButtonStyle() {{
      up       = new TextureRegionDrawable(dc.getTexture(index));
      down     = new TextureRegionDrawable(dc.getTexture(index + 1));
      disabled = up;
      pressedOffsetX = pressedOffsetY = -2;
    }});

    this.skillId = skillId;
    this.chargedSkill = chargedSkill;
    add(hotkey = new Label("", Riiablo.fonts.font16, Riiablo.colors.gold)).align(Align.topRight);
    row();
    add().grow();
    row();
    add(charges = new Label(chargedSkill != null ? Integer.toString(chargedSkill.value0()) : "", Riiablo.fonts.font16, Riiablo.colors.blue)).align(Align.bottomLeft);
    pad(2);
    pack();

    setDisabledBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenRed);
    refreshDisabled();
  }

  public void map(MappedKey mapping) {
    this.mapping = mapping;
    hotkey.setText(Input.Keys.toString(mapping.getPrimaryAssignment()));
  }

  public MappedKey getMapping() {
    return mapping;
  }

  public int getSkill() {
    return skillId;
  }

  /**
   * Updates the red disabled overlay to match the current player state.
   *
   * <p>The original game evaluates this continuously because weapon swaps and
   * throwing-weapon quantity changes can make an otherwise learned skill
   * unusable.  Keeping the check on the button itself also covers the main HUD
   * observer and the quick-skill panel without requiring a second event bus.
   */
  public boolean refreshDisabled() {
    boolean disabled = skillId < 0 || Riiablo.charData == null;
    String reason = disabled ? (skillId < 0 ? "unassigned" : "no_char_data") : "";

    if (!disabled) {
      ItemData items = Riiablo.charData.getItems();
      if (skillId == SkillCodes.throw_ || skillId == SkillCodes.left_hand_throw) {
        Item throwable = items.getEquippedThrowableWeapon();
        StatRef quantity = throwable == null || throwable.attrs == null
            ? null : throwable.attrs.base().get(Stat.quantity);
        int value = quantity == null ? 0 : quantity.asInt();
        disabled = throwable == null || value <= 0;
        if (throwable == null) reason = "no_throwable_weapon";
        else if (value <= 0) reason = "empty_quantity";
      }

      StatRef hp = Riiablo.charData.getStats().get(Stat.hitpoints);
      if (!disabled && hp != null && hp.asFixed() <= 0) {
        disabled = true;
        reason = "dead";
      }
    }

    setDisabled(disabled);
    if (!disabledInitialized || disabled != lastDisabled) {
      com.riiablo.logger.LogManager.getLogger(HotkeyButton.class).info(
          "[SKILL_DISABLED] skill={} disabled={} reason={}", skillId, disabled,
          disabled ? reason : "usable");
      lastDisabled = disabled;
      disabledInitialized = true;
    }
    return disabled;
  }

  public void copy(HotkeyButton other) {
    if (other == null) {
      setDisabled(true);
      return;
    }
    setStyle(other.getStyle());
    setBlendMode(other.blendMode, other.color);
    setDisabledBlendMode(other.disabledBlendMode, other.disabledColor);
    setHighlightedBlendMode(other.highlightedBlendMode, other.highlightedColor);
    hotkey.setText(other.hotkey.getText());
    skillId = other.skillId;
    setDisabled(other.refreshDisabled());
  }

  @Override
  public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
    refreshDisabled();
    super.draw(batch, parentAlpha);
  }
}
