package com.riiablo.widget;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;

/** Detached display values; reading the character panel must never clamp combat stats. */
final class ResistanceDisplayValue {
  final int raw;
  final int maximum;
  final int displayed;
  final boolean capped;

  private ResistanceDisplayValue(int raw, int maximum) {
    this.raw = raw;
    this.maximum = maximum;
    displayed = Math.min(raw, maximum);
    capped = raw >= maximum;
  }

  static ResistanceDisplayValue capture(Attributes attrs, short stat) {
    final short maxStat;
    switch (stat) {
      case Stat.fireresist: maxStat = Stat.maxfireresist; break;
      case Stat.coldresist: maxStat = Stat.maxcoldresist; break;
      case Stat.lightresist: maxStat = Stat.maxlightresist; break;
      case Stat.poisonresist: maxStat = Stat.maxpoisonresist; break;
      default: throw new IllegalArgumentException("Not a character resistance: " + stat);
    }
    // Riiablo's character aggregate already includes difficulty penalties and
    // the base 75 maximum. Do not add either again in the presentation layer.
    // Store primitives, never the reusable StatRef returned by Attributes.get.
    int raw = attrs.getValue(stat, 0);
    int maximum = attrs.getValue(maxStat, Integer.MAX_VALUE);
    return new ResistanceDisplayValue(raw, maximum);
  }
}
