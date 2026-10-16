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
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.item.Item;
import com.riiablo.item.StoreLoc;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.save.CharData;
import com.riiablo.widget.Button;
import com.riiablo.widget.GoldAmountDialog;
import com.riiablo.widget.Label;
import com.riiablo.util.Disposal;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;

public class StashPanel extends WidgetGroup implements Disposable, ItemGrid.GridListener {
  private static final String TAG = "StashPanel";
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final ItemData itemData = java.util.Objects.requireNonNull(character.getItems(), "items");

  final AssetDescriptor<DC6> TradeStashDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\TradeStash.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion TradeStash;

  final AssetDescriptor<DC6> goldcoinbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\goldcoinbtn.dc6", DC6.class);
  Button btnDropGold;
  Label stashgold;
  GoldAmountDialog goldDialog;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  Button btnExit;

  final Inventory.Entry inventory;
  ItemGrid grid;
  private boolean disposed;

  @Wire(name = "itemController")
  protected ItemController itemController;

  public StashPanel() {
    inventory = Riiablo.files.inventory.get("Big Bank Page 1");
    try { initialize(); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(TradeStashDescriptor);
    assets.finishLoadingAsset(TradeStashDescriptor);
    TradeStash = assets.get(TradeStashDescriptor).getTexture();
    setSize(TradeStash.getRegionWidth(), TradeStash.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    btnExit = new SessionButton(new Button.ButtonStyle() {{
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(272, 15);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        setVisible(false);
      }
    });
    addActor(btnExit);

    grid = new ItemGrid(inventory, this) {
      @Override protected boolean isActive() { return super.isActive() && isCurrentSession(); }
    };
    IntArray stashItems = itemData.getStore(StoreLoc.STASH);
    Array<Item> items = itemData.toItemArray(stashItems);
    grid.populate(items);
    grid.setPosition(
        inventory.gridLeft - inventory.invLeft,
        getHeight() - inventory.gridTop - grid.getHeight());
    addActor(grid);

    StatRef goldbankStat = character.getStats().get(Stat.goldbank);
    stashgold = new Label(Integer.toString(goldbankStat != null ? goldbankStat.asInt() : 0), Riiablo.fonts.font16);
    stashgold.setSize(150, 16);
    stashgold.setPosition(98, 393);
    addActor(stashgold);

    btnDropGold = new SessionButton(new Button.ButtonStyle() {{
      references.load(goldcoinbtnDescriptor);
      assets.finishLoadingAsset(goldcoinbtnDescriptor);
      DC6 goldcoinbtn = assets.get(goldcoinbtnDescriptor);
      up   = new TextureRegionDrawable(goldcoinbtn.getTexture(0));
      down = new TextureRegionDrawable(goldcoinbtn.getTexture(1));
    }});
    btnDropGold.setPosition(74, 392);
    btnDropGold.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        goldDialog.open();
      }
    });
    addActor(btnDropGold);

    goldDialog = new GoldAmountDialog(Riiablo.bundle.get("stash_gold_prompt"),
        new GoldAmountDialog.Listener() {
      @Override
      public void submitted(String text) {
        if (!isCurrentSession()) return;
        try {
          int amount = Integer.parseInt(text.trim());
          if (itemController != null && amount > 0) itemController.withdrawGold(amount);
        } catch (NumberFormatException ignored) {
          // The native dialog filters digits; retain defensive parsing.
        }
      }

      @Override
      public void canceled() {}
        });
    goldDialog.setMaximumProvider(negative -> {
      if (!isCurrentSession()) return 0;
      StatRef gold = character.getStats().get(Stat.goldbank);
      return gold == null ? 0 : gold.asInt();
    });
    addActor(goldDialog);

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
      cleanup.dispose(btnDropGold);
      cleanup.dispose(goldDialog);
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> ActorTree.clear(btnExit));
      cleanup.run(() -> ActorTree.clear(btnDropGold));
      cleanup.dispose(grid);
      cleanup.dispose(references);
    } finally {
      TradeStash = null; btnExit = null; btnDropGold = null; grid = null;
      stashgold = null; goldDialog = null; itemController = null;
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

  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }

  @Override
  public void draw(Batch batch, float a) {
    if (!isCurrentSession()) return;
    StatRef currentGold = character.getStats().get(Stat.goldbank);
    if (stashgold != null) {
      int value = currentGold == null ? 0 : currentGold.asInt();
      if (!Integer.toString(value).contentEquals(stashgold.getText())) stashgold.setText(Integer.toString(value));
    }
    batch.draw(TradeStash, getX(), getY());
    super.draw(batch, a);
  }

  @Override
  public void onDrop(int x, int y) {
    if (!isCurrentSession() || itemController == null) return;
    itemController.cursorToStore(StoreLoc.STASH, x, y);
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
    itemController.swapStoreItem(i, StoreLoc.STASH, x, y);
  }

  @Override
  public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    if (!visible && goldDialog != null) goldDialog.close();
    super.setVisible(visible);
    if (visible && Riiablo.game != null) {
      Riiablo.game.setRightPanel(Riiablo.game.inventoryPanel);
    }
  }

  public boolean isGoldDialogOpen() {
    return goldDialog != null && goldDialog.isOpen();
  }
}
