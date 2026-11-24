package com.riiablo.presentation;

import com.riiablo.Files;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import java.util.ArrayList;

/** D2Client 1.10f setfunc8 (RVA AC560): three paired armor effects. */
public final class NativeCycloneArmorPresentation {
  private NativeCycloneArmorPresentation() {}

  public static int count(UnitState state) {
    if (state == null || state.expired || state.stateId != StateId.CYCLONEARMOR) return 0;
    int current = state.getStatContributionValue(Stat.bonearmor);
    int maximum = state.getStatContributionValue(Stat.bonearmormax);
    if (current <= 0 || maximum <= 0) return 0;
    int third = maximum / 3;
    // Retail compares against floor(max/3) and twice THAT rounded value.
    return current < third ? 1 : current < 2 * third ? 2 : 3;
  }

  static String[] overlays(Files files, States.Entry definition, int count) {
    if (files == null || files.Overlay == null || definition == null
        || definition.setFunc != 8 || count <= 0) return new String[0];
    ArrayList<String> selected = new ArrayList<>(6);
    for (int i = 0; i < Math.min(3, count); i++) {
      int front = files.Overlay.index(definition.overlays[i]);
      if (front < 0) continue;
      // Native creates both the configured numeric ID and ID+1, not a
      // synthesized resource name. In retail data ID+1 is its back half.
      for (int offset = 0; offset < 2; offset++) {
        com.riiablo.codec.excel.Overlay.Entry entry = files.Overlay.get(front + offset);
        if (entry != null) selected.add(entry.overlay);
      }
    }
    return selected.toArray(new String[0]);
  }
}
