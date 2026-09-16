package com.riiablo.engine.client.automap;

import com.badlogic.gdx.utils.Array;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Detects duplicate and conflicting cells before they reach the DC6 renderer. */
public final class AutomapCellAudit {
  private static final int MAX_SAMPLES = 12;

  private AutomapCellAudit() {}

  public static Result audit(AutomapLayer layer) {
    Result result = new Result();
    if (layer == null) return result;

    Map<String, Array<AutomapCell>> categories = new LinkedHashMap<>();
    categories.put("floors", layer.floors);
    categories.put("roads", layer.roads);
    categories.put("walls", layer.walls);
    categories.put("objects", layer.objects);
    categories.put("extras", layer.extras);

    Map<String, String> exactOwner = new HashMap<>();
    Map<String, Set<Integer>> cellsByPosition = new HashMap<>();
    Map<String, Set<String>> categoriesByPosition = new HashMap<>();
    for (Map.Entry<String, Array<AutomapCell>> category : categories.entrySet()) {
      Set<String> seenInCategory = new HashSet<>();
      for (AutomapCell cell : category.getValue()) {
        result.total++;
        String exact = exactKey(cell);
        if (!seenInCategory.add(exact)) {
          result.withinCategoryExact++;
          result.sample("within " + category.getKey() + " " + exact);
        }
        String owner = exactOwner.putIfAbsent(exact, category.getKey());
        if (owner != null && !owner.equals(category.getKey())) {
          result.crossCategoryExact++;
          result.sample("cross " + owner + "/" + category.getKey() + " " + exact);
        }
        String position = positionKey(cell);
        cellsByPosition.computeIfAbsent(position, ignored -> new LinkedHashSet<>())
            .add(cell.cellNo);
        categoriesByPosition.computeIfAbsent(position, ignored -> new LinkedHashSet<>())
            .add(category.getKey());
      }
    }

    for (Map.Entry<String, Set<Integer>> position : cellsByPosition.entrySet()) {
      if (position.getValue().size() > 1) {
        result.samePositionDifferentCell++;
        String[] xy = position.getKey().split(",");
        Conflict conflict = new Conflict(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]),
            position.getValue(), categoriesByPosition.get(position.getKey()));
        result.conflicts.add(conflict);
        result.sample(conflict.toString());
      }
    }
    return result;
  }

  private static String exactKey(AutomapCell cell) {
    return cell.cellNo + "@" + cell.xPixel + "," + cell.yPixel;
  }

  private static String positionKey(AutomapCell cell) {
    return cell.xPixel + "," + cell.yPixel;
  }

  public static final class Result {
    public int total;
    public int withinCategoryExact;
    public int crossCategoryExact;
    public int samePositionDifferentCell;
    public final Array<String> samples = new Array<>();
    public final Array<Conflict> conflicts = new Array<>();

    private void sample(String value) {
      if (samples.size < MAX_SAMPLES) samples.add(value);
    }

    public boolean hasDuplicates() {
      return withinCategoryExact != 0 || crossCategoryExact != 0
          || samePositionDifferentCell != 0;
    }
  }

  public static final class Conflict {
    public final int worldX;
    public final int worldY;
    public final Set<Integer> cellNos;
    public final Set<String> categories;
    public final int maX;
    public final int maY;

    Conflict(int worldX, int worldY, Set<Integer> cellNos, Set<String> categories) {
      this.worldX = worldX;
      this.worldY = worldY;
      this.cellNos = new LinkedHashSet<>(cellNos);
      this.categories = new LinkedHashSet<>(categories);
      int tx = Math.floorDiv(worldX, 5);
      int ty = Math.floorDiv(worldY, 5);
      this.maX = 8 * (tx - ty);
      this.maY = 4 * (tx + ty);
    }

    @Override public String toString() {
      return "position " + worldX + "," + worldY + " ma=" + maX + "," + maY
          + " cells=" + cellNos + " categories=" + categories;
    }
  }
}
