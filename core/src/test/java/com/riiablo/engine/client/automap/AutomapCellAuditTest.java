package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutomapCellAuditTest {
  @Test
  void detectsWithinCrossCategoryAndPositionConflicts() {
    AutomapLayer layer = new AutomapLayer(2);
    layer.floors.add(new AutomapCell(10, 100, 200));
    layer.floors.add(new AutomapCell(10, 100, 200));
    layer.walls.add(new AutomapCell(10, 100, 200));
    layer.objects.add(new AutomapCell(11, 100, 200));

    AutomapCellAudit.Result result = AutomapCellAudit.audit(layer);

    assertEquals(4, result.total);
    assertEquals(1, result.withinCategoryExact);
    assertEquals(1, result.crossCategoryExact);
    assertEquals(1, result.samePositionDifferentCell);
    assertTrue(result.hasDuplicates());
  }

  @Test
  void acceptsDistinctCells() {
    AutomapLayer layer = new AutomapLayer(1);
    layer.addFloor(10, 100, 200);
    layer.addWall(11, 105, 205);

    AutomapCellAudit.Result result = AutomapCellAudit.audit(layer);

    assertEquals(2, result.total);
    assertEquals(0, result.withinCategoryExact);
    assertEquals(0, result.crossCategoryExact);
    assertEquals(0, result.samePositionDifferentCell);
  }
}
