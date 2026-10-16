package com.riiablo.screen.panel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.SnapshotArray;

import com.riiablo.Riiablo;
import com.riiablo.attributes.AttributesUpdater;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Misc;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.item.Item;
import com.riiablo.item.ItemRequirements;
import com.riiablo.save.ItemData;
import com.riiablo.util.Disposal;

public class ItemGrid extends Group implements Disposable {
  private static final String TAG = "ItemGrid";
  final int width;
  final int height;
  final int boxWidth;
  final int boxHeight;

  final Texture fill;
  final Color backgroundColorR;
  final Color backgroundColorG;
  final Color backgroundColorB;
  final Color backgroundColorW;

  final ClickListener clickListener;

  final ObjectSet<Actor> hits = new ObjectSet<>(16, 0.99f); // 8, 1.0f no longer supported

  final ItemData itemData = Riiablo.charData.getItems();
  final GridListener gridListener;

  boolean showFill = true;
  boolean blocked = true;
  StoredItem swap = null;
  Vector2 coords = new Vector2();
  Vector2 grid = new Vector2();
  Vector2 itemSize = new Vector2();

  protected AttributesUpdater updater = new AttributesUpdater(); // TODO: inject
  private boolean disposed;

  public ItemGrid(Inventory.Entry inv) {
    this(inv, null);
  }

  public ItemGrid(Inventory.Entry inv, GridListener gridListener) {
    this(inv.gridX, inv.gridY, inv.gridBoxWidth, inv.gridBoxHeight, gridListener);
  }

  public ItemGrid(int width, int height, int boxWidth, int boxHeight) {
    this(width, height, boxWidth, boxHeight, null);
  }

  public ItemGrid(int width, int height, int boxWidth, int boxHeight, GridListener gridListener) {
    this.width = width;
    this.height = height;
    this.boxWidth = boxWidth;
    this.boxHeight = boxHeight;
    this.gridListener = gridListener;

    backgroundColorR = Riiablo.colors.invRed;
    backgroundColorG = Riiablo.colors.invGreen;
    backgroundColorB = Riiablo.colors.invBlue;
    backgroundColorW = Riiablo.colors.invWhite;

    setTouchable(Touchable.enabled);
    setSize(width * boxWidth, height * boxHeight);
    addListener(clickListener = new ClickListener(Input.Buttons.LEFT) {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isActive()) return;
        if (event.isHandled()) return;
        ItemGrid.this.mouseMoved();
        Item cursor = itemData.getCursor();
        if (blocked) {
          if (cursor != null && onCursorDrop(cursor)) {
            event.handle();
            return;
          }
          if (cursor != null) Riiablo.audio.play("sorceress_impossible_1", false);
          event.handle();
          return;
        }

        if (cursor != null) {
          Riiablo.audio.play(cursor.getDropSound(), true);
          if (swap != null) {
            onSwap(swap.item, (int) grid.x, (int) grid.y);
            removeActor(swap);
            swap = null;
          } else {
            onDrop((int) grid.x, (int) grid.y);
          }

          cursor.gridX = (byte) grid.x; // TODO: delete ? why was this note added
          cursor.gridY = (byte) grid.y; // TODO: delete ? why was this note added
          swap = addItem(cursor);
          event.handle();
        }
      }
    });
    // Last fallible step; PanelTextures releases upload pixels even if texture creation fails.
    fill = PanelTextures.solid(Color.BLACK);
  }

  public void populate(Array<Item> items) {
    if (!isActive()) return;
    for (Item item : items) {
      addItem(item);
    }
  }

  /**
   * Reconciles the visible grid with the authoritative ItemData collection.
   * Direct ground pickup and network snapshots mutate ItemData without going
   * through the grid's cursor click handlers, so the UI cannot rely on those
   * handlers to add or remove actors.
   */
  public void syncItems(Array<Item> items) {
    if (!isActive()) return;
    SnapshotArray<Actor> children = getChildren();
    Actor[] snapshot = children.begin();
    try {
      for (int i = children.size - 1; i >= 0; i--) {
        Actor child = snapshot[i];
        if (!(child instanceof ItemGrid.StoredItem)) continue;
        StoredItem stored = (StoredItem) child;
        if (items == null || !items.contains(stored.item, true)) removeActor(stored);
      }
    } finally { children.end(); }

    if (items == null) return;
    for (Item item : items) {
      StoredItem stored = findItemActor(item);
      if (stored == null) {
        stored = addItem(item);
      } else {
        stored.setPosition(item.gridX * boxWidth, itemY(item, stored.getHeight()));
      }
    }
  }

  private StoredItem findItemActor(Item item) {
    SnapshotArray<Actor> children = getChildren();
    Actor[] snapshot = children.begin();
    try {
      for (int i = 0, size = children.size; i < size; i++) {
        Actor child = snapshot[i];
        if (child instanceof ItemGrid.StoredItem
            && ((StoredItem) child).item == item) return (StoredItem) child;
      }
      return null;
    } finally {
      children.end();
    }
  }

  protected StoredItem addItem(Item item) {
    StoredItem store = new StoredItem(item);
    try {
      store.setPosition(item.gridX * boxWidth, itemY(item, store.getHeight()));
      addActor(store);
      return store;
    } catch (RuntimeException | Error failure) {
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(store::retire);
        cleanup.run(store::remove);
      } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  /** StoredItem actors belong to this grid; removal is final, not a transferable view. */
  @Override public Actor removeActorAt(int index, boolean unfocus) {
    Actor child = getChildren().get(index);
    try (Disposal cleanup = new Disposal()) {
      if (child instanceof StoredItem) cleanup.run(((StoredItem) child)::retire);
      cleanup.run(() -> super.removeActorAt(index, unfocus));
    }
    return child;
  }

  @Override public void clearChildren(boolean unfocus) {
    try (Disposal cleanup = new Disposal()) {
      for (Actor child : getChildren().toArray(Actor.class)) {
        cleanup.run(() -> removeActor(child, unfocus));
      }
    }
  }

  /** Converts a persisted grid row into the actor's bottom-origin y coordinate. */
  protected float itemY(Item item, float itemHeight) {
    return getHeight() - item.gridY * boxHeight - itemHeight;
  }

  /** Converts a clamped bottom-origin actor y coordinate back to a grid row. */
  protected int gridY(float y, float itemHeight) {
    return (int) (getHeight() - y - itemHeight) / boxHeight;
  }

  protected boolean accept(Item item) {
    return true;
  }

  /** Hook for grids that represent a non-player store (for example vendors). */
  protected boolean onStoredItemClicked(Item item) {
    return false;
  }

  /** Store-grid hook that can distinguish left-click pickup from right-click direct actions. */
  protected boolean onStoredItemClicked(Item item, boolean direct) {
    return onStoredItemClicked(item);
  }

  /** Hook for a store grid that accepts a cursor item outside its cells. */
  protected boolean onCursorDrop(Item item) {
    return false;
  }

  /** Lets specialized grids route a right-click through their item controller. */
  protected boolean onUse(Item item) {
    return gridListener != null && gridListener.onUse(item);
  }

  void onDrop(int x, int y) {
    if (gridListener == null) return;
    gridListener.onDrop(x, y);
  }

  boolean onPickup(Item item) {
    if (gridListener == null) return false;
    int i = itemData.indexOf(item);
    return gridListener.onPickup(i);
  }

  void onSwap(Item item, int x, int y) {
    if (gridListener == null) return;
    int i = itemData.indexOf(item);
    gridListener.onSwap(i, x, y);
  }

  protected void mouseMoved() {
    if (!isActive()) return;
    swap = null;
    blocked = true;
    hits.clear();
    Item cursor = itemData.getCursor();
    if (cursor != null && clickListener.isOver()) {
      coords = Riiablo.cursor.getCoords();
      screenToLocalCoordinates(coords);
      float x = coords.x - (coords.x % boxWidth);
      float y = coords.y - (coords.y % boxHeight);
      float itemWidth = cursor.base.invwidth * boxWidth;
      float itemHeight = cursor.base.invheight * boxHeight;
      coords.x = coords.x % boxWidth;
      coords.y = coords.y % boxHeight;

      switch (cursor.base.invwidth) {
        case 1:  break;
        case 2:  if (coords.x < boxWidth / 2) x -= boxWidth; break;
        default: Gdx.app.error(TAG, "Unsupported invWidth for " + cursor.getNameString() + ": " + cursor.base.invwidth);
      }

      switch (cursor.base.invheight) {
        case 1:
          break;

        case 2:
          if (coords.y < boxHeight / 2) y -= boxHeight;
          break;

        case 3:
          y -= boxHeight;
          break;

        case 4:
          if (coords.y < boxHeight / 2)
            y -= (boxHeight * 2);
          else
            y -= boxHeight;
          break;

        default: Gdx.app.error(TAG, "Unsupported invHeight for " + cursor.getNameString() + ": " + cursor.base.invheight);
      }

      x = MathUtils.clamp(x, 0, getWidth()  - itemWidth);
      y = MathUtils.clamp(y, 0, getHeight() - itemHeight);
      coords.set(x, y);
      itemSize.set(itemWidth, itemHeight);

      grid.x = (int) x / boxWidth;
      grid.y = gridY(y, itemHeight);

      hits.clear();
      float boxWidth50  = boxWidth  / 2f;
      float boxHeight50 = boxHeight / 2f;
      for (int ix = 0; ix < cursor.base.invwidth; ix++) {
        for (int iy = 0; iy < cursor.base.invheight; iy++) {
          float px = x + ix * boxWidth;
          float py = y + iy * boxHeight;
          Actor hit = hit(px + boxWidth50, py + boxHeight50, true);
          if (hit instanceof StoredItem) {
            hits.add(hit);
          }
        }
      }
    }

    switch (hits.size) {
      case 0:
        blocked = !accept(cursor);
        swap = null;
        break;

      case 1:
        blocked = !accept(cursor);
        swap = (StoredItem) hits.first();
        break;

      default:
        blocked = true;
        swap = null;
    }
  }

  @Override
  protected void drawChildren(Batch batch, float parentAlpha) {
    if (!isActive()) return;
    super.drawChildren(batch, parentAlpha);
    mouseMoved();
    Item cursor = itemData.getCursor();
    if (cursor != null && clickListener.isOver() && showFill) {
      PaletteIndexedBatch b = (PaletteIndexedBatch) batch;
      if (!accept(cursor)) {
        b.setBlendMode(BlendMode.SOLID, backgroundColorR);
        b.draw(fill, coords.x, coords.y, itemSize.x, itemSize.y);
      } else {
        switch (hits.size) {
          case 0:
            b.setBlendMode(BlendMode.SOLID, backgroundColorG);
            b.draw(fill, coords.x, coords.y, itemSize.x, itemSize.y);
            break;

          case 1:
            b.setBlendMode(BlendMode.SOLID, backgroundColorW);
            b.draw(fill, swap.getX(), swap.getY(), swap.getWidth(), swap.getHeight());
            break;

          default:
            b.flush();
            clipBegin(coords.x, coords.y, itemSize.x, itemSize.y);
            for (Actor hit : hits) {
              b.setBlendMode(BlendMode.SOLID, backgroundColorR);
              b.draw(fill, hit.getX(), hit.getY(), hit.getWidth(), hit.getHeight());
            }
            b.flush();
            clipEnd();
        }
      }
      b.resetBlendMode();
    }
  }

  public boolean isDisposed() { return disposed; }

  /** Owners may also retire input/rendering when their captured session is no longer current. */
  protected boolean isActive() { return !disposed; }

  @Override public void draw(Batch batch, float parentAlpha) {
    if (isActive()) super.draw(batch, parentAlpha);
  }

  /** Releases only this grid's private fill, never ItemData or borrowed item graphics. */
  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    swap = null;
    hits.clear();
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearListeners);
      cleanup.run(this::clearActions);
      for (Actor child : getChildren()) if (!(child instanceof StoredItem)) cleanup.run(child::clearListeners);
      cleanup.run(this::clearChildren);
      cleanup.run(this::remove);
      cleanup.dispose(fill);
    }
  }

  public interface GridListener {
    void onDrop(int x, int y);
    boolean onPickup(int i);
    void onSwap(int i, int x, int y);
    default boolean onUse(Item item) { return false; }
  }

  /** Resolves the clicked inventory actor before a targeting action can pick it up. */
  public static Item targetItem(Actor actor) {
    for (; actor != null; actor = actor.getParent()) {
      if (actor instanceof ItemGrid.StoredItem) {
        StoredItem stored = (StoredItem) actor;
        return stored.isCurrentItem() ? stored.item : null;
      }
    }
    return null;
  }

  class StoredItem extends Actor {
    final Item item;
    final ClickListener clickListener;
    private boolean retired;

    private boolean isCurrentItem() {
      return !retired && ItemGrid.this.isActive() && getParent() == ItemGrid.this;
    }

    private void retire() {
      if (retired) return;
      retired = true;
      hits.remove(this);
      if (swap == this) swap = null;
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(() -> setTouchable(Touchable.disabled));
        cleanup.run(this::clearListeners);
        cleanup.run(this::clearActions);
      }
      // Item, wrapper and details are borrowed; another view may already use them.
    }

    StoredItem(final Item item) {
      this.item = item;

      ItemEntry entry = item.base;
      setSize(entry.invwidth * boxWidth, entry.invheight * boxHeight);
      item.wrapper.setSocketGridSize(boxWidth, boxHeight);
      addListener(clickListener = new ClickListener(Input.Buttons.LEFT) {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentItem()) return;
          if (item.hasFlag2(Item.ITEMFLAG2_INSTORE)) {
            if (onStoredItemClicked(item, false)) removeActor(StoredItem.this);
            event.handle();
            return;
          }
          if (itemData.getCursor() == null) {
            if (onPickup(StoredItem.this.item)) removeActor(StoredItem.this);
            event.handle();
          } else {
            ItemGrid.this.clickListener.clicked(event, x, y);
          }
        }
      });
      addListener(new ClickListener(Input.Buttons.RIGHT) {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentItem()) return;
          if (item.hasFlag2(Item.ITEMFLAG2_INSTORE)) {
            if (onStoredItemClicked(item, true)) removeActor(StoredItem.this);
            event.handle();
            return;
          }
          // The inventory listener gets first refusal for every owned item.
          // Vendor selling applies to equipment as well as usable consumables;
          // restricting this hook to ItemEntry.useable made equipment clicks
          // disappear and let potions fall through to their normal use sound.
          boolean routed = onUse(StoredItem.this.item);
          if (routed) {
            event.handle();
            return;
          }
          ItemEntry entry = StoredItem.this.item.base;
          if (entry.useable) {
            Riiablo.audio.play(StoredItem.this.item.getUseSound(), true);
            if (entry instanceof Misc.Entry) {
              Misc.Entry misc = StoredItem.this.item.getBase();
              switch (misc.pSpell) {
                case 7:
                  /**
                   * FIXME: custom logic is needed -- should display OVER stash panel, but also
                   *        close out when escaped (close stash and cube at once)
                   */
                  Riiablo.game.setLeftPanel(Riiablo.game.cubePanel);
                  break;
              }
            }
          }/* else if (Riiablo.cursor.getItem() == null) {
            String[] BodyLocs = item.type.BodyLoc;
            for (String BodyLoc : BodyLocs) {
              if (BodyLoc.isEmpty()) break;
              int loc = Riiablo.files.bodylocs.index(BodyLoc);
              System.out.println("loc = " + loc);
              if (player.getSlot(loc) == null) {
                Riiablo.cursor.setItem(item);
                removeActor(StoredItem.this);
                player.setSlot(loc, item);
                break;
              }
            }
          }*/
          event.handle();
        }
      });
    }

    @Override
    public void setPosition(float x, float y) {
      if (retired || !ItemGrid.this.isActive()) return;
      super.setPosition(x, y);
      item.wrapper.setPosition(x, y);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      if (!isCurrentItem()) return;
      PaletteIndexedBatch b = (PaletteIndexedBatch) batch;
      boolean requirementsMet = ItemRequirements.check(item, Riiablo.charData).usable();
      Color background = !requirementsMet
          ? backgroundColorR
          : clickListener.isOver() && itemData.getCursor() == null
              ? backgroundColorG : backgroundColorB;
      b.setBlendMode(BlendMode.SOLID, background);
      b.draw(fill, getX(), getY(), getWidth(), getHeight());
      b.resetBlendMode();
      item.wrapper.setHovered(clickListener.isOver());
      item.draw(b, 1);
      if (clickListener.isOver() && itemData.getCursor() == null) {
        Actor details = item.details(updater);
        VendorPanel vendor = Riiablo.game.vendorPanel;
        if (!item.hasFlag2(Item.ITEMFLAG2_INSTORE)
            && vendor != null && vendor.canSellItems()) {
          details = vendor.sellDetails(item, updater);
        }
        Riiablo.game.setDetails(details, item, ItemGrid.this, item.wrapper);
      }
    }

    @Override public void act(float delta) {
      if (isCurrentItem()) super.act(delta);
    }

    @Override public Actor hit(float x, float y, boolean touchable) {
      return isCurrentItem() ? super.hit(x, y, touchable) : null;
    }
  }
}
