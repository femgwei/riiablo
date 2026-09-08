package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeTxtSchemaTest {
  @Test
  void reportsMissingDuplicateAndInvalidTypedFieldsWithStableCoordinates() throws Exception {
    LosslessTxtTable table = LosslessTxtTable.parse(
        "Name\tEnabled\tEnabled\tCount\nfoo\t2\t1\tnan\n"
            .getBytes(StandardCharsets.ISO_8859_1));
    NativeTxtSchema schema = NativeTxtSchema.builder("Fixture.txt")
        .strings("Name", "Missing")
        .booleans("Enabled")
        .integers("Count")
        .build();

    List<NativeTxtSchema.Issue> issues = schema.validate(table);

    assertEquals(4, issues.size());
    assertEquals(NativeTxtSchema.Kind.MISSING_COLUMN, issues.get(0).kind);
    assertEquals(NativeTxtSchema.Kind.DUPLICATE_COLUMN, issues.get(1).kind);
    assertEquals(NativeTxtSchema.Kind.INVALID_BOOLEAN, issues.get(2).kind);
    assertEquals(0, issues.get(2).row);
    assertEquals(1, issues.get(2).column);
    assertEquals(NativeTxtSchema.Kind.INVALID_INTEGER, issues.get(3).kind);
  }

  @Test
  void acceptsWhitespaceOnlyTypedCellsWithoutMutatingRawData() throws Exception {
    LosslessTxtTable table = LosslessTxtTable.parse(
        "Enabled\tCount\n \t  \n".getBytes(StandardCharsets.ISO_8859_1));
    NativeTxtSchema schema = NativeTxtSchema.builder("Fixture.txt")
        .booleans("Enabled")
        .integers("Count")
        .build();

    assertEquals(0, schema.validate(table).size());
    assertEquals(" ", table.get(0, "Enabled"));
    assertEquals("  ", table.get(0, "Count"));
  }
}
