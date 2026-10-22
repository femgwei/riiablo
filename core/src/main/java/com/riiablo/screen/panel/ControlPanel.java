package com.riiablo.screen.panel;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.artemis.annotations.Wire;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;

import com.riiablo.CharacterClass;
import com.riiablo.Keys;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.ExperienceTable;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.key.MappedKey;
import com.riiablo.loader.DC6Loader;
import com.riiablo.map.RenderSystem;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.widget.Button;
import com.riiablo.widget.HotkeyButton;
import com.riiablo.widget.Label;
import com.riiablo.util.Disposal;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;

public class ControlPanel extends Table implements Disposable, EscapeController {
  private static final String TAG = "ControlPanel";
  private static final boolean DEBUG_MOBILE = !true;
  private final AssetManager assets = Riiablo.assets;
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final ItemData items = java.util.Objects.requireNonNull(character.getItems(), "items");
  private final com.riiablo.Cursor cursor = Riiablo.cursor;
  private final com.riiablo.audio.Audio audio = Riiablo.audio;
  private final com.riiablo.Client.InputProcessor input = Riiablo.input;
  private GameScreen owner;
  private com.artemis.World world;
  private boolean initializing = true;
  private boolean disposed;

  final AssetDescriptor<DC6> ctrlpnlDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\ctrlpnl7.DC6", DC6.class);
  HealthWidget healthWidget;
  ManaWidget manaWidget;
  StaminaWidget staminaWidget;
  ControlWidget controlWidget;

  final AssetDescriptor<DC6> hlthmanaDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\hlthmana.DC6", DC6.class);
  DC6 hlthmana;

  final AssetDescriptor<DC6> overlapDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\overlap.DC6", DC6.class);
  DC6 overlap;

  /** Native low-ammo/low-durability warning sprites used by the right HUD. */
  final AssetDescriptor<DC6> invwarnDescriptor =
      new AssetDescriptor<>("data\\global\\ui\\PANEL\\invwarn.dc6", DC6.class);
  DC6 invwarn;
  InventoryWarningWidget inventoryWarningWidget;
  private final Vector2 inventoryWarningPosition = new Vector2();

  ExperienceWidget experienceWidget;
  
  // 经验条尺寸（Stage 世界坐标）。位置由 controlWidget 的实时布局决定，
  // 不使用屏幕像素或构造时缓存的绝对坐标。
  private static final float EXP_BAR_WIDTH = 120f;
  // OpenDiablo2 uses a 120x4 widget and renders a 2px fill inside it.
  private static final int EXP_BAR_HEIGHT = 4;
  private static final float EXP_BAR_TOP_OFFSET = 11f;
  // The assembled control-panel texture has a small transparent/asymmetric
  // margin. Keep the bar geometrically centered, then apply this world-space
  // nudge so it aligns with the visible center of the panel at every scale.
  // The visible slot in ctrlpnl7 starts 10 Stage units to the right of the
  // assembled control widget's geometric center. This keeps the fill aligned
  // with the slot border rather than the transparent texture margin.
  private static final float EXP_BAR_CENTER_OFFSET_X = 21.5f;
  // The native 800-wide HUD places the stamina fill at x=273. Express that
  // from the HUD center so grouped and full-width panel layouts agree.
  private static final float STATUS_BAR_CENTER_OFFSET_X = 273f - 400f;
  private static final float STAMINA_BAR_BOTTOM_OFFSET = 9f;
  private static final float ADD_POINTS_LABEL_GAP = 24f;
  private static final float ADD_POINTS_VERTICAL_ADJUST = -10f;
  private static final float PROMPT_LABEL_GAP = 2f;
  private static final float QUEST_PROMPT_STEP_Y = 60f;

  final AssetDescriptor<DC6> levelButtonDescriptor =
      new AssetDescriptor<>("data\\global\\ui\\PANEL\\level.DC6", DC6.class);
  final AssetDescriptor<Sound> levelUpSoundDescriptor =
      new AssetDescriptor<>("data\\global\\sfx\\cursor\\levelup.wav", Sound.class);
  Button btnAddStats;
  Button btnAddSkills;
  Button btnQuestLog;
  Label addStatsLabel;
  Label addSkillsLabel;
  Label questLogLabel;
  Sound levelUpSound;
  private int observedLevel = Integer.MIN_VALUE;
  private long observedQuestRevision = Long.MIN_VALUE;
  private long acknowledgedQuestRevision;
  private boolean questLogAcknowledged;

  final AssetDescriptor<DC6> popbeltDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\ctrlpnl_popbelt.DC6", DC6.class);
  TextureRegion popbelt;

  final AssetDescriptor<DC6> SkilliconDescriptor = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\Skillicon.DC6", DC6.class);
  DC6 Skillicon;

  final AssetDescriptor<DC6> CharSkilliconDescriptor[] = new AssetDescriptor[7];
  DC6 CharSkillicon[];

  @Wire(name = "itemController")
  protected ItemController itemController;

  private static int getClassId(String charClass) {
    if (charClass.isEmpty()) return -1;
    switch (charClass.charAt(0)) {
      case 'a': return charClass.charAt(1) == 'm' ? CharacterClass.AMAZON.id : CharacterClass.ASSASSIN.id;
      case 'b': return CharacterClass.BARBARIAN.id;
      case 'd': return CharacterClass.DRUID.id;
      case 'n': return CharacterClass.NECROMANCER.id;
      case 'p': return CharacterClass.PALADIN.id;
      case 's': return CharacterClass.SORCERESS.id;
      default:  return -1;
    }
  }

  private DC getSkillicon(String charClass, int i) {
    int classId = getClassId(charClass);
    DC icons = classId == -1 ? Skillicon : CharSkillicon[classId];
    return i < icons.getNumPages() ? icons : null;
  }

  HotkeyButton leftSkill, rightSkill;

  public ControlPanel() { this(null); }

  public ControlPanel(GameScreen owner) {
    this.owner = owner;
    world = owner == null ? Riiablo.engine : null;
    try { initialize(); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    } finally { initializing = false; }
  }

  public void bindWorld(com.artemis.World world) {
    if (disposed) return;
    java.util.Objects.requireNonNull(world, "world");
    if (this.world != null && this.world != world) throw new IllegalStateException("world already bound");
    this.world = world;
  }

  private boolean isCurrentSession() {
    return !disposed && assets == Riiablo.assets && character == Riiablo.charData
        && items == character.getItems() && cursor == Riiablo.cursor && audio == Riiablo.audio
        && input == Riiablo.input && (world == null || world == Riiablo.engine)
        && (initializing || (owner != null ? owner == Riiablo.game
            : Riiablo.game == null || Riiablo.game.controlPanel == this));
  }

  private GameScreen game() { return owner != null ? owner : Riiablo.game; }

  private class SessionButton extends Button {
    private boolean retired;
    SessionButton(ButtonStyle style) { super(style); }
    @Override public boolean isDisabled() { return retired || !isCurrentSession() || super.isDisabled(); }
    @Override public void dispose() { retired = true; super.dispose(); }
    @Override public void draw(Batch batch, float alpha) {
      if (!retired && isCurrentSession()) super.draw(batch, alpha);
    }
    @Override public void draw(com.riiablo.graphics.PaletteIndexedBatch batch, float alpha) {
      if (!retired && isCurrentSession()) super.draw(batch, alpha);
    }
  }

  private class SessionHotkeyButton extends HotkeyButton {
    SessionHotkeyButton(DC icons, int index, int skill) { super(icons, index, skill); }
    @Override protected boolean isActive() { return super.isActive() && isCurrentSession(); }
  }

  @Override public Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }

  @Override public void draw(Batch batch, float alpha) {
    if (isCurrentSession()) super.draw(batch, alpha);
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    super.setVisible(visible);
  }

  private void initialize() {
    references.load(hlthmanaDescriptor);
    assets.finishLoadingAsset(hlthmanaDescriptor);
    hlthmana = assets.get(hlthmanaDescriptor);

    references.load(overlapDescriptor);
    assets.finishLoadingAsset(overlapDescriptor);
    overlap = assets.get(overlapDescriptor);

    references.load(invwarnDescriptor);
    assets.finishLoadingAsset(invwarnDescriptor);
    invwarn = assets.get(invwarnDescriptor);

    references.load(ctrlpnlDescriptor);
    assets.finishLoadingAsset(ctrlpnlDescriptor);
    DC6 ctrlpnl = assets.get(ctrlpnlDescriptor);

    references.load(levelButtonDescriptor);
    assets.finishLoadingAsset(levelButtonDescriptor);
    DC6 levelButton = assets.get(levelButtonDescriptor);
    Button.ButtonStyle addPointStyle = new Button.ButtonStyle(
        new TextureRegionDrawable(levelButton.getTexture(0)),
        new TextureRegionDrawable(levelButton.getTexture(1)));
    btnAddStats = new SessionButton(addPointStyle);
    btnAddStats.setVisible(false);
    btnAddStats.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        if (game() != null) game().setLeftPanel(game().characterPanel);
      }
    });
    btnAddSkills = new SessionButton(addPointStyle);
    btnAddSkills.setVisible(false);
    btnAddSkills.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        if (game() != null) game().setRightPanel(game().spellsPanel);
      }
    });
    btnQuestLog = new SessionButton(new Button.ButtonStyle(addPointStyle));
    btnQuestLog.setVisible(false);
    btnQuestLog.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        if (game() != null) {
          game().setLeftPanel(game().questsPanel);
        }
        acknowledgeQuestLog();
      }
    });
    addStatsLabel = createPromptLabel(Riiablo.bundle.get("new_stats"));
    addSkillsLabel = createPromptLabel(Riiablo.bundle.get("new_skill"));
    questLogLabel = createPromptLabel(Riiablo.bundle.get("quest_log"));

    references.load(levelUpSoundDescriptor);
    assets.finishLoadingAsset(levelUpSoundDescriptor);
    levelUpSound = assets.get(levelUpSoundDescriptor);

    references.load(popbeltDescriptor);
    assets.finishLoadingAsset(popbeltDescriptor);
    popbelt = assets.get(popbeltDescriptor).getTexture();

    references.load(SkilliconDescriptor);
    assets.finishLoadingAsset(SkilliconDescriptor);
    Skillicon = assets.get(SkilliconDescriptor);

    CharSkillicon = new DC6[CharSkilliconDescriptor.length];
    for (int i = 0; i < CharSkilliconDescriptor.length; i++) {
      CharSkilliconDescriptor[i] = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\" + CharacterClass.get(i).spellIcons + ".DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
      references.load(CharSkilliconDescriptor[i]);
      assets.finishLoadingAsset(CharSkilliconDescriptor[i]);
      CharSkillicon[i] = assets.get(CharSkilliconDescriptor[i]);
    }

    final int numFrames = ctrlpnl.getNumFramesPerDir();
    healthWidget = new HealthWidget(ctrlpnl.getTexture(0));
    manaWidget = new ManaWidget(ctrlpnl.getTexture(numFrames - 2));
    staminaWidget = new StaminaWidget();

    // Create experience widget (无纹理依赖，参考 OpenDiablo2)
    experienceWidget = new ExperienceWidget();

    if (!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop) {
      int leftSkillId = character.getAction(Input.Buttons.LEFT);
      // Skill id 0 is the valid D2 normal attack, not an unassigned slot.
      if (leftSkillId >= 0) {
        final Skills.Entry skill = Riiablo.files.skills.get(leftSkillId);
        final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
        int iconCel = desc.IconCel;
        DC icons = getSkillicon(skill.charclass, iconCel);
        if (icons == null) {
          icons = Skillicon;
          iconCel = 20;
        }

        leftSkill = new SessionHotkeyButton(icons, iconCel, skill.Id);
        if (skill.aura) leftSkill.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
        int index = character.getHotkey(Input.Buttons.LEFT, leftSkillId);
        if (index != ArrayUtils.INDEX_NOT_FOUND) {
          MappedKey mapping = Keys.Skill[index];
          leftSkill.map(mapping);
        }
      } else {
        leftSkill = new SessionHotkeyButton(Skillicon, 0, -1);
      }
      leftSkill.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession() || game() == null) return;
          game().spellsQuickPanelL.setVisible(!game().spellsQuickPanelL.isVisible());
        }
      });

      int rightSkillId = character.getAction(Input.Buttons.RIGHT);
      // Skill id 0 is the valid D2 normal attack, not an unassigned slot.
      if (rightSkillId >= 0) {
        final Skills.Entry skill = Riiablo.files.skills.get(rightSkillId);
        final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
        int iconCel = desc.IconCel;
        DC icons = getSkillicon(skill.charclass, iconCel);
        if (icons == null) {
          icons = Skillicon;
          iconCel = 20;
        }

        rightSkill = new SessionHotkeyButton(icons, iconCel, skill.Id);
        if (skill.aura) rightSkill.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
        int index = character.getHotkey(Input.Buttons.RIGHT, rightSkillId);
        if (index != ArrayUtils.INDEX_NOT_FOUND) {
          MappedKey mapping = Keys.Skill[index];
          rightSkill.map(mapping);
        }
      } else {
        rightSkill = new SessionHotkeyButton(Skillicon, 0, -1);
      }
      rightSkill.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession() || game() == null) return;
          game().spellsQuickPanelR.setVisible(!game().spellsQuickPanelR.isVisible());
        }
      });

      controlWidget = new ControlWidget(PanelTextures.controlBackground(ctrlpnl));
    }

    final float height = controlWidget == null ? 0 : controlWidget.background.getHeight() - 7;
    add(healthWidget).height(height).growX().left().bottom();
    if (leftSkill != null) add(leftSkill).bottom();
    if (controlWidget != null) add(controlWidget).size(controlWidget.background.getWidth(), height).bottom();
    if (rightSkill != null) add(rightSkill).bottom();
    add(manaWidget).height(height).growX().right().bottom();
    pack();
    
    experienceWidget.setWidth(EXP_BAR_WIDTH);
    experienceWidget.setHeight(EXP_BAR_HEIGHT);
    addActor(experienceWidget); // 直接添加到面板，但位置跟随 controlWidget 布局
    addActor(staminaWidget);
    addActor(btnAddStats);
    addActor(btnAddSkills);
    addActor(btnQuestLog);
    addActor(addStatsLabel);
    addActor(addSkillsLabel);
    addActor(questLogLabel);
    inventoryWarningWidget = new InventoryWarningWidget(invwarn);
    addActor(inventoryWarningWidget);
    updateExperienceWidgetLayout();
    updateStaminaWidgetLayout();
    updateAddPointButtonLayout();
    updateInventoryWarningWidgetLayout();

    //setHeight(controlWidget.background.getHeight() - 7);
    //setY(0);
    setTouchable(Touchable.childrenOnly);
    //setDebug(true, true);
  }

  /** Re-anchor the experience bar after Table layout, width changes, or resize. */
  private void updateExperienceWidgetLayout() {
    if (experienceWidget == null) return;

    final float barWidth = experienceWidget.getWidth() > 0
        ? experienceWidget.getWidth() : EXP_BAR_WIDTH;
    final float anchorX;
    final float anchorY;
    if (controlWidget != null) {
      // Center the complete bar over the actual control widget. The previous
      // -38 offset left the bar visibly shifted and was calculated only once.
      anchorX = controlWidget.getX() + (controlWidget.getWidth() - barWidth) / 2f
          + EXP_BAR_CENTER_OFFSET_X;
      anchorY = controlWidget.getY() + controlWidget.getHeight() - EXP_BAR_TOP_OFFSET;
    } else {
      anchorX = (getWidth() - barWidth) / 2f + EXP_BAR_CENTER_OFFSET_X;
      anchorY = getHeight() - EXP_BAR_TOP_OFFSET;
    }
    experienceWidget.setPosition(anchorX, anchorY);
  }

  /** Keeps the stamina fill left-aligned with the experience bar. */
  private void updateStaminaWidgetLayout() {
    if (staminaWidget == null) return;
    float anchorX = getWidth() / 2f + STATUS_BAR_CENTER_OFFSET_X;
    float anchorY;
    if (controlWidget != null) {
      anchorY = controlWidget.getY() + STAMINA_BAR_BOTTOM_OFFSET;
    } else {
      anchorY = STAMINA_BAR_BOTTOM_OFFSET;
    }
    staminaWidget.setPosition(anchorX, anchorY);
  }

  /**
   * Position warning sprites in screen space rather than relative to the
   * bottom HUD. The quantity icon is centered on the horizontal screen
   * midpoint: quantity starts 10 pixels above it, durability ends 10 pixels
   * below it, and both icons keep a 20-pixel inset from the right edge.
   */
  private void updateInventoryWarningWidgetLayout() {
    if (inventoryWarningWidget == null || getStage() == null) return;
    inventoryWarningPosition.set(
        InventoryWarning.rightInsetX(
            getStage().getWidth(), inventoryWarningWidget.getWidth(),
            InventoryWarningWidget.RIGHT_INSET),
        InventoryWarning.screenSlotY(
            InventoryWarning.Kind.DURABILITY,
            getStage().getHeight(),
            InventoryWarningWidget.ICON_HEIGHT,
            InventoryWarningWidget.CENTER_OFFSET));
    stageToLocalCoordinates(inventoryWarningPosition);
    inventoryWarningWidget.setPosition(
        inventoryWarningPosition.x, inventoryWarningPosition.y);
  }

  private void updateAddPointButtonLayout() {
    if (btnAddStats == null || btnAddSkills == null || btnQuestLog == null
        || healthWidget == null || manaWidget == null) return;
    btnAddStats.setPosition(
        healthWidget.getX()
            + (healthWidget.background.getRegionWidth() - btnAddStats.getWidth()) / 2f,
        healthWidget.getY() + healthWidget.background.getRegionHeight()
            + healthWidget.label.getPrefHeight() + ADD_POINTS_LABEL_GAP
            + ADD_POINTS_VERTICAL_ADJUST);
    btnAddSkills.setPosition(
        manaWidget.getX()
            + (manaWidget.background.getRegionWidth() - btnAddSkills.getWidth()) / 2f,
        manaWidget.getY() + manaWidget.background.getRegionHeight()
            + manaWidget.label.getPrefHeight() + ADD_POINTS_LABEL_GAP
            + ADD_POINTS_VERTICAL_ADJUST);
    btnQuestLog.setPosition(btnAddStats.getX(), btnAddStats.getY() + QUEST_PROMPT_STEP_Y);
    positionPromptLabel(addStatsLabel, btnAddStats);
    positionPromptLabel(addSkillsLabel, btnAddSkills);
    positionPromptLabel(questLogLabel, btnQuestLog);
  }

  private static Label createPromptLabel(String text) {
    Label label = new Label(text, Riiablo.fonts.font16, Riiablo.colors.white);
    label.setSize(label.getPrefWidth(), label.getPrefHeight());
    label.setAlignment(Align.center);
    label.setTouchable(Touchable.disabled);
    label.setVisible(false);
    return label;
  }

  private static void positionPromptLabel(Label label, Button button) {
    if (label == null || button == null) return;
    label.setPosition(
        button.getX() + (button.getWidth() - label.getWidth()) / 2f,
        button.getY() + button.getHeight() + PROMPT_LABEL_GAP);
  }

  private void setQuestPromptVisible(boolean visible) {
    btnQuestLog.setVisible(visible);
    questLogLabel.setVisible(visible);
  }

  private boolean hasQuestProgress() {
    if (character == null) return false;
    for (int act = 0; act < Riiablo.NUM_ACTS; act++) {
      short[] quests = character.getQuests(act);
      if (quests == null) continue;
      for (short quest : quests) {
        if (quest != 0 && !NativeQuestRecord.has(quest, NativeQuestRecord.UPDATE_QUEST_LOG)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Acknowledges the displayed revision; only the local Tick owner persists its bit. */
  public void acknowledgeQuestLog() {
    if (!isCurrentSession()) return;
    if (character == null) return;
    long revision = currentQuestRevision();
    if (!questLogAcknowledged || acknowledgedQuestRevision != revision) {
      acknowledgedQuestRevision = revision;
      questLogAcknowledged = true;
      com.riiablo.engine.client.ClientQuestIntentSystem intents = world == null ? null
          : world.getSystem(com.riiablo.engine.client.ClientQuestIntentSystem.class);
      if (intents != null && game() != null) intents.acknowledgeLog(game().player);
    }
    setQuestPromptVisible(false);
  }

  private long currentQuestRevision() {
    long revision = 1469598103934665603L;
    if (character == null) return revision;
    int index = 0;
    for (int act = 0; act < Riiablo.NUM_ACTS; act++) {
      short[] quests = character.getQuests(act);
      if (quests == null) continue;
      for (short quest : quests) {
        revision ^= (quest & 0xFFFFL & ~(1L << NativeQuestRecord.UPDATE_QUEST_LOG)) + ((long) index++ << 16);
        revision *= 1099511628211L;
      }
    }
    return revision;
  }

  @Override
  public void layout() {
    super.layout();
    updateExperienceWidgetLayout();
    updateStaminaWidgetLayout();
    updateAddPointButtonLayout();
    updateInventoryWarningWidgetLayout();
  }

  @Override
  public void act(float delta) {
    if (!isCurrentSession()) return;
    super.act(delta);
    if (inventoryWarningWidget != null) {
      updateInventoryWarningWidgetLayout();
      inventoryWarningWidget.refresh();
    }
    if (character == null || character.getStats() == null) return;
    StatListRef stats = character.getStats().aggregate();
    int level = stats.getValue(Stat.level, character.level & 0xFF);
    if (observedLevel != Integer.MIN_VALUE && level > observedLevel) {
      levelUpSound.play();
      Gdx.app.log(TAG, "[LEVEL_UP_FEEDBACK] level=" + observedLevel + "->" + level
          + " sound=cursor_level_up");
    }
    observedLevel = level;
    boolean showStats = stats.getValue(Stat.statpts, 0) > 0;
    boolean showSkills = stats.getValue(Stat.newskills, 0) > 0;
    btnAddStats.setVisible(showStats);
    addStatsLabel.setVisible(showStats);
    btnAddSkills.setVisible(showSkills);
    addSkillsLabel.setVisible(showSkills);

    long questRevision = currentQuestRevision();
    if (observedQuestRevision == Long.MIN_VALUE) {
      observedQuestRevision = questRevision;
      setQuestPromptVisible(hasQuestProgress() && (!questLogAcknowledged || acknowledgedQuestRevision != questRevision));
    } else if (questRevision != observedQuestRevision) {
      observedQuestRevision = questRevision;
      setQuestPromptVisible(hasQuestProgress() && (!questLogAcknowledged || acknowledgedQuestRevision != questRevision));
    }
    if (game() != null && game().questsPanel != null
        && game().questsPanel.isVisible()) {
      acknowledgeQuestLog();
    }
  }

  @Override
  public Button getEscapeButton() {
    return isCurrentSession() && controlWidget != null && controlWidget.minipanelWidget != null
        ? controlWidget.minipanelWidget.btnEscapeMenu : null;
  }

  public void setMinipanelVisible(boolean b) {
    if (isCurrentSession() && controlWidget != null) controlWidget.setMinipanelVisible(b);
  }

  public HotkeyButton getLeftSkill() {
    return leftSkill;
  }

  public HotkeyButton getRightSkill() {
    return rightSkill;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(() -> Button.disposeClickSounds(this));
      cleanup.dispose(btnAddStats);
      cleanup.dispose(btnAddSkills);
      cleanup.dispose(btnQuestLog);
      cleanup.dispose(leftSkill);
      cleanup.dispose(rightSkill);
      if (experienceWidget != null) cleanup.run(experienceWidget::dispose);
      cleanup.dispose(inventoryWarningWidget);
      cleanup.dispose(controlWidget);
      // Some controls may not yet have been attached when construction fails.
      for (Actor actor : new Actor[] {btnAddStats, btnAddSkills, btnQuestLog, leftSkill,
          rightSkill, addStatsLabel, addSkillsLabel, questLogLabel, healthWidget, manaWidget,
          staminaWidget, experienceWidget, inventoryWarningWidget}) {
        cleanup.run(() -> ActorTree.clear(actor));
      }
      if (healthWidget != null) cleanup.run(() -> ActorTree.clear(healthWidget.label));
      if (manaWidget != null) cleanup.run(() -> ActorTree.clear(manaWidget.label));
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.dispose(references);
    } finally {
      owner = null; world = null; itemController = null; levelUpSound = null;
      hlthmana = overlap = invwarn = Skillicon = null; popbelt = null; CharSkillicon = null;
      leftSkill = rightSkill = null; btnAddStats = btnAddSkills = btnQuestLog = null;
      addStatsLabel = addSkillsLabel = questLogLabel = null;
      healthWidget = null; manaWidget = null; staminaWidget = null; controlWidget = null;
      experienceWidget = null; inventoryWarningWidget = null;
    }
  }

  private class HealthWidget extends Actor {
    TextureRegion background;
    TextureRegion health;
    TextureRegion overlay;
    Label label;
    private final TextureRegion tempHealth = new TextureRegion(); // 用于裁剪的临时区域

    HealthWidget(TextureRegion background) {
      this.background = background;
      //setSize(background.getRegionWidth(), background.getRegionHeight());
      setWidth(background.getRegionWidth());
      health = hlthmana.getTexture(0);
      overlay = overlap.getTexture(0);
      setTouchable(Touchable.enabled);
      label = new Label(Riiablo.fonts.font16);
      label.setY(background.getRegionHeight());
      label.setVisible(!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop);
    }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      final float x = getX();
      final float y = getY();
      batch.draw(background, x, y);
      
      // 计算 HP 比例并裁剪血条高度（暗黑2中血条是垂直的，从底部向上填充，减少时从顶部裁剪）
      float currentHP = character.getStats().get(Stat.hitpoints).asFixed();
      float maxHP = character.getStats().get(Stat.maxhp).asFixed();
      float hpRatio = maxHP > 0 ? Math.max(0, Math.min(1, currentHP / maxHP)) : 0;
      
      int fullHeight = health.getRegionHeight();
      int healthHeight = (int)(fullHeight * hpRatio);
      if (healthHeight > 0) {
        // 使用临时区域裁剪血条（从底部开始，向上填充，减少时从顶部裁剪）
        tempHealth.setRegion(health);
        int startY = fullHeight - healthHeight; // 从底部开始计算起始位置
        tempHealth.setRegionY(startY); // 裁剪起始位置（从顶部裁剪掉的部分）
        tempHealth.setRegionHeight(healthHeight); // 保留的高度
        // 绘制位置保持不变（y + 14），使血条底部对齐
        batch.draw(tempHealth, x + 30, y + 14);
      }
      
      batch.draw(overlay, x + 28, y +  6);
      super.draw(batch, a);
      if (label.isVisible() && !hasVisibleSidePanel()) {
        label.setX(getX());
        label.setText(Riiablo.string.format("panelhealth",
            (int) currentHP,
            (int) maxHP));
        label.draw(batch, a);
      }
    }
  }
  private class ManaWidget extends Actor {
    TextureRegion background;
    TextureRegion mana;
    TextureRegion overlay;
    Label label;
    private final TextureRegion tempMana = new TextureRegion(); // 用于裁剪的临时区域

    ManaWidget(TextureRegion background) {
      this.background = background;
      //setSize(background.getRegionWidth(), background.getRegionHeight());
      setWidth(background.getRegionWidth());
      mana = hlthmana.getTexture(1);
      overlay = overlap.getTexture(1);
      setTouchable(Touchable.enabled);
      label = new Label(Riiablo.fonts.font16);
      label.setY(background.getRegionHeight());
      label.setVisible(!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop);
    }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      final float x = getX();
      final float y = getY();
      batch.draw(background, x, y);
      
      // 计算 MP 比例并裁剪法力条高度（暗黑2中法力条是垂直的，从底部向上填充，减少时从顶部裁剪）
      float currentMana = character.getStats().get(Stat.mana).asFixed();
      float maxMana = character.getStats().get(Stat.maxmana).asFixed();
      float manaRatio = maxMana > 0 ? Math.max(0, Math.min(1, currentMana / maxMana)) : 0;
      
      int fullHeight = mana.getRegionHeight();
      int manaHeight = (int)(fullHeight * manaRatio);
      if (manaHeight > 0) {
        // 使用临时区域裁剪法力条（从底部开始，向上填充，减少时从顶部裁剪）
        tempMana.setRegion(mana);
        int startY = fullHeight - manaHeight; // 从底部开始计算起始位置
        tempMana.setRegionY(startY); // 裁剪起始位置（从顶部裁剪掉的部分）
        tempMana.setRegionHeight(manaHeight); // 保留的高度
        // 绘制位置保持不变（y + 14），使法力条底部对齐
        batch.draw(tempMana, x + 8, y + 14);
      }
      
      batch.draw(overlay, x + 8, y + 10);
      super.draw(batch, a);
      if (label.isVisible() && !hasVisibleSidePanel()) {
        label.setX(getX() - 32);
        label.setText(Riiablo.string.format("panelmana",
            (int) currentMana,
            (int) maxMana));
        label.draw(batch, a);
      }
    }
  }

  /** Native-size stamina fill drawn over the control panel's built-in slot. */
  private class StaminaWidget extends Actor {
    private static final float WIDTH = 102f;
    private static final float HEIGHT = 19f;
    private static final float LOW_STAMINA_RATIO = 0.25f;
    private final PaletteIndexedColorDrawable normalFill =
        new PaletteIndexedColorDrawable(new Color(0xAF8848C8));
    private final PaletteIndexedColorDrawable lowFill =
        new PaletteIndexedColorDrawable(new Color(0xFF0000C8));
    /** Native stambarblue presentation used by the stamina shrine state. */
    private final PaletteIndexedColorDrawable shrineFill =
        new PaletteIndexedColorDrawable(new Color(0x6969FFC8));

    StaminaWidget() {
      setSize(WIDTH, HEIGHT);
      setTouchable(Touchable.disabled);
    }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      if (character == null || character.getStats() == null) return;
      StatListRef stats = character.getStats().aggregate();
      float current = stats.getValue(Stat.stamina, 0f);
      float maximum = stats.getValue(Stat.maxstamina, 0f);
      float ratio = maximum > 0f ? Math.max(0f, Math.min(1f, current / maximum)) : 0f;
      float x = getX();
      float y = getY();
      int fillPixels = Math.round(WIDTH * ratio);
      if (fillPixels > 0) {
        PaletteIndexedColorDrawable fill = hasStaminaShrine()
            ? shrineFill : ratio < LOW_STAMINA_RATIO ? lowFill : normalFill;
        fill.setPercent(fillPixels / WIDTH);
        fill.draw(batch, x, y, WIDTH, HEIGHT);
      }
    }

    private boolean hasStaminaShrine() {
      if (world == null || game() == null || game().player < 0) {
        return false;
      }
      UnitStates states = world.getMapper(UnitStates.class)
          .get(game().player);
      return states != null && states.stateList != null
          && states.stateList.hasState(StateId.SHRINE_STAMINA);
    }
  }

  private boolean hasVisibleSidePanel() {
    return game() != null && game().hasVisibleSidePanel();
  }

  /**
   * Renders the native invwarn sprites above the mana globe.  The original
   * client exposes one quantity warning and one durability warning at a time;
   * when several equipped items qualify, the most severe item in each class is
   * selected before drawing.
   */
  private class InventoryWarningWidget extends Actor implements Disposable {
    private static final float RIGHT_INSET = 20f;
    private static final float CENTER_OFFSET = 10f;
    private static final float GAP = CENTER_OFFSET * 2f;
    private static final float ICON_WIDTH = 40f;
    private static final float ICON_HEIGHT = 41f;

    private final DC6 sprites;
    private final List<InventoryWarning.Entry> entries = new ArrayList<>();

    InventoryWarningWidget(DC6 sprites) {
      this.sprites = sprites;
      setSize(ICON_WIDTH, ICON_HEIGHT * 2f + GAP);
      setTouchable(Touchable.disabled);
      setVisible(false);
    }

    void refresh() {
      if (!isCurrentSession()) return;
      entries.clear();
      if (character == null || character.getItems() == null) {
        setVisible(false);
        return;
      }

      InventoryWarning.Entry quantity = null;
      InventoryWarning.Entry[] durabilityByGroup =
          new InventoryWarning.Entry[InventoryWarning.FRAME_GROUPS];
      for (InventoryWarning.Entry entry : InventoryWarning.collect(character.getItems())) {
        if (entry.kind == InventoryWarning.Kind.QUANTITY) {
          if (isMoreSevere(entry, quantity)) quantity = entry;
        } else if (isMoreSevere(entry, durabilityByGroup[entry.group])) {
          durabilityByGroup[entry.group] = entry;
        }
      }
      // Quantity uses the upper slot. Durability parts share the lower slot
      // and overlay into the native equipment-status figure.
      if (quantity != null) entries.add(quantity);
      for (InventoryWarning.Entry durability : durabilityByGroup) {
        if (durability != null) entries.add(durability);
      }
      setVisible(!entries.isEmpty());
    }

    private boolean isMoreSevere(InventoryWarning.Entry candidate,
        InventoryWarning.Entry current) {
      if (current == null) return true;
      int candidateSeverity = InventoryWarning.severity(
          candidate.current, candidate.threshold, candidate.threshold);
      int currentSeverity = InventoryWarning.severity(
          current.current, current.threshold, current.threshold);
      if (candidateSeverity != currentSeverity) return candidateSeverity > currentSeverity;
      long candidateRatio = (long) candidate.current * Math.max(1, current.threshold);
      long currentRatio = (long) current.current * Math.max(1, candidate.threshold);
      return candidateRatio < currentRatio;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      if (!isCurrentSession() || sprites == null || entries.isEmpty()) return;
      for (int i = 0; i < entries.size(); i++) {
        InventoryWarning.Entry entry = entries.get(i);
        TextureRegion icon = sprites.getTexture(entry.frame);
        if (icon != null) {
          batch.draw(icon, getX(), getY()
              + InventoryWarning.slotOffsetY(entry.kind, ICON_HEIGHT, GAP));
        }
      }
    }

    @Override
    public void dispose() {
      // The asset manager owns the DC6 texture and unloads it from ControlPanel.dispose().
      entries.clear();
    }
  }

  private class ExperienceWidget extends Actor {
    // OpenDiablo2 renders a 2 Stage-unit high fill in a 4-unit widget.
    private static final int EXP_BAR_FILL_HEIGHT = 2;

    // 1x1白色像素纹理用于填充
    private final TextureRegion whitePixel;
    private final Texture whitePixelTexture;
    private boolean retired;
    private long lastLoggedExperience = Long.MIN_VALUE;
    private int lastLoggedLevel = Integer.MIN_VALUE;
    private long lastLoggedCurrentLevelExp = Long.MIN_VALUE;
    private long lastLoggedNextLevelExp = Long.MIN_VALUE;
    private boolean missingStatsLogged;
    private float lastLayoutX = Float.NaN;
    private float lastLayoutY = Float.NaN;
    private float lastLayoutWidth = Float.NaN;
    private float lastLayoutHeight = Float.NaN;

    ExperienceWidget() {
      setHeight(EXP_BAR_HEIGHT);
      // 宽度和位置由外部设置

      // 创建1x1白色像素纹理
      whitePixelTexture = PanelTextures.solid(Color.WHITE);
      try { whitePixel = new TextureRegion(whitePixelTexture); }
      catch (RuntimeException | Error failure) {
        try { whitePixelTexture.dispose(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        throw failure;
      }
    }

    void dispose() {
      if (retired) return;
      retired = true;
      whitePixelTexture.dispose();
    }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      if (retired) return;
      // Table children can move when the panel is regrouped or the viewport is
      // resized. Re-anchor immediately before drawing as a final safeguard.
      updateExperienceWidgetLayout();
      final float x = getX();
      final float y = getY();

      if (Float.isNaN(lastLayoutX) || Math.abs(lastLayoutX - x) > 0.5f
          || Math.abs(lastLayoutY - y) > 0.5f
          || Math.abs(lastLayoutWidth - getWidth()) > 0.5f
          || Math.abs(lastLayoutHeight - getHeight()) > 0.5f) {
        final String controlRect = controlWidget == null
            ? "none"
            : String.format("(%.1f,%.1f %.1fx%.1f)", controlWidget.getX(),
                controlWidget.getY(), controlWidget.getWidth(), controlWidget.getHeight());
        final com.badlogic.gdx.scenes.scene2d.Stage stage = getStage();
        final String stageSize = stage == null
            ? "none"
            : String.format("(%.1fx%.1f)", stage.getWidth(), stage.getHeight());
        Gdx.app.log(TAG, String.format(
            "[XP_BAR_LAYOUT] bar=(%.1f,%.1f %.1fx%.1f) control=%s panel=(%.1f,%.1f %.1fx%.1f) stage=%s",
            x, y, getWidth(), getHeight(), controlRect,
            ControlPanel.this.getX(), ControlPanel.this.getY(),
            ControlPanel.this.getWidth(), ControlPanel.this.getHeight(), stageSize));
        lastLayoutX = x;
        lastLayoutY = y;
        lastLayoutWidth = getWidth();
        lastLayoutHeight = getHeight();
      }

      // 计算经验百分比。经验条没有自己的计数器，始终消费 CharData 的
      // aggregate（服务端同步也会更新该列表），因此不会与战斗经验分叉。
      if (character == null || character.getStats() == null) {
        if (!missingStatsLogged) {
          Gdx.app.log(TAG, "[XP_BAR] character stats unavailable; drawing empty bar");
          missingStatsLogged = true;
        }
        batch.setColor(Color.WHITE);
        return;
      }
      StatListRef aggregate = character.getStats().aggregate();
      StatListRef base = character.getStats().base();
      long currentExp = aggregate.getValue(Stat.experience,
          base.getValue(Stat.experience, 0L));
      int currentLevel = aggregate.getValue(Stat.level,
          character.level & 0xFF);
      currentLevel = Math.max(1, Math.min(ExperienceTable.MAX_LEVEL, currentLevel));
      int charClass = character.charClass & 0xFF;

      // 获取当前等级和下一等级所需经验
      long nextLevelExp = getNextLevelExperience(currentLevel, charClass);
      long currentLevelExp = getCurrentLevelExperience(currentLevel, charClass);

      // 计算百分比（0.0 到 1.0）
      float percentage = ExperienceTable.getInstance().getProgress(
          currentLevel, charClass, currentExp);

      if (currentExp != lastLoggedExperience || currentLevel != lastLoggedLevel
          || currentLevelExp != lastLoggedCurrentLevelExp
          || nextLevelExp != lastLoggedNextLevelExp) {
        Gdx.app.log(TAG, String.format(
            "[XP_BAR] level=%d experience=%d range=[%d,%d] progress=%.4f",
            currentLevel, currentExp, currentLevelExp, nextLevelExp, percentage));
        lastLoggedExperience = currentExp;
        lastLoggedLevel = currentLevel;
        lastLoggedCurrentLevelExp = currentLevelExp;
        lastLoggedNextLevelExp = nextLevelExp;
      }

      // OpenDiablo2 renders only the filled portion. The panel texture supplies
      // the dark/ornamental area underneath, so drawing an extra border here
      // makes the bar look like a detached rectangle and becomes too faint
      // after viewport scaling.
      if (percentage > 0) {
        float fillWidth = Math.min(getWidth(), Math.max(0f, getWidth() * percentage));
        batch.setColor(Color.WHITE);
        batch.draw(whitePixel, x, y, fillWidth, EXP_BAR_FILL_HEIGHT);
      }

      // 重置颜色
      batch.setColor(Color.WHITE);
    }

    private long getNextLevelExperience(int level, int charClass) {
      return ExperienceTable.getInstance().getExperienceForNextLevel(level, charClass);
    }

    private long getCurrentLevelExperience(int level, int charClass) {
      return ExperienceTable.getInstance().getExperienceForCurrentLevel(level, charClass);
    }
  }

  private class ControlWidget extends WidgetGroup implements Disposable, ItemGrid.GridListener {
    private final AssetReferenceScope references = new AssetReferenceScope(assets);
    private boolean retired;
    private static final float RUN_BUTTON_X = 10f;
    private static final float RUN_BUTTON_Y = 10f;

    final AssetDescriptor<DC6> runButtonDescriptor =
        new AssetDescriptor<>("data\\global\\ui\\PANEL\\runbutton.dc6", DC6.class);
    Button btnRun;
    Button.ButtonStyle walkStyle, runStyle;

    final AssetDescriptor<DC6> menubuttonDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\menubutton.DC6", DC6.class);
    Button btnMenu;
    Button.ButtonStyle menuHidden, menuShown;

    final AssetDescriptor<DC6> minipanelDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\minipanel.dc6", DC6.class);
    MinipanelWidget minipanelWidget;

    Texture background;
    BeltGrid belt;

    ControlWidget(Texture background) {
      this.background = background;
      try { initialize(); }
      catch (RuntimeException | Error failure) {
        try { dispose(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        throw failure;
      }
    }

    private void initialize() {
      setSize(background.getWidth(), background.getHeight() - 7);
      setTouchable(Touchable.enabled);

      references.load(runButtonDescriptor);
      assets.finishLoadingAsset(runButtonDescriptor);
      DC6 runButton = assets.get(runButtonDescriptor);
      walkStyle = new Button.ButtonStyle(
          new TextureRegionDrawable(runButton.getTexture(0)),
          new TextureRegionDrawable(runButton.getTexture(1)));
      runStyle = new Button.ButtonStyle(
          new TextureRegionDrawable(runButton.getTexture(2)),
          new TextureRegionDrawable(runButton.getTexture(3)));
      btnRun = new SessionButton(walkStyle);
      // The centered 640-wide panel maps this widget's left edge to native x=245;
      // runbutton's native x=255 therefore becomes child-local x=10.
      btnRun.setPosition(RUN_BUTTON_X, RUN_BUTTON_Y);
      btnRun.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (retired || !isCurrentSession()) return;
          if (game() != null) game().toggleRunWalk();
        }
      });
      addActor(btnRun);

      references.load(minipanelDescriptor);
      assets.finishLoadingAsset(minipanelDescriptor);
      minipanelWidget = new MinipanelWidget(assets.get(minipanelDescriptor).getTexture(0));
      minipanelWidget.setPosition((getWidth() / 2) - (minipanelWidget.getWidth() / 2), getHeight());
      addActor(minipanelWidget);

      references.load(menubuttonDescriptor);
      assets.finishLoadingAsset(menubuttonDescriptor);
      menuHidden = new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(menubuttonDescriptor).getTexture(0));
        down = new TextureRegionDrawable(assets.get(menubuttonDescriptor).getTexture(1));
      }};
      menuShown = new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(menubuttonDescriptor).getTexture(2));
        down = new TextureRegionDrawable(assets.get(menubuttonDescriptor).getTexture(3));
      }};
      btnMenu = new SessionButton(minipanelWidget.isVisible() ? menuShown : menuHidden);
      btnMenu.setPosition((getWidth() / 2) - (btnMenu.getWidth() / 2), 15);
      btnMenu.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (retired || !isCurrentSession()) return;
          boolean visible = !minipanelWidget.isVisible();
          setMinipanelVisible(visible);
        }
      });
      addActor(btnMenu);

      final ItemData itemData = items;
      IntArray beltItems = itemData.getLocation(Location.BELT);
      Array<Item> items = itemData.toItemArray(beltItems);

      belt = new BeltGrid(4, 4, 31, 31, this) {
        @Override protected boolean isActive() {
          return super.isActive() && !retired && isCurrentSession();
        }
      };
      belt.setRows(itemData.getBeltRows());
      belt.setBackground(popbelt);
      belt.setPosition(177, 8);
      belt.populate(items);
      belt.setHidden(true);
      addActor(belt);
      //setDebug(true, true);
    }

    void setMinipanelVisible(boolean b) {
        if (retired || !isCurrentSession()) return;
        btnMenu.setStyle(b ? menuShown : menuHidden);
        minipanelWidget.setVisible(b);
    }

    @Override
    public void dispose() {
      if (retired) return;
      retired = true;
      try (Disposal cleanup = new Disposal()) {
        cleanup.dispose(btnRun);
        cleanup.dispose(btnMenu);
        cleanup.dispose(minipanelWidget);
        if (belt != null) cleanup.run(() -> ActorTree.clear(belt));
        cleanup.dispose(belt);
        cleanup.run(() -> ActorTree.clear(btnRun));
        cleanup.run(() -> ActorTree.clear(btnMenu));
        cleanup.run(() -> ActorTree.clear(this));
        cleanup.dispose(background);
        cleanup.dispose(references);
      } finally {
        btnRun = btnMenu = null; minipanelWidget = null; belt = null; background = null;
        walkStyle = runStyle = menuHidden = menuShown = null;
      }
    }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      if (retired) return;
      ItemData itemData = items;
      belt.setRows(itemData.getBeltRows());
      belt.syncItems(itemData.toItemArray(itemData.getLocation(Location.BELT)));
      Button.ButtonStyle desiredRunStyle =
          game() != null && game().isRunModeEnabled() ? runStyle : walkStyle;
      if (btnRun.getStyle() != desiredRunStyle) btnRun.setStyle(desiredRunStyle);
      batch.draw(background, getX(), getY());
      super.draw(batch, a);
    }

    @Override
    public void onDrop(int x, int y) {
      if (retired || !isCurrentSession() || itemController == null) return;
      itemController.cursorToBelt(x, y);
    }

    @Override
    public boolean onPickup(int i) {
      if (retired || !isCurrentSession() || itemController == null) return false;
      itemController.beltToCursor(i);
      return true;
    }

    @Override
    public void onSwap(int i, int x, int y) {
      if (retired || !isCurrentSession() || itemController == null) return;
      itemController.swapBeltItem(i);
    }

    @Override
    public boolean onUse(Item item) {
      if (retired || !isCurrentSession() || itemController == null) return false;
      itemController.useBeltSlot(item.gridX);
      return true;
    }

    private class MinipanelWidget extends WidgetGroup implements Disposable {
      private final AssetReferenceScope references = new AssetReferenceScope(assets);
      private boolean retired;
      final AssetDescriptor<DC6> minipanelbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\minipanelbtn.DC6", DC6.class);

      Button btnCharacter;
      Button btnInventory;
      Button btnSkillTree;
      Button btnParty;
      Button btnMap;
      Button btnMessages;
      Button btnQuests;
      Button btnEscapeMenu;

      TextureRegion background;
      MinipanelWidget(TextureRegion background) {
        this.background = background;
        try { initialize(); }
        catch (RuntimeException | Error failure) {
          try { dispose(); }
          catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
          throw failure;
        }
      }

      private void initialize() {
        setSize(background.getRegionWidth(), background.getRegionHeight());
        //setVisible(false);

        references.load(minipanelbtnDescriptor);
        assets.finishLoadingAsset(minipanelbtnDescriptor);
        ClickListener clickListener = new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            if (retired || !isCurrentSession() || game() == null) return;
            Actor actor = event.getListenerActor();
            if (actor == btnCharacter) {
              Actor panel = game().characterPanel;
              game().setLeftPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnInventory) {
              Actor panel = game().inventoryPanel;
              game().setRightPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnSkillTree) {
              Actor panel = game().spellsPanel;
              game().setRightPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnParty) {
              Actor panel = game().partyPanel;
              game().setLeftPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnMap) {
              RenderSystem.toggleAutomap();
            } else if (actor == btnMessages) {

            } else if (actor == btnQuests) {
              Actor panel = game().questsPanel;
              game().setLeftPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnEscapeMenu) {
              if (game().escapePanel.isVisible()) game().escapePanel.close();
              else game().escapePanel.open();
            }
          }
        };
        btnCharacter = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(0));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(1));
        }});
        btnCharacter.addListener(clickListener);
        btnInventory = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(2));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(3));
        }});
        btnInventory.addListener(clickListener);
        btnSkillTree = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(4));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(5));
        }});
        btnSkillTree.addListener(clickListener);
        btnParty = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(6));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(7));
        }});
        btnParty.addListener(clickListener);
        btnMap = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(8));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(9));
        }});
        btnMap.addListener(clickListener);
        btnMessages = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(10));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(11));
        }});
        btnMessages.addListener(clickListener);
        btnQuests = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(12));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(13));
        }});
        btnQuests.addListener(clickListener);
        btnEscapeMenu = new SessionButton(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(14));
          down = new TextureRegionDrawable(assets.get(minipanelbtnDescriptor).getTexture(15));
        }});
        btnEscapeMenu.addListener(clickListener);
        Table table = new Table();
        table.setFillParent(true);
        table.align(Align.topLeft);
        table.pad(3);
        table.add(btnCharacter).space(1);
        table.add(btnInventory).space(1);
        table.add(btnSkillTree).space(1);
        table.add(btnParty).space(1);
        table.add(btnMap).space(1);
        table.add(btnMessages).space(1);
        table.add(btnQuests).space(1);
        table.add(btnEscapeMenu).space(1);
        addActor(table);
      }

      @Override
      public void dispose() {
        if (retired) return;
        retired = true;
        try (Disposal cleanup = new Disposal()) {
          for (Button button : new Button[] {btnCharacter, btnInventory, btnSkillTree, btnParty,
              btnMap, btnMessages, btnQuests, btnEscapeMenu}) {
            cleanup.dispose(button);
            cleanup.run(() -> ActorTree.clear(button));
          }
          cleanup.run(() -> ActorTree.clear(this));
          cleanup.dispose(references);
        } finally {
          btnCharacter = btnInventory = btnSkillTree = btnParty = null;
          btnMap = btnMessages = btnQuests = btnEscapeMenu = null; background = null;
        }
      }

      @Override
      public void draw(Batch batch, float a) {
        if (retired || !isCurrentSession()) return;
        batch.draw(background, getX(), getY());
        super.draw(batch, a);
      }
    }
  }
}
