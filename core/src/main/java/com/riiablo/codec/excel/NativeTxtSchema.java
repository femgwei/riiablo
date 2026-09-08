package com.riiablo.codec.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Declarative D2MOO TXT projection schema with stable field-level diagnostics. */
public final class NativeTxtSchema {
  private final String table;
  private final Map<String, Column> columns;

  private NativeTxtSchema(String table, Map<String, Column> columns) {
    this.table = table;
    this.columns = Collections.unmodifiableMap(columns);
  }

  public static Builder builder(String table) {
    return new Builder(table);
  }

  public List<Issue> validate(LosslessTxtTable source) {
    List<Issue> issues = new ArrayList<>();
    for (Column column : columns.values()) {
      List<Integer> indexes = source.columnIndexes(column.name);
      if (indexes.isEmpty()) {
        issues.add(new Issue(table, Kind.MISSING_COLUMN, -1, -1, column.name, ""));
        continue;
      }
      if (indexes.size() > 1) {
        issues.add(new Issue(table, Kind.DUPLICATE_COLUMN, -1, indexes.get(1),
            column.name, Integer.toString(indexes.size())));
      }
      int index = indexes.get(0);
      for (int row = 0; row < source.rowCount(); row++) {
        String value = source.get(row, index);
        // Keep the raw whitespace in LosslessTxtTable, but match Blizzard's
        // numeric conversion: whitespace-only typed cells are default zero.
        if (value.trim().isEmpty()) continue;
        if (column.type == Type.INTEGER && source.getInt(row, column.name) == null) {
          issues.add(new Issue(table, Kind.INVALID_INTEGER, row, index, column.name, value));
        } else if (column.type == Type.BOOLEAN
            && !("0".equals(value.trim()) || "1".equals(value.trim()))) {
          issues.add(new Issue(table, Kind.INVALID_BOOLEAN, row, index, column.name, value));
        }
      }
    }
    return Collections.unmodifiableList(issues);
  }

  public Map<String, Column> columns() {
    return columns;
  }

  public enum Type { STRING, INTEGER, BOOLEAN }

  public enum Kind { MISSING_COLUMN, DUPLICATE_COLUMN, INVALID_INTEGER, INVALID_BOOLEAN }

  public static final class Column {
    public final String name;
    public final Type type;

    private Column(String name, Type type) {
      this.name = name;
      this.type = type;
    }
  }

  public static final class Issue {
    public final String table;
    public final Kind kind;
    public final int row;
    public final int column;
    public final String field;
    public final String actual;

    private Issue(String table, Kind kind, int row, int column, String field, String actual) {
      this.table = table;
      this.kind = kind;
      this.row = row;
      this.column = column;
      this.field = field;
      this.actual = actual;
    }

    @Override
    public String toString() {
      return table + ":" + kind + "[row=" + row + ",column=" + column
          + ",field='" + field + "'] actual='" + actual + "'";
    }
  }

  public static final class Builder {
    private final String table;
    private final Map<String, Column> columns = new LinkedHashMap<>();

    private Builder(String table) {
      this.table = table;
    }

    public Builder strings(String... names) {
      return add(Type.STRING, names);
    }

    public Builder integers(String... names) {
      return add(Type.INTEGER, names);
    }

    public Builder booleans(String... names) {
      return add(Type.BOOLEAN, names);
    }

    private Builder add(Type type, String... names) {
      for (String name : names) {
        String key = name.toLowerCase(Locale.ROOT);
        if (columns.containsKey(key)) {
          throw new IllegalArgumentException("Duplicate schema field " + name + " in " + table);
        }
        columns.put(key, new Column(name, type));
      }
      return this;
    }

    public NativeTxtSchema build() {
      return new NativeTxtSchema(table, new LinkedHashMap<>(columns));
    }
  }
}
