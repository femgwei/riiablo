package com.riiablo.engine.client.automap;

import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;

/** Resolves native entity Automap cells from D2 1.10f TXT rows. */
public final class AutomapEntityCells {
  private AutomapEntityCells() {}

  /** Returns Objects.txt AutoMap, or -1 when the row has no native icon. */
  public static int objectCell(Objects.Entry object) {
    if (object == null) return -1;
    // Town portals are animated object overlays.  They do not use a
    // MaxiMap.dc6 cell; returning a table value here can make them render as
    // an unrelated yellow/NPC glyph.
    if (AutomapMarkerPolicy.isTownPortalObject(object)) return -1;
    // D2CLIENT only allocates an object AutomapCell when Objects.txt AutoMap
    // is non-zero. Zero is the table sentinel used by ordinary scenery such
    // as camp torches; treating it as MaxiMap frame 0 draws a path fragment.
    if (object.AutoMap > 0) return object.AutoMap;
    // D2MOO object rows sometimes omit AutoMap while the object class has a
    // well-known native cell (waypoint, shrine, well, stash).
    int fallback = AutomapIconType.getIconForObject(object.Id, object.ShrineFunction);
    return fallback > 0 ? fallback : -1;
  }

  /** Returns MonStats2.txt automapCel, or -1 for monsters hidden on automap. */
  public static int monsterCell(MonStats2.Entry monster) {
    // automapCel=0 is the native table sentinel used by ordinary monsters and
    // town NPCs. Treating it as MaxiMap frame 0 paints the same path fragment
    // once per entity, which looks like repeated scenery/chest markers.
    return monster == null || monster.automapCel <= 0 ? -1 : monster.automapCel;
  }

  /** Native cells are valid DC6 frame numbers; zero is a valid frame. */
  public static boolean hasCell(int cell) {
    return cell >= 0;
  }
}
