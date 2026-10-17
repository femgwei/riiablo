package com.riiablo.codec.excel;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lossless 1.10f {@code ItemStatCost.txt} projection.
 *
 * <p>The native compiler uses the row position as the stat id and treats the
 * ID column as data only. Both values are retained here so callers cannot
 * accidentally substitute a later/custom table id. Numeric operation fields
 * are kept separate from the legacy Excel compatibility fixes.</p>
 */
public final class NativeItemStatCost implements Iterable<NativeItemStatCost.Entry> {
  public static final NativeTxtSchema SCHEMA = NativeTxtSchema.builder("ItemStatCost.txt")
      .strings("stat", "maxstat", "itemevent1", "itemevent2", "descstrpos", "descstrneg",
          "descstr2", "dgrpstrpos", "dgrpstrneg", "dgrpstr2", "op base", "op stat1",
          "op stat2", "op stat3")
      .integers("send bits", "send param bits", "divide", "multiply", "add", "valshift",
          "minaccr", "save bits", "save add", "save param bits", "1.09-save bits",
          "1.09-save add", "encode", "csvbits", "csvparam", "itemeventfunc1",
          "itemeventfunc2", "descpriority", "descfunc", "descval", "dgrp", "dgrpfunc",
          "dgrpval", "keepzero", "op", "op param", "stuff")
      .booleans("send other", "signed", "updateanimrate", "fmin", "damagerelated",
          "itemspecific", "direct", "fcallback", "saved", "csvsigned")
      .build();

  private final LosslessTxtTable source;
  private final List<Entry> entries;
  private final Map<String, Entry> byName;
  private final List<NativeTxtSchema.Issue> schemaIssues;

  private NativeItemStatCost(LosslessTxtTable source) {
    this.source = source;
    schemaIssues = SCHEMA.validate(source);
    List<Entry> entries = new ArrayList<>();
    Map<String, Entry> byName = new LinkedHashMap<>();
    for (int row = 0; row < source.rowCount(); row++) {
      String stat = source.get(row, "stat");
      if (stat.isEmpty() || "Expansion".equalsIgnoreCase(stat)) continue;
      Entry entry = new Entry(entries.size(), source.row(row).sourceLine(), source, row);
      entries.add(entry);
      String key = canonical(stat);
      if (!byName.containsKey(key)) byName.put(key, entry);
    }
    this.entries = Collections.unmodifiableList(entries);
    this.byName = Collections.unmodifiableMap(byName);
  }

  public static NativeItemStatCost load(FileHandle handle) {
    if (handle == null) throw new GdxRuntimeException("ItemStatCost.txt was not found");
    try {
      return parse(handle.readBytes());
    } catch (IOException e) {
      throw new GdxRuntimeException("Couldn't read ItemStatCost.txt", e);
    }
  }

  public static NativeItemStatCost parse(byte[] bytes) throws IOException {
    return new NativeItemStatCost(LosslessTxtTable.parse(bytes));
  }

  public LosslessTxtTable source() {
    return source;
  }

  public List<NativeTxtSchema.Issue> schemaIssues() { return schemaIssues; }

  public int size() {
    return entries.size();
  }

  public Entry get(int id) {
    return id >= 0 && id < entries.size() ? entries.get(id) : null;
  }

  public Entry get(String stat) {
    return stat == null ? null : byName.get(canonical(stat));
  }

  @Override
  public Iterator<Entry> iterator() {
    return entries.iterator();
  }

  private static String canonical(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static int integer(LosslessTxtTable table, int row, String column) {
    return table.getNativeInt(row, column);
  }

  public static final class Entry {
    /** Native stat id, assigned from the compiled row position. */
    public final int id;
    /** ID column retained for diagnostics; D2MOO does not use it as the key. */
    public final int txtId;
    public final int sourceLine;
    public final String stat;

    public final boolean sendOther;
    public final boolean signed;
    public final int sendBits;
    public final int sendParamBits;
    public final boolean updateAnimRate;
    public final boolean saved;
    public final boolean csvSigned;
    public final int csvBits;
    public final int csvParam;
    public final boolean callback;
    public final boolean min;
    public final int minAccr;
    public final int encode;
    public final int add;
    public final int multiply;
    public final int divide;
    public final int valShift;
    public final int saveBits109;
    public final int saveAdd109;
    public final int saveBits;
    public final int saveAdd;
    public final int saveParamBits;
    public final int keepZero;
    public final int op;
    public final int opParam;
    public final String opBase;
    public final String[] opStats;
    public final boolean direct;
    public final String maxStat;
    public final boolean itemSpecific;
    public final boolean damageRelated;
    public final String itemEvent1;
    public final int itemEventFunc1;
    public final String itemEvent2;
    public final int itemEventFunc2;
    public final int descPriority;
    public final int descFunc;
    public final int descVal;
    public final String descStrPos;
    public final String descStrNeg;
    public final String descStr2;
    public final int descGroup;
    public final int descGroupFunc;
    public final int descGroupVal;
    public final String descGroupStrPos;
    public final String descGroupStrNeg;
    public final String descGroupStr2;
    public final int stuff;

    private Entry(int id, int sourceLine, LosslessTxtTable table, int row) {
      this.id = id;
      this.txtId = integer(table, row, "id");
      this.sourceLine = sourceLine;
      this.stat = table.get(row, "stat");
      sendOther = table.getNativeBit(row, "send other");
      signed = table.getNativeBit(row, "signed");
      sendBits = integer(table, row, "send bits");
      sendParamBits = integer(table, row, "send param bits");
      updateAnimRate = table.getNativeBit(row, "updateanimrate");
      saved = table.getNativeBit(row, "saved");
      csvSigned = table.getNativeBit(row, "csvsigned");
      csvBits = integer(table, row, "csvbits");
      csvParam = integer(table, row, "csvparam");
      callback = table.getNativeBit(row, "fcallback");
      min = table.getNativeBit(row, "fmin");
      minAccr = integer(table, row, "minaccr");
      encode = integer(table, row, "encode");
      add = integer(table, row, "add");
      multiply = integer(table, row, "multiply");
      divide = integer(table, row, "divide");
      valShift = integer(table, row, "valshift");
      saveBits109 = integer(table, row, "1.09-save bits");
      saveAdd109 = integer(table, row, "1.09-save add");
      saveBits = integer(table, row, "save bits");
      saveAdd = integer(table, row, "save add");
      saveParamBits = integer(table, row, "save param bits");
      keepZero = integer(table, row, "keepzero");
      op = integer(table, row, "op");
      opParam = integer(table, row, "op param");
      opBase = table.get(row, "op base");
      opStats = new String[] {
          table.get(row, "op stat1"), table.get(row, "op stat2"), table.get(row, "op stat3")};
      direct = table.getNativeBit(row, "direct");
      maxStat = table.get(row, "maxstat");
      itemSpecific = table.getNativeBit(row, "itemspecific");
      damageRelated = table.getNativeBit(row, "damagerelated");
      itemEvent1 = table.get(row, "itemevent1");
      itemEventFunc1 = integer(table, row, "itemeventfunc1");
      itemEvent2 = table.get(row, "itemevent2");
      itemEventFunc2 = integer(table, row, "itemeventfunc2");
      descPriority = integer(table, row, "descpriority");
      descFunc = integer(table, row, "descfunc");
      descVal = integer(table, row, "descval");
      descStrPos = table.get(row, "descstrpos");
      descStrNeg = table.get(row, "descstrneg");
      descStr2 = table.get(row, "descstr2");
      descGroup = integer(table, row, "dgrp");
      descGroupFunc = integer(table, row, "dgrpfunc");
      descGroupVal = integer(table, row, "dgrpval");
      descGroupStrPos = table.get(row, "dgrpstrpos");
      descGroupStrNeg = table.get(row, "dgrpstrneg");
      descGroupStr2 = table.get(row, "dgrpstr2");
      stuff = integer(table, row, "stuff");
    }
  }
}
