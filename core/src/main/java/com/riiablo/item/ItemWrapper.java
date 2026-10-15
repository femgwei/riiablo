package com.riiablo.item;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.Index;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;

public class ItemWrapper extends Actor implements Disposable {
  final Item item;

  AssetDescriptor<DC6> invFileDescriptor;
  public DC6   invFile;

  private AssetDescriptor<DC6> socketDescriptor;
  private DC6 socketFile;

  /** Inventory grid cell size used by the native 28x28 socket overlay. */
  private float socketCellWidth = 28f;
  private float socketCellHeight = 28f;
  private boolean hovered;

  public Index invColormap;
  public int   invColorIndex;

  public Index charColormap;
  public int   charColorIndex;

  /** Last quantity reported to the diagnostic log; avoids per-frame spam. */
  private int lastLoggedQuantity = Integer.MIN_VALUE;

  ItemWrapper(Item item) {
    this.item = item;
  }

  /** Sets the inventory cell size used when placing socket overlays. */
  public void setSocketGridSize(float cellWidth, float cellHeight) {
    if (cellWidth > 0) socketCellWidth = cellWidth;
    if (cellHeight > 0) socketCellHeight = cellHeight;
  }

  /** Socket overlays are shown by the native client only while the item is hovered. */
  public void setHovered(boolean hovered) {
    this.hovered = hovered;
  }

  @Override
  public String getName() {
    return item.getNameString();
  }

  public void resize() {
    BBox box = invFile.getBox();
    setSize(box.width, box.height);
  }

  public void resize(Inventory.Entry inv) {
    setSize(item.base.invwidth * inv.gridBoxWidth, item.base.invheight * inv.gridBoxHeight);
  }

  public void load() {
    if (invFileDescriptor != null) return;
    invFileDescriptor = new AssetDescriptor<>("data\\global\\items\\" + item.getInvFileName() + '.' + DC6.EXT, DC6.class);
    Riiablo.assets.load(invFileDescriptor);
    checkLoaded();

    invColormap      = Riiablo.colormaps.get(item.base.InvTrans);
    String invColor  = item.getInvColor();
    invColorIndex    = invColor != null ? Riiablo.files.colors.index(invColor) + 1 : 0;

    charColormap     = Riiablo.colormaps.get(item.base.Transform);
    String charColor = item.getCharColor();
    charColorIndex   = charColor != null ? Riiablo.files.colors.index(charColor) + 1 : 0;

    if (getSocketCount() > 0) {
      socketDescriptor = new AssetDescriptor<>(
          "data\\global\\ui\\PANEL\\gemsocket.dc6", DC6.class);
      Riiablo.assets.load(socketDescriptor);
      checkSocketLoaded();
    }
  }

  public boolean checkLoaded() {
    boolean b = Riiablo.assets.isLoaded(invFileDescriptor);
    if (b && invFile == null) {
      invFile = Riiablo.assets.get(invFileDescriptor);
      resize();
    }

    return b;
  }

  private boolean checkSocketLoaded() {
    if (socketDescriptor == null) return false;
    boolean loaded = Riiablo.assets.isLoaded(socketDescriptor);
    if (loaded && socketFile == null) socketFile = Riiablo.assets.get(socketDescriptor);
    return loaded;
  }

  @Override
  public void draw(Batch batch, float a) {
    if (invFile == null && !checkLoaded()) return;
    PaletteIndexedBatch b = (PaletteIndexedBatch) batch;
    boolean ethereal = item.isEthereal();
    if (ethereal) b.setAlpha(Item.ETHEREAL_ALPHA);
    if (invColormap != null) b.setColormap(invColormap, invColorIndex);
    invFile.draw(b, getX(), getY());
    if (invColormap != null) b.resetColormap();
    drawSockets(b);
    if (ethereal) b.resetColor();

    drawQuantity(b);
  }

  /** Draws the native 28x28 empty-socket overlay over the item footprint. */
  private void drawSockets(PaletteIndexedBatch b) {
    int socketCount = getSocketCount();
    if (!hovered || socketCount <= 0 || !checkSocketLoaded() || socketFile == null || item.base == null) return;

    int columns = Math.max(1, item.base.invwidth);
    int rows = Math.max(1, item.base.invheight);
    int filled = Math.min(socketCount, Math.max(0, item.socketsFilled));

    // The native client lays sockets out in the inventory footprint, not in
    // the rendered DC6 image's bounding box.  Center the final, possibly
    // partial row so 1/3/4 socket items do not look pinned to one corner.
    Array<SocketPosition> positions = socketPositions(socketCount, columns, rows);
    b.setBlendMode(BlendMode.SOURCE_INDEX_ALPHA, Riiablo.colors.trans50);
    for (int i = filled; i < positions.size; i++) {
      SocketPosition position = positions.get(i);
      socketFile.draw(b,
          getX() + position.column * socketCellWidth,
          getY() + position.row * socketCellHeight);
    }
    b.resetBlendMode();
  }

  private static Array<SocketPosition> socketPositions(int count, int columns, int rows) {
    Array<SocketPosition> positions = new Array<>(count);
    if (count <= 0) return positions;

    // Native socket layouts: 1/2 are always a centered vertical row; 3 and
    // 4 use that row when it fits, otherwise the familiar stepped/square
    // layouts. Five is a 2-1-2 flower, while six and eight are two columns.
    if ((count == 1 || count == 2 || count == 3 || count == 4) && rows >= count) {
      addPattern(positions, columns, rows, repeat(1, count));
    } else if (count == 3) {
      addPattern(positions, columns, rows, new int[] {1, 2}); // 品
    } else if (count == 4) {
      addPattern(positions, columns, rows, new int[] {2, 2});
    } else if (count == 5) {
      addPattern(positions, columns, rows, new int[] {2, 1, 2});
    } else if (count == 6) {
      addPattern(positions, columns, rows, new int[] {2, 2, 2});
    } else if (count == 8) {
      addPattern(positions, columns, rows, new int[] {2, 2, 2, 2});
    } else {
      int rowCount = Math.min(rows, (count + columns - 1) / columns);
      int remaining = count;
      int[] pattern = new int[rowCount];
      for (int i = 0; i < rowCount; i++) {
        pattern[i] = Math.min(columns, remaining);
        remaining -= pattern[i];
      }
      addPattern(positions, columns, rows, pattern);
    }
    return positions;
  }

  private static int[] repeat(int value, int count) {
    int[] result = new int[count];
    java.util.Arrays.fill(result, value);
    return result;
  }

  /** Adds a top-to-bottom row pattern, centered in the item footprint. */
  private static void addPattern(Array<SocketPosition> positions,
      int columns, int rows, int[] rowCounts) {
    float firstRow = Math.max(0f, (rows - rowCounts.length) / 2f);
    for (int row = 0; row < rowCounts.length; row++) {
      int inRow = Math.min(columns, rowCounts[row]);
      float firstColumn = Math.max(0f, (columns - inRow) / 2f);
      float y = rows - 1f - (firstRow + row);
      for (int column = 0; column < inRow; column++) {
        positions.add(new SocketPosition(firstColumn + column, y));
      }
    }
  }

  private static final class SocketPosition {
    final float column;
    final float row;

    SocketPosition(float column, float row) {
      this.column = column;
      this.row = row;
    }
  }

  private int getSocketCount() {
    if (!item.hasFlag(Item.ITEMFLAG_SOCKETED) || item.attrs == null) return 0;
    StatRef sockets = item.attrs.base().get(Stat.item_numsockets);
    return sockets == null ? 0 : Math.max(0, sockets.asInt());
  }

  /** Draws the stack count used by throwing weapons and other stackable items. */
  private void drawQuantity(PaletteIndexedBatch b) {
    if (item.base == null || !item.base.stackable || item.attrs == null) return;

    StatRef quantity = item.attrs.base().get(Stat.quantity);
    if (quantity == null) {
      if (lastLoggedQuantity != -1) {
        com.riiablo.logger.LogManager.getLogger(ItemWrapper.class).warn(
            "[ITEM_QUANTITY_UI] missing quantity itemCode={} itemId={}", item.code, item.id);
        lastLoggedQuantity = -1;
      }
      return;
    }

    int value = quantity.asInt();
    if (value != lastLoggedQuantity) {
      com.riiablo.logger.LogManager.getLogger(ItemWrapper.class).info(
          "[ITEM_QUANTITY_UI] itemCode={} itemId={} quantity={}", item.code, item.id, value);
      lastLoggedQuantity = value;
    }

    String text = Integer.toString(Math.max(0, value));
    com.riiablo.codec.FontTBL.BitmapFont font = Riiablo.fonts.font16;
    GlyphLayout layout = new GlyphLayout(font, text);
    // Item numbers sit in the lower-right corner of the icon in D2. The
    // one-pixel inset keeps the final glyph inside the inventory slot.
    float x = getX() + getWidth() - layout.width - 1;
    float y = getY() + layout.height + 1;
    b.setBlendMode(font.getBlendMode(), Riiablo.colors.white);
    font.draw(b, text, x, y);
    b.resetBlendMode();
  }

  /** Identification may change inventory art and palette translation. */
  public void refresh() {
    if (invFileDescriptor == null) return;
    dispose();
    load();
  }

  @Override
  public void dispose() {
    if (Riiablo.assets == null) return;
    if (invFileDescriptor != null && Riiablo.assets.contains(invFileDescriptor.fileName)) {
      Riiablo.assets.unload(invFileDescriptor.fileName);
    }
    if (socketDescriptor != null && Riiablo.assets.contains(socketDescriptor.fileName)) {
      Riiablo.assets.unload(socketDescriptor.fileName);
    }
    invFileDescriptor = null;
    invFile = null;
    socketDescriptor = null;
    socketFile = null;
  }
}
