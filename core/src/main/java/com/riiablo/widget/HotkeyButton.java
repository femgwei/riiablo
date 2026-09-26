package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC;
import com.riiablo.codec.excel.Skills;
import com.riiablo.graphics.BlendMode;
import com.riiablo.key.MappedKey;
import com.riiablo.item.Item;
import com.riiablo.save.ItemData;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.skill.NativeSkillResolver;

public class HotkeyButton extends Button {
  MappedKey mapping;
  Label hotkey;
  Label charges;
  int skillId;
  StatRef chargedSkill;
  final SkillDetails details;
  private boolean lastDisabled;
  private boolean disabledInitialized;
  private String displayedQuantity;
  /** Quick-selection grids tint only native weapon/ammunition restrictions. */
  private boolean weaponRestrictionOnly;

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
    this.details = new SkillDetails(skillId, chargedSkill);
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
   * Uses the native quick-skill palette rule: missing required equipment is
   * red, while temporary cast failures such as town or low mana stay white.
   */
  public void setWeaponRestrictionOnly(boolean weaponRestrictionOnly) {
    this.weaponRestrictionOnly = weaponRestrictionOnly;
    refreshDisabled();
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
    String quantityText = chargedSkill != null ? Integer.toString(chargedSkill.value0()) : "";

    if (!disabled) {
      ItemData items = Riiablo.charData.getItems();
      Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
          ? Riiablo.files.skills.get(skillId) : null;

      if (chargedSkill == null) {
        Item rangedWeapon = items.getEquippedRangedWeapon();
        if (NativeSkillResolver.isAmazonBowSkill(skill) && rangedWeapon == null) {
          disabled = true;
          reason = "no_ranged_weapon";
        } else if (NativeSkillResolver.requiresRangedAmmo(skill, rangedWeapon)) {
          Item ammo = items.getEquippedAmmo(rangedWeapon);
          int value = itemQuantity(ammo);
          quantityText = Integer.toString(value);
          disabled = value <= 0;
          if (ammo == null) reason = "missing_ammo";
          else if (value <= 0) reason = "empty_quantity";
        } else if (!NativeSkillResolver.isAmazonBowSkill(skill)
            && NativeSkillResolver.requiresThrowableWeapon(skill)) {
          boolean javelinSkill = NativeSkillResolver.isAmazonJavelinSkill(skill);
          Item throwable = javelinSkill
              ? items.getEquippedJavelinWeapon()
              : items.getEquippedThrowableWeapon();
          int value = itemQuantity(throwable);
          if (throwable != null && NativeSkillResolver.isThrowableSkill(skill)) {
            quantityText = Integer.toString(value);
          }
          // Javelin-tree melee skills only require the active javelin/spear;
          // quantity is relevant to explicit throw skills, not Jab/Fend/etc.
          boolean emptyThrowStack = !javelinSkill && value <= 0;
          disabled = throwable == null || emptyThrowStack;
          if (throwable == null) reason = "no_throwable_weapon";
          else if (emptyThrowStack) reason = "empty_quantity";
        }
      }

      if (!weaponRestrictionOnly) {
        StatRef hp = Riiablo.charData.getStats().get(Stat.hitpoints);
        if (!disabled && hp != null && hp.asFixed() <= 0) {
          disabled = true;
          reason = "dead";
        }

        // Item charges replace the mana payment. Learned and system skills use
        // the same fixed-point cost comparison as the authoritative cast path.
        if (!disabled && chargedSkill == null) {
          int level = Math.max(1, SkillDetails.effectivePlayerSkillLevel(skillId));
          float manaCost = NativeSkillResolver.manaCost(skill, level);
          StatRef mana = Riiablo.charData.getStats().get(Stat.mana);
          float currentMana = mana == null ? 0f : mana.asFixed();
          if (!NativeSkillResolver.hasEnoughMana(currentMana, manaCost)) {
            disabled = true;
            reason = "insufficient_mana";
          }
        }

        // Town and mana are immediate cast-state restrictions. Native D2
        // applies them to the selected HUD action, not to every entry in the
        // expanded quick-skill grid.
        if (!disabled && isPlayerInTown()
            && !NativeSkillResolver.isAllowedInTown(skill)) {
          disabled = true;
          reason = "town";
        }
      }
    }

    updateQuantityText(quantityText);

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

  private static int itemQuantity(Item item) {
    if (item == null || item.attrs == null) return 0;
    StatRef quantity = item.attrs.base().get(Stat.quantity);
    return quantity == null ? 0 : Math.max(0, quantity.asInt());
  }

  private void updateQuantityText(String text) {
    if (text.equals(displayedQuantity)) return;
    charges.setText(text);
    displayedQuantity = text;
  }

  private static boolean isPlayerInTown() {
    if (Riiablo.game == null || Riiablo.engine == null || Riiablo.game.player < 0) return false;
    try {
      com.artemis.ComponentMapper<MapWrapper> mapper = Riiablo.engine.getMapper(MapWrapper.class);
      if (mapper == null || !mapper.has(Riiablo.game.player)) return false;
      MapWrapper wrapper = mapper.get(Riiablo.game.player);
      return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
    } catch (RuntimeException ignored) {
      // UI can be drawn during world teardown; retain the previous usable
      // state instead of making a transient mapper failure fatal.
      return false;
    }
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
    chargedSkill = other.chargedSkill;
    details.setSkill(skillId, chargedSkill);
    setDisabled(other.refreshDisabled());
  }

  @Override
  public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
    refreshDisabled();
    super.draw(batch, parentAlpha);
    if (skillId >= 0 && isOver() && Riiablo.game != null && getParent() != null) {
      details.refresh();
      Riiablo.game.setDetails(details, null, getParent(), this);
    }
  }
}
