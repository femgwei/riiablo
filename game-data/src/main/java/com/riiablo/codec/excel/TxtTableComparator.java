package com.riiablo.codec.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Produces stable field-level differences between two parsed Diablo II TXT tables. */
public final class TxtTableComparator {
  private TxtTableComparator() {}

  public static List<Difference> compare(
      String table, LosslessTxtTable expected, LosslessTxtTable actual) {
    if (expected == null) throw new NullPointerException("expected");
    if (actual == null) throw new NullPointerException("actual");
    List<Difference> differences = new ArrayList<>();
    int columns = Math.max(expected.valueColumnCount(), actual.valueColumnCount());
    for (int column = 0; column < columns; column++) {
      String expectedName = header(expected, column);
      String actualName = header(actual, column);
      if (!expectedName.equals(actualName)) {
        differences.add(new Difference(table, Kind.HEADER, -1, column,
            expectedName, actualName));
      }
    }
    int rows = Math.max(expected.rowCount(), actual.rowCount());
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        String expectedValue = expected.get(row, column);
        String actualValue = actual.get(row, column);
        if (!expectedValue.equals(actualValue)) {
          differences.add(new Difference(table, Kind.FIELD, row, column,
              expectedValue, actualValue));
        }
      }
    }
    return Collections.unmodifiableList(differences);
  }

  private static String header(LosslessTxtTable table, int column) {
    return column >= 0 && column < table.columnCount() ? table.headers().get(column) : "";
  }

  public enum Kind {
    HEADER,
    FIELD
  }

  public static final class Difference {
    public final String table;
    public final Kind kind;
    /** Zero-based data row, or -1 for a header difference. */
    public final int row;
    public final int column;
    public final String expected;
    public final String actual;

    Difference(String table, Kind kind, int row, int column,
        String expected, String actual) {
      this.table = table == null ? "" : table;
      this.kind = kind;
      this.row = row;
      this.column = column;
      this.expected = expected;
      this.actual = actual;
    }

    @Override
    public String toString() {
      return table + ":" + kind + "[row=" + row + ",column=" + column
          + "] expected='" + expected + "' actual='" + actual + "'";
    }
  }
}
