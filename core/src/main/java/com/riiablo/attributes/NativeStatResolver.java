package com.riiablo.attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic Base -> Add -> Percent resolver for native encoded stat values. */
public final class NativeStatResolver {
  private NativeStatResolver() {}

  public enum Operation { ADD, PERCENT }

  /** One durable contribution owned by equipment, a state, an aura, or another named source. */
  public static final class Source {
    public final String id;
    public final int order;
    public final Operation operation;
    public final int encodedValue;

    public Source(String id, int order, Operation operation, int encodedValue) {
      this.id = id == null ? "" : id;
      this.order = order;
      this.operation = operation == null ? Operation.ADD : operation;
      this.encodedValue = encodedValue;
    }
  }

  /**
   * Resolves an ItemStatCost-encoded value without converting fixed-point stats to float.
   * Stable source order is retained for replay diagnostics; all Add values are folded before
   * the combined Percent phase, so callers cannot change the result by insertion order.
   */
  public static int resolveEncoded(int encodedBase, Iterable<Source> sources) {
    List<Source> ordered = new ArrayList<>();
    if (sources != null) for (Source source : sources) if (source != null) ordered.add(source);
    ordered.sort(Comparator.comparingInt((Source source) -> source.order)
        .thenComparing(source -> source.id));

    int flat = encodedBase;
    int percent = 0;
    for (Source source : ordered) {
      if (source.operation == Operation.PERCENT) percent += source.encodedValue;
      else flat += source.encodedValue;
    }
    return applyPercentEncoded(flat, percent);
  }

  /** Applies the native combined percentage once, truncating only at the encoded phase boundary. */
  public static int applyPercentEncoded(int encodedBase, int percent) {
    return (int) ((long) encodedBase * (100L + percent) / 100L);
  }
}
