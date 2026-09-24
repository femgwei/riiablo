package com.riiablo.screen.panel;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
    tooltip = new Label("", Riiablo.fonts.fontformal12, Color.WHITE);
    tooltip.setPosition(-20, -16);
    tooltip.setSize(WIDTH + 40, 14);
    tooltip.setAlignment(Align.center);
    tooltip.setVisible(false);
    addActor(tooltip);

    addListener(new ClickListener() {
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
        Item item = Riiablo.cursor == null ? null : Riiablo.cursor.getItem();
        if (item != null && itemController != null) itemController.useCursorPotionOnMercenary();
      }
    });
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
    String mercName = Riiablo.bundle.get("key_hireling");
    name.setText(mercName == null || mercName.isEmpty() ? "佣兵" : mercName);
    name.setSize(WIDTH, 14);
    tooltip.setText(hovered ? "拖动治疗药水到此处" : "");
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
      float y = getY() + 20f;
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
    batch.setColor(0.18f, 0.05f, 0.03f, parentAlpha);
    batch.draw(fill, barX, barY, 46f, 5f);
    batch.setColor(0.12f, 0.75f, 0.16f, parentAlpha);
    batch.draw(fill, barX, barY, 46f * ratio, 5f);
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
