package com.riiablo.screen.panel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
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
import com.riiablo.loader.DC6Loader;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;

public class VendorPanel extends WidgetGroup implements Disposable {
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

  // Match ItemLabeler's compact tooltip padding (6px on each side). The
  // vertical buttons do not require a fixed-width dialog reservation.
  private static final float PURCHASE_PROMPT_CONTENT_MARGIN = 12;
  private static final float PURCHASE_PROMPT_HEIGHT = 116;
  private static final float PURCHASE_PROMPT_SIDE_MARGIN = 6;
  private static final float PURCHASE_PROMPT_BUTTON_HEIGHT = 22;

  public VendorPanel() {
    Riiablo.assets.load(buysellDescriptor);
    Riiablo.assets.finishLoadingAsset(buysellDescriptor);
    buysell = Riiablo.assets.get(buysellDescriptor).getTexture();
    setSize(buysell.getRegionWidth(), buysell.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);
    addListener(new ClickListener(Input.Buttons.LEFT) {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (event.isHandled() || purchasePrompt != null && purchasePrompt.isVisible()) return;
        if (sellCursorItem()) event.handle();
      }
    });

    Riiablo.assets.load(buyselltabsDescriptor);
    Riiablo.assets.finishLoadingAsset(buyselltabsDescriptor);
    buyselltabs = Riiablo.assets.get(buyselltabsDescriptor);

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
          setTab((Tab) label.getUserObject());
        }
      });
      addActor(label);
    }

    final float[] X = {116, 168, 220, 272};
    Button.ButtonStyle blankButtonStyle = new Button.ButtonStyle() {{
      up = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(0));
    }};
    btnBlank = new Button[4];
    for (int i = 0; i < btnBlank.length; i++) {
      Button button = btnBlank[i] = new Button(blankButtonStyle);
      button.setDisabledBlendMode(BlendMode.NONE, Riiablo.colors.white);
      button.setDisabled(true);
      button.setPosition(X[i], 15);
      button.setVisible(false);
      addActor(button);
    }

    btnBuy = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(2));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(3));
      checked = down;
    }});
    btnBuy.setPosition(btnBlank[0].getX(), btnBlank[0].getY());
    btnBuy.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        repairing = false;
        selling = false;
        if (btnBuy.isChecked()) {
          Riiablo.cursor.setCursor(Riiablo.cursor.buysell, 3);
        } else {
          Riiablo.cursor.resetCursor();
        }
      }
    });
    btnBuy.setVisible(false);
    addActor(btnBuy);

    btnSell = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(4));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(5));
      checked = down;
    }});
    btnSell.setPosition(btnBlank[1].getX(), btnBlank[1].getY());
    btnSell.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        repairing = false;
        selling = btnSell.isChecked();
        Gdx.app.log(TAG, "[VENDOR_MODE] mode=sell enabled=" + selling);
        if (btnSell.isChecked()) {
          Riiablo.cursor.setCursor(Riiablo.cursor.buysell, 4);
        } else {
          Riiablo.cursor.resetCursor();
        }
      }
    });
    btnSell.setVisible(false);
    addActor(btnSell);

    btnRepair = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(6));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(7));
      checked = down;
    }});
    btnRepair.setPosition(btnBlank[2].getX(), btnBlank[2].getY());
    btnRepair.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        repairing = btnRepair.isChecked();
        selling = false;
        if (btnRepair.isChecked()) {
          Riiablo.cursor.setCursor(Riiablo.cursor.buysell, 1);
        } else {
          Riiablo.cursor.resetCursor();
        }
      }
    });
    btnRepair.setVisible(false);
    addActor(btnRepair);

    btnRepairAll = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(18));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(19));
    }});
    btnRepairAll.setPosition(btnBlank[3].getX(), btnBlank[3].getY());
    btnRepairAll.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        repairing = true;
        selling = false;
        repairAll();
      }
    });
    btnRepairAll.setVisible(false);
    addActor(btnRepairAll);

    Riiablo.assets.load(buysellbtnDescriptor);
    Riiablo.assets.finishLoadingAsset(buysellbtnDescriptor);
    btnExit = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(btnBlank[3].getX(), btnBlank[3].getY());
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        setVisible(false);
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

    StatRef goldbankStat = Riiablo.charData.getStats().get(Stat.goldbank);
    goldLabel = new Label(Integer.toString(goldbankStat != null ? goldbankStat.asInt() : 0), Riiablo.fonts.font16);
    goldLabel.setSize(goldbankLabel.getWidth(), goldbankLabel.getHeight());
    goldLabel.setPosition(goldbankLabel.getX(), goldbankLabel.getY());
    goldLabel.setAlignment(Align.right);
    addActor(goldLabel);

    inventory = Riiablo.files.inventory.get("Monster");
    for (int i = 0; i < tabs.length; i++) {
      VendorGrid grid = tabs[i].grid = new VendorGrid(inventory, null);
      grid.setPurchaseListener(new VendorGrid.PurchaseListener() {
        @Override
        public boolean onPurchase(Item item, boolean direct) {
          return purchase(item, direct);
        }

        @Override
        public boolean onSell(Item item) {
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
    purchasePrompt.setSize(1, PURCHASE_PROMPT_HEIGHT);
    centerPurchasePrompt();
    purchasePrompt.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        return true;
      }
    });
    purchasePromptBackground = new Label("", Riiablo.fonts.fontformal11);
    // Reuse the native MENU/boxpieces.dc6 frame used by NPC/task dialogs.
    // This changes only the prompt presentation; purchase confirmation and
    // cancellation behavior remain unchanged.
    purchasePromptBackground.getStyle().background = new BorderedPaletteIndexedDrawable();
    purchasePromptBackground.setTouchable(Touchable.disabled);
    purchasePromptBackground.setBounds(0, 0, purchasePrompt.getWidth(), purchasePrompt.getHeight());
    purchasePrompt.addActor(purchasePromptBackground);
    purchasePromptTitle = new Label(Riiablo.bundle.get("vendor_buy_title"),
        Riiablo.fonts.fontformal11, Riiablo.colors.gold);
    purchasePromptTitle.setAlignment(Align.center);
    purchasePromptTitle.setBounds(0, 88, purchasePrompt.getWidth(), 16);
    purchasePrompt.addActor(purchasePromptTitle);
    purchasePromptItem = new Label("", Riiablo.fonts.fontformal11, Riiablo.colors.gold);
    purchasePromptItem.setAlignment(Align.center);
    purchasePromptItem.setBounds(0, 68, purchasePrompt.getWidth(), 18);
    purchasePrompt.addActor(purchasePromptItem);
    purchasePromptPrice = new Label("", Riiablo.fonts.fontformal11, Riiablo.colors.gold);
    purchasePromptPrice.setAlignment(Align.center);
    purchasePromptPrice.setBounds(0, 49, purchasePrompt.getWidth(), 16);
    purchasePrompt.addActor(purchasePromptPrice);
    // LabelButton already follows the native text treatment: white at rest and
    // palette blue while hovered/pressed.
    purchaseConfirm = new LabelButton(Riiablo.bundle.get("yes"), Riiablo.fonts.fontformal11);
    purchaseConfirm.setAlignment(Align.center);
    purchaseConfirm.setBounds(0, 25, 1, PURCHASE_PROMPT_BUTTON_HEIGHT);
    purchaseConfirm.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Item item = pendingPurchaseItem;
        hidePurchasePrompt();
        if (item != null) purchaseNow(item, false);
      }
    });
    purchasePrompt.addActor(purchaseConfirm);
    purchaseCancel = new LabelButton(Riiablo.bundle.get("no"), Riiablo.fonts.fontformal11);
    purchaseCancel.setAlignment(Align.center);
    purchaseCancel.setBounds(0, 2, 1, PURCHASE_PROMPT_BUTTON_HEIGHT);
    purchaseCancel.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        hidePurchasePrompt();
      }
    });
    purchasePrompt.addActor(purchaseCancel);
    purchasePrompt.setVisible(false);
    addActor(purchasePrompt);

    //setDebug(true, true);
  }

  @Override
  public void dispose() {
    btnBuy.dispose();
    btnSell.dispose();
    btnRepair.dispose();
    btnRepairAll.dispose();
    btnExit.dispose();
    for (int i = 0; i < btnBlank.length; i++) btnBlank[i].dispose();
    Riiablo.assets.unload(buysellDescriptor.fileName);
    Riiablo.assets.unload(buyselltabsDescriptor.fileName);
    Riiablo.assets.unload(buysellbtnDescriptor.fileName);
  }

  private void showPurchasePrompt(Item item) {
    if (item == null) return;
    pendingPurchaseItem = item;
    int price = isGambling()
        ? VendorPricing.gamblePrice(item, Riiablo.charData)
        : localPricing == null
            ? VendorPricing.buyPrice(item)
            : VendorPricing.buyPrice(item, localPricing, Riiablo.charData);
    purchasePromptItem.setText(item.getNameString());
    purchasePromptPrice.setText(Integer.toString(price));
    // The native dialog grows only for a long item name. Keep a small fixed
    // margin around the widest line instead of reserving the whole vendor
    // panel, then re-center every child in the resized window.
    float contentWidth = Math.max(purchasePromptItem.getPrefWidth(), purchasePromptTitle.getPrefWidth());
    contentWidth = Math.max(contentWidth, purchaseConfirm.getPrefWidth());
    contentWidth = Math.max(contentWidth, purchaseCancel.getPrefWidth());
    float width = contentWidth + PURCHASE_PROMPT_CONTENT_MARGIN;
    width = Math.min(width, Math.max(16, getWidth() - 16));
    purchasePrompt.setWidth(width);
    purchasePromptBackground.setBounds(0, 0, width, purchasePrompt.getHeight());
    purchasePromptTitle.setBounds(0, 88, width, 16);
    purchasePromptItem.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, 68,
        width - PURCHASE_PROMPT_SIDE_MARGIN * 2, 18);
    purchasePromptPrice.setBounds(0, 49, width, 16);
    float buttonWidth = Math.max(1, width - PURCHASE_PROMPT_SIDE_MARGIN * 2);
    purchaseConfirm.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, 25,
        buttonWidth, PURCHASE_PROMPT_BUTTON_HEIGHT);
    purchaseCancel.setBounds(PURCHASE_PROMPT_SIDE_MARGIN, 2,
        buttonWidth, PURCHASE_PROMPT_BUTTON_HEIGHT);
    centerPurchasePrompt();
    purchasePrompt.setVisible(true);
  }

  private void centerPurchasePrompt() {
    purchasePrompt.setPosition((getWidth() - purchasePrompt.getWidth()) / 2f,
        (getHeight() - purchasePrompt.getHeight()) / 2f);
  }

  private void hidePurchasePrompt() {
    pendingPurchaseItem = null;
    if (purchasePrompt != null) purchasePrompt.setVisible(false);
  }

  private boolean sellCursorItem() {
    if (Riiablo.charData == null) return false;
    Item cursor = Riiablo.charData.getItems().getCursor();
    return cursor != null && canSellItems()
        && sellItem(Riiablo.charData.getItems().indexOf(cursor));
  }

  @Override
  public void draw(Batch batch, float a) {
    refreshGold();
    batch.draw(buysell, getX(), getY());
    super.draw(batch, a);
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible) {
      Riiablo.game.setRightPanel(Riiablo.game.inventoryPanel);
    } else {
      hidePurchasePrompt();
      networkFlags = 0;
      clearPendingRequest();
      if (buttonGroup != null && buttonGroup.getCheckedIndex() >= 0) {
        Riiablo.cursor.resetCursor();
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
    this.networkSynchronizer = networkSynchronizer;
  }

  /** Opens a server-owned vendor session. Local inventory generation is skipped. */
  public void configNetwork(int flags, int npcEntityId, byte service) {
    configNetwork(flags, npcEntityId, service, null);
  }

  public void configNetwork(int flags, int npcEntityId, byte service, Npc.Entry pricing) {
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
    return clientSocket != null && networkSynchronizer != null;
  }

  /** Applies an authoritative vendor/repair response to the visible UI. */
  public void applyNetworkResult(NpcServiceResult result) {
    if (result == null) return;
    VendorPricing.setGoldSnapshot(Riiablo.charData,
        (int) Math.min(Integer.MAX_VALUE, result.gold()),
        (int) Math.min(Integer.MAX_VALUE, result.goldBank()));
    if (result.requestId() == pendingRequestId) {
      if (result.success() && pendingOperation == NpcServiceOperation.BUY
          && result.itemDataLength() > 0) {
        Item purchased = decodeItem(result.itemId(), result.itemDataAsByteBuffer(), false);
        if (purchased != null && !Riiablo.charData.getItems().contains(purchased)) {
          boolean placed = pendingBuyToCursor
              ? Riiablo.charData.getItems().addToCursor(purchased)
              : Riiablo.charData.getItems().addAutoPickup(purchased, Riiablo.charData);
          if (!placed) {
            Gdx.app.error(TAG, "[VENDOR_BUY] requested destination unavailable; preserving item in inventory");
            Riiablo.charData.getItems().addToInventory(purchased);
          } else if (Riiablo.audio != null) {
            Riiablo.audio.play(purchased.getDropSound(), true);
          }
        }
      } else if (result.success() && pendingOperation == NpcServiceOperation.SELL) {
        Item sold = pendingItemIndex >= 0 && pendingItemIndex < Riiablo.charData.getItems().getItems().size
            ? Riiablo.charData.getItems().getItems().get(pendingItemIndex) : null;
        if (sold != null) {
          Riiablo.charData.getItems().removeOwnedItem(sold);
          playSellSounds(sold);
        }
      } else if (result.success() && pendingOperation == NpcServiceOperation.REPAIR_ITEM) {
        Item repaired = findOwnedItem(result.itemId(), pendingItemIndex);
        if (repaired != null) ItemDurabilityManager.INSTANCE.restoreDurability(repaired);
      } else if (result.success() && pendingOperation == NpcServiceOperation.REPAIR_ALL) {
        applyRepairAllResult();
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
    if (restoreSelling && (configuredFlags & SELL) != 0) {
      btnSell.setChecked(true);
      selling = true;
      repairing = false;
      Riiablo.cursor.setCursor(Riiablo.cursor.buysell, 4);
    } else if (restoreRepairing && (configuredFlags & REPAIR) != 0) {
      btnRepair.setChecked(true);
      repairing = true;
      selling = false;
      Riiablo.cursor.setCursor(Riiablo.cursor.buysell, 1);
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
    return isVisible() && (selling || btnSell.isChecked());
  }

  public boolean isRepairing() {
    return isVisible() && (repairing || btnRepair.isChecked());
  }

  /** The trade window accepts native right-click selling without selecting Sell first. */
  public boolean canSellItems() {
    return isVisible() && (configuredFlags & SELL) != 0;
  }

  /** Returns the exact local/native sale value shown by the trade tooltip. */
  public int sellPrice(Item item) {
    if (item == null || Riiablo.charData == null) return 0;
    return VendorPricing.sellPrice(
        item, localPricing, Riiablo.charData, Riiablo.charData.diff);
  }

  /** Uses a separate table so the ordinary cached inventory tooltip is untouched. */
  public com.badlogic.gdx.scenes.scene2d.ui.Table sellDetails(
      Item item, AttributesUpdater updater) {
    return item.sellDetails(updater, sellPrice(item));
  }

  private boolean isGambling() {
    return serviceType == NpcServiceType.GAMBLE;
  }

  public boolean repairItem(int itemIndex) {
    if (!isVisible() || !repairing || Riiablo.charData == null) return false;
    if (itemIndex < 0 || itemIndex >= Riiablo.charData.getItems().getItems().size) return false;
    Item item = Riiablo.charData.getItems().getItem(itemIndex);
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
    int cost = ItemDurabilityManager.INSTANCE.calculateRepairCost(item);
    if (cost <= 0 || !VendorPricing.chargeGold(Riiablo.charData, cost)) return false;
    ItemDurabilityManager.INSTANCE.repairItem(item, cost);
    refreshGold();
    Gdx.app.debug(TAG, "Repaired " + item.code + " for " + cost + " gold");
    return true;
  }

  private void repairAll() {
    if (Riiablo.charData == null) return;
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
    int available = VendorPricing.availableGold(Riiablo.charData);
    int cost = ItemDurabilityManager.INSTANCE.repairAllEquipment(Riiablo.charData.getItems(), available);
    if (cost > 0) {
      // repairAllEquipment mutates durability but does not own the wallet.
      VendorPricing.chargeGold(Riiablo.charData, cost);
      refreshGold();
      Gdx.app.debug(TAG, "Repaired all equipment for " + cost + " gold");
    }
  }

  private Item findOwnedItem(int itemId, int fallbackIndex) {
    if (Riiablo.charData == null) return null;
    Array<Item> items = Riiablo.charData.getItems().getItems();
    if (itemId != 0) {
      for (Item item : items) if (item != null && item.id == itemId) return item;
    }
    return fallbackIndex >= 0 && fallbackIndex < items.size ? items.get(fallbackIndex) : null;
  }

  private void applyRepairAllResult() {
    if (Riiablo.charData == null) return;
    for (Item item : Riiablo.charData.getItems().getItems()) {
      if (item != null && item.location == com.riiablo.item.Location.EQUIPPED) {
        ItemDurabilityManager.INSTANCE.restoreDurability(item);
      }
    }
  }

  /** Called by the inventory grid when the Sell mode is active. */
  public boolean sellItem(int itemIndex) {
    if ((!isSelling() && !canSellItems()) || Riiablo.charData == null) return false;
    Item item = itemIndex >= 0 && itemIndex < Riiablo.charData.getItems().getItems().size
        ? Riiablo.charData.getItems().getItem(itemIndex) : null;
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
    int value = VendorPricing.sellPrice(item, localPricing, Riiablo.charData,
        Riiablo.charData.diff);
    boolean sold = VendorPricing.sell(Riiablo.charData, itemIndex, localPricing,
        Riiablo.charData.diff);
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
    if (Riiablo.audio == null) return;
    Riiablo.audio.play("item_gold", true);
    if (item != null) Riiablo.audio.play(item.getDropSound(), true);
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
    if (!direct) {
      showPurchasePrompt(item);
      return false;
    }
    return purchaseNow(item, true);
  }

  /** Executes a confirmed purchase. */
  private boolean purchaseNow(Item item, boolean direct) {
    if (selling || Riiablo.charData == null) return false;
    boolean toCursor = !direct;
    if (toCursor && Riiablo.charData.getItems().getCursor() != null) return false;
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
        ? VendorPricing.gamblePrice(item, Riiablo.charData)
        : VendorPricing.buyPrice(item, localPricing, Riiablo.charData);
    boolean bought = isGambling()
        ? (toCursor
            ? VendorPricing.gambleToCursor(Riiablo.charData, item)
            : VendorPricing.gamble(Riiablo.charData, item))
        : (toCursor
            ? VendorPricing.buyToCursor(Riiablo.charData, item, localPricing)
            : VendorPricing.buy(Riiablo.charData, item, localPricing));
    if (bought) {
      if (localStock != null) {
        int stockIndex = localStock.indexOf(item, true);
        Item replacement = !isGambling() && VendorPricing.isInfiniteStockItem(item)
            && localVendorGenerator != null ? localVendorGenerator.restock(item) : null;
        if (replacement != null && stockIndex >= 0) localStock.set(stockIndex, replacement);
        else localStock.removeValue(item, true);
        config(configuredFlags, localStock, localPricing, serviceType, localVendorGenerator);
      }
      Gdx.app.debug(TAG, "Bought " + item.code + " for " + value + " gold");
      refreshGold();
      if (Riiablo.audio != null) Riiablo.audio.play(item.getDropSound(), true);
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
    if (goldLabel != null && Riiablo.charData != null) {
      goldLabel.setText(Integer.toString(VendorPricing.availableGold(Riiablo.charData)));
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
    for (Tab t : tabs) t.setMode(Tab.INACTIVE);
    tab.setMode(Tab.ACTIVE);
    if (activeGrid != null) activeGrid.setVisible(false);
    activeGrid = tab.grid;
    activeGrid.setVisible(true);
  }

  public void setTab(int i) {
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
      super.setVisible(visible);
      label.setVisible(visible);
    }

    public void setMode(int mode) {
      this.mode = mode;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      batch.draw(modes[mode], getX(), getY());
      super.draw(batch, parentAlpha);
    }
  }
}
