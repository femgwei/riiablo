package com.riiablo.engine.client;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.Index;
import com.riiablo.engine.server.component.Monster;

/** Applies a unit's native palette shift or the normal packed transform. */
final class MonsterTransform {
  private MonsterTransform() {}

  static void apply(Animation.Layer layer, Monster monster, byte packedTransform) {
    if (layer == null || Riiablo.colormaps == null) return;

    int packed = packedTransform & 0xFF;
    if (monster != null && monster.monstats != null
        && monster.monstats.TransLvl > 0 && packed != 0xFF
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
