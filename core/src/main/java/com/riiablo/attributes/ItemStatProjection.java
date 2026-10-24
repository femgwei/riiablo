package com.riiablo.attributes;

import java.util.Arrays;

/** Immutable equipment stat view passed from M6 into consumers such as combat. */
public final class ItemStatProjection {
  private final short[] ids;
  private final long[] values;

  private ItemStatProjection(short[] ids, long[] values) {
    this.ids = ids;
    this.values = values;
  }

  /** Builds a deterministic sum of aggregate item stat lists. */
  public static ItemStatProjection from(Attributes... sources) {
    long[] totals = new long[Short.MAX_VALUE - Short.MIN_VALUE + 1];
    boolean[] present = new boolean[totals.length];
    if (sources != null) for (Attributes source : sources) {
      if (source == null || source.aggregate() == null) continue;
      for (StatRef stat : source.aggregate()) {
        int index = stat.id() - Short.MIN_VALUE;
        if (!present[index]) present[index] = true;
        totals[index] = saturatedAdd(totals[index], stat.asLong());
      }
    }
    int count = 0;
    for (boolean value : present) if (value) count++;
    short[] ids = new short[count];
    long[] values = new long[count];
    int out = 0;
    for (int i = 0; i < present.length; i++) if (present[i]) {
      ids[out] = (short) (i + Short.MIN_VALUE);
      values[out++] = totals[i];
    }
    return new ItemStatProjection(ids, values);
  }

  /** Creates a projection from already aggregated stat/value pairs. */
  public static ItemStatProjection fromValues(short[] statIds, long[] statValues) {
    if (statIds == null || statValues == null || statIds.length != statValues.length) {
      throw new IllegalArgumentException("stat ids and values must have equal lengths");
    }
    short[] ids = statIds.clone();
    long[] values = statValues.clone();
    for (int i = 1; i < ids.length; i++) {
      short id = ids[i];
      long value = values[i];
      int j = i - 1;
      while (j >= 0 && ids[j] > id) {
        ids[j + 1] = ids[j];
        values[j + 1] = values[j];
        j--;
      }
      ids[j + 1] = id;
      values[j + 1] = value;
    }
    return new ItemStatProjection(ids, values);
  }

  public long get(short stat, long defaultValue) {
    int index = Arrays.binarySearch(ids, stat);
    return index < 0 ? defaultValue : values[index];
  }

  public int size() { return ids.length; }

  public short statIdAt(int index) { return ids[index]; }

  public long valueAt(int index) { return values[index]; }

  private static long saturatedAdd(long left, long right) {
    if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
    if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
    return left + right;
  }
}
