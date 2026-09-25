package com.riiablo.screen.panel;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
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
  private static final float HEIGHT = 70f;
  private static final String ICON_ROOT = "data\\global\\ui\\HIREABLES\\";
  private static final String[] ICON_NAMES = {
      "rogueicon.dc6", "act2hireableicon.dc6", "act3hireableicon.dc6", "barbhirable_icon.dc6"
  };
  private static final String UNNAMED = "UNNAMED";
  private static final float HEALTH_GREEN_THRESHOLD = 2f / 3f;
  private static final float HEALTH_YELLOW_THRESHOLD = 1f / 3f;

  private final AssetDescriptor<DC6>[] iconDescriptors;
  private final Texture fill;
  private final Label name;
  private final Label tooltip;
  private EntitySubscription mercenaries;
  private final ItemController itemController;
  private int mercenaryId = -1;
  private int mercenaryType = -1;
  private float life;
  private float maxLife;
  private boolean hovered;

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
    tooltip = new Label("", Riiablo.fonts.font16, Color.WHITE);
    // Native tooltip is a two-line hint below the name, not a single line
    // overlaying the portrait.
    tooltip.setPosition(0, -35);
    tooltip.setSize(300, 32);
    tooltip.setAlignment(Align.left);
    tooltip.setTouchable(Touchable.disabled);
    tooltip.setVisible(false);
    addActor(tooltip);

    addListener(new ClickListener(Input.Buttons.LEFT) {
      @Override public void enter(InputEvent event, float x, float y, int pointer,
          com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
        hovered = true;
        tooltip.setVisible(true);
      }

      @Override public void exit(InputEvent event, float x, float y, int pointer,
          com.badlogic.gdx.scenes.scene2d.Actor toActor) {
        hovered = false;
        tooltip.setVisible(false);
      }

      @Override public void clicked(InputEvent event, float x, float y) {
        useCursorPotion();
      }
    });
    // Keep the right-button action separate from the potion drop listener.
    // This avoids the default ClickListener's button state being shared with
    // a left-button drag/drop interaction.
    addListener(new ClickListener(Input.Buttons.RIGHT) {
      @Override public void clicked(InputEvent event, float x, float y) {
        if (Riiablo.game != null && Riiablo.game.hirelingPanel != null) {
          Riiablo.game.setLeftPanel(Riiablo.game.hirelingPanel);
          event.handle();
        }
      }
    });
  }

  /** Handles a potion released here after the drag began in another actor. */
  public boolean useCursorPotion() {
    Item item = Riiablo.cursor == null ? null : Riiablo.cursor.getItem();
    return item != null && itemController != null && itemController.useCursorPotionOnMercenary();
  }

  /** Tests a screen/stage point against the whole portrait drop target. */
  public boolean containsStagePoint(float stageX, float stageY) {
    Vector2 point = stageToLocalCoordinates(new Vector2(stageX, stageY));
    return point.x >= 0 && point.y >= 0 && point.x <= getWidth() && point.y <= getHeight();
  }

  @Override public void act(float delta) {
    super.act(delta);
    refreshState();
  }

  private void refreshState() {
    mercenaryId = -1;
    mercenaryType = -1;
    life = 0;
    maxLife = 0;
    if (mercenaries == null && Riiablo.engine != null) {
      mercenaries = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(Mercenary.class, AttributesWrapper.class));
    }
    if (mercenaries == null || Riiablo.game == null) {
      setVisible(false);
      return;
    }
    IntBag entities = mercenaries.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      Mercenary merc = Riiablo.engine.getMapper(Mercenary.class).get(ids[i]);
      if (merc == null || merc.ownerId != Riiablo.game.player) continue;
      mercenaryId = ids[i];
      mercenaryType = Math.max(0, Math.min(iconDescriptors.length - 1, merc.mercType));
      AttributesWrapper attrs = Riiablo.engine.getMapper(AttributesWrapper.class).get(mercenaryId);
      if (attrs != null && attrs.attrs != null) {
        StatRef hp = attrs.attrs.aggregate().get(Stat.hitpoints, StatRef.obtain());
        StatRef max = attrs.attrs.aggregate().get(Stat.maxhp, StatRef.obtain());
        life = hp == null ? 0 : hp.asFixed();
        maxLife = max == null ? 0 : max.asFixed();
      }
      break;
    }
    setVisible(mercenaryId >= 0);
    if (!isVisible()) return;
    // MercData.name is a compact native name id, not a printable string. Until
    // Hireling.txt's NameFirst/NameLast mapping is loaded, do not leak 0x0000.
    Mercenary merc = Riiablo.engine.getMapper(Mercenary.class).get(mercenaryId);
    name.setText(resolveMercenaryName(merc));
    name.setSize(WIDTH, 14);
    tooltip.setText(hovered ? localized("mercenary_heal_hint",
        "将药水放在肖像上即可治疗\n按下滑鼠右键可打開物品栏（O)") : "");
    tooltip.setSize(300, 32);
  }

  /** Resolves the native Hireling.txt name key (the saved value is a name slot). */
  static String resolveMercenaryName(Mercenary merc) {
    if (merc == null || Riiablo.string == null) return localized("hireling_unnamed", UNNAMED);
    int id = Math.max(0, merc.nameId);
    String[] keys;
    switch (merc.mercType) {
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
    if (iconDescriptor != null && Riiablo.assets.isLoaded(iconDescriptor.fileName, DC6.class)) {
      DC6 icon = Riiablo.assets.get(iconDescriptor.fileName, DC6.class);
      TextureRegion region = icon.getTexture(0);
      // Native portraits are 46x41 (barbarian is 47x41). Do not rescale them.
      float x = getX() + (getWidth() - region.getRegionWidth()) * 0.5f;
      float y = getY() + 19f;
      batch.setColor(1f, 1f, 1f, parentAlpha);
      if (hovered && batch instanceof PaletteIndexedBatch) {
        PaletteIndexedBatch indexed = (PaletteIndexedBatch) batch;
        indexed.setBlendMode(BlendMode.BRIGHTEN, Riiablo.colors.highlight);
        batch.draw(region, x, y);
        indexed.resetBlendMode();
      } else {
        batch.draw(region, x, y);
      }
    }

    float ratio = maxLife <= 0 ? 0 : Math.max(0, Math.min(1, life / maxLife));
    float barX = getX() + 5f;
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
    batch.setColor(Color.WHITE);
    super.draw(batch, parentAlpha);
  }

  @Override public void dispose() {
    fill.dispose();
    for (AssetDescriptor<DC6> iconDescriptor : iconDescriptors) {
      Riiablo.assets.unload(iconDescriptor.fileName);
    }
  }
}
