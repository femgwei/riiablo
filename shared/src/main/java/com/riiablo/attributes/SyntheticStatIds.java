package com.riiablo.attributes;

/** Synthetic table identifiers shared by the schema and attribute runtime. */
public final class SyntheticStatIds {
  public static final short REQUIRED_STRENGTH = (1 << 9) - 2;
  public static final short REQUIRED_DEXTERITY = (1 << 9) - 3;

  private SyntheticStatIds() {}
}
