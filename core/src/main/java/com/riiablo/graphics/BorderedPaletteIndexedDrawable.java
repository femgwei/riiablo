package com.riiablo.graphics;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.loader.DC6Loader;
import com.riiablo.util.AssetReferenceScope;

public class BorderedPaletteIndexedDrawable extends PaletteIndexedColorDrawable implements Disposable {
  private final AssetManager assets;
  private final AssetReferenceScope references;
  private boolean disposed;

  static final AssetDescriptor<DC6> boxpiecesDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\boxpieces.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC6 boxpieces;

  static final float X_INC = 12;
  static final float Y_INC = 12;

  TextureRegion TOPLEFT, TOPRIGHT, BOTTOMLEFT, BOTTOMRIGHT;
  TextureRegion LEFT[], RIGHT[], TOP[], BOTTOM[];

  public static final float PADDING = 8;

  public BorderedPaletteIndexedDrawable() {
    super(Riiablo.colors.modal75);

    setLeftWidth(PADDING);
    setTopHeight(PADDING);
    setRightWidth(PADDING);
    setBottomHeight(PADDING);

    assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
    references = new AssetReferenceScope(assets);
    references.load(boxpiecesDescriptor);
  }

  @Override
  public void draw(Batch batch, float x, float y, float width, float height) {
    if (disposed) return;
    super.draw(batch, x + 2, y + 2, width - 4, height - 4);
    if (!(batch instanceof PaletteIndexedBatch)) {
      return;
    } else if (boxpieces == null) {
      if (!assets.isLoaded(boxpiecesDescriptor)) return;
      boxpieces = assets.get(boxpiecesDescriptor);
      TOPLEFT     = boxpieces.getTexture(0);
      TOPRIGHT    = boxpieces.getTexture(1);
      BOTTOMLEFT  = boxpieces.getTexture(8);
      BOTTOMRIGHT = boxpieces.getTexture(9);
      LEFT   = new TextureRegion[] { boxpieces.getTexture(10), boxpieces.getTexture(11), boxpieces.getTexture(12) };
      RIGHT  = new TextureRegion[] { boxpieces.getTexture(13), boxpieces.getTexture(14), boxpieces.getTexture(15) };
      TOP    = new TextureRegion[] { boxpieces.getTexture(2 ), boxpieces.getTexture(3 ), boxpieces.getTexture(4 ),
                                     boxpieces.getTexture(5 ), boxpieces.getTexture(6 ), boxpieces.getTexture(7 ) };
      BOTTOM = new TextureRegion[] { boxpieces.getTexture(16), boxpieces.getTexture(17), boxpieces.getTexture(18),
                                     boxpieces.getTexture(19), boxpieces.getTexture(20), boxpieces.getTexture(21) };
    }

    PaletteIndexedBatch b = (PaletteIndexedBatch) batch;

    int i = 0, j = 0;
    float tmpX = x + X_INC;
    while (tmpX < x + width - TOPRIGHT.getRegionWidth()) {
      if (i == TOP.length) i = 0;
      b.draw(TOP[i++], tmpX, y + height - boxpieces.getTexture(0).getRegionHeight());
      if (j == BOTTOM.length) j = 0;
      b.draw(BOTTOM[j++], tmpX, y - 9);
      tmpX += X_INC;
    }

    i = 0;
    j = 0;
    float tmpY = y + Y_INC;
    while (tmpY < y + height - TOPLEFT.getRegionHeight()) {
      if (i == LEFT.length) i = 0;
      b.draw(LEFT[i++], x - 4, tmpY);
      if (j == RIGHT.length) j = 0;
      b.draw(RIGHT[j++], x + width - 9, tmpY);
      tmpY += Y_INC;
    }

    b.draw(TOPLEFT, x, y + height - TOPLEFT.getRegionHeight());
    b.draw(TOPRIGHT, x + width - TOPRIGHT.getRegionWidth(), y + height - TOPRIGHT.getRegionHeight());
    b.draw(BOTTOMLEFT, x, y);
    b.draw(BOTTOMRIGHT, x + width - BOTTOMRIGHT.getRegionWidth(), y);
  }

  @Override public void draw(Batch batch, float x, float y, float originX, float originY,
      float width, float height, float scaleX, float scaleY, float rotation) {
    if (!disposed) super.draw(batch, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    boxpieces = null;
    TOPLEFT = TOPRIGHT = BOTTOMLEFT = BOTTOMRIGHT = null;
    LEFT = RIGHT = TOP = BOTTOM = null;
    setRegion(null);
    references.dispose();
  }
}
