package com.riiablo.screen.panel;

import org.apache.commons.lang3.ArrayUtils;

import com.artemis.annotations.Wire;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;

import com.riiablo.Riiablo;
import com.riiablo.attributes.AttributesUpdater;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.client.NetworkedClientItemManager;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.BodyLocs;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemRequirements;
import com.riiablo.item.StoreLoc;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;
import com.riiablo.widget.Button;
import com.riiablo.widget.GoldAmountDialog;
import com.riiablo.widget.Label;
import com.riiablo.util.Disposal;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;

public class InventoryPanel extends WidgetGroup implements Disposable, ItemGrid.GridListener {
  private static final String TAG = "InventoryPanel";
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
  private ItemData.AlternateListener alternateListener;
  private boolean disposed;

  final AssetDescriptor<DC6> invcharDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\invchar6.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion invchar;

  final AssetDescriptor<DC6> invcharTabDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\invchar6Tab.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion invcharTabR;
  TextureRegion invcharTabL;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  Button btnExit;

  final AssetDescriptor<DC6> goldcoinbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\goldcoinbtn.dc6", DC6.class);
  Button btnDropGold;
  Label invgold;
  GoldAmountDialog goldDialog;
  boolean depositGoldToStash;

  final AssetDescriptor<DC6> inv_armorDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_armor.DC6", DC6.class);
  final AssetDescriptor<DC6> inv_beltDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_belt.DC6", DC6.class);
  final AssetDescriptor<DC6> inv_bootsDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_boots.DC6", DC6.class);
  final AssetDescriptor<DC6> inv_helm_gloveDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_helm_glove.DC6", DC6.class);
  final AssetDescriptor<DC6> inv_ring_amuletDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_ring_amulet.DC6", DC6.class);
  final AssetDescriptor<DC6> inv_weaponsDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\inv_weapons.DC6", DC6.class);
  DC6 inv_armor, inv_belt, inv_boots, inv_helm_glove, inv_ring_amulet, inv_weapons;

  final Inventory.Entry inventory;

  Texture fill;
  final Color backgroundColorG = Riiablo.colors.invGreen;
  final Color backgroundColorR = Riiablo.colors.invRed;

  @Wire(name = "itemController")
  protected ItemController itemController;

  protected AttributesUpdater updater = new AttributesUpdater(); // TODO: inject
  final BodyPart[] bodyParts = new BodyPart[BodyLocs.NUM_LOCS];
  ItemGrid grid;

  public InventoryPanel() {
    this(null);
  }

  public InventoryPanel(GameScreen owner) {
    this.owner = owner;
    world = owner == null ? Riiablo.engine : null;
    inventory = Riiablo.files.inventory.getClass(character.charClass);
    try { initialize(); PanelSession.guardTree(this, this::isCurrentSession); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    } finally { initializing = false; }
  }

  private void initialize() {
    references.load(invcharDescriptor);
    assets.finishLoadingAsset(invcharDescriptor);
    invchar = assets.get(invcharDescriptor).getTexture(1);
    setSize(invchar.getRegionWidth(), invchar.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    references.load(invcharTabDescriptor);
    assets.finishLoadingAsset(invcharTabDescriptor);
    invcharTabR = assets.get(invcharTabDescriptor).getTexture(0);
    invcharTabL = assets.get(invcharTabDescriptor).getTexture(1);

    fill = PanelTextures.solid(Color.BLACK);

    btnExit = new SessionButton(new Button.ButtonStyle() {{
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(18, 15);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        GameScreen game = game();
        if (game != null && game.vendorPanel != null && game.vendorPanel.isVisible()) {
          game.closeVendorTradePanels();
        } else if (game != null) {
          game.setRightPanel(null);
        } else {
          setVisible(false);
        }
      }
    });
    addActor(btnExit);

    references.load(inv_armorDescriptor);
    references.load(inv_beltDescriptor);
    references.load(inv_bootsDescriptor);
    references.load(inv_helm_gloveDescriptor);
    references.load(inv_ring_amuletDescriptor);
    references.load(inv_weaponsDescriptor);
    assets.finishLoadingAsset(inv_armorDescriptor);
    assets.finishLoadingAsset(inv_beltDescriptor);
    assets.finishLoadingAsset(inv_bootsDescriptor);
    assets.finishLoadingAsset(inv_helm_gloveDescriptor);
    assets.finishLoadingAsset(inv_ring_amuletDescriptor);
    assets.finishLoadingAsset(inv_weaponsDescriptor);

    inv_armor = assets.get(inv_armorDescriptor);
    inv_belt = assets.get(inv_beltDescriptor);
    inv_boots = assets.get(inv_bootsDescriptor);
    inv_helm_glove = assets.get(inv_helm_gloveDescriptor);
    inv_ring_amulet = assets.get(inv_ring_amuletDescriptor);
    inv_weapons = assets.get(inv_weaponsDescriptor);

    BodyPart torso = bodyParts[BodyLocs.TORS] = new BodyPart(BodyLoc.TORS, inv_armor.getTexture());
    torso.setSize(inventory.torsoWidth, inventory.torsoHeight);
    torso.setPosition(
        inventory.torsoLeft - inventory.invLeft,
        getHeight() - inventory.torsoBottom);
    torso.xOffs = torso.yOffs = 2;
    addActor(torso);

    BodyPart rArm = bodyParts[BodyLocs.RARM] = new BodyPart(BodyLoc.RARM, inv_weapons.getTexture());
    rArm.setSize(inventory.rArmWidth, inventory.rArmHeight);
    rArm.setPosition(
        inventory.rArmLeft - inventory.invLeft,
        getHeight() - inventory.rArmBottom);
    rArm.yOffs = 2;
    rArm.xOffsAlt = 4;
    rArm.yOffsAlt = 4;
    addActor(rArm);

    BodyPart lArm = bodyParts[BodyLocs.LARM] = new BodyPart(BodyLoc.LARM, inv_weapons.getTexture());
    lArm.setSize(inventory.lArmWidth, inventory.lArmHeight);
    lArm.setPosition(
        inventory.lArmLeft - inventory.invLeft,
        getHeight() - inventory.lArmBottom);
    lArm.yOffs = 2;
    lArm.xOffsAlt = 4;
    lArm.yOffsAlt = 4;
    addActor(lArm);

    BodyPart head = bodyParts[BodyLocs.HEAD] = new BodyPart(BodyLoc.HEAD, inv_helm_glove.getTexture(1));
    head.setSize(inventory.headWidth, inventory.headHeight);
    head.setPosition(
        inventory.headLeft - inventory.invLeft,
        getHeight() - inventory.headBottom);
    head.yOffs = 1;
    addActor(head);

    BodyPart neck = bodyParts[BodyLocs.NECK] = new BodyPart(BodyLoc.NECK, inv_ring_amulet.getTexture(0));
    neck.setSize(inventory.neckWidth, inventory.neckHeight);
    neck.setPosition(
        inventory.neckLeft - inventory.invLeft,
        getHeight() - inventory.neckBottom);
    neck.xOffs = -1;
    neck.yOffs = 1;
    addActor(neck);

    BodyPart rHand = bodyParts[BodyLocs.RRIN] = new BodyPart(BodyLoc.RRIN, inv_ring_amulet.getTexture(1));
    rHand.setSize(inventory.rHandWidth, inventory.rHandHeight);
    rHand.setPosition(
        inventory.rHandLeft - inventory.invLeft,
        getHeight() - inventory.rHandBottom);
    rHand.xOffs = -1;
    rHand.yOffs = 1;
    addActor(rHand);

    BodyPart lHand = bodyParts[BodyLocs.LRIN] = new BodyPart(BodyLoc.LRIN, inv_ring_amulet.getTexture(1));
    lHand.setSize(inventory.lHandWidth, inventory.lHandHeight);
    lHand.setPosition(
        inventory.lHandLeft - inventory.invLeft,
        getHeight() - inventory.lHandBottom);
    lHand.xOffs = -1;
    lHand.yOffs = 1;
    addActor(lHand);

    BodyPart belt = bodyParts[BodyLocs.BELT] = new BodyPart(BodyLoc.BELT, inv_belt.getTexture());
    belt.setSize(inventory.beltWidth, inventory.beltHeight);
    belt.setPosition(
        inventory.beltLeft - inventory.invLeft,
        getHeight() - inventory.beltBottom);
    belt.yOffs = 2;
    addActor(belt);

    BodyPart feet = bodyParts[BodyLocs.FEET] = new BodyPart(BodyLoc.FEET, inv_boots.getTexture());
    feet.setSize(inventory.feetWidth, inventory.feetHeight);
    feet.setPosition(
        inventory.feetLeft - inventory.invLeft,
        getHeight() - inventory.feetBottom);
    feet.xOffs = -1;
    feet.yOffs = 2;
    addActor(feet);

    BodyPart gloves = bodyParts[BodyLocs.GLOV] = new BodyPart(BodyLoc.GLOV, inv_helm_glove.getTexture(0));
    gloves.setSize(inventory.glovesWidth, inventory.glovesHeight);
    gloves.setPosition(
        inventory.glovesLeft - inventory.invLeft,
        getHeight() - inventory.glovesBottom);
    gloves.xOffs = -1;
    gloves.yOffs = 2;
    addActor(gloves);

    for (int i = BodyLocs.HEAD; i < BodyLocs.NUM_LOCS; i++) {
      if (bodyParts[i] == null) continue;
      bodyParts[i].slot = i;
      bodyParts[i].item = itemData.getSlot(BodyLoc.valueOf(i));
      bodyParts[i].setBodyPart(Riiablo.files.bodylocs.get(i).Code);
    }

    Actor alternateWeaponsL = new Actor();
    alternateWeaponsL.setSize(invcharTabR.getRegionWidth(), 20); // width in file seems to be incorrectly stored as 135
    alternateWeaponsL.setPosition(
        inventory.lArmLeft - inventory.invLeft - 5,
        getHeight() - inventory.lArmBottom - 4 + invcharTabL.getRegionHeight() - alternateWeaponsL.getHeight());
    addActor(alternateWeaponsL);

    Actor alternateWeaponsR = new Actor();
    alternateWeaponsR.setSize(invcharTabR.getRegionWidth(), 20);
    alternateWeaponsR.setPosition(
        inventory.rArmLeft - inventory.invLeft - 5,
        getHeight() - inventory.rArmBottom - 4 + invcharTabR.getRegionHeight() - alternateWeaponsR.getHeight());
    addActor(alternateWeaponsR);

    EventListener swapListener = new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        itemData.alternate();
      }
    };
    alternateWeaponsL.addListener(swapListener);
    alternateWeaponsR.addListener(swapListener);

    alternateListener = new ItemData.AlternateListener() {
      @Override
      public void onAlternated(ItemData items, int alternate, Item LH, Item RH) {
        if (!isCurrentSession() || items != itemData) return;
        syncActiveWeaponSet(alternate, LH, RH);
      }
    };
    itemData.addAlternateListener(alternateListener);
    // D2SReader restores the active set before UI listeners exist. Initialize
    // the two visible weapon slots from that saved state instead of waiting
    // for the player to switch sets once after entering the game.
    syncActiveWeaponSet(itemData.getAlternate(),
        itemData.getEquipped(BodyLoc.LARM), itemData.getEquipped(BodyLoc.RARM));

    grid = new ItemGrid(inventory, this) {
      @Override protected boolean isActive() { return super.isActive() && isCurrentSession(); }
    };
    IntArray inventoryItems = itemData.getStore(StoreLoc.INVENTORY);
    Array<Item> items = itemData.toItemArray(inventoryItems);
    grid.populate(items);
    grid.setPosition(
        inventory.gridLeft - inventory.invLeft,
        getHeight() - inventory.gridTop - grid.getHeight());
    addActor(grid);

    StatRef gold = character.getStats().get(Stat.gold);
    invgold = new Label(Integer.toString(gold != null ? gold.asInt() : 0), Riiablo.fonts.font16);
    invgold.setSize(88, 16);
    invgold.setPosition(109, 24);
    addActor(invgold);

    btnDropGold = new SessionButton(new Button.ButtonStyle() {{
      references.load(goldcoinbtnDescriptor);
      assets.finishLoadingAsset(goldcoinbtnDescriptor);
      DC6 goldcoinbtn = assets.get(goldcoinbtnDescriptor);
      up   = new TextureRegionDrawable(goldcoinbtn.getTexture(0));
      down = new TextureRegionDrawable(goldcoinbtn.getTexture(1));
    }});
    btnDropGold.setPosition(84, 23);
    btnDropGold.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        GameScreen game = game();
        depositGoldToStash = game != null && game.stashPanel != null && game.stashPanel.isVisible();
        goldDialog.setTitle(Riiablo.bundle.get(
            depositGoldToStash ? "stash_gold_prompt" : "drop_gold_prompt"));
        goldDialog.open();
      }
    });
    addActor(btnDropGold);

    goldDialog = new GoldAmountDialog(Riiablo.bundle.get("drop_gold_prompt"),
        new GoldAmountDialog.Listener() {
      @Override
      public void submitted(String text) {
        if (!isCurrentSession()) return;
        try {
          int amount = Integer.parseInt(text.trim());
          if (amount > 0 && itemController != null) {
            if (depositGoldToStash) itemController.depositGold(amount);
            else itemController.dropGold(amount);
          }
        } catch (NumberFormatException ignored) {
          // The native dialog filters digits; retain defensive parsing.
        }
      }

      @Override
      public void canceled() {}
        });
    goldDialog.setMaximumProvider(negative -> {
      if (!isCurrentSession()) return 0;
      StatRef carriedGold = character.getStats().get(Stat.gold);
      return carriedGold == null ? 0 : carriedGold.asInt();
    });
    addActor(goldDialog);

    //setDebug(true, true);
  }

  private void syncActiveWeaponSet(int alternate, Item leftHand, Item rightHand) {
    bodyParts[BodyLocs.RARM].bodyLoc = BodyLoc.getAlternate(BodyLoc.RARM, alternate);
    bodyParts[BodyLocs.LARM].bodyLoc = BodyLoc.getAlternate(BodyLoc.LARM, alternate);
    bodyParts[BodyLocs.RARM].item = rightHand;
    bodyParts[BodyLocs.LARM].item = leftHand;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      if (alternateListener != null) cleanup.run(() -> itemData.removeAlternateListener(alternateListener));
      cleanup.dispose(btnExit);
      cleanup.dispose(btnDropGold);
      cleanup.dispose(goldDialog);
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> ActorTree.clear(btnExit));
      cleanup.run(() -> ActorTree.clear(btnDropGold));
      cleanup.dispose(grid);
      cleanup.dispose(fill);
      cleanup.dispose(references);
    } finally {
      if (bodyParts != null) for (BodyPart part : bodyParts) if (part != null) {
        part.item = null; part.background = null;
      }
      invchar = invcharTabR = invcharTabL = null;
      inv_armor = inv_belt = inv_boots = inv_helm_glove = inv_ring_amulet = inv_weapons = null;
      alternateListener = null; itemController = null; depositGoldToStash = false;
      owner = null; world = null;
      btnExit = null; btnDropGold = null; goldDialog = null; invgold = null; grid = null; fill = null;
    }
  }

  private boolean isCurrentSession() {
    return !disposed && character == Riiablo.charData && assets == Riiablo.assets
        && itemData == character.getItems() && audio == Riiablo.audio && cursor == Riiablo.cursor
        // GameScreen publishes itself only after create() has built its panels.
        && (initializing || world == Riiablo.engine && (owner != null ? Riiablo.game == owner
            : Riiablo.game == null || Riiablo.game.inventoryPanel == this));
  }

  private GameScreen game() {
    return owner != null ? owner : Riiablo.game;
  }

  private class SessionButton extends Button {
    SessionButton(ButtonStyle style) { super(style); }
    @Override public boolean isDisabled() { return !isCurrentSession() || super.isDisabled(); }
  }

  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }

  @Override public Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }

  @Override
  public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    if (!visible && goldDialog != null) goldDialog.close();
    super.setVisible(visible);
  }

  public boolean isGoldDialogOpen() {
    return goldDialog != null && goldDialog.isOpen();
  }

  @Override
  public void draw(Batch batch, float a) {
    if (!isCurrentSession()) return;
    StatRef gold = character.getStats().get(Stat.gold);
    int carriedGold = gold == null ? 0 : gold.asInt();
    if (!Integer.toString(carriedGold).contentEquals(invgold.getText())) {
      invgold.setText(Integer.toString(carriedGold));
    }
    // BodyPart caches are UI state only. Equipment may also change through
    // death/corpse handling, so refresh from the authoritative slot map and
    // never leave a clickable image for an already detached item.
    grid.syncItems(itemData.toItemArray(itemData.getStore(StoreLoc.INVENTORY)));
    for (BodyPart bodyPart : bodyParts) {
      if (bodyPart != null) bodyPart.item = itemData.getSlot(bodyPart.bodyLoc);
    }

    batch.draw(invchar, getX(), getY());
    if (itemData.getAlternate() > 0) {
      batch.draw(invcharTabR,
          getX() + inventory.rArmLeft - inventory.invLeft - 5,
          getY() + getHeight() - inventory.rArmBottom - 4);
      batch.draw(invcharTabL,
          getX() + inventory.lArmLeft - inventory.invLeft - 5,
          getY() + getHeight() - inventory.lArmBottom - 4);
    }
    super.draw(batch, a);
  }

  @Override
  public void onDrop(int x, int y) {
    if (!isCurrentSession() || itemController == null) return;
    itemController.cursorToStore(StoreLoc.INVENTORY, x, y);
  }

  @Override
  public boolean onPickup(int i) {
    if (!isCurrentSession() || itemController == null) return false;
    GameScreen game = game();
    if (game != null && game.vendorPanel != null && game.vendorPanel.isRepairing()) {
      game.vendorPanel.repairItem(i);
      return false;
    }
    Item item = i >= 0 && i < itemData.getItems().size ? itemData.getItems().get(i) : null;
    if (isShiftHeld() && item != null && item.type != null
        && item.type.is(com.riiablo.item.Type.POTI)) {
      // Native D2's Shift+left-click auto-belt action bypasses the mouse
      // cursor and leaves the item in the first compatible free quick slot.
      return itemController.inventoryToBelt(i);
    }
    itemController.storeToCursor(i);
    return true;
  }

  private static boolean isShiftHeld() {
    return Gdx.input != null
        && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
  }

  @Override
  public boolean onUse(Item item) {
    if (!isCurrentSession() || itemController == null) return false;
    GameScreen game = game();
    // Right-click starts a normal item action. Cancel any vendor mode first
    // so repair/sell/buy cannot consume or block that action.
    if (game != null && game.vendorPanel != null && game.vendorPanel.isVisible()) {
      game.vendorPanel.cancelTradeMode();
    }
    // Selling is intentionally not handled here. Native D2 sells by dragging
    // an item from the inventory onto the vendor area; right-click remains the
    // use action for consumables and does nothing for equipment.
    if (item != null && item.base != null && item.base.useable) {
      if (item.type != null && item.type.is(com.riiablo.item.Type.POTI)) {
        if (itemController instanceof NetworkedClientItemManager) {
          itemController.useInventoryItem(item);
          return true;
        }
        if (character.useInventoryPotion(item)) {
          if (audio != null) audio.play(item.getUseSound(), true);
          return true;
        }
      }
      // Misc consumables (notably tsc/tbk) must be routed through the item
      // controller instead of falling through to the legacy "play sound only"
      // branch in ItemGrid.StoredItem.
      if ("tsc".equalsIgnoreCase(item.code) || "tbk".equalsIgnoreCase(item.code)
          || "isc".equalsIgnoreCase(item.code) || "ibk".equalsIgnoreCase(item.code)) {
        itemController.useInventoryItem(item);
        return true;
      }
    }
    return item != null && item.base != null && !item.base.useable;
  }

  @Override
  public void onSwap(int i, int x, int y) {
    if (!isCurrentSession() || itemController == null) return;
    itemController.swapStoreItem(i, StoreLoc.INVENTORY, x, y);
  }

  public Item targetEquippedItem(Actor actor) {
    if (!isCurrentSession()) return null;
    for (; actor != null; actor = actor.getParent()) {
      if (actor instanceof BodyPart && ((BodyPart) actor).panel() == this)
        return itemData.getSlot(((BodyPart) actor).bodyLoc);
    }
    return null;
  }

  private class BodyPart extends Actor {
    TextureRegion background;
    Item item;
    BodyLoc bodyLoc;
    String bodyPart;
    int slot;
    final ClickListener clickListener;
    int xOffs, yOffs;
    int xOffsAlt, yOffsAlt;

    BodyPart(BodyLoc bodyLoc, TextureRegion background) {
      this.bodyLoc = bodyLoc;
      this.background = background;
      addListener(clickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession() || itemController == null) return;
          // A death/corpse transfer can change equipment outside this panel's
          // click flow. Resolve the slot again before deciding whether this is
          // an unequip, equip, or swap operation.
          item = itemData.getSlot(BodyPart.this.bodyLoc);
          Item cursorItem = cursor.getItem();
          if (cursorItem != null) {
            if (!ArrayUtils.contains(cursorItem.typeEntry.BodyLoc, bodyPart)) {
              audio.play(character.classId.name().toLowerCase() + "_impossible_1", false);
              return;
            }

            audio.play(cursorItem.getDropSound(), true);
            if (item != null) {
              itemController.swapBodyItem(BodyPart.this.bodyLoc, false);
            } else {
              itemController.cursorToBody(BodyPart.this.bodyLoc, false);
            }
            item = cursorItem;
          } else {
            if (item == null) return;
            GameScreen game = game();
            VendorPanel vendor = game == null ? null : game.vendorPanel;
            if (vendor != null && vendor.isRepairing()) {
              vendor.repairItem(itemData.indexOf(item));
              return;
            }
            item = null;
            itemController.bodyToCursor(BodyPart.this.bodyLoc, false);
          }
        }
      });
      xOffs = yOffs = 0;
      xOffsAlt = Integer.MIN_VALUE;
      yOffsAlt = Integer.MIN_VALUE;
    }

    public void setBodyPart(String code) {
      bodyPart = code;
    }

    private InventoryPanel panel() { return InventoryPanel.this; }

    @Override
    public void draw(Batch batch, float a) {
      if (!isCurrentSession()) return;
      int x, y;
      if (itemData.getAlternate() > 0) {
        x = xOffsAlt == Integer.MIN_VALUE ? xOffs : xOffsAlt;
        y = yOffsAlt == Integer.MIN_VALUE ? yOffs : yOffsAlt;
      } else {
        x = xOffs;
        y = yOffs;
      }

      if (item == null) {
        batch.draw(background, getX() + x, getY() + y);
      }

      boolean blocked = false;
      Item cursorItem = cursor.getItem();
      if (cursorItem != null) {
        blocked = !ArrayUtils.contains(cursorItem.typeEntry.BodyLoc, bodyPart)
            || !ItemRequirements.check(cursorItem, character).usable();
      }

      boolean isOver = clickListener.isOver();
      PaletteIndexedBatch b = (PaletteIndexedBatch) batch;
      boolean itemRequirementsMet = item == null
          || ItemRequirements.check(item, character).usable();
      if (item != null && !itemRequirementsMet) {
        b.setBlendMode(BlendMode.SOLID, backgroundColorR);
        b.draw(fill, getX(), getY(), getWidth(), getHeight());
        b.resetBlendMode();
      }
      if (isOver && !blocked && itemRequirementsMet && (cursorItem != null || item != null)) {
        b.setBlendMode(BlendMode.SOLID, backgroundColorG);
        b.draw(fill, getX(), getY(), getWidth(), getHeight());
        b.resetBlendMode();
      }

      // FIXME: Alt images on weapons are slightly off by maybe a pixel or so (rounding?) -- backgrounds fine
      if (item != null && item.wrapper.checkLoaded()) {
        BBox box = item.wrapper.invFile.getBox();
        item.wrapper.setPosition(
            getX() + getWidth()  / 2 - box.width  / 2f + x,
            getY() + getHeight() / 2 - box.height / 2f + y);
        item.draw(b, 1);
      }

      if (isOver && blocked) {
        b.setBlendMode(BlendMode.SOLID, backgroundColorR);
        b.draw(fill, getX(), getY(), getWidth(), getHeight());
        b.resetBlendMode();
      }

      if (isOver && item != null && cursorItem == null) {
        Actor details = item.details(updater);
        GameScreen game = game();
        VendorPanel vendor = game == null ? null : game.vendorPanel;
        if (!item.hasFlag2(Item.ITEMFLAG2_INSTORE)
            && vendor != null && vendor.canSellItems()) {
          details = vendor.sellDetails(item, updater);
        }
        if (game != null) game.setDetails(details, item, InventoryPanel.this, this);
      }
    }
  }
}
