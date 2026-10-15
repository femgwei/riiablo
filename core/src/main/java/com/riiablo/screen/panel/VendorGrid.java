package com.riiablo.screen.panel;

import java.util.Comparator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.SnapshotArray;

import com.riiablo.codec.excel.Inventory;
import com.riiablo.item.Item;
import com.riiablo.item.Type;

public class VendorGrid extends ItemGrid {
  private static final String TAG = "VendorGrid";

  protected static final Comparator<Item> ITEM_CODE_COMPARATOR = new Comparator<Item>() {
    @Override
    public int compare(Item i1, Item i2) {
      return Integer.compare(i1.getBaseIndex(), i2.getBaseIndex());
    }
  };

  private PurchaseListener purchaseListener;

  public VendorGrid(Inventory.Entry inv, GridListener gridListener) {
    super(inv, gridListener);
    showFill = false;
  }

  @Override
  protected boolean accept(Item item) {
    return false;
  }

  public void setPurchaseListener(PurchaseListener listener) {
    this.purchaseListener = listener;
  }

  @Override
  protected boolean onStoredItemClicked(Item item, boolean direct) {
    Item cursor = itemData.getCursor();
    if (!direct && cursor != null) {
      onCursorDrop(cursor);
      // The held item was offered for sale, not the clicked vendor item.
      // Returning true would remove that vendor item's actor as a purchase.
      return false;
    }
    return purchaseListener != null && purchaseListener.onPurchase(item, direct);
  }

  @Override
  protected boolean onCursorDrop(Item item) {
    return purchaseListener != null && purchaseListener.onSell(item);
  }

  public interface PurchaseListener {
    boolean onPurchase(Item item, boolean direct);
    default boolean onSell(Item item) { return false; }
  }

  public static Array<Item> sort(Array<Item> items) {
    items.sort(ITEM_CODE_COMPARATOR);
    return items;
  }

  public int drain(Array<Item> items) {
    clearChildren();
    sort(items);
    Gdx.app.debug(TAG, "Draining " + items);
    GridPoint2 coords = new GridPoint2(0, 0);
    Array<Item> placedItems = new Array<>(true, items.size, Item.class);
    for (Array.ArrayIterator<Item> it = new Array.ArrayIterator<>(items); it.hasNext(); ) {
      Item item = it.next();
      coords.set(0, 0); // TODO: handle non-zero when switching below state
      boolean placed = rightAligned(item)
          ? placeMultibuyItem(coords, item, placedItems)
          : placeItem(coords, item, placedItems);
      if (placed) {
        item.gridX = (byte) coords.x;
        item.gridY = (byte) coords.y;
        it.remove();
        addItem(item);
        placedItems.add(item);
        Gdx.app.debug(TAG, "Placing " + item.code + " @ " + coords);
      } else {
        Gdx.app.debug(TAG, "Draining " + item.code);
      }
    }

    return placedItems.size;
  }

  /** Returns whether another item can be displayed on this page. */
  public boolean hasRoom(Item item) {
    if (item == null || item.base == null) return false;
    Array<Item> placed = new Array<>(true, getChildren().size, Item.class);
    SnapshotArray<Actor> children = getChildren();
    for (Actor child : children) {
      if (child instanceof ItemGrid.StoredItem) placed.add(((ItemGrid.StoredItem) child).item);
    }
    GridPoint2 coords = new GridPoint2();
    return rightAligned(item)
        ? placeMultibuyItem(coords, item, placed)
        : placeItem(coords, item, placed);
  }

  private static boolean rightAligned(Item item) {
    return item.base.multibuy || item.type != null
        && (item.type.is(Type.POTI)
            || item.type.is(Type.TPOT)
            || item.type.is(Type.KEY)
            || item.type.is(Type.SCRO)
            || item.type.is(Type.BOOK));
  }

  // y isn't used -- just for posterity in case it will be needed
  private boolean placeItem(GridPoint2 coords, Item item, Array<Item> placed) {
    for (int x = coords.x; x + item.base.invwidth <= width; x++) {
      for (int y = 0; y + item.base.invheight <= height; y++) {
        if (canPlaceAt(item, x, y, placed)) {
          coords.set(x, y);
          return true;
        }
      }
    }
    return false;
  }

  private boolean placeMultibuyItem(GridPoint2 coords, Item item, Array<Item> placed) {
    for (int x = width - item.base.invwidth; x >= 0; x--) {
      for (int y = 0; y + item.base.invheight <= height; y++) {
        if (canPlaceAt(item, x, y, placed)) {
          coords.set(x, y);
          return true;
        }
      }
    }
    return false;
  }

  /** Checks the complete item footprint, not just the candidate's top-left cell. */
  private static boolean canPlaceAt(Item item, int x, int y, Array<Item> placed) {
    int width = item.base.invwidth;
    int height = item.base.invheight;
    for (Item placedItem : placed) {
      int placedX = placedItem.gridX;
      int placedY = placedItem.gridY;
      if (x < placedX + placedItem.base.invwidth
          && x + width > placedX
          && y < placedY + placedItem.base.invheight
          && y + height > placedY) {
        return false;
      }
    }
    return true;
  }
}
