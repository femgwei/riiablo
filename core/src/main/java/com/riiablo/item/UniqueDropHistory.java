package com.riiablo.item;

import java.util.BitSet;

/** UniqueItems row identities already generated in one game. */
public final class UniqueDropHistory {
  private final BitSet dropped = new BitSet();

  public boolean claim(int id, boolean noLimit) {
    if (id < 0) return false;
    if (noLimit) return true;
    if (id >= 4096) return false;
    if (dropped.get(id)) return false;
    dropped.set(id);
    return true;
  }
}
