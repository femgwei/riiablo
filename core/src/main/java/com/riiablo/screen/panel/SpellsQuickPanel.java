package com.riiablo.screen.panel;

import org.apache.commons.lang3.ArrayUtils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.CharacterClass;
import com.riiablo.Keys;
import com.riiablo.Riiablo;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.graphics.BlendMode;
import com.riiablo.item.Item;
import com.riiablo.key.MappedKey;
import com.riiablo.key.MappedKeyStateAdapter;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.widget.HotkeyButton;
import com.riiablo.widget.Button;
import com.riiablo.util.Disposal;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.ActorTree;
import com.riiablo.engine.client.NetworkedActionSender;
import com.riiablo.engine.server.AuraEcsSystem;

public class SpellsQuickPanel extends Table implements Disposable, CharData.SkillListener {
  private static final String SPELLS_PATH = "data\\global\\ui\\SPELLS\\";
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final ItemData items = character.getItems();
  private ItemData.AlternateListener alternateListener;
  private boolean disposed;
  private int generation;
  private final Array<HotkeyButton> ownedButtons = new Array<>();

  final AssetDescriptor<DC6> SkilliconDescriptor = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\Skillicon.DC6", DC6.class);
  DC6 Skillicon;

  final AssetDescriptor<DC6> CharSkilliconDescriptor[];
  DC6 CharSkillicon[];

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

  ObjectMap<MappedKey, HotkeyButton> keyMappings;
  MappedKeyStateAdapter mappedKeyListener;
  HotkeyButton observer;
  boolean leftSkills;
  Table[] tables;
  final float SIZE;
  final int ALIGN;
  IntMap<HotkeyButton> buttons;
  final int buttonId;
  final Socket socket;

  public SpellsQuickPanel(final HotkeyButton o, final boolean leftSkills) {
    this(o, leftSkills, null);
  }

  public SpellsQuickPanel(final HotkeyButton o, final boolean leftSkills, Socket socket) {
    this.observer = o;
    this.leftSkills = leftSkills;
    this.buttonId = leftSkills ? Input.Buttons.LEFT : Input.Buttons.RIGHT;
    this.socket = socket;

    SIZE = Gdx.app.getType() == Application.ApplicationType.Android ? 64 : 48;
    ALIGN = leftSkills ? Align.left : Align.right;
    CharSkilliconDescriptor = new AssetDescriptor[7];
    CharSkillicon = new DC6[CharSkilliconDescriptor.length];
    try {
      initialize();
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(SkilliconDescriptor);
    assets.finishLoadingAsset(SkilliconDescriptor);
    Skillicon = assets.get(SkilliconDescriptor);

    for (int i = 0; i < CharSkilliconDescriptor.length; i++) {
      CharSkilliconDescriptor[i] = new AssetDescriptor<>(SPELLS_PATH + CharacterClass.get(i).spellIcons + ".DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
      references.load(CharSkilliconDescriptor[i]);
      assets.finishLoadingAsset(CharSkilliconDescriptor[i]);
      CharSkillicon[i] = assets.get(CharSkilliconDescriptor[i]);
    }

    buttons = new IntMap<>(31);
    keyMappings = new ObjectMap<>(31);
    tables = new Table[5];
    for (int i = tables.length - 1; i >= 0; i--) {
      Table table = tables[i] = new Table();
      add(table).align(ALIGN).row();
    }
    // A click on empty space in the expanded grid is also a dismissal. A
    // click that originated on a skill button is left to that button's own
    // listener, which selects the skill and then hides the grid.
    addListener(new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (!isCurrentSession()) return false;
        if (!isSkillButtonTarget(event.getTarget())) setVisible(false);
        return false;
      }
    });
    pack();
    //setDebug(true, true);

    mappedKeyListener = new MappedKeyStateAdapter() {
      @Override
      public void onPressed(MappedKey key, int keycode) {
        if (!isCurrentSession()) return;
        HotkeyButton button = keyMappings.get(key);
        if (button == null) return;
        if (observer != null) observer.copy(button);
        selectSkill(button.getSkill());
      }
    };
    for (MappedKey Skill : Keys.Skill) Skill.addStateListener(mappedKeyListener);
    character.addSkillListener(this);

    alternateListener = new ItemData.AlternateListener() {
      @Override
      public void onAlternated(ItemData items, int alternate, Item LH, Item RH) {
        if (!isCurrentSession() || items != SpellsQuickPanel.this.items) return;
        for (IntMap.Entry<HotkeyButton> entry : buttons) {
          entry.value.refreshDisabled();
        }
        int skillId = character.getAction(buttonId);
        HotkeyButton button = buttons.get(skillId);
        if (observer != null) observer.copy(button); // observer is null on android
      }
    };
    items.addAlternateListener(alternateListener);
  }

  @Override
  public void onChanged(CharData client, IntIntMap skills, Array<StatRef> chargedSkills) {
    if (!isCurrentSession() || client != character) return;
    clearButtons();
    try {
      rebuildButtons(client, skills, chargedSkills);
    } catch (RuntimeException | Error failure) {
      try { clearButtons(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void clearButtons() {
    generation++;
    if (buttons != null) buttons.clear();
    if (keyMappings != null) keyMappings.clear();
    Array<Actor> retiring = new Array<>();
    if (ownedButtons != null) {
      retiring.addAll(ownedButtons);
      ownedButtons.clear();
    }
    try (Disposal cleanup = new Disposal()) {
      if (tables != null) for (Table table : tables) if (table != null) {
        cleanup.run(() -> {
          for (Actor child : table.getChildren()) if (!retiring.contains(child, true)) retiring.add(child);
        });
      }
      for (Actor child : retiring) {
        if (child instanceof HotkeyButton) cleanup.dispose((HotkeyButton) child);
        else {
          cleanup.run(() -> Button.disposeClickSounds(child));
          cleanup.run(() -> ActorTree.clear(child));
        }
      }
      if (tables == null) return;
      for (Table table : tables) {
        if (table == null) continue;
        cleanup.run(table::clear);
      }
    }
  }

  private HotkeyButton createButton(DC icons, int icon, int skill, StatRef charges) {
    final int buttonGeneration = generation;
    HotkeyButton button = new HotkeyButton(icons, icon, skill, charges) {
      @Override protected boolean isActive() {
        return super.isActive() && isCurrentSession() && buttonGeneration == generation;
      }
    };
    // Own returned controls before Table.add or later initialization can fail.
    ownedButtons.add(button);
    return button;
  }

  private void rebuildButtons(CharData client, IntIntMap skills, Array<StatRef> chargedSkills) {
    for (StatRef chargedSkill : chargedSkills) {
      if (chargedSkill.param0() <= 0) continue; // level <= 0

      final Skills.Entry skill = Riiablo.files.skills.get(chargedSkill.param1());
      if (leftSkills && !skill.leftskill) continue;
      if (skill.passive) continue;

      final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
      if (desc.ListRow < 0) continue;

      int ListRow = 4;
      Table table = tables[ListRow];
      int iconCel = desc.IconCel;
      DC icons = getSkillicon(skill.charclass, iconCel);
      if (icons == null) {
        icons = Skillicon;
        iconCel = 20;
      }
      final HotkeyButton button = createButton(icons, iconCel, skill.Id, chargedSkill);
      table.add(button).size(SIZE); // Own the returned button before further initialization can fail.
      button.setWeaponRestrictionOnly(true);
      if (skill.aura) {
        button.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
      }

      int index = character.getHotkey(buttonId, chargedSkill.param1());
      if (index != ArrayUtils.INDEX_NOT_FOUND) {
        MappedKey mapping = Keys.Skill[index];
        button.map(mapping);
        keyMappings.put(mapping, button);
      }

      final int buttonGeneration = generation;
      button.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession() || buttonGeneration != generation) return;
          if (observer != null) observer.copy(button);
          selectSkill(button.getSkill());
          SpellsQuickPanel.this.setVisible(false);
        }
      });
      buttons.put(button.getSkill(), button);
    }
    for (IntIntMap.Entry skillId : skills) {
      if (skillId.value <= 0) continue; // level <= 0

      final Skills.Entry skill = Riiablo.files.skills.get(skillId.key);
      if (leftSkills && !skill.leftskill) continue;
      if (skill.passive) continue;

      final SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
      if (desc.ListRow < 0) continue;

      Table table = tables[desc.ListRow];
      int iconCel = desc.IconCel;
      DC icons = getSkillicon(skill.charclass, iconCel);
      if (icons == null) {
        icons = Skillicon;
        iconCel = 20;
      }
      final HotkeyButton button = createButton(icons, iconCel, skill.Id, null);
      table.add(button).size(SIZE);
      button.setWeaponRestrictionOnly(true);
      if (skill.aura) {
        button.setBlendMode(BlendMode.DARKEN, Riiablo.colors.darkenGold);
      }

      int index = character.getHotkey(buttonId, skillId.key);
      if (index != ArrayUtils.INDEX_NOT_FOUND) {
        MappedKey mapping = Keys.Skill[index];
        button.map(mapping);
        keyMappings.put(mapping, button);
      }

      final int buttonGeneration = generation;
      button.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession() || buttonGeneration != generation) return;
          if (observer != null) observer.copy(button);
          selectSkill(button.getSkill());
          SpellsQuickPanel.this.setVisible(false);
        }
      });
      buttons.put(button.getSkill(), button);
    }
    // Match the skill-book layout within each quick-skill row: page order,
    // then top-to-bottom SkillRow, then left-to-right SkillColumn. The left
    // button uses that order and the right button reverses it. Skill id is
    // only a deterministic fallback for entries sharing one book position.
    // Re-add the sorted buttons instead of sorting getChildren() in place:
    // LibGDX Table lays out from its Cell list, not the child array order.
    for (Table table : tables) {
      Array<HotkeyButton> sorted = new Array<>(table.getChildren().size);
      for (Actor child : table.getChildren()) sorted.add((HotkeyButton) child);
      sorted.sort((left, right) -> {
        int leftId = left.getSkill();
        int rightId = right.getSkill();
        int order = compareSkillBookOrder(leftId, rightId);
        return leftSkills ? order : -order;
      });
      table.clear();
      for (HotkeyButton button : sorted) table.add(button).size(SIZE);
    }
    float x = getX(ALIGN);
    float y = getY();
    pack();
    setPosition(x, y, Align.bottom | ALIGN);
  }

  private static int compareSkillBookOrder(int leftId, int rightId) {
    if (leftId == rightId) return 0;
    Skills.Entry leftSkill = Riiablo.files.skills.get(leftId);
    Skills.Entry rightSkill = Riiablo.files.skills.get(rightId);
    SkillDesc.Entry leftDesc = Riiablo.files.skilldesc.get(leftSkill.skilldesc);
    SkillDesc.Entry rightDesc = Riiablo.files.skilldesc.get(rightSkill.skilldesc);

    int order = Integer.compare(leftDesc.SkillPage, rightDesc.SkillPage);
    if (order != 0) return order;
    order = Integer.compare(leftDesc.SkillRow, rightDesc.SkillRow);
    if (order != 0) return order;
    order = Integer.compare(leftDesc.SkillColumn, rightDesc.SkillColumn);
    return order != 0 ? order : Integer.compare(leftId, rightId);
  }

  public void setObserver(HotkeyButton observer) {
    if (!disposed) this.observer = observer;
  }

  @Override public void setVisible(boolean visible) {
    if (!disposed) super.setVisible(visible);
  }

  private boolean isSkillButtonTarget(Actor target) {
    for (Actor current = target; current != null && current != this;
        current = current.getParent()) {
      if (current instanceof HotkeyButton) return true;
    }
    return false;
  }

  private void selectSkill(int skillId) {
    if (!isCurrentSession()) return;
    character.setAction(buttonId, skillId);
    if (socket != null) {
      NetworkedActionSender.selectSkill(socket, buttonId, skillId);
      return;
    }
    if (Riiablo.engine == null || Riiablo.game == null || Riiablo.game.player < 0) return;
    AuraEcsSystem auraSystem = Riiablo.engine.getSystem(AuraEcsSystem.class);
    if (auraSystem == null || leftSkills) return;
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill != null && skill.aura) {
      auraSystem.selectAura(Riiablo.game.player, skillId);
    } else {
      auraSystem.clearAura(Riiablo.game.player);
    }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    observer = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearListeners);
      cleanup.run(() -> character.removeSkillListener(this));
      if (items != null && alternateListener != null) {
        cleanup.run(() -> items.removeAlternateListener(alternateListener));
      }
      cleanup.run(this::clearButtons);
      if (mappedKeyListener != null) for (MappedKey Skill : Keys.Skill) {
        cleanup.run(() -> Skill.removeStateListener(mappedKeyListener));
      }
      cleanup.run(this::clearChildren);
      cleanup.run(this::remove);
      cleanup.dispose(references);
    } finally {
      Skillicon = null;
      CharSkillicon = null;
    }
  }

  private boolean isCurrentSession() {
    return !disposed && character == Riiablo.charData;
  }

  @Override public void act(float delta) {
    if (isCurrentSession()) super.act(delta);
  }

  @Override public void draw(Batch batch, float parentAlpha) {
    if (isCurrentSession()) super.draw(batch, parentAlpha);
  }
}
