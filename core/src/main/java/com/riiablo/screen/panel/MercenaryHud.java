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
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.item.Item;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.widget.Label;

/** Compact native-style hireling portrait, life bar and potion drop target. */
public final class MercenaryHud extends WidgetGroup implements Disposable {
  private static final float WIDTH = 176f;
  private static final float HEIGHT = 44f;
  private final AssetDescriptor<DC6> iconDescriptor = new AssetDescriptor<>(
      "data\\global\\ui\\HIREABLES\\rogueicon.dc6", DC6.class,
      DC6Loader.DC6Parameters.COMBINE);
  private final Texture fill;
  private final Label name;
  private final Label tooltip;
  private EntitySubscription mercenaries;
  private final ItemController itemController;
  private int mercenaryId = -1;
  private float life;
  private float maxLife;
  private boolean hovered;

  public MercenaryHud(ItemController itemController) {
    this.itemController = itemController;
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    Riiablo.assets.load(iconDescriptor);

    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    fill = new Texture(pixmap);
    pixmap.dispose();

    name = new Label("", Riiablo.fonts.font16, Color.WHITE);
    name.setPosition(42, 25);
    name.setSize(128, 14);
    addActor(name);
    tooltip = new Label("", Riiablo.fonts.fontformal12, Color.WHITE);
    tooltip.setPosition(42, 8);
    tooltip.setSize(128, 14);
    tooltip.setVisible(false);
    addActor(tooltip);

    addListener(new ClickListener() {
      @Override public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
        hovered = true;
        tooltip.setVisible(true);
      }
      @Override public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
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
    String mercName = Riiablo.charData == null ? "佣兵" : Riiablo.charData.getMerc().getName();
    name.setText(mercName);
    tooltip.setText(hovered ? "拖动治疗药水到此处" : "");
  }

  @Override public void draw(Batch batch, float parentAlpha) {
    if (!isVisible()) return;
    batch.setColor(0f, 0f, 0f, 0.72f * parentAlpha);
    batch.draw(fill, getX(), getY(), getWidth(), getHeight());
    batch.setColor(1f, 1f, 1f, parentAlpha);
    if (Riiablo.assets.isLoaded(iconDescriptor.fileName, DC6.class)) {
      DC6 icon = Riiablo.assets.get(iconDescriptor.fileName, DC6.class);
      TextureRegion region = icon.getTexture(0);
      float scale = Math.min(32f / region.getRegionWidth(), 32f / region.getRegionHeight());
      batch.draw(region, getX() + 5, getY() + 6,
          region.getRegionWidth() * scale, region.getRegionHeight() * scale);
    }
    float ratio = maxLife <= 0 ? 0 : Math.max(0, Math.min(1, life / maxLife));
    batch.setColor(0.65f, 0.08f, 0.06f, parentAlpha);
    batch.draw(fill, getX() + 42, getY() + 18, 126, 5);
    batch.setColor(0.12f, 0.75f, 0.16f, parentAlpha);
    batch.draw(fill, getX() + 42, getY() + 18, 126 * ratio, 5);
    batch.setColor(Color.WHITE);
    super.draw(batch, parentAlpha);
  }

  @Override public void dispose() {
    fill.dispose();
    Riiablo.assets.unload(iconDescriptor.fileName);
  }
}
