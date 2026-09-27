package com.riiablo.screen.panel;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.item.Item;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.widget.Label;

/** Compact native-style hireling portrait, life bar and potion drop target. */
public final class MercenaryHud extends WidgetGroup implements Disposable {
  // Native D2 uses a small portrait, rather than a wide opaque status panel.
  private static final float WIDTH = 56f;
  private static final float SLOT_GAP = 0f;
  private static final float HEIGHT = 70f;
  private static final String ICON_ROOT = "data\\global\\ui\\HIREABLES\\";
  private static final String[] ICON_NAMES = {
      "rogueicon.dc6", "act2hireableicon.dc6", "act3hireableicon.dc6", "barbhirable_icon.dc6"
  };
  private static final String VALKYRIE_ICON_NAME = "valkarieicon.dc6";
  private static final String UNNAMED = "UNNAMED";
  private static final float HEALTH_GREEN_THRESHOLD = 2f / 3f;
  private static final float HEALTH_YELLOW_THRESHOLD = 1f / 3f;

  private final AssetDescriptor<DC6>[] iconDescriptors;
  private final AssetDescriptor<DC6> valkyrieIconDescriptor;
  private final Texture fill;
  private final Label name;
  private final Label valkyrieName;
  private final Label tooltip;
  private final Label feedback;
  private EntitySubscription mercenaries;
  private EntitySubscription summonedPets;
  private final ItemController itemController;
  private int mercenaryId = -1;
  private int mercenaryType = -1;
  private float life;
  private float maxLife;
  private int valkyrieId = -1;
  private float valkyrieLife;
  private float valkyrieMaxLife;
  private boolean hovered;
  private float feedbackRemaining;

  @SuppressWarnings("unchecked")
  public MercenaryHud(ItemController itemController) {
    this.itemController = itemController;
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    iconDescriptors = new AssetDescriptor[ICON_NAMES.length];
    for (int i = 0; i < ICON_NAMES.length; i++) {
      iconDescriptors[i] = new AssetDescriptor<>(
          ICON_ROOT + ICON_NAMES[i], DC6.class, DC6Loader.DC6Parameters.COMBINE);
      Riiablo.assets.load(iconDescriptors[i]);
    }
    valkyrieIconDescriptor = new AssetDescriptor<>(
        ICON_ROOT + VALKYRIE_ICON_NAME, DC6.class, DC6Loader.DC6Parameters.COMBINE);
    Riiablo.assets.load(valkyrieIconDescriptor);

    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    fill = new Texture(pixmap);
    pixmap.dispose();

    name = new Label("", Riiablo.fonts.font16, Color.WHITE);
    name.setPosition(0, 1);
    name.setSize(WIDTH, 14);
    name.setAlignment(Align.center);
    addActor(name);
    valkyrieName = new Label(resolveValkyrieName(), Riiablo.fonts.font16, Color.WHITE);
    valkyrieName.setPosition(WIDTH + SLOT_GAP, 1);
    valkyrieName.setSize(WIDTH, 14);
    valkyrieName.setAlignment(Align.center);
    valkyrieName.setTouchable(Touchable.disabled);
    valkyrieName.setVisible(false);
    addActor(valkyrieName);
    tooltip = new Label("", Riiablo.fonts.font16, Color.WHITE);
    // Native tooltip is a two-line hint below the name, not a single line
    // overlaying the portrait.
    tooltip.setPosition(0, -35);
    tooltip.setSize(300, 32);
    tooltip.setAlignment(Align.left);
    tooltip.setTouchable(Touchable.disabled);
    tooltip.setVisible(false);
    addActor(tooltip);

    feedback = new Label("", Riiablo.fonts.font16, Color.WHITE);
    feedback.setPosition(-38, HEIGHT + 2);
    feedback.setSize(132, 16);
    feedback.setAlignment(Align.center);
    feedback.setTouchable(Touchable.disabled);
    feedback.setVisible(false);
    addActor(feedback);

    addListener(new ClickListener(Input.Buttons.LEFT) {
      @Override public void enter(InputEvent event, float x, float y, int pointer,
          com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
        updateHover(x);
      }

      @Override public void exit(InputEvent event, float x, float y, int pointer,
          com.badlogic.gdx.scenes.scene2d.Actor toActor) {
        hovered = false;
        tooltip.setVisible(false);
      }

      @Override public boolean mouseMoved(InputEvent event, float x, float y) {
        updateHover(x);
        return true;
      }

      @Override public void clicked(InputEvent event, float x, float y) {
        if (isMercenaryPoint(x, y)) useCursorPotion();
      }
    });
    // Keep the right-button action separate from the potion drop listener.
    // This avoids the default ClickListener's button state being shared with
    // a left-button drag/drop interaction.
    addListener(new ClickListener(Input.Buttons.RIGHT) {
      @Override public void clicked(InputEvent event, float x, float y) {
        if (isMercenaryPoint(x, y) && Riiablo.game != null
            && Riiablo.game.hirelingPanel != null) {
          Riiablo.game.setLeftPanel(Riiablo.game.hirelingPanel);
          event.handle();
        }
      }
    });
  }

  /** Handles a potion released here after the drag began in another actor. */
  public boolean useCursorPotion() {
    Item item = Riiablo.cursor == null ? null : Riiablo.cursor.getItem();
    if (item == null) {
      Gdx.app.log("MercenaryHud", "[MERC_POTION_UI] phase=reject reason=empty_cursor");
      return false;
    }
    if (itemController == null) {
      Gdx.app.log("MercenaryHud", "[MERC_POTION_UI] phase=reject reason=item_controller_missing item="
          + item.id);
      return false;
    }
    // Avoid sending a network request for a native no-op.  D2 leaves the
    // potion on the cursor and lets the hireling answer above the portrait.
    if (maxLife > 0 && life >= maxLife) {
      showTemporaryMessage("暂时还不用，谢谢");
      Gdx.app.log("MercenaryHud", "[MERC_POTION_UI] phase=reject reason=mercenary_full_health item="
          + item.id + " hp=" + life + " max=" + maxLife);
      return false;
    }
    boolean used = itemController.useCursorPotionOnMercenary();
    Gdx.app.log("MercenaryHud", "[MERC_POTION_UI] phase=dispatch item=" + item.id
        + " code=" + item.code + " location=" + item.location + " used=" + used);
    return used;
  }

  /** Shows a short native-style response above the hireling portrait. */
  public void showTemporaryMessage(String message) {
    feedback.setText(message == null ? "" : message);
    feedbackRemaining = 1.5f;
    feedback.setVisible(message != null && !message.isEmpty());
  }

  /** Tests a screen/stage point against the whole portrait drop target. */
  public boolean containsStagePoint(float stageX, float stageY) {
    Vector2 point = stageToLocalCoordinates(new Vector2(stageX, stageY));
    return isMercenaryPoint(point.x, point.y);
  }

  @Override public void act(float delta) {
    super.act(delta);
    if (feedbackRemaining > 0) {
      feedbackRemaining -= delta;
      if (feedbackRemaining <= 0) {
        feedbackRemaining = 0;
        feedback.setVisible(false);
      }
    }
    refreshState();
  }

  private void refreshState() {
    mercenaryId = -1;
    mercenaryType = -1;
    life = 0;
    maxLife = 0;
    valkyrieId = -1;
    valkyrieLife = 0;
    valkyrieMaxLife = 0;
    if (mercenaries == null && Riiablo.engine != null) {
      mercenaries = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(Mercenary.class, AttributesWrapper.class));
    }
    if (summonedPets == null && Riiablo.engine != null) {
      summonedPets = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(SummonedPet.class, AttributesWrapper.class));
    }
    if (Riiablo.game == null) {
      setVisible(false);
      return;
    }
    if (mercenaries != null) {
      IntBag entities = mercenaries.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Mercenary merc = Riiablo.engine.getMapper(Mercenary.class).get(ids[i]);
        if (merc == null || merc.ownerId != Riiablo.game.player) continue;
        mercenaryId = ids[i];
        mercenaryType = Math.max(0, Math.min(iconDescriptors.length - 1, merc.mercType));
        float[] vitals = readVitals(mercenaryId);
        life = vitals[0];
        maxLife = vitals[1];
        break;
      }
    }
    if (summonedPets != null) {
      IntBag entities = summonedPets.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        SummonedPet pet = Riiablo.engine.getMapper(SummonedPet.class).get(ids[i]);
        if (pet == null || pet.ownerId != Riiablo.game.player || !isValkyriePetType(pet.petType)) {
          continue;
        }
        valkyrieId = ids[i];
        float[] vitals = readVitals(valkyrieId);
        valkyrieLife = vitals[0];
        valkyrieMaxLife = vitals[1];
        break;
      }
    }
    int slots = companionSlotCount(mercenaryId >= 0, valkyrieId >= 0);
    setSize(slots * WIDTH + Math.max(0, slots - 1) * SLOT_GAP, HEIGHT);
    valkyrieName.setPosition(companionSlotX(mercenaryId >= 0, true), 1);
    name.setVisible(mercenaryId >= 0);
    valkyrieName.setVisible(valkyrieId >= 0);
    setVisible(slots > 0);
    if (!isVisible()) return;
    // MercData.name is a compact native name id, not a printable string. Until
    // Hireling.txt's NameFirst/NameLast mapping is loaded, do not leak 0x0000.
    Mercenary merc = Riiablo.engine.getMapper(Mercenary.class).get(mercenaryId);
    name.setText(resolveMercenaryName(merc));
    name.setPosition(companionSlotX(false, false), 1);
    name.setSize(WIDTH, 14);
    valkyrieName.setText(resolveValkyrieName());
    tooltip.setPosition(companionSlotX(false, false), -35);
    tooltip.setText(hovered ? localized("mercenary_heal_hint",
        "将药水放在肖像上即可治疗\n按下滑鼠右键可打開物品栏（O)") : "");
    tooltip.setSize(300, 32);
  }

  private float[] readVitals(int entityId) {
    AttributesWrapper attrs = Riiablo.engine.getMapper(AttributesWrapper.class).get(entityId);
    if (attrs == null || attrs.attrs == null) return new float[] {0, 0};
    StatRef hp = attrs.attrs.aggregate().get(Stat.hitpoints, StatRef.obtain());
    StatRef max = attrs.attrs.aggregate().get(Stat.maxhp, StatRef.obtain());
    return new float[] {hp == null ? 0 : hp.asFixed(), max == null ? 0 : max.asFixed()};
  }

  private void updateHover(float x) {
    hovered = isMercenaryPoint(x, 0);
    tooltip.setVisible(hovered);
  }

  private boolean isMercenaryPoint(float x, float y) {
    return mercenaryId >= 0 && x >= 0 && x <= WIDTH && y >= 0 && y <= HEIGHT;
  }

  static boolean isValkyriePetType(String petType) {
    return "valkyrie".equals(PetType.canonical(petType));
  }

  static int companionSlotCount(boolean hasMercenary, boolean hasValkyrie) {
    return hasMercenary || hasValkyrie ? (hasMercenary && hasValkyrie ? 2 : 1) : 0;
  }

  static float companionSlotX(boolean hasMercenary, boolean valkyrie) {
    return valkyrie && hasMercenary ? WIDTH + SLOT_GAP : 0;
  }

  private static String resolveValkyrieName() {
    if (Riiablo.string != null && Riiablo.files != null && Riiablo.files.skills != null
        && Riiablo.files.skilldesc != null) {
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get("Valkyrie");
      if (skill != null) {
        com.riiablo.codec.excel.SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
        if (desc != null && desc.str_name != null) {
          String value = Riiablo.string.lookup(desc.str_name);
          if (value != null && !value.startsWith("ERROR:")) return value;
        }
      }
    }
    return "女武神";
  }

  /** Resolves the native Hireling.txt name key (the saved value is a name slot). */
  public static String resolveMercenaryName(Mercenary merc) {
    return merc == null ? localized("hireling_unnamed", UNNAMED)
        : resolveMercenaryName(merc.mercType, merc.nameId);
  }

  /** Resolves a persisted hireling header after its level-scoped entity unloads. */
  public static String resolveMercenaryName(int mercType, int nameId) {
    if (Riiablo.string == null) return localized("hireling_unnamed", UNNAMED);
    int id = Math.max(0, nameId);
    String[] keys;
    switch (mercType) {
      case 0:
        keys = new String[] {String.format(java.util.Locale.ROOT, "merc%02d", id + 1)};
        break;
      case 1:
        keys = new String[] {String.format(java.util.Locale.ROOT, "merca%03d", id + 181),
            String.format(java.util.Locale.ROOT, "merca%03d", id + 201)};
        break;
      case 2:
        keys = new String[] {String.format(java.util.Locale.ROOT, "merca%03d", id + 182),
            String.format(java.util.Locale.ROOT, "merca%03d", Math.min(241, id + 222))};
        break;
      case 3:
        keys = new String[] {String.format(java.util.Locale.ROOT, "MercX%03d", id + 31),
            String.format(java.util.Locale.ROOT, "MercX%03d", id + 101)};
        break;
      default: return localized("hireling_unnamed", UNNAMED);
    }
    for (String key : keys) {
      String value = Riiablo.string.lookup(key);
      if (value != null && !value.startsWith("ERROR:")) return value;
    }
    return localized("hireling_unnamed", UNNAMED);
  }

  private static String localized(String key, String fallback) {
    if (Riiablo.bundle == null) return fallback;
    String value = Riiablo.bundle.get(key);
    return value == null || value.isEmpty() ? fallback : value;
  }

  /** Native hireling life-bar color bands: green, yellow, then red. */
  static Color healthBarColor(float ratio) {
    if (ratio > HEALTH_GREEN_THRESHOLD) return Color.GREEN;
    if (ratio > HEALTH_YELLOW_THRESHOLD) return Color.YELLOW;
    return Color.RED;
  }

  @Override public void draw(Batch batch, float parentAlpha) {
    if (!isVisible()) return;
    AssetDescriptor<DC6> iconDescriptor = mercenaryType >= 0
        ? iconDescriptors[mercenaryType] : null;
    drawCompanion(batch, parentAlpha, iconDescriptor, 0, life, maxLife, hovered);
    drawCompanion(batch, parentAlpha, valkyrieIconDescriptor,
        companionSlotX(mercenaryId >= 0, true), valkyrieLife, valkyrieMaxLife, false);
    batch.setColor(Color.WHITE);
    super.draw(batch, parentAlpha);
  }

  private void drawCompanion(Batch batch, float parentAlpha, AssetDescriptor<DC6> descriptor,
      float slotX, float currentLife, float maximumLife, boolean highlight) {
    if (descriptor == null || !Riiablo.assets.isLoaded(descriptor.fileName, DC6.class)) return;
    DC6 icon = Riiablo.assets.get(descriptor.fileName, DC6.class);
    TextureRegion region = icon.getTexture(0);
    // Native portraits are 46x41 (barbarian is 47x41). Do not rescale them.
    float x = getX() + slotX + (WIDTH - region.getRegionWidth()) * 0.5f;
    float y = getY() + 19f;
    batch.setColor(1f, 1f, 1f, parentAlpha);
    if (highlight && batch instanceof PaletteIndexedBatch) {
      PaletteIndexedBatch indexed = (PaletteIndexedBatch) batch;
      indexed.setBlendMode(BlendMode.BRIGHTEN, Riiablo.colors.highlight);
      batch.draw(region, x, y);
      indexed.resetBlendMode();
    } else {
      batch.draw(region, x, y);
    }

    float ratio = maximumLife <= 0 ? 0 : Math.max(0, Math.min(1, currentLife / maximumLife));
    float barX = getX() + slotX + 5f;
    float barY = getY() + 63f;
    Color healthColor = healthBarColor(ratio);
    // The original portrait has no dark/opaque background behind the life bar.
    if (batch instanceof PaletteIndexedBatch) {
      ((PaletteIndexedBatch) batch).setBlendMode(BlendMode.SOLID, healthColor);
    } else {
      batch.setColor(healthColor.r, healthColor.g, healthColor.b, parentAlpha);
    }
    batch.draw(fill, barX, barY, 46f * ratio, 5f);
    if (batch instanceof PaletteIndexedBatch) {
      ((PaletteIndexedBatch) batch).resetBlendMode();
    }
  }

  @Override public void dispose() {
    fill.dispose();
    for (AssetDescriptor<DC6> iconDescriptor : iconDescriptors) {
      Riiablo.assets.unload(iconDescriptor.fileName);
    }
    Riiablo.assets.unload(valkyrieIconDescriptor.fileName);
  }
}
