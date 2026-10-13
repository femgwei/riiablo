package com.riiablo.map;

import com.riiablo.codec.excel.Levels;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Deterministic diagnostic itinerary; Vis is a hint, not proof of walkability. */
public final class MapTourPlan {
  private MapTourPlan() {}

  /** Explicit, ordered reproduction only; callers label this as partial coverage. */
  public static List<Integer> focusedLevels(List<Integer> complete, String selection) {
    if (selection == null) return complete;
    List<Integer> focused = new ArrayList<>();
    for (String token : selection.split(",", -1)) {
      int level = Integer.parseInt(token.trim());
      if (!complete.contains(level) || focused.contains(level)) {
        throw new IllegalArgumentException("Unknown, out-of-act or repeated tour level: " + level);
      }
      focused.add(level);
    }
    return focused;
  }

  /** Independently checks the complete 1.10f id range, not just available rows. */
  public static void requireCoverage(List<Integer> itinerary, int firstAct, int lastAct) {
    int[] first = {1, 40, 75, 103, 109};
    int[] last = {39, 74, 102, 108, 132};
    for (int id = first[firstAct - 1]; id <= last[lastAct - 1]; id++) {
      if (!itinerary.contains(id)) throw new IllegalStateException("Missing 1.10f tour level " + id);
    }
  }

  public static List<Integer> levels(Iterable<Levels.Entry> rows, int firstAct, int lastAct) {
    if (firstAct < 1 || lastAct > 5 || firstAct > lastAct) {
      throw new IllegalArgumentException("Expected an Act range within 1..5");
    }
    TreeMap<Integer, Levels.Entry> remaining = new TreeMap<>();
    for (Levels.Entry row : rows) {
      // 1.10f playable levels end at Worldstone Chamber=132. Do not silently
      // expand a 1.10f gate into later-version Uber areas or level zero.
      if (row != null && row.Id >= 1 && row.Id <= 132
          && row.Act + 1 >= firstAct && row.Act + 1 <= lastAct) {
        remaining.put(row.Id, row);
      }
    }
    List<Integer> result = new ArrayList<>();
    Levels.Entry previous = null;
    while (!remaining.isEmpty()) {
      Integer next = null;
      if (previous != null && previous.Vis != null) {
        for (int id : previous.Vis) {
          Levels.Entry candidate = remaining.get(id);
          if (candidate != null && candidate.Act == previous.Act
              && !(id >= 128 && remaining.firstKey() < 128 && previous.Act == 4)
              && (next == null || id < next)) next = id;
        }
      }
      if (next == null) next = remaining.firstKey();
      previous = remaining.remove(next);
      result.add(next);
    }
    return result;
  }

  public static boolean declaresWarp(Levels.Entry from, int to) {
    if (from == null || from.Vis == null || from.Warp == null) return false;
    for (int i = 0; i < Math.min(from.Vis.length, from.Warp.length); i++) {
      if (from.Vis[i] == to && from.Warp[i] >= 0) return true;
    }
    return false;
  }
}
