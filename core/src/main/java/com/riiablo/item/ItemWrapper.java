package com.riiablo.item;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.Index;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.PaletteIndexedBatch;

public class ItemWrapper extends Actor implements Disposable {
  final Item item;

  AssetDescriptor<DC6> invFileDescriptor;
  public DC6   invFile;

  public Index invColormap;
  public int   invColorIndex;

  public Index charColormap;
  public int   charColorIndex;

  /** Last quantity reported to the diagnostic log; avoids per-frame spam. */
  private int lastLoggedQuantity = Integer.MIN_VALUE;

  ItemWrapper(Item item) {
    this.item = item;
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

    // TODO: load images of socketed items
  }

  public boolean checkLoaded() {
    boolean b = Riiablo.assets.isLoaded(invFileDescriptor);
    if (b && invFile == null) {
      invFile = Riiablo.assets.get(invFileDescriptor);
      resize();
    }

    return b;
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
    if (ethereal) b.resetColor();

    drawQuantity(b);
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

  @Override
  public void dispose() {
    Riiablo.assets.unload(invFileDescriptor.fileName);
  }
}
