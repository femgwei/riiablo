package com.riiablo.screen.panel;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.item.Item;
import com.riiablo.item.StoreLoc;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.save.CharData;
import com.riiablo.widget.Button;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

public class CubePanel extends WidgetGroup implements Disposable, ItemGrid.GridListener {
  private static final String TAG = "CubePanel";
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final ItemData itemData = java.util.Objects.requireNonNull(character.getItems(), "items");

  final AssetDescriptor<DC6> supertransmogrifierDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\supertransmogrifier.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion supertransmogrifier;

  final AssetDescriptor<DC6> miniconvertDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\miniconvert.DC6", DC6.class);
  Button btnTransmog;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  Button btnExit;

  final Inventory.Entry inventory;
  ItemGrid grid;
  private boolean disposed;

  @Wire(name = "itemController")
  protected ItemController itemController;

  public CubePanel() {
    inventory = Riiablo.files.inventory.get("Transmogrify Box Page 1");
    try { initialize(); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(supertransmogrifierDescriptor);
    assets.finishLoadingAsset(supertransmogrifierDescriptor);
    supertransmogrifier = assets.get(supertransmogrifierDescriptor).getTexture();
    setSize(supertransmogrifier.getRegionWidth(), supertransmogrifier.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    btnExit = new SessionButton(new Button.ButtonStyle() {{
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(275, 15);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        setVisible(false);
      }
    });
    addActor(btnExit);

    btnTransmog = new SessionButton(new Button.ButtonStyle() {{
      references.load(miniconvertDescriptor);
      assets.finishLoadingAsset(miniconvertDescriptor);
      up   = new TextureRegionDrawable(assets.get(miniconvertDescriptor).getTexture(0));
      down = new TextureRegionDrawable(assets.get(miniconvertDescriptor).getTexture(1));
    }});
    btnTransmog.setPosition(144, 139);
    btnTransmog.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        //...
      }
    });
    addActor(btnTransmog);

    grid = new ItemGrid(inventory, this) {
      @Override protected boolean isActive() { return super.isActive() && isCurrentSession(); }
    };
    IntArray cubeItems = itemData.getStore(StoreLoc.CUBE);
    Array<Item> items = itemData.toItemArray(cubeItems);
    grid.populate(items);
    grid.setPosition(
        inventory.gridLeft - inventory.invLeft,
        getHeight() - inventory.gridTop - grid.getHeight());
    addActor(grid);

    //setDebug(true, true);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.dispose(btnExit);
      cleanup.dispose(btnTransmog);
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> ActorTree.clear(btnExit));
      cleanup.run(() -> ActorTree.clear(btnTransmog));
      cleanup.dispose(grid);
      cleanup.dispose(references);
    } finally {
      supertransmogrifier = null; btnExit = null; btnTransmog = null; grid = null; itemController = null;
    }
  }

  private boolean isCurrentSession() {
    return !disposed && character == Riiablo.charData && assets == Riiablo.assets
        && itemData == character.getItems();
  }

  private class SessionButton extends Button {
    SessionButton(ButtonStyle style) { super(style); }
    @Override public boolean isDisabled() { return !isCurrentSession() || super.isDisabled(); }
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    super.setVisible(visible);
  }

  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }

  @Override
  public void draw(Batch batch, float a) {
    if (!isCurrentSession()) return;
    batch.draw(supertransmogrifier, getX(), getY());
    super.draw(batch, a);
  }

  @Override
  public void onDrop(int x, int y) {
    if (!isCurrentSession() || itemController == null) return;
    itemController.cursorToStore(StoreLoc.CUBE, x, y);
  }

  @Override
  public boolean onPickup(int i) {
    if (!isCurrentSession() || itemController == null) return false;
    itemController.storeToCursor(i);
    return true;
  }

  @Override
  public void onSwap(int i, int x, int y) {
    if (!isCurrentSession() || itemController == null) return;
    itemController.swapStoreItem(i, StoreLoc.CUBE, x, y);
  }
}
