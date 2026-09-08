package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeTxtProjectionReportTest {
  @Test
  void keepsExtraDiagnosticColumnsAndCoordinatesIssues() throws Exception {
    LosslessTxtTable source = LosslessTxtTable.parse(
        "Id\thcIdx\tenabled\nfallen\t27\tx\n".getBytes(StandardCharsets.ISO_8859_1));
    NativeTxtSchema schema = NativeTxtSchema.builder("MonStats.txt")
        .strings("Id").booleans("enabled").build();

    NativeTxtProjectionReport.TableReport report =
        NativeTxtProjectionReport.create("MonStats.txt", schema, source);

    assertEquals(1, report.sourceRows);
    assertEquals(3, report.sourceColumns);
    assertEquals(List.of("hcIdx"), report.extraColumns);
    assertFalse(report.isClean());
    assertEquals(NativeTxtSchema.Kind.INVALID_BOOLEAN, report.issues.get(0).kind);
    assertEquals(0, report.issues.get(0).row);
    assertEquals(2, report.issues.get(0).column);
  }

  @Test
  void createAllUsesStableCallerOrder() throws Exception {
    LosslessTxtTable source = LosslessTxtTable.parse(
        "Id\nfallen\n".getBytes(StandardCharsets.ISO_8859_1));
    Map<String, NativeTxtSchema> schemas = new LinkedHashMap<>();
    schemas.put("B.txt", NativeTxtSchema.builder("B.txt").strings("Id").build());
    schemas.put("A.txt", NativeTxtSchema.builder("A.txt").strings("Id").build());
    Map<String, LosslessTxtTable> sources = new LinkedHashMap<>();
    sources.put("B.txt", source);
    sources.put("A.txt", source);

    List<NativeTxtProjectionReport.TableReport> reports =
        NativeTxtProjectionReport.createAll(schemas, sources);
    assertEquals("B.txt", reports.get(0).table);
    assertEquals("A.txt", reports.get(1).table);
    assertTrue(reports.get(0).isClean());
  }
}
