package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;
import org.junit.jupiter.api.Test;

class AutomapEntityCellsTest {
  @Test void objectAutoMapCellUsesNativeFrame() {
    Objects.Entry object = new Objects.Entry();
    object.AutoMap = 307;
    assertEquals(307, AutomapEntityCells.objectCell(object));
    object.AutoMap = -1;
    assertEquals(-1, AutomapEntityCells.objectCell(object));
  }

  @Test void monsterAutomapCellUsesNativeFrame() {
    MonStats2.Entry monster = new MonStats2.Entry();
    monster.automapCel = 405;
    assertEquals(405, AutomapEntityCells.monsterCell(monster));
    monster.automapCel = -1;
    assertEquals(-1, AutomapEntityCells.monsterCell(monster));
  }

  @Test void nullRowsAreHidden() {
    assertEquals(-1, AutomapEntityCells.objectCell(null));
    assertEquals(-1, AutomapEntityCells.monsterCell(null));
    assertTrue(AutomapEntityCells.hasCell(0));
    assertFalse(AutomapEntityCells.hasCell(-1));
  }
}
