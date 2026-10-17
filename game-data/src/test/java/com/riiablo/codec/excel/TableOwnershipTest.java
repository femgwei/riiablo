package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.attributes.SyntheticStatIds;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableOwnershipTest {
  @Test void rowEquivalencesStayWithTheirTableWithoutTheGameHost() {
    Table first = new Table();
    first.add("armor");
    ItemTypes.Entry cap = first.add("cap", "armor");
    Table second = new Table();
    second.add("weapon");
    ItemTypes.Entry otherCap = second.add("cap", "weapon");
    assertTrue(cap.is("armor"));
    assertFalse(cap.is("weapon"));
    assertTrue(otherCap.is("weapon"));
    assertFalse(otherCap.is("armor"));
    assertThrows(ClassNotFoundException.class, () -> Class.forName("com.riiablo.Riiablo"));
  }

  @Test void syntheticStatRowsInitializeWithoutTheAttributeRuntime() {
    ItemStatCost table = new ItemStatCost();
    table.init();
    assertEquals("reqstr", table.get(SyntheticStatIds.REQUIRED_STRENGTH).Stat);
    assertEquals("reqdex", table.get(SyntheticStatIds.REQUIRED_DEXTERITY).Stat);
    assertThrows(ClassNotFoundException.class, () -> Class.forName("com.riiablo.attributes.Stat"));
  }

  private static class Table extends ItemTypes {
    private final Map<String, Entry> rows = new HashMap<>();
    Entry add(String code, String... equivalents) {
      Entry row = new Entry();
      row.Code = code;
      row.Equiv = equivalents;
      put(rows.size(), row);
      rows.put(code, row);
      return row;
    }
    @Override public Entry get(String code) { return rows.get(code); }
  }
}
