package com.riiablo;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.codec.Index;
import com.riiablo.codec.PL2;
import com.riiablo.codec.Palette;

public class Colormaps implements Disposable {
  public final Index brown, gold;
  public final Index grey, grey2, greybrown;
  public final Index invgrey, invgrey2, invgreybrown;

  private final Index[] stateTransforms = new Index[111];
  private PL2 statePalette;
  private boolean statePaletteAttempted;

  public Colormaps(AssetManager assets) {
    brown        = load(assets, "brown").render();
    gold         = load(assets, "gold").render();
    grey         = load(assets, "grey").render();
    grey2        = load(assets, "grey2").render();
    greybrown    = load(assets, "greybrown").render();
    invgrey      = load(assets, "invgrey").render();
    invgrey2     = load(assets, "invgrey2").render();
    invgreybrown = load(assets, "invgreybrown").render();
  }

  public Index get(int index) {
    switch (index) {
      case 1:  return grey;
      case 2:  return grey2;
      case 3:  return null;//brown;
      case 4:  return null;//gold;
      case 5:  return greybrown;
      case 6:  return invgrey;
      case 7:  return invgrey2;
      case 8:  return invgreybrown;
      default: return null;
    }
  }

  public Texture getTexture(int index) {
    return get(index).texture;
  }

  public String toString(int index) {
    return get(index).name;
  }

  /**
   * Builds the 22-row colormap expected by the renderer for a native
   * States.txt colorshift.  The actual state row comes from Pal.pl2's
   * HueVariations section; row zero remains identity as required by the
   * palette shader.
   */
  public Index getStateTransform(int colorShift) {
    if (colorShift < 0 || colorShift >= stateTransforms.length) return null;
    if (stateTransforms[colorShift] != null) return stateTransforms[colorShift];
    if (!statePaletteAttempted) {
      statePaletteAttempted = true;
      try {
        if (Riiablo.mpqs != null) {
          statePalette = PL2.loadFromFile(
              Riiablo.mpqs.resolve("data\\global\\palette\\ACT1\\Pal.pl2"));
        }
      } catch (Throwable ignored) {
        statePalette = null;
      }
    }
    if (statePalette == null) return null;
    byte[] hue = statePalette.getHueVariation(colorShift);
    if (hue == null) return null;
    byte[][] rows = new byte[Index.INDEXES][Palette.COLORS];
    for (int row = 0; row < rows.length; row++) {
      for (int color = 0; color < Palette.COLORS; color++) {
        rows[row][color] = (byte) color;
      }
    }
    // Layer.setTransform(..., 1) selects this row while preserving the
    // identity row for untransformed pixels.
    System.arraycopy(hue, 0, rows[1], 0, Palette.COLORS);
    return stateTransforms[colorShift] =
        Index.fromColormaps("state-colorshift-" + colorShift, rows).render();
  }

  private Index load(AssetManager assets, String fontName) {
    AssetDescriptor<Index> descriptor = getDescriptor(fontName);
    assets.load(descriptor);
    assets.finishLoadingAsset(descriptor);
    return assets.get(descriptor);
  }

  private static AssetDescriptor<Index> getDescriptor(String paletteName) {
    return new AssetDescriptor<>("data\\global\\items\\Palette\\" + paletteName + ".dat", Index.class);
  }

  @Override
  public void dispose() {
    brown.dispose();
    gold.dispose();
    grey.dispose();
    grey2.dispose();
    greybrown.dispose();
    invgrey.dispose();
    invgrey2.dispose();
    invgreybrown.dispose();
    for (Index transform : stateTransforms) {
      if (transform != null) transform.dispose();
    }
  }
}
