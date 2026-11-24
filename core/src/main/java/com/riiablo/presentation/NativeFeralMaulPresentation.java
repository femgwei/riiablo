package com.riiablo.presentation;

import com.riiablo.Files;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;

/** D2Client 1.10f setfunc 16 (RVA ACDA0), two charge-dependent overlay ranges. */
public final class NativeFeralMaulPresentation {
  private NativeFeralMaulPresentation() {}

  public static boolean handles(int stateId) {
    return stateId == StateId.FERALRAGE || stateId == StateId.MAUL;
  }

  static String[] overlays(Files files, States.Entry definition, UnitState state) {
    if (files == null || files.Overlay == null || files.skills == null
        || definition == null || definition.setFunc != 16 || state == null
        || state.expired || !handles(state.stateId) || definition.overlays.length < 4)
      return new String[0];
    Skills.Entry skill = files.skills.get(state.skillId);
    if (skill == null || DruidSkills.getFeralMaulStateId(skill) != state.stateId)
      return new String[0];
    int first = files.Overlay.index(definition.overlays[0]);
    int last = files.Overlay.index(definition.overlays[1]);
    if (first < 0 || last < first) return new String[0];
    int count = last - first + 1;
    int maximum = Math.min(count, DruidSkills.getFeralMaulMaxStacks(skill, state.level));
    int charges = Math.min(count, state.runtimeValue);
    if (maximum <= 0 || charges <= 0) return new String[0];
    // Native clamps both max and current charge by the FRONT range, then
    // uses that same denominator to choose an index in each range.
    String front = select(files, first, last, charges, maximum);
    int backFirst = files.Overlay.index(definition.overlays[2]);
    int backLast = files.Overlay.index(definition.overlays[3]);
    String back = select(files, backFirst, backLast, charges, maximum);
    if (front == null) return back == null ? new String[0] : new String[] {back};
    return back == null ? new String[] {front} : new String[] {front, back};
  }

  private static String select(Files files, int first, int last, int charges, int maximum) {
    if (first < 0 || last < first) return null;
    int count = last - first + 1;
    long index = (long) (charges - 1) * count / maximum;
    if (index < 0 || index >= count) return null;
    com.riiablo.codec.excel.Overlay.Entry row = files.Overlay.get(first + (int) index);
    return row == null ? null : row.overlay;
  }
}
