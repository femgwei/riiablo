package com.riiablo.engine.client.automap;

/** Client facade for the map-owned AutoMap.txt name classification. */
public final class AutomapLevelNames {
  private AutomapLevelNames() {}

  public static String resolve(int act, String typeName, boolean inside) {
    return com.riiablo.map.MapLevelNames.resolve(act, typeName, inside);
  }
}
