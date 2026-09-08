package com.riiablo.codec.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stable, human-readable summary of a native D2MOO TXT projection.
 *
 * <p>The report deliberately keeps the raw table separate from the typed
 * projection. Extra columns such as {@code hcIdx} are therefore reported as
 * diagnostics instead of being silently discarded, while every schema issue
 * retains the source row and column coordinates.</p>
 */
public final class NativeTxtProjectionReport {
  private NativeTxtProjectionReport() {}

  public static TableReport create(String table, NativeTxtSchema schema,
      LosslessTxtTable source) {
    if (schema == null) throw new NullPointerException("schema");
    if (source == null) throw new NullPointerException("source");
    Set<String> projected = new LinkedHashSet<>();
    for (NativeTxtSchema.Column column : schema.columns().values()) {
      projected.add(column.name.toLowerCase(Locale.ROOT));
    }
    List<String> extras = new ArrayList<>();
    for (String header : source.headers()) {
      String key = header.toLowerCase(Locale.ROOT);
      if (!projected.contains(key) && !extras.contains(header)) extras.add(header);
    }
    return new TableReport(table, source.rowCount(), source.columnCount(),
        schema.columns().size(), extras, schema.validate(source));
  }

  public static final class TableReport {
    public final String table;
    public final int sourceRows;
    public final int sourceColumns;
    public final int schemaColumns;
    public final List<String> extraColumns;
    public final List<NativeTxtSchema.Issue> issues;

    private TableReport(String table, int sourceRows, int sourceColumns, int schemaColumns,
        List<String> extraColumns, List<NativeTxtSchema.Issue> issues) {
      this.table = table == null ? "" : table;
      this.sourceRows = sourceRows;
      this.sourceColumns = sourceColumns;
      this.schemaColumns = schemaColumns;
      this.extraColumns = Collections.unmodifiableList(new ArrayList<>(extraColumns));
      this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean isClean() { return issues.isEmpty(); }

    @Override
    public String toString() {
      StringBuilder out = new StringBuilder(table)
          .append(" rows=").append(sourceRows)
          .append(" sourceColumns=").append(sourceColumns)
          .append(" schemaColumns=").append(schemaColumns)
          .append(" extras=").append(extraColumns)
          .append(" issues=").append(issues.size());
      for (NativeTxtSchema.Issue issue : issues) out.append('\n').append("  ").append(issue);
      return out.toString();
    }
  }

  /** Builds reports in caller-provided map order for deterministic five-table output. */
  public static List<TableReport> createAll(
      Map<String, NativeTxtSchema> schemas, Map<String, LosslessTxtTable> sources) {
    if (schemas == null) throw new NullPointerException("schemas");
    if (sources == null) throw new NullPointerException("sources");
    List<TableReport> reports = new ArrayList<>();
    for (Map.Entry<String, NativeTxtSchema> entry : schemas.entrySet()) {
      LosslessTxtTable source = sources.get(entry.getKey());
      if (source != null) reports.add(create(entry.getKey(), entry.getValue(), source));
    }
    return Collections.unmodifiableList(reports);
  }
}
