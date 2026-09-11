package com.riiablo.engine.client.automap;

/** Resolves LvlTypes names to the canonical D2MOO AutoMap.txt LevelName. */
public final class AutomapLevelNames {
  private AutomapLevelNames() {}

  public static String resolve(int act, String typeName, boolean inside) {
    if (act < 1 || typeName == null || typeName.trim().isEmpty()) return null;
    String n = typeName.trim().toLowerCase().replace(" ", "");
    String kind;
    if (n.contains("town") || n.contains("camp")) kind = "Town";
    else if (n.contains("wilderness") || n.contains("outdoor")) kind = wilderness(act);
    else if (n.contains("monest") || n.contains("monast")) kind = "Monestary";
    else if (n.contains("courtyard")) kind = "Courtyard";
    else if (n.contains("barrack")) kind = "Barracks";
    else if (n.contains("catacomb")) kind = "Catacombs";
    else if (n.contains("cathedral")) kind = "Cathedral";
    else if (n.contains("jail")) kind = "Jail";
    else if (n.contains("crypt")) kind = "Crypt";
    else if (n.contains("tristram")) kind = "Tristram";
    else if (n.contains("ice")) kind = "Ice";
    else if (n.contains("cave")) kind = "Cave";
    else if (n.contains("sewer")) kind = "Sewer";
    else if (n.contains("desert")) kind = "Desert";
    else if (n.contains("tomb")) kind = "Tomb";
    else if (n.contains("harem")) kind = "Harem";
    else if (n.contains("basement")) kind = "Basement";
    else if (n.contains("arcane")) kind = "Arcane";
    else if (n.contains("jungle")) kind = "Jungle";
    else if (n.contains("kurast")) kind = "Kurast";
    else if (n.contains("spider")) kind = "Spider";
    else if (n.contains("dungeon")) kind = "Dungeon";
    else if (n.contains("mesa")) kind = "Mesa";
    else if (n.contains("lava")) kind = "Lava";
    else if (n.contains("siege")) kind = "Siege";
    else if (n.contains("barricade")) kind = "Barricade";
    else if (n.contains("temple")) kind = "Temple";
    else if (n.contains("baal")) kind = "Baal";
    else return null;
    return act + " " + kind;
  }

  private static String wilderness(int act) {
    switch (act) {
      case 1: return "Wilderness";
      case 2: return "Desert";
      case 3: return "Jungle";
      case 4: return "Mesa";
      default: return "Ice";
    }
  }
}
