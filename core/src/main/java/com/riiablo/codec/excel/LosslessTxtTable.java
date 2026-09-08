package com.riiablo.codec.excel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lossless reader for Diablo II tab-separated Excel tables.
 *
 * <p>This is deliberately independent from {@link TxtParser}. It preserves
 * empty cells, duplicate/unnamed headers, the {@code Expansion} sentinel and
 * each raw data line. Short rows are padded to the header width, matching the
 * defensive access model used by libd2. Values are not trimmed or repaired;
 * typed accessors trim only the temporary value being converted.</p>
 */
public final class LosslessTxtTable {
  private static final int UTF8_BOM_0 = 0xEF;
  private static final int UTF8_BOM_1 = 0xBB;
  private static final int UTF8_BOM_2 = 0xBF;

  private final List<String> headers;
  private final List<Row> rows;
  private final Map<String, List<Integer>> columns;
  private final int valueColumnCount;

  private LosslessTxtTable(List<String> headers, List<Row> rows) {
    this.headers = Collections.unmodifiableList(headers);
    this.rows = Collections.unmodifiableList(rows);
    Map<String, List<Integer>> indexes = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String key = canonical(headers.get(i));
      List<Integer> matching = indexes.get(key);
      if (matching == null) {
        matching = new ArrayList<>();
        indexes.put(key, matching);
      }
      matching.add(i);
    }
    for (Map.Entry<String, List<Integer>> entry : indexes.entrySet()) {
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }
    columns = Collections.unmodifiableMap(indexes);
    int width = headers.size();
    for (Row row : rows) width = Math.max(width, row.cells.size());
    valueColumnCount = width;
  }

  public static LosslessTxtTable parse(byte[] bytes) throws IOException {
    if (bytes == null) throw new NullPointerException("bytes");
    int offset = hasUtf8Bom(bytes) ? 3 : 0;
    return parse(new ByteArrayInputStream(bytes, offset, bytes.length - offset));
  }

  public static LosslessTxtTable parse(InputStream input) throws IOException {
    if (input == null) throw new NullPointerException("input");
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.ISO_8859_1));
    String headerLine = reader.readLine();
    if (headerLine == null) throw new IOException("Empty Diablo II TXT table");
    if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
      headerLine = headerLine.substring(1);
    }
    List<String> headers = split(headerLine);
    List<Row> rows = new ArrayList<>();
    String line;
    int sourceLine = 1;
    while ((line = reader.readLine()) != null) {
      sourceLine++;
      List<String> cells = split(line);
      while (cells.size() < headers.size()) cells.add("");
      rows.add(new Row(sourceLine, line,
          Collections.unmodifiableList(new ArrayList<>(cells))));
    }
    return new LosslessTxtTable(new ArrayList<>(headers), rows);
  }

  private static boolean hasUtf8Bom(byte[] bytes) {
    return bytes.length >= 3
        && (bytes[0] & 0xFF) == UTF8_BOM_0
        && (bytes[1] & 0xFF) == UTF8_BOM_1
        && (bytes[2] & 0xFF) == UTF8_BOM_2;
  }

  private static List<String> split(String line) {
    List<String> cells = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || line.charAt(i) == '\t') {
        cells.add(line.substring(start, i));
        start = i + 1;
      }
    }
    return cells;
  }

  private static String canonical(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  public List<String> headers() {
    return headers;
  }

  public List<Row> rows() {
    return rows;
  }

  public int columnCount() {
    return headers.size();
  }

  /** Maximum width of the header and every raw row, including surplus cells. */
  public int valueColumnCount() {
    return valueColumnCount;
  }

  public int rowCount() {
    return rows.size();
  }

  public Row row(int index) {
    return rows.get(index);
  }

  /** Returns the first matching column, as Blizzard consumers conventionally do. */
  public int columnIndex(String name) {
    List<Integer> matching = columns.get(canonical(name));
    return matching == null || matching.isEmpty() ? -1 : matching.get(0);
  }

  /** Returns every matching index so duplicate and unnamed headers remain observable. */
  public List<Integer> columnIndexes(String name) {
    List<Integer> matching = columns.get(canonical(name));
    return matching == null ? Collections.<Integer>emptyList() : matching;
  }

  public String get(int row, String column) {
    int index = columnIndex(column);
    return index < 0 ? "" : get(row, index);
  }

  public String get(int row, int column) {
    if (row < 0 || row >= rows.size() || column < 0) return "";
    List<String> cells = rows.get(row).cells;
    return column < cells.size() ? cells.get(column) : "";
  }

  public Integer getInt(int row, String column) {
    String value = get(row, column).trim();
    if (value.isEmpty()) return null;
    try {
      if (value.startsWith("0x") || value.startsWith("0X")) {
        long parsed = Long.parseLong(value.substring(2), 16);
        return (parsed & ~0xFFFFFFFFL) == 0 ? (int) parsed : null;
      }
      return Integer.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * Numeric conversion used by native TXT projection fields. A leading '*'
   * marks a disabled value and compiles to the field default while the raw
   * cell remains observable through {@link #get(int, String)}.
   */
  public int getNativeInt(int row, String column) {
    String value = get(row, column).trim();
    if (value.isEmpty() || value.startsWith("*")) return 0;
    Integer parsed = getInt(row, column);
    return parsed == null ? 0 : parsed;
  }

  /** Blizzard TXT booleans are true only for the numeric value 1. */
  public boolean getBoolean(int row, String column) {
    return "1".equals(get(row, column).trim());
  }

  /** TXTFIELD_BIT uses the native integer conversion and treats nonzero as set. */
  public boolean getNativeBit(int row, String column) {
    return getNativeInt(row, column) != 0;
  }

  public int findFirst(String column, String value) {
    int index = columnIndex(column);
    if (index < 0) return -1;
    for (int row = 0; row < rows.size(); row++) {
      if (get(row, index).equals(value)) return row;
    }
    return -1;
  }

  public static final class Row {
    private final int sourceLine;
    private final String rawLine;
    private final List<String> cells;

    private Row(int sourceLine, String rawLine, List<String> cells) {
      this.sourceLine = sourceLine;
      this.rawLine = rawLine;
      this.cells = cells;
    }

    public int sourceLine() {
      return sourceLine;
    }

    public String rawLine() {
      return rawLine;
    }

    public List<String> cells() {
      return cells;
    }
  }
}
