package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.ItemRatio;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.engine.server.item.ItemQuality;
import org.junit.jupiter.api.Test;


class NativeItemQualityResolverTest {
  @Test
  void nativeOrderHonorsTcModifierAndMagicFind() {
    ItemRatio.Entry ratio = new ItemRatio.Entry();
    ratio.Unique = 100;
    ratio.UniqueDivisor = 1;
    ratio.UniqueMin = 1;
    ratio.Set = 100;
    ratio.SetDivisor = 1;
    ratio.SetMin = 1;
    ratio.Rare = 100;
    ratio.RareDivisor = 1;
    ratio.RareMin = 1;
    ratio.Magic = 100;
    ratio.MagicDivisor = 1;
    ratio.MagicMin = 1;
    ratio.HiQuality = 100;
    ratio.HiQualityDivisor = 1;
    ratio.Normal = 100;
    ratio.NormalDivisor = 1;
    ItemEntry base = new ItemEntry();
    base.level = 1;
    ItemTypes.Entry type = new ItemTypes.Entry();

    assertEquals(ItemQuality.UNIQUE, NativeItemQualityResolver.roll(ratio, base, type,
        1, 0, 0, 0, 0, 0, 0, 0, bound -> 0));
    assertEquals(ItemQuality.INFERIOR, NativeItemQualityResolver.roll(ratio, base, type,
        1, 0, 0, 0, 0, 0, 0, 0, bound -> bound - 1));

    // A fully inherited TC modifier subtracts the complete denominator and
    // therefore succeeds without consuming a random roll, as in D2Game.
    assertEquals(ItemQuality.UNIQUE, NativeItemQualityResolver.roll(ratio, base, type,
        1, 0, 1024, 0, 0, 0, 0, 0, bound -> { throw new AssertionError(); }));

    int noMf = NativeItemQualityResolver.denominator(100, 1, 1, 0, 0, 50, 150, 5, 0);
    int withMf = NativeItemQualityResolver.denominator(100, 1, 1, 0, 200, 50, 150, 5, 0);
    assertTrue(withMf < noMf, "MF should reduce the quality denominator");
  }

  @Test
  void forcedTypeFlagsMatchNativeOrder() {
    ItemRatio.Entry ratio = new ItemRatio.Entry();
    ratio.Unique = ratio.Set = ratio.Rare = ratio.Magic = 100000;
    ratio.UniqueDivisor = ratio.SetDivisor = ratio.RareDivisor = ratio.MagicDivisor = 1;
    ItemEntry base = new ItemEntry();
    ItemTypes.Entry normal = new ItemTypes.Entry();
    normal.Normal = true;
    assertEquals(ItemQuality.NORMAL, NativeItemQualityResolver.roll(ratio, base, normal,
        1, 0, 0, 0, 0, 0, 0, 0, bound -> 0));

    ItemTypes.Entry magic = new ItemTypes.Entry();
    magic.Magic = true;
    assertEquals(ItemQuality.MAGIC, NativeItemQualityResolver.roll(ratio, base, magic,
        1, 0, 0, 0, 0, 0, 0, 0, bound -> bound - 1));
  }

}
