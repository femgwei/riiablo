package com.riiablo.item;

import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.ItemRatio;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.engine.server.item.ItemQuality;

/** Native D2GAME_DropTC ItemRatio/MF quality sequence. */
public final class NativeItemQualityResolver {
  private NativeItemQualityResolver() {}

  public interface RandomSource { int nextInt(int bound); }

  public static int roll(ItemRatio.Entry ratio, ItemEntry base, ItemTypes.Entry type,
                         int itemLevel, int magicFind,
                         int uniqueMod, int setMod, int rareMod, int magicMod,
                         int superiorMod, int normalMod, RandomSource random) {
    if (ratio == null || base == null || random == null) return ItemQuality.NORMAL;
    if (base.quest != 0 || (type != null && type.Normal)) return ItemQuality.NORMAL;

    int levelDiff = itemLevel - base.level;
    if (success(denominator(ratio.Unique, ratio.UniqueDivisor, ratio.UniqueMin,
        levelDiff, magicFind, 50, 150, 5, uniqueMod), random)) return ItemQuality.UNIQUE;
    if (success(denominator(ratio.Set, ratio.SetDivisor, ratio.SetMin,
        levelDiff, magicFind, 100, 400, 5, setMod), random)) return ItemQuality.SET;
    if (type == null || type.Rare) {
      if (success(denominator(ratio.Rare, ratio.RareDivisor, ratio.RareMin,
          levelDiff, magicFind, 200, 500, 3, rareMod), random)) return ItemQuality.RARE;
    }
    if (type != null && type.Magic) return ItemQuality.MAGIC;
    if (success(denominator(ratio.Magic, ratio.MagicDivisor, ratio.MagicMin,
        levelDiff, magicFind, 0, 0, 0, magicMod), random)) return ItemQuality.MAGIC;
    if (forced(superiorMod, random)) return ItemQuality.SUPERIOR;
    if (forced(normalMod, random)) return ItemQuality.NORMAL;
    if (success(simpleDenominator(ratio.HiQuality, ratio.HiQualityDivisor, levelDiff), random))
      return ItemQuality.SUPERIOR;
    if (success(simpleDenominator(ratio.Normal, ratio.NormalDivisor, levelDiff), random))
      return ItemQuality.NORMAL;
    return ItemQuality.INFERIOR;
  }

  static int denominator(int base, int divisor, int minimum, int levelDiff, int magicFind,
                         int mfFactor, int mfOffset, int mfMultiplier, int tcModifier) {
    int raw = base - divide(levelDiff, divisor);
    long denominator = (long) raw << 7;
    int mfDivisor = magicFind + 100;
    if (magicFind != 0 && mfDivisor != 0) {
      int adjusted = mfDivisor;
      if (mfFactor > 0 && mfDivisor > 110) {
        adjusted = mfFactor * (mfMultiplier * mfDivisor - mfMultiplier * 100)
            / (mfDivisor + mfOffset) + 100;
      }
      if (adjusted != 0) denominator = 12800L * raw / adjusted;
    }
    denominator = Math.max(denominator, minimum);
    denominator -= denominator * Math.max(0, tcModifier) / 1024L;
    return clampDenominator(denominator);
  }

  static int simpleDenominator(int base, int divisor, int levelDiff) {
    return clampDenominator((long) (base - divide(levelDiff, divisor)) << 7);
  }

  private static int divide(int value, int divisor) { return divisor == 0 ? 0 : value / divisor; }
  private static int clampDenominator(long value) {
    if (value <= 0) return 0;
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }
  private static boolean success(int denominator, RandomSource random) {
    return denominator <= 0 || random.nextInt(Math.max(1, denominator)) < 128;
  }
  private static boolean forced(int modifier, RandomSource random) {
    return modifier > 0 && random.nextInt(1024) < Math.min(1024, modifier);
  }
}
