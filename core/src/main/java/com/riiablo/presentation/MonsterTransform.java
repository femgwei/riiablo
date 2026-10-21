package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.Index;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Monster;

/** Applies a unit's native palette shift or the normal packed transform. */
public final class MonsterTransform {
  private MonsterTransform() {}

  public static void apply(Animation.Layer layer, Monster monster, byte packedTransform) {
    if (layer == null || Riiablo.colormaps == null) return;

    int packed = packedTransform & 0xFF;
    byte monsterPaletteRow = monster != null && monster.monstats != null
        ? CofTransforms.monsterPaletteRow(monster.monstats.TransLvl)
        : CofTransforms.TRANSFORM_NULL;
    if (monsterPaletteRow != CofTransforms.TRANSFORM_NULL
        && packed == (monsterPaletteRow & 0xFF)
        && (packed >>> 5) == 0) {
      Index palshift = Riiablo.colormaps.getMonsterTransform(monster.monstats.Code);
      if (palshift != null) {
        layer.setTransform(palshift, packed & 0x1F);
        return;
      }
    }
    layer.setTransform(packedTransform);
  }
}
