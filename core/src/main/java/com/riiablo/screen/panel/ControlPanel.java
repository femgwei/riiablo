package com.riiablo.screen.panel;

import org.apache.commons.lang3.ArrayUtils;

import com.artemis.annotations.Wire;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
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
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.graphics.BlendMode;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.key.MappedKey;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.widget.Button;
import com.riiablo.widget.HotkeyButton;
import com.riiablo.widget.Label;

public class ControlPanel extends Table implements Disposable, EscapeController {
  private static final String TAG = "ControlPanel";
  private static final boolean DEBUG_MOBILE = !true;

  final AssetDescriptor<DC6> ctrlpnlDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\ctrlpnl7.DC6", DC6.class);
  HealthWidget healthWidget;
  ManaWidget manaWidget;
  ControlWidget controlWidget;

  final AssetDescriptor<DC6> hlthmanaDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\hlthmana.DC6", DC6.class);
  DC6 hlthmana;

  final AssetDescriptor<DC6> overlapDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\overlap.DC6", DC6.class);
  DC6 overlap;

  ExperienceWidget experienceWidget;
  
  // 经验条位置和尺寸常量（参考 OpenDiablo2）
  // 经验条相对于 controlWidget 中心的 x 偏移（使其居中在 controlWidget 上方）
  private static final float EXP_BAR_OFFSET_X_FROM_CONTROL_CENTER = -38f; // 
  private static final float EXP_BAR_OFFSET_Y_EXTRA = -11f;                 // 经验条相对于面板底部的额外 y 偏移（在 height 基础上）
  private static final float EXP_BAR_WIDTH = 120f;                        // 经验条宽度（参考 OpenDiablo2）
  private static final int EXP_BAR_HEIGHT = 2;                            // 经验条高度

  final AssetDescriptor<DC6> popbeltDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\ctrlpnl_popbelt.DC6", DC6.class);
  TextureRegion popbelt;

  final AssetDescriptor<DC6> SkilliconDescriptor = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\Skillicon.DC6", DC6.class);
  DC6 Skillicon;

  final AssetDescriptor<DC6> CharSkilliconDescriptor[];
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

  public ControlPanel() {
    Riiablo.assets.load(hlthmanaDescriptor);
    Riiablo.assets.finishLoadingAsset(hlthmanaDescriptor);
    hlthmana = Riiablo.assets.get(hlthmanaDescriptor);

    Riiablo.assets.load(overlapDescriptor);
    Riiablo.assets.finishLoadingAsset(overlapDescriptor);
    overlap = Riiablo.assets.get(overlapDescriptor);

    Riiablo.assets.load(ctrlpnlDescriptor);
    Riiablo.assets.finishLoadingAsset(ctrlpnlDescriptor);
    DC6 ctrlpnl = Riiablo.assets.get(ctrlpnlDescriptor);

    Riiablo.assets.load(popbeltDescriptor);
    Riiablo.assets.finishLoadingAsset(popbeltDescriptor);
    popbelt = Riiablo.assets.get(popbeltDescriptor).getTexture();

    Riiablo.assets.load(SkilliconDescriptor);
    Riiablo.assets.finishLoadingAsset(SkilliconDescriptor);
    Skillicon = Riiablo.assets.get(SkilliconDescriptor);

    CharSkilliconDescriptor = new AssetDescriptor[7];
    CharSkillicon = new DC6[CharSkilliconDescriptor.length];
    for (int i = 0; i < CharSkilliconDescriptor.length; i++) {
      CharSkilliconDescriptor[i] = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\" + CharacterClass.get(i).spellIcons + ".DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
      Riiablo.assets.load(CharSkilliconDescriptor[i]);
      Riiablo.assets.finishLoadingAsset(CharSkilliconDescriptor[i]);
      CharSkillicon[i] = Riiablo.assets.get(CharSkilliconDescriptor[i]);
    }

    final int numFrames = ctrlpnl.getNumFramesPerDir();
    healthWidget = new HealthWidget(ctrlpnl.getTexture(0));
    manaWidget = new ManaWidget(ctrlpnl.getTexture(numFrames - 2));

    // Create experience widget (无纹理依赖，参考 OpenDiablo2)
    experienceWidget = new ExperienceWidget();

    if (!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop) {
      int leftSkillId = Riiablo.charData.getAction(Input.Buttons.LEFT);
      if (leftSkillId > 0) {
        final Skills.Entry skill = Riiablo.files.skills.get(leftSkillId);
        final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
        int iconCel = desc.IconCel;
        DC icons = getSkillicon(skill.charclass, iconCel);
        if (icons == null) {
          icons = Skillicon;
          iconCel = 20;
        }

        leftSkill = new HotkeyButton(icons, iconCel, skill.Id);
        if (skill.aura) leftSkill.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
        int index = Riiablo.charData.getHotkey(Input.Buttons.LEFT, leftSkillId);
        if (index != ArrayUtils.INDEX_NOT_FOUND) {
          MappedKey mapping = Keys.Skill[index];
          leftSkill.map(mapping);
        }
      } else {
        leftSkill = new HotkeyButton(Skillicon, 0, -1);
      }
      leftSkill.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          Riiablo.game.spellsQuickPanelL.setVisible(!Riiablo.game.spellsQuickPanelL.isVisible());
        }
      });

      int rightSkillId = Riiablo.charData.getAction(Input.Buttons.RIGHT);
      if (rightSkillId > 0) {
        final Skills.Entry skill = Riiablo.files.skills.get(rightSkillId);
        final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
        int iconCel = desc.IconCel;
        DC icons = getSkillicon(skill.charclass, iconCel);
        if (icons == null) {
          icons = Skillicon;
          iconCel = 20;
        }

        rightSkill = new HotkeyButton(icons, iconCel, skill.Id);
        if (skill.aura) rightSkill.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
        int index = Riiablo.charData.getHotkey(Input.Buttons.RIGHT, rightSkillId);
        if (index != ArrayUtils.INDEX_NOT_FOUND) {
          MappedKey mapping = Keys.Skill[index];
          rightSkill.map(mapping);
        }
      } else {
        rightSkill = new HotkeyButton(Skillicon, 0, -1);
      }
      rightSkill.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          Riiablo.game.spellsQuickPanelR.setVisible(!Riiablo.game.spellsQuickPanelR.isVisible());
        }
      });

      int width = 0;
      int height = Integer.MIN_VALUE;
      for (int i = 1; i < numFrames - 2; i++) {
        Pixmap frame = ctrlpnl.getPixmap(0, i);
        width += frame.getWidth();
        height = Math.max(height, frame.getHeight());
      }
      Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
      pixmap.setBlending(Pixmap.Blending.None);
      int x = 0;
      for (int i = 1; i < numFrames - 2; i++) {
        Pixmap frame = ctrlpnl.getPixmap(0, i);
        pixmap.drawPixmap(frame, x, pixmap.getHeight() - frame.getHeight());
        x += frame.getWidth();
      }

      controlWidget = new ControlWidget(new Texture(new PixmapTextureData(pixmap, null, false, false, false)));
    }

    final float height = controlWidget == null ? 0 : controlWidget.background.getHeight() - 7;
    add(healthWidget).height(height).growX().left().bottom();
    if (leftSkill != null) add(leftSkill).bottom();
    if (controlWidget != null) add(controlWidget).size(controlWidget.background.getWidth(), height).bottom();
    if (rightSkill != null) add(rightSkill).bottom();
    add(manaWidget).height(height).growX().right().bottom();
    pack();
    
    // 经验条使用固定位置，不参与 Table 布局（参考 OpenDiablo2）
    // 位置相对于 controlWidget 的中心，使其居中显示在控制面板上方
    float expBarX;
    if (controlWidget != null) {
      // 相对于 controlWidget 的中心定位
      float controlCenterX = controlWidget.getX() + controlWidget.getWidth() / 2f;
      expBarX = controlCenterX + EXP_BAR_OFFSET_X_FROM_CONTROL_CENTER; // 居中，偏移半个宽度
    } else {
      // 如果没有 controlWidget，则相对于面板中心
      expBarX = getWidth() / 2f + EXP_BAR_OFFSET_X_FROM_CONTROL_CENTER;
    }
    // y 坐标：距离底部 height + 4 像素（保持之前的逻辑）
    float expBarY = height + EXP_BAR_OFFSET_Y_EXTRA;
    experienceWidget.setPosition(expBarX, expBarY);
    experienceWidget.setWidth(EXP_BAR_WIDTH);
    experienceWidget.setHeight(EXP_BAR_HEIGHT);
    addActor(experienceWidget); // 直接添加到面板，而不是 Table 布局

    //setHeight(controlWidget.background.getHeight() - 7);
    //setY(0);
    setTouchable(Touchable.childrenOnly);
    //setDebug(true, true);
  }

  @Override
  public Button getEscapeButton() {
    return controlWidget.minipanelWidget.btnEscapeMenu;
  }

  public void setMinipanelVisible(boolean b) {
    if (controlWidget != null) controlWidget.setMinipanelVisible(b);
  }

  public HotkeyButton getLeftSkill() {
    return leftSkill;
  }

  public HotkeyButton getRightSkill() {
    return rightSkill;
  }

  @Override
  public void dispose() {
    Riiablo.assets.unload(ctrlpnlDescriptor.fileName);
    Riiablo.assets.unload(popbeltDescriptor.fileName);
    Riiablo.assets.unload(overlapDescriptor.fileName);
    Riiablo.assets.unload(hlthmanaDescriptor.fileName);
    Riiablo.assets.unload(SkilliconDescriptor.fileName);
    if (controlWidget != null) controlWidget.dispose();
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
      final float x = getX();
      final float y = getY();
      batch.draw(background, x, y);
      
      // 计算 HP 比例并裁剪血条高度（暗黑2中血条是垂直的，从底部向上填充，减少时从顶部裁剪）
      float currentHP = Riiablo.charData.getStats().get(Stat.hitpoints).asFixed();
      float maxHP = Riiablo.charData.getStats().get(Stat.maxhp).asFixed();
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
      if (label.isVisible()) {
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
      final float x = getX();
      final float y = getY();
      batch.draw(background, x, y);
      
      // 计算 MP 比例并裁剪法力条高度（暗黑2中法力条是垂直的，从底部向上填充，减少时从顶部裁剪）
      float currentMana = Riiablo.charData.getStats().get(Stat.mana).asFixed();
      float maxMana = Riiablo.charData.getStats().get(Stat.maxmana).asFixed();
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
      if (label.isVisible()) {
        label.setX(getX() - 32);
        label.setText(Riiablo.string.format("panelmana",
            (int) currentMana,
            (int) maxMana));
        label.draw(batch, a);
      }
    }
  }

  private class ExperienceWidget extends Actor {
    // 经验条尺寸（参考 OpenDiablo2 的常量）
    // 注意：实际宽度和位置由外部设置，这里只保留高度常量
    private static final int EXP_BAR_HEIGHT = 2;

    // 1x1白色像素纹理用于填充
    private final TextureRegion whitePixel;

    ExperienceWidget() {
      setHeight(EXP_BAR_HEIGHT);
      // 宽度和位置由外部设置

      // 创建1x1白色像素纹理
      Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixmap.setColor(Color.WHITE);
      pixmap.fill();
      Texture texture = new Texture(pixmap);
      whitePixel = new TextureRegion(texture);
      pixmap.dispose();
    }

    @Override
    public void draw(Batch batch, float a) {
      final float x = getX();
      final float y = getY();

      // 绘制背景（深灰色）
      batch.setColor(new Color(0.2f, 0.2f, 0.2f, 1f));
      batch.draw(whitePixel, x, y, getWidth(), getHeight());

      // 计算经验百分比
      long currentExp = Riiablo.charData.getStats().get(Stat.experience).asLong();
      int currentLevel = Riiablo.charData.getStats().get(Stat.level).asInt();
      int charClass = Riiablo.charData.charClass;

      // 获取当前等级和下一等级所需经验
      long nextLevelExp = getNextLevelExperience(currentLevel, charClass);
      long currentLevelExp = getCurrentLevelExperience(currentLevel, charClass);

      // 计算百分比（0.0 到 1.0）
      float percentage = 0;
      if (nextLevelExp > currentLevelExp && currentExp >= currentLevelExp) {
        percentage = (float) (currentExp - currentLevelExp) / (nextLevelExp - currentLevelExp);
        percentage = Math.min(1.0f, Math.max(0.0f, percentage));
      }

      // 绘制填充（白色，参考 OpenDiablo2）
      if (percentage > 0) {
        float fillWidth = getWidth() * percentage;
        batch.setColor(Color.WHITE);
        batch.draw(whitePixel, x, y, fillWidth, getHeight());
      }

      // 重置颜色
      batch.setColor(Color.WHITE);
    }

    private long getNextLevelExperience(int level, int charClass) {
      if (level >= 99) return Long.MAX_VALUE;
      return (long) (500 + Math.pow(level * 0.8, 2.5) * 100);
    }

    private long getCurrentLevelExperience(int level, int charClass) {
      if (level <= 1) return 0;
      return getNextLevelExperience(level - 1, charClass);
    }
  }

  private class ControlWidget extends WidgetGroup implements Disposable, ItemGrid.GridListener {
    final AssetDescriptor<DC6> menubuttonDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\menubutton.DC6", DC6.class);
    Button btnMenu;
    Button.ButtonStyle menuHidden, menuShown;

    final AssetDescriptor<DC6> minipanelDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\minipanel.dc6", DC6.class);
    MinipanelWidget minipanelWidget;

    Texture background;

    ControlWidget(Texture background) {
      this.background = background;
      setSize(background.getWidth(), background.getHeight() - 7);
      setTouchable(Touchable.enabled);

      Riiablo.assets.load(minipanelDescriptor);
      Riiablo.assets.finishLoadingAsset(minipanelDescriptor);
      minipanelWidget = new MinipanelWidget(Riiablo.assets.get(minipanelDescriptor).getTexture(0));
      minipanelWidget.setPosition((getWidth() / 2) - (minipanelWidget.getWidth() / 2), getHeight());
      addActor(minipanelWidget);

      Riiablo.assets.load(menubuttonDescriptor);
      Riiablo.assets.finishLoadingAsset(menubuttonDescriptor);
      menuHidden = new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(Riiablo.assets.get(menubuttonDescriptor).getTexture(0));
        down = new TextureRegionDrawable(Riiablo.assets.get(menubuttonDescriptor).getTexture(1));
      }};
      menuShown = new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(Riiablo.assets.get(menubuttonDescriptor).getTexture(2));
        down = new TextureRegionDrawable(Riiablo.assets.get(menubuttonDescriptor).getTexture(3));
      }};
      btnMenu = new Button(minipanelWidget.isVisible() ? menuShown : menuHidden);
      btnMenu.setPosition((getWidth() / 2) - (btnMenu.getWidth() / 2), 15);
      btnMenu.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          boolean visible = !minipanelWidget.isVisible();
          setMinipanelVisible(visible);
        }
      });
      addActor(btnMenu);

      final ItemData itemData = Riiablo.charData.getItems();
      IntArray beltItems = itemData.getLocation(Location.BELT);
      Array<Item> items = itemData.toItemArray(beltItems);

      final BeltGrid belt = new BeltGrid(4, 4, 31, 31, this);
      belt.setRows(4);
      belt.setBackground(popbelt);
      belt.setPosition(177, 8);
      belt.populate(items);
      belt.setHidden(true);
      addActor(belt);
      //setDebug(true, true);
    }

    void setMinipanelVisible(boolean b) {
        btnMenu.setStyle(b ? menuShown : menuHidden);
        minipanelWidget.setVisible(b);
    }

    @Override
    public void dispose() {
      btnMenu.dispose();
      Riiablo.assets.unload(minipanelDescriptor.fileName);
      minipanelWidget.dispose();
      background.dispose();
      Riiablo.assets.unload(menubuttonDescriptor.fileName);
    }

    @Override
    public void draw(Batch batch, float a) {
      batch.draw(background, getX(), getY());
      super.draw(batch, a);
    }

    @Override
    public void onDrop(int x, int y) {
      itemController.cursorToBelt(x, y);
    }

    @Override
    public void onPickup(int i) {
      itemController.beltToCursor(i);
    }

    @Override
    public void onSwap(int i, int x, int y) {
      itemController.swapBeltItem(i);
    }

    private class MinipanelWidget extends WidgetGroup implements Disposable {
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
        setSize(background.getRegionWidth(), background.getRegionHeight());
        //setVisible(false);

        Riiablo.assets.load(minipanelbtnDescriptor);
        Riiablo.assets.finishLoadingAsset(minipanelbtnDescriptor);
        ClickListener clickListener = new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            Actor actor = event.getListenerActor();
            if (actor == btnCharacter) {
              Actor panel = Riiablo.game.characterPanel;
              Riiablo.game.setLeftPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnInventory) {
              Actor panel = Riiablo.game.inventoryPanel;
              Riiablo.game.setRightPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnSkillTree) {
              Actor panel = Riiablo.game.spellsPanel;
              Riiablo.game.setRightPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnParty) {

            } else if (actor == btnMap) {

            } else if (actor == btnMessages) {

            } else if (actor == btnQuests) {
              Actor panel = Riiablo.game.questsPanel;
              Riiablo.game.setLeftPanel(panel.isVisible() ? null : panel);
            } else if (actor == btnEscapeMenu) {
              Riiablo.game.escapePanel.setVisible(!Riiablo.game.escapePanel.isVisible());
            }
          }
        };
        btnCharacter = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(0));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(1));
        }});
        btnCharacter.addListener(clickListener);
        btnInventory = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(2));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(3));
        }});
        btnInventory.addListener(clickListener);
        btnSkillTree = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(4));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(5));
        }});
        btnSkillTree.addListener(clickListener);
        btnParty = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(6));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(7));
        }});
        btnParty.addListener(clickListener);
        btnMap = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(8));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(9));
        }});
        btnMap.addListener(clickListener);
        btnMessages = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(10));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(11));
        }});
        btnMessages.addListener(clickListener);
        btnQuests = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(12));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(13));
        }});
        btnQuests.addListener(clickListener);
        btnEscapeMenu = new Button(new Button.ButtonStyle() {{
          up   = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(14));
          down = new TextureRegionDrawable(Riiablo.assets.get(minipanelbtnDescriptor).getTexture(15));
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
        btnCharacter.dispose();
        btnInventory.dispose();
        Riiablo.assets.unload(minipanelbtnDescriptor.fileName);
      }

      @Override
      public void draw(Batch batch, float a) {
        batch.draw(background, getX(), getY());
        super.draw(batch, a);
      }
    }
  }
}
