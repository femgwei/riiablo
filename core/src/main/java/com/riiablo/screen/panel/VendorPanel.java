package com.riiablo.screen.panel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.net.Socket;

import com.riiablo.Riiablo;
import com.riiablo.attributes.AttributesUpdater;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Npc;
import com.riiablo.graphics.BorderedPaletteIndexedDrawable;
import com.riiablo.graphics.BlendMode;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.VendorPricing;
import com.riiablo.item.VendorGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.io.ByteInput;
import com.riiablo.util.BufferUtils;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.net.packet.d2gs.NpcServiceOperation;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.net.packet.d2gs.NpcServiceStock;
import com.riiablo.net.packet.d2gs.NpcServiceType;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import com.riiablo.engine.server.npc.NpcRepairService;
import com.riiablo.loader.DC6Loader;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;
import com.riiablo.util.ActorTree;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.screen.GameScreen;

public class VendorPanel extends WidgetGroup implements Disposable {
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final ItemData itemData = java.util.Objects.requireNonNull(character.getItems(), "items");
  private final com.riiablo.audio.Audio audio = Riiablo.audio;
  private final com.riiablo.Cursor cursor = Riiablo.cursor;
  private GameScreen owner;
  private com.artemis.World world;
  public void bindWorld(com.artemis.World world) {
    if (disposed) return;
    java.util.Objects.requireNonNull(world, "world");
    if (this.world != null && this.world != world) throw new IllegalStateException("world already bound");
    this.world = world;
  }
  private boolean initializing = true;
  private Stage purchasePromptStage;
  private BorderedPaletteIndexedDrawable purchasePromptBorder;
  private boolean disposed;
  private static final String TAG = "VendorPanel";

  public static final int BUY        = 1 << 0;
  public static final int SELL       = 1 << 1;
  public static final int REPAIR     = 1 << 2;
  public static final int REPAIR_ALL = 1 << 4;
  public static final int EXIT       = 1 << 5;

  public static final int TAB_ARMOR    = 0;
  public static final int TAB_WEAPONS  = 1;
  public static final int TAB_WEAPONS2 = 2;
  public static final int TAB_MISC     = 3;

  public static final int BUYSELL  = BUY | SELL;
  public static final int REPAIRER = REPAIR | REPAIR_ALL;
  public static final int TRADER   = BUYSELL | EXIT;
  public static final int SMITHY   = BUYSELL | REPAIRER;
  /** Gambling stock is buy-only, but the gambling page still accepts sales. */
  public static final int GAMBLER  = BUYSELL | EXIT;

  static final int BLANK_MASKS[] = {
      BUY,
      SELL,
      REPAIR,
      EXIT | REPAIR_ALL
  };

  final AssetDescriptor<DC6> buysellDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysell.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion buysell;

  final AssetDescriptor<DC6> buyselltabsDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buyselltabs.DC6", DC6.class);
  DC buyselltabs;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  Button btnBuy;
  Button btnSell;
  Button btnRepair;
  Button btnRepairAll;
  Button btnExit;
  Button btnBlank[];
  ButtonGroup<Button> buttonGroup;

  Tab[] tabs;

  final Inventory.Entry inventory;
  VendorGrid activeGrid = null;
  private Label goldLabel;
  private boolean selling;
  private boolean repairing;
  /** Local-mode stock is retained between opening/closing the panel. */
  private Array<Item> localStock;
  /** Base items that were permanently supplied by the currently opened vendor. */
  private final ObjectSet<ItemEntry> permanentStockBases = new ObjectSet<>();
  private Npc.Entry localPricing;
  private VendorGenerator localVendorGenerator;
  @com.artemis.annotations.Wire(name = "client.socket", failOnNull = false)
  private Socket clientSocket;
  // Artemis 2.3 treats a field-level @Wire system dependency as mandatory,
  // even when failOnNull=false. GameScreen assigns this only for a networked
  // world so the local vendor panel remains usable without the system.
  @com.artemis.annotations.SkipWire
  private ClientNetworkSynchronizer networkSynchronizer;
  private int networkFlags;
  private int configuredFlags;
  private int networkNpcEntityId;
  private byte networkService;
  /** Current local/network service. Gambling sales are accepted but never restocked. */
  private byte serviceType = NpcServiceType.TRADE;
  private long networkStockRevision;
  private final ItemReader networkItemReader = new ItemReader();
  private long pendingRequestId;
  private byte pendingOperation;
  private int pendingItemIndex = -1;
  private boolean pendingBuyToCursor;
  private WidgetGroup purchasePrompt;
  private Label purchasePromptBackground;
  private Label purchasePromptTitle;
  private Label purchasePromptItem;
  private Label purchasePromptPrice;
  private LabelButton purchaseConfirm;
  private LabelButton purchaseCancel;
  private Item pendingPurchaseItem;
  private final Vector2 purchasePromptPoint = new Vector2();
  private final InputListener purchasePromptDismissListener = new InputListener() {
    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
      if (!isCurrentSession() || purchasePrompt == null || !purchasePrompt.isVisible()) return false;
      if (isInsidePurchasePrompt(event)) return false;
      hidePurchasePrompt();
      return true;
    }
  };

  // Keep the purchase prompt as compact as ItemLabeler's tooltip. The
  // vertical buttons do not require a fixed-width dialog reservation.
  private static final float PURCHASE_PROMPT_CONTENT_MARGIN = 12;
  private static final float PURCHASE_PROMPT_PADDING = 0;
  private static final float PURCHASE_PROMPT_ROW_SPACING = 0;
  private static final float PURCHASE_PROMPT_SIDE_MARGIN = 6;

  public VendorPanel() {
    this(null);
  }

  public VendorPanel(GameScreen owner) {
    this.owner = owner;
    world = owner == null ? Riiablo.engine : null;
    try {
      references.load(buysellDescriptor);
      assets.finishLoadingAsset(buysellDescriptor);
      buysell = assets.get(buysellDescriptor).getTexture();
      setSize(buysell.getRegionWidth(), buysell.getRegionHeight());
      setTouchable(Touchable.enabled);
      setVisible(false);
      addListener(new ClickListener(Input.Buttons.LEFT) {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          if (event.isHandled() || purchasePrompt != null && purchasePrompt.isVisible()) return;
          if (sellCursorItem()) event.handle();
        }
      });

      references.load(buyselltabsDescriptor);
      assets.finishLoadingAsset(buyselltabsDescriptor);
      buyselltabs = assets.get(buyselltabsDescriptor);
      // Own the button sheet before constructing styles; do not depend on another panel.
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);

      float tabY = getHeight() - buyselltabs.getTexture().getRegionHeight();
      float tabX = 0;
      tabs = new Tab[4];
      for (int i = 0; i < tabs.length; i++) {
        Tab tab = tabs[i] = new Tab(buyselltabs.getTexture(i), buyselltabs.getTexture(i + tabs.length));
        tab.setPosition(tabX, tabY);
        tabX += tab.getWidth() + 1;
        addActor(tab);
      }

      String[] names = {"strBSArmor", "strBSWeapons", "strBSWeapons", "strBSMisc"};
      LabelButton[] labels = new LabelButton[4];
      for (int i = 0; i < labels.length; i++) {
        final LabelButton label = labels[i] = LabelButton.i18n(names[i], Riiablo.fonts.font16);
        label.setAlignment(Align.center);
        Tab tab = tabs[i];
        tab.label = label;
        label.setPosition(tab.getX(), tab.getY());
        label.setSize(tab.getWidth(), tab.getHeight());
        label.setUserObject(tab);
        label.addListener(new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            if (!isCurrentSession()) return;
            setTab((Tab) label.getUserObject());
          }
        });
        addActor(label);
      }

      final float[] X = {116, 168, 220, 272};
      Button.ButtonStyle blankButtonStyle = new Button.ButtonStyle() {{
        up = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(0));
      }};
      btnBlank = new Button[4];
      for (int i = 0; i < btnBlank.length; i++) {
        Button button = btnBlank[i] = new SessionButton(blankButtonStyle);
        button.setDisabledBlendMode(BlendMode.NONE, Riiablo.colors.white);
        button.setDisabled(true);
        button.setPosition(X[i], 15);
        button.setVisible(false);
        addActor(button);
      }

      btnBuy = new SessionButton(new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(2));
        down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(3));
        checked = down;
      }});
      btnBuy.setPosition(btnBlank[0].getX(), btnBlank[0].getY());
      btnBuy.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          repairing = false;
          selling = false;
          if (btnBuy.isChecked()) {
            cursor.setCursor(cursor.buysell, 3);
          } else {
            cursor.resetCursor();
          }
        }
      });
      btnBuy.setVisible(false);
      addActor(btnBuy);

      btnSell = new SessionButton(new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(4));
        down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(5));
        checked = down;
      }});
      btnSell.setPosition(btnBlank[1].getX(), btnBlank[1].getY());
      btnSell.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          repairing = false;
          selling = btnSell.isChecked();
          Gdx.app.log(TAG, "[VENDOR_MODE] mode=sell enabled=" + selling);
          if (btnSell.isChecked()) {
            cursor.setCursor(cursor.buysell, 4);
          } else {
            cursor.resetCursor();
          }
        }
      });
      btnSell.setVisible(false);
      addActor(btnSell);

      btnRepair = new SessionButton(new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(6));
        down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(7));
        checked = down;
      }});
      btnRepair.setPosition(btnBlank[2].getX(), btnBlank[2].getY());
      btnRepair.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          repairing = btnRepair.isChecked();
          selling = false;
          if (btnRepair.isChecked()) {
            cursor.setCursor(cursor.buysell, 1);
          } else {
            cursor.resetCursor();
          }
        }
      });
      btnRepair.setVisible(false);
      addActor(btnRepair);

      btnRepairAll = new SessionButton(new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(18));
        down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(19));
      }});
      // Repair feedback (including no-op repairs) is played after the result.
      btnRepairAll.setClickSoundEnabled(false);
      btnRepairAll.setPosition(btnBlank[3].getX(), btnBlank[3].getY());
      btnRepairAll.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          repairAll();
          event.handle();
        }
      });
      btnRepairAll.setVisible(false);
      addActor(btnRepairAll);

      btnExit = new SessionButton(new Button.ButtonStyle() {{
        up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
        down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
      }});
      btnExit.setPosition(btnBlank[3].getX(), btnBlank[3].getY());
      btnExit.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          GameScreen game = game();
          if (game != null) game.closeVendorTradePanels();
          else setVisible(false);
        }
      });
      btnExit.setVisible(false);
      addActor(btnExit);

      buttonGroup = new ButtonGroup<>();
      buttonGroup.setMinCheckCount(0);
      buttonGroup.add(btnBuy, btnSell, btnRepair);

      Label goldbankLabel = Label.i18n("stash", Riiablo.fonts.font16);
      goldbankLabel.setSize(180, 16);
      goldbankLabel.setPosition(20, 57);
      addActor(goldbankLabel);

      StatRef goldbankStat = character.getStats().get(Stat.goldbank);
      goldLabel = new Label(Integer.toString(goldbankStat != null ? goldbankStat.asInt() : 0), Riiablo.fonts.font16);
      goldLabel.setAutoSize(false);
      goldLabel.setSize(goldLabel.getPrefWidth(), goldbankLabel.getHeight());
      goldLabel.setPosition(
          goldbankLabel.getX() + goldbankLabel.getWidth() - goldLabel.getWidth(),
          goldbankLabel.getY());
      addActor(goldLabel);

      inventory = Riiablo.files.inventory.get("Monster");
      for (int i = 0; i < tabs.length; i++) {
        VendorGrid grid = tabs[i].grid = new VendorGrid(inventory, null) {
          @Override protected boolean isActive() { return super.isActive() && isCurrentSession(); }
        };
        grid.setPurchaseListener(new VendorGrid.PurchaseListener() {
          @Override
          public boolean onPurchase(Item item, boolean direct) {
            return purchase(item, direct);
          }

          @Override
          public boolean onSell(Item item) {
            if (!isCurrentSession()) return false;
            return sellCursorItem();
          }
        });
        grid.setPosition(
            inventory.gridLeft - inventory.invLeft,
            getHeight() - inventory.gridTop - grid.getHeight());
        grid.setVisible(false);
        addActor(grid);
      }

      purchasePrompt = new WidgetGroup();
      purchasePrompt.setTouchable(Touchable.enabled);
      purchasePrompt.setSize(1, 1);
      centerPurchasePrompt();
      purchasePrompt.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
          return isCurrentSession();
        }
      });
      purchasePromptBackground = new Label("", Riiablo.fonts.fontformal11);
      // Reuse the native MENU/boxpieces.dc6 frame used by NPC/task dialogs.
      // This changes only the prompt presentation; purchase confirmation and
      // cancellation behavior remain unchanged.
      purchasePromptBackground.getStyle().background = purchasePromptBorder = new BorderedPaletteIndexedDrawable();
      purchasePromptBackground.setTouchable(Touchable.disabled);
      purchasePromptBackground.setBounds(0, 0, purchasePrompt.getWidth(), purchasePrompt.getHeight());
      purchasePrompt.addActor(purchasePromptBackground);
      purchasePromptTitle = new Label(Riiablo.bundle.get("vendor_buy_title"),
          Riiablo.fonts.fontformal11, Riiablo.colors.gold);
      purchasePromptTitle.setAlignment(Align.center);
      purchasePromptTitle.setBounds(0, 0, purchasePrompt.getWidth(), 1);
      purchasePrompt.addActor(purchasePromptTitle);
      purchasePromptItem = new Label("", Riiablo.fonts.fontformal11, Riiablo.colors.gold);
      purchasePromptItem.setAlignment(Align.center);
      purchasePromptItem.setBounds(0, 0, purchasePrompt.getWidth(), 1);
      purchasePrompt.addActor(purchasePromptItem);
      purchasePromptPrice = new Label("", Riiablo.fonts.fontformal11, Riiablo.colors.gold);
      purchasePromptPrice.setAlignment(Align.center);
      purchasePromptPrice.setBounds(0, 0, purchasePrompt.getWidth(), 1);
      purchasePrompt.addActor(purchasePromptPrice);
      // LabelButton already follows the native text treatment: white at rest and
      // palette blue while hovered/pressed.
      purchaseConfirm = new LabelButton(Riiablo.bundle.get("yes"), Riiablo.fonts.fontformal11);
      purchaseConfirm.setAlignment(Align.center);
      purchaseConfirm.setBounds(0, 0, 1, 1);
      purchaseConfirm.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          Item item = pendingPurchaseItem;
          hidePurchasePrompt();
          if (item != null) purchaseNow(item, false);
        }
      });
      purchasePrompt.addActor(purchaseConfirm);
      purchaseCancel = new LabelButton(Riiablo.bundle.get("no"), Riiablo.fonts.fontformal11);
      purchaseCancel.setAlignment(Align.center);
      purchaseCancel.setBounds(0, 0, 1, 1);
      purchaseCancel.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          hidePurchasePrompt();
        }
      });
      purchasePrompt.addActor(purchaseCancel);
      purchasePrompt.setVisible(false);
      addActor(purchasePrompt);

      //setDebug(true, true);
      PanelSession.guardTree(this, this::isCurrentSession);
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    } finally { initializing = false; }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    boolean resetCursor = isCurrentSession();
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::hidePurchasePrompt);
      cleanup.run(() -> resetTradeMode(resetCursor));
      for (Button button : new Button[] {btnBuy, btnSell, btnRepair, btnRepairAll, btnExit}) {
        cleanup.dispose(button); cleanup.run(() -> ActorTree.clear(button));
      }
      if (btnBlank != null) for (Button button : btnBlank) {
        cleanup.dispose(button); cleanup.run(() -> ActorTree.clear(button));
      }
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> ActorTree.clear(purchasePrompt));
      if (tabs != null) for (Tab tab : tabs) {
        if (tab != null) {
          cleanup.run(() -> ActorTree.clear(tab.grid));
          cleanup.dispose(tab.grid);
        }
      }
      cleanup.run(() -> {
        if (purchasePromptBackground != null) purchasePromptBackground.getStyle().background = null;
      });
      cleanup.dispose(purchasePromptBorder);
      cleanup.dispose(references);
    } finally {
      if (tabs != null) for (Tab tab : tabs) if (tab != null) {
        if (tab.label != null) tab.label.setUserObject(null);
        tab.grid = null; tab.label = null; java.util.Arrays.fill(tab.modes, null);
      }
      tabs = null; activeGrid = null; buttonGroup = null; btnBlank = null;
      btnBuy = btnSell = btnRepair = btnRepairAll = btnExit = null;
      purchasePrompt = null; purchasePromptBackground = null; purchasePromptTitle = null;
      purchasePromptItem = null; purchasePromptPrice = null; purchaseConfirm = purchaseCancel = null;
      purchasePromptBorder = null;
      buysell = null;
      buyselltabs = null;
      pendingPurchaseItem = null;
      localStock = null; localPricing = null; localVendorGenerator = null; permanentStockBases.clear();
      networkSynchronizer = null; clientSocket = null; owner = null; world = null; goldLabel = null;
      networkFlags = configuredFlags = networkNpcEntityId = 0; networkStockRevision = 0;
      clearPendingRequest();
    }
  }

  private boolean isCurrentSession() {
    return !disposed && character == Riiablo.charData && assets == Riiablo.assets
        && itemData == character.getItems() && audio == Riiablo.audio && cursor == Riiablo.cursor
        // GameScreen publishes itself only after create() has built its panels.
        && (initializing || world == Riiablo.engine && (owner != null ? Riiablo.game == owner
            : Riiablo.game == null || Riiablo.game.vendorPanel == this));
  }

  private GameScreen game() { return owner != null ? owner : Riiablo.game; }

  private class SessionButton extends Button {
    SessionButton(ButtonStyle style) { super(style); }
    @Override public boolean isDisabled() { return !isCurrentSession() || super.isDisabled(); }
  }

  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }

  @Override public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }

  private void showPurchasePrompt(Item item) {
    if (!isCurrentSession() || item == null
        || itemData.getCursor() != null) return;
    hidePurchasePrompt();
    pendingPurchaseItem = item;
    try {
      int price = isGambling()
          ? VendorPricing.gamblePrice(item, character)
          : localPricing == null
              ? VendorPricing.buyPrice(item)
              : VendorPricing.buyPrice(item, localPricing, character);
      purchasePromptItem.setText(item.getNameString());
      purchasePromptPrice.setText(Riiablo.bundle.format("vendor_buy_gold", price));
      // The native dialog grows only for a long item name. Keep a small fixed
      // margin around the widest line instead of reserving the whole vendor
      // panel, then re-center every child in the resized window.
      float contentWidth = Math.max(purchasePromptItem.getPrefWidth(), purchasePromptTitle.getPrefWidth());
      contentWidth = Math.max(contentWidth, purchaseConfirm.getPrefWidth());
      contentWidth = Math.max(contentWidth, purchaseCancel.getPrefWidth());
      float width = contentWidth + PURCHASE_PROMPT_CONTENT_MARGIN;
      width = Math.min(width, Math.max(16, getWidth() - 16));
      float lineHeight = Riiablo.fonts.fontformal11.getLineHeight();
      float priceHeight = Math.max(lineHeight, purchasePromptPrice.getPrefHeight());
      float height = PURCHASE_PROMPT_PADDING * 2
          + lineHeight * 4
          + priceHeight
          + PURCHASE_PROMPT_ROW_SPACING * 4;
      purchasePrompt.setSize(width, height);
      purchasePromptBackground.setBounds(0, 0, width, height);
      float buttonWidth = Math.max(1, width - PURCHASE_PROMPT_SIDE_MARGIN * 2);
      float y = PURCHASE_PROMPT_PADDING;
      purchaseCancel.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, y, buttonWidth, lineHeight);
      y += lineHeight + PURCHASE_PROMPT_ROW_SPACING;
      purchaseConfirm.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, y, buttonWidth, lineHeight);
      y += lineHeight + PURCHASE_PROMPT_ROW_SPACING;
      purchasePromptPrice.setBounds(0, y, width, priceHeight);
      y += priceHeight + PURCHASE_PROMPT_ROW_SPACING;
      purchasePromptItem.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, y,
          width - PURCHASE_PROMPT_SIDE_MARGIN * 2, lineHeight);
      y += lineHeight + PURCHASE_PROMPT_ROW_SPACING;
      purchasePromptTitle.setBounds(0, y, width, lineHeight);
      positionPurchasePromptAtMouse();
      purchasePromptStage = getStage();
      if (purchasePromptStage != null) purchasePromptStage.addCaptureListener(purchasePromptDismissListener);
      purchasePrompt.setVisible(true);
    } catch (RuntimeException | Error failure) {
      try { hidePurchasePrompt(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void centerPurchasePrompt() {
    purchasePrompt.setPosition((getWidth() - purchasePrompt.getWidth()) / 2f,
        (getHeight() - purchasePrompt.getHeight()) / 2f);
  }

  private void positionPurchasePromptAtMouse() {
    if (getStage() == null || Gdx.input == null) {
      centerPurchasePrompt();
      return;
    }
    purchasePromptPoint.set(Gdx.input.getX(), Gdx.input.getY());
    getStage().screenToStageCoordinates(purchasePromptPoint);
    stageToLocalCoordinates(purchasePromptPoint);

    float yesCenterX = purchaseConfirm.getX() + purchaseConfirm.getWidth() / 2f;
    float yesCenterY = purchaseConfirm.getY() + purchaseConfirm.getHeight() / 2f;
    purchasePrompt.setPosition(
        purchasePromptPoint.x - yesCenterX,
        purchasePromptPoint.y - yesCenterY);
  }

  private boolean isInsidePurchasePrompt(InputEvent event) {
    purchasePromptPoint.set(event.getStageX(), event.getStageY());
    purchasePrompt.stageToLocalCoordinates(purchasePromptPoint);
    return purchasePrompt.hit(purchasePromptPoint.x, purchasePromptPoint.y, true) != null;
  }

  private void hidePurchasePrompt() {
    pendingPurchaseItem = null;
    Stage stage = purchasePromptStage;
    purchasePromptStage = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> { if (purchasePrompt != null) purchasePrompt.setVisible(false); });
      if (stage != null) cleanup.run(() -> stage.removeCaptureListener(purchasePromptDismissListener));
    }
  }

  private boolean sellCursorItem() {
    if (!isCurrentSession()) return false;
    Item cursor = itemData.getCursor();
    return cursor != null && canSellItems()
        && sellItem(itemData.indexOf(cursor));
  }

  @Override
  public void draw(Batch batch, float a) {
    if (!isCurrentSession()) return;
    refreshGold();
    batch.draw(buysell, getX(), getY());
    super.draw(batch, a);
  }

  @Override
  public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    super.setVisible(visible);
    if (visible) {
      GameScreen game = game();
      if (game != null) game.setRightPanel(game.inventoryPanel);
    } else {
      networkFlags = 0;
      clearPendingRequest();
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(this::hidePurchasePrompt);
        cleanup.run(() -> resetTradeMode(isCurrentSession()));
      }
    }
  }

  public void config(int flags, Array<Item> items) {
    config(flags, items, localPricing);
  }

  public void config(int flags, Array<Item> items, Npc.Entry pricing) {
    config(flags, items, pricing, NpcServiceType.TRADE);
  }

  public void config(int flags, Array<Item> items, Npc.Entry pricing, byte service) {
    config(flags, items, pricing, service, null);
  }

  public void config(int flags, Array<Item> items, Npc.Entry pricing, byte service,
                     VendorGenerator generator) {
    if (!isCurrentSession()) return;
    boolean newStock = localStock != items || serviceType != service;
    if (newStock) {
      permanentStockBases.clear();
      if (service != NpcServiceType.GAMBLE && items != null) {
        for (Item item : items) {
          if (VendorPricing.isPermanentStoreItem(item)) permanentStockBases.add(item.base);
        }
      }
    }
    serviceType = service;
    networkFlags = 0;
    clearPendingRequest();
    localStock = items;
    localPricing = pricing;
    localVendorGenerator = generator;
    configuredFlags = flags;
    selling = false;
    repairing = false;
    buttonGroup.uncheckAll();
    btnBuy.setVisible((flags & BUY) == BUY);
    btnSell.setVisible((flags & SELL) == SELL);
    btnRepair.setVisible((flags & REPAIR) == REPAIR);
    btnRepairAll.setVisible((flags & REPAIR_ALL) == REPAIR_ALL);
    btnExit.setVisible((flags & EXIT) == EXIT);
    for (int i = 0; i < btnBlank.length; i++) {
      btnBlank[i].setVisible((flags & BLANK_MASKS[i]) == 0);
    }

    // TODO: supply cleaner API grid.drain(items, "misc") or similar
    Array<Item> tmp = new Array<>(true, items.size, Item.class);
    try {
      int count;
      collect(items, tmp, "armo");
      count = tabs[TAB_ARMOR].grid.drain(tmp);
      tabs[TAB_ARMOR].setVisible(count > 0);
      Gdx.app.debug(TAG, "Dropping " + tmp);

      collect(items, tmp, "weap");
      count = tabs[TAB_WEAPONS].grid.drain(tmp);
      tabs[TAB_WEAPONS].setVisible(count > 0);

      count = tabs[TAB_WEAPONS2].grid.drain(tmp);
      tabs[TAB_WEAPONS2].setVisible(count > 0);
      Gdx.app.debug(TAG, "Dropping " + tmp);

      collect(items, tmp, "misc");
      count = tabs[TAB_MISC].grid.drain(tmp);
      tabs[TAB_MISC].setVisible(count > 0);
      Gdx.app.debug(TAG, "Dropping " + tmp);
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
    }

    setTab(TAB_MISC);
  }

  public void setNetworkSynchronizer(ClientNetworkSynchronizer networkSynchronizer) {
    if (disposed) return;
    this.networkSynchronizer = networkSynchronizer;
  }

  /** Opens a server-owned vendor session. Local inventory generation is skipped. */
  public void configNetwork(int flags, int npcEntityId, byte service) {
    configNetwork(flags, npcEntityId, service, null);
  }

  public void configNetwork(int flags, int npcEntityId, byte service, Npc.Entry pricing) {
    if (!isCurrentSession()) return;
    config(flags, new Array<Item>(false, 0, Item.class), pricing, service);
    networkFlags = flags;
    networkNpcEntityId = npcEntityId;
    networkService = service;
    networkStockRevision = 0;
    if (networkSynchronizer != null) {
      long requestId = networkSynchronizer.requestNpcService(npcEntityId, service, NpcServiceOperation.OPEN,
          0, -1, 0);
      if (requestId != 0) {
        pendingRequestId = requestId;
        pendingOperation = NpcServiceOperation.OPEN;
      }
    } else {
      Gdx.app.error(TAG, "Network vendor requested without ClientNetworkSynchronizer");
    }
  }

  public boolean isNetworked() {
    return isCurrentSession() && clientSocket != null && networkSynchronizer != null;
  }

  /** Applies an authoritative vendor/repair response to the visible UI. */
  public void applyNetworkResult(NpcServiceResult result) {
    if (!isCurrentSession() || result == null) return;
    VendorPricing.setGoldSnapshot(character,
        (int) Math.min(Integer.MAX_VALUE, result.gold()),
        (int) Math.min(Integer.MAX_VALUE, result.goldBank()));
    if (result.requestId() == pendingRequestId) {
      if (result.success() && pendingOperation == NpcServiceOperation.BUY
          && result.itemDataLength() > 0) {
        Item purchased = decodeItem(result.itemId(), result.itemDataAsByteBuffer(), false);
        if (purchased != null && !itemData.contains(purchased)) {
          boolean placed = pendingBuyToCursor
              ? itemData.addToCursor(purchased)
              : itemData.addAutoPickup(purchased, character);
          if (!placed) {
            Gdx.app.error(TAG, "[VENDOR_BUY] requested destination unavailable; preserving item in inventory");
            itemData.addToInventory(purchased);
          } else if (audio != null) {
            audio.play(purchased.getDropSound(), true);
          }
        }
      } else if (result.success() && pendingOperation == NpcServiceOperation.SELL) {
        Item sold = pendingItemIndex >= 0 && pendingItemIndex < itemData.getItems().size
            ? itemData.getItems().get(pendingItemIndex) : null;
        if (sold != null) {
          itemData.removeOwnedItem(sold);
          playSellSounds(sold);
        }
      } else if (pendingOperation == NpcServiceOperation.REPAIR_ITEM) {
        if (result.success()) {
          Item repaired = findOwnedItem(result.itemId(), pendingItemIndex);
          if (repaired != null && new ItemDurabilityManager().restoreDurability(repaired)) {
            itemData.updateStats();
          }
        }
        if (result.success() || "NOTHING_TO_REPAIR".equals(result.reason())) {
          playRepairSound();
        }
      } else if (pendingOperation == NpcServiceOperation.REPAIR_ALL) {
        if (result.success()) applyRepairAllResult();
        if (result.success() || "NOTHING_TO_REPAIR".equals(result.reason())) {
          playRepairSound();
        }
      }
      if (pendingOperation == NpcServiceOperation.SELL) {
        Gdx.app.log(TAG, "[VENDOR_SELL] phase=result request=" + result.requestId()
            + " success=" + result.success() + " reason=" + result.reason()
            + " itemIndex=" + pendingItemIndex);
      }
      pendingRequestId = 0;
      pendingItemIndex = -1;
      pendingBuyToCursor = false;
    }
    if (networkFlags == 0) return;
    // REPAIR and early validation failures carry no vendor-stock snapshot.
    // Preserve the already visible trade page instead of replacing it with an
    // empty list when only durability or wallet state changed.
    if (result.stockRevision() == 0 && result.stockLength() == 0) {
      if (!result.success()) Gdx.app.log(TAG, "[NPC_SERVICE] " + result.reason());
      return;
    }
    networkStockRevision = result.stockRevision();
    boolean restoreSelling = isSelling();
    boolean restoreRepairing = isRepairing();
    Array<Item> stock = new Array<>(true, result.stockLength(), Item.class);
    for (int i = 0; i < result.stockLength(); i++) {
      NpcServiceStock entry = result.stock(i);
      if (entry == null || entry.itemDataLength() == 0) continue;
      try {
        Item item = decodeItem(entry.itemId(), entry.itemDataAsByteBuffer(), true);
        if (item != null) item.vendorPrice = (int) Math.min(Integer.MAX_VALUE, entry.price());
        if (item != null) stock.add(item);
      } catch (Throwable t) {
        Gdx.app.error(TAG, "Failed to decode server vendor item " + entry.itemId(), t);
      }
    }
    int flags = networkFlags;
    config(flags, stock, localPricing, networkService);
    networkFlags = flags;
    restoreTradeMode(restoreSelling, restoreRepairing);
    if (!result.success()) Gdx.app.log(TAG, "[NPC_SERVICE] " + result.reason());
  }

  private void restoreTradeMode(boolean restoreSelling, boolean restoreRepairing) {
    if (!isCurrentSession()) return;
    if (restoreSelling && (configuredFlags & SELL) != 0) {
      btnSell.setChecked(true);
      selling = true;
      repairing = false;
      cursor.setCursor(cursor.buysell, 4);
    } else if (restoreRepairing && (configuredFlags & REPAIR) != 0) {
      btnRepair.setChecked(true);
      repairing = true;
      selling = false;
      cursor.setCursor(cursor.buysell, 1);
    }
  }

  private Item decodeItem(int itemId, java.nio.ByteBuffer data, boolean inStore) {
    if (data == null) return null;
    byte[] bytes = BufferUtils.readRemaining(data);
    Item item = networkItemReader.readItem(ByteInput.wrap(bytes));
    item.id = itemId;
    if (inStore) item.flags2 |= Item.ITEMFLAG2_INSTORE;
    item.load();
    return item;
  }

  public boolean isSelling() {
    return isCurrentSession() && isVisible() && (selling || btnSell.isChecked());
  }

  public boolean isRepairing() {
    return isCurrentSession() && isVisible() && (repairing || btnRepair.isChecked());
  }

  /** Clears the active Buy/Sell/Repair cursor mode without closing the vendor. */
  public void cancelTradeMode() {
    if (!isCurrentSession()) return;
    resetTradeMode(true);
  }

  private void resetTradeMode(boolean resetCursor) {
    boolean hadTradeMode = selling || repairing
        || buttonGroup != null && buttonGroup.getCheckedIndex() >= 0;
    selling = false;
    repairing = false;
    if (buttonGroup != null) buttonGroup.uncheckAll();
    if (resetCursor && hadTradeMode && cursor != null && cursor.getItem() == null) {
      cursor.resetCursor();
    }
  }

  /** The trade window accepts native right-click selling without selecting Sell first. */
  public boolean canSellItems() {
    return isCurrentSession() && isVisible() && (configuredFlags & SELL) != 0;
  }

  /** Returns the exact local/native sale value shown by the trade tooltip. */
  public int sellPrice(Item item) {
    if (!isCurrentSession() || item == null) return 0;
    return VendorPricing.sellPrice(
        item, localPricing, character, character.diff);
  }

  /** Uses a separate table so the ordinary cached inventory tooltip is untouched. */
  public com.badlogic.gdx.scenes.scene2d.ui.Table sellDetails(
      Item item, AttributesUpdater updater) {
    if (!isCurrentSession()) return null;
    return item.sellDetails(updater, sellPrice(item));
  }

  private boolean isGambling() {
    return serviceType == NpcServiceType.GAMBLE;
  }

  public boolean repairItem(int itemIndex) {
    if (!isCurrentSession() || !isVisible() || !repairing) return false;
    if (itemIndex < 0 || itemIndex >= itemData.getItems().size) return false;
    Item item = itemData.getItem(itemIndex);
    if (networkFlags != 0 && networkSynchronizer != null) {
      if (pendingRequestId != 0 || item == null) return false;
      long requestId = networkSynchronizer.requestNpcService(networkNpcEntityId,
          NpcServiceType.REPAIR, NpcServiceOperation.REPAIR_ITEM,
          item.id, itemIndex, 0);
      if (requestId == 0) return false;
      pendingRequestId = requestId;
      pendingOperation = NpcServiceOperation.REPAIR_ITEM;
      pendingItemIndex = itemIndex;
      return false;
    }
    NpcRepairService.Result result = NpcRepairService.repairItem(
        character, localPricing, itemIndex, item.id);
    if (!result.success) {
      if ("NOTHING_TO_REPAIR".equals(result.reason)) playRepairSound();
      return false;
    }
    refreshGold();
    playRepairSound();
    Gdx.app.debug(TAG, "Repaired " + item.code + " for " + result.cost + " gold");
    return true;
  }

  private void repairAll() {
    if (!isCurrentSession()) return;
    cancelTradeMode();
    if (networkFlags != 0 && networkSynchronizer != null) {
      if (pendingRequestId != 0) return;
      long requestId = networkSynchronizer.requestNpcService(networkNpcEntityId,
          NpcServiceType.REPAIR, NpcServiceOperation.REPAIR_ALL, 0, -1, 0);
      if (requestId == 0) return;
      pendingRequestId = requestId;
      pendingOperation = NpcServiceOperation.REPAIR_ALL;
      pendingItemIndex = -1;
      return;
    }
    NpcRepairService.Result result = NpcRepairService.repairAll(
        character, localPricing);
    if (!result.success) {
      if ("NOTHING_TO_REPAIR".equals(result.reason)) playRepairSound();
      return;
    }
    refreshGold();
    playRepairSound();
    Gdx.app.debug(TAG, "Repaired all equipment for " + result.cost + " gold");
  }

  private Item findOwnedItem(int itemId, int fallbackIndex) {
    if (character == null) return null;
    Array<Item> items = itemData.getItems();
    if (itemId != 0) {
      for (Item item : items) if (item != null && item.id == itemId) return item;
    }
    return fallbackIndex >= 0 && fallbackIndex < items.size ? items.get(fallbackIndex) : null;
  }

  private void applyRepairAllResult() {
    if (character == null) return;
    boolean changed = false;
    for (Item item : itemData.getItems()) {
      if (item != null && item.location == Location.EQUIPPED) {
        changed |= new ItemDurabilityManager().restoreDurability(item);
      }
    }
    if (changed) itemData.updateStats();
  }

  private void playRepairSound() {
    if (audio != null) audio.play("cursor_repair_item", true);
  }

  /** Called by the inventory grid when the Sell mode is active. */
  public boolean sellItem(int itemIndex) {
    if (!isCurrentSession() || (!isSelling() && !canSellItems())) return false;
    Item item = itemIndex >= 0 && itemIndex < itemData.getItems().size
        ? itemData.getItem(itemIndex) : null;
    if (item == null) {
      Gdx.app.log(TAG, "[VENDOR_SELL] phase=reject itemIndex=" + itemIndex
          + " reason=item_not_owned");
      return false;
    }
    boolean addToStock = localStock != null && !isGambling() && canAddSoldItem(item);
    if (networkFlags != 0 && networkSynchronizer != null) {
      if (pendingRequestId != 0) {
        Gdx.app.log(TAG, "[VENDOR_SELL] phase=reject item=" + item.id
            + " reason=request_pending request=" + pendingRequestId);
        return false;
      }
      long requestId = networkSynchronizer.requestNpcService(networkNpcEntityId, networkService,
          NpcServiceOperation.SELL, 0, itemIndex, networkStockRevision);
      if (requestId == 0) return false;
      pendingRequestId = requestId;
      pendingOperation = NpcServiceOperation.SELL;
      pendingItemIndex = itemIndex;
      Gdx.app.log(TAG, "[VENDOR_SELL] phase=request request=" + requestId
          + " itemIndex=" + itemIndex + " stockRevision=" + networkStockRevision);
      // The click is handled even though inventory removal waits for the
      // authoritative result. Returning false would make InventoryPanel send
      // a conflicting STORE_TO_CURSOR request for the same item.
      return true;
    }
    int value = VendorPricing.sellPrice(item, localPricing, character,
        character.diff);
    boolean sold = VendorPricing.sell(character, itemIndex, localPricing,
        character.diff);
    if (sold) {
      if (localStock != null) {
        // Native D2 adds a vendor copy to the shared trade inventory. Keep the
        // object out of the player inventory while preserving its id and data
        // so it can be bought back before the town stock is refreshed.
        item.flags2 |= Item.ITEMFLAG2_INSTORE;
        item.location = Location.STORED;
        item.storeLoc = StoreLoc.NONE;
        item.gridX = 0;
        item.gridY = 0;
        item.vendorPrice = -1;
        if (addToStock && !VendorPricing.isQuiver(item)
            && !localStock.contains(item, true)) localStock.insert(0, item);
      }
      Gdx.app.debug(TAG, "Sold " + item.code + " for " + value + " gold");
      Gdx.app.log(TAG, "[VENDOR_SELL] phase=result mode=local success=true item="
          + item.id + " value=" + value);
      refreshGold();
      playSellSounds(item);
      if (localStock != null) {
        boolean restoreSelling = selling;
        boolean restoreRepairing = repairing;
        config(configuredFlags, localStock, localPricing, serviceType, localVendorGenerator);
        restoreTradeMode(restoreSelling, restoreRepairing);
      }
    } else {
      Gdx.app.log(TAG, "[VENDOR_SELL] phase=reject mode=local item=" + item.id
          + " location=" + item.location + " store=" + item.storeLoc);
    }
    return sold;
  }

  private void playSellSounds(Item item) {
    if (audio == null) return;
    audio.play("item_gold", true);
    if (item != null) audio.play(item.getDropSound(), true);
  }

  private boolean canAddSoldItem(Item item) {
    if (item == null || VendorPricing.isQuiver(item)
        || item.base != null && permanentStockBases.contains(item.base)) return false;
    String page = item.typeEntry == null ? "misc" : item.typeEntry.StorePage;
    if ("armo".equalsIgnoreCase(page)) return tabs[TAB_ARMOR].grid.hasRoom(item);
    if ("weap".equalsIgnoreCase(page)) {
      return tabs[TAB_WEAPONS].grid.hasRoom(item) || tabs[TAB_WEAPONS2].grid.hasRoom(item);
    }
    return tabs[TAB_MISC].grid.hasRoom(item);
  }

  /** Left-click asks for confirmation; right-click purchases directly. */
  private boolean purchase(Item item, boolean direct) {
    if (!isCurrentSession()) return false;
    if (!direct) {
      showPurchasePrompt(item);
      return false;
    }
    // A right-click is a new action. Leave Buy/Sell/Repair mode before
    // allowing the normal direct-purchase path to run.
    cancelTradeMode();
    return purchaseNow(item, true);
  }

  /** Executes a confirmed purchase. */
  private boolean purchaseNow(Item item, boolean direct) {
    if (!isCurrentSession() || selling) return false;
    boolean toCursor = !direct;
    if (toCursor && itemData.getCursor() != null) return false;
    if (networkFlags != 0 && networkSynchronizer != null) {
      if (pendingRequestId != 0) return false;
      long requestId = networkSynchronizer.requestNpcService(networkNpcEntityId, networkService,
          NpcServiceOperation.BUY, item.id,
          toCursor ? com.riiablo.engine.server.npc.NpcServiceProtocol.BUY_TO_CURSOR_ITEM_INDEX : -1,
          networkStockRevision);
      if (requestId == 0) return false;
      pendingRequestId = requestId;
      pendingOperation = NpcServiceOperation.BUY;
      pendingBuyToCursor = toCursor;
      return false;
    }
    int value = isGambling()
        ? VendorPricing.gamblePrice(item, character)
        : VendorPricing.buyPrice(item, localPricing, character);
    boolean bought = isGambling()
        ? (toCursor
            ? VendorPricing.gambleToCursor(character, item)
            : VendorPricing.gamble(character, item))
        : (toCursor
            ? VendorPricing.buyToCursor(character, item, localPricing)
            : VendorPricing.buy(character, item, localPricing));
    if (bought) {
      if (localStock != null) {
        int stockIndex = localStock.indexOf(item, true);
        Item replacement = !isGambling() && VendorPricing.isInfiniteStockItem(item)
            && localVendorGenerator != null ? localVendorGenerator.restock(item,
                character == null ? Riiablo.NORMAL : character.diff) : null;
        if (replacement != null && stockIndex >= 0) localStock.set(stockIndex, replacement);
        else localStock.removeValue(item, true);
        config(configuredFlags, localStock, localPricing, serviceType, localVendorGenerator);
      }
      Gdx.app.debug(TAG, "Bought " + item.code + " for " + value + " gold");
      refreshGold();
      if (audio != null) audio.play(item.getDropSound(), true);
    }
    return bought;
  }

  private void clearPendingRequest() {
    pendingRequestId = 0;
    pendingOperation = 0;
    pendingItemIndex = -1;
    pendingBuyToCursor = false;
  }

  private void refreshGold() {
    if (goldLabel != null && character != null) {
      goldLabel.setText(Integer.toString(VendorPricing.availableGold(character)));
      goldLabel.setWidth(goldLabel.getPrefWidth());
      goldLabel.setX(20 + 180 - goldLabel.getWidth());
    }
  }

  private static Array<Item> collect(Array<Item> items, Array<Item> to, String page) {
    to.clear();
    for (Item item : items) {
      if (item.typeEntry.StorePage.equalsIgnoreCase(page)) {
        to.add(item);
      }
    }
    return to;
  }

  void setTab(Tab tab) {
    if (!isCurrentSession()) return;
    for (Tab t : tabs) t.setMode(Tab.INACTIVE);
    tab.setMode(Tab.ACTIVE);
    if (activeGrid != null) activeGrid.setVisible(false);
    activeGrid = tab.grid;
    activeGrid.setVisible(true);
  }

  public void setTab(int i) {
    if (!isCurrentSession()) return;
    setTab(tabs[i]);
  }

  private class Tab extends WidgetGroup {
    static final int ACTIVE   = 0;
    static final int INACTIVE = 1;

    final TextureRegion[] modes;
    int mode;

    Label label;
    VendorGrid grid;

    public Tab(TextureRegion active, TextureRegion inactive) {
      setSize(active.getRegionWidth(), active.getRegionHeight());
      modes = new TextureRegion[2];
      modes[ACTIVE]   = active;
      modes[INACTIVE] = inactive;
      mode = INACTIVE;
    }

    @Override
    public void setVisible(boolean visible) {
      if (disposed || visible && !isCurrentSession()) return;
      super.setVisible(visible);
      if (label != null) label.setVisible(visible);
    }

    public void setMode(int mode) {
      this.mode = mode;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      if (!isCurrentSession()) return;
      batch.draw(modes[mode], getX(), getY());
      super.draw(batch, parentAlpha);
    }
  }
}
