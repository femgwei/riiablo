package com.riiablo.engine.client.automap;

import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;

/** Resolves native entity Automap cells from D2 1.10f TXT rows. */
public final class AutomapEntityCells {
  private AutomapEntityCells() {}

  /** Returns Objects.txt AutoMap, or -1 when the row has no native icon. */
  public static int objectCell(Objects.Entry object) {
    if (object == null) return -1;
    if (object.AutoMap >= 0) return object.AutoMap;
    // D2MOO object rows sometimes omit AutoMap while the object class has a
    // well-known native cell (waypoint, shrine, well, stash).
    return AutomapIconType.getIconForObject(object.Id, object.ShrineFunction);
  }

  /** Returns MonStats2.txt automapCel, or -1 for monsters hidden on automap. */
  public static int monsterCell(MonStats2.Entry monster) {
    return monster == null || monster.automapCel < 0 ? -1 : monster.automapCel;
  }

  /** Native cells are valid DC6 frame numbers; zero is a valid frame. */
  public static boolean hasCell(int cell) {
    return cell >= 0;
  }
}
