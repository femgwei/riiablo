package com.d2moo.testutil;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectDifferencesTest {
  static final class Node {
    int value;
    Node next, alternate;
    Node(int value) { this.value = value; }
  }
  private static void node(ObjectDifferences.Visitor v, Node a, Node b) {
    v.field("value", a.value, b.value);
    v.object("next", a.next, b.next, ObjectDifferencesTest::node);
    v.object("alternate", a.alternate, b.alternate, ObjectDifferencesTest::node);
  }

  @Test void cyclesTerminateAndSharedActualDoesNotHideDifferentExpected() {
    Node a = new Node(1), b = new Node(1), c = new Node(2);
    a.next = a; a.alternate = a;
    b.next = b; b.alternate = c;
    c.next = b; c.alternate = c;
    List<ObjectDifferences.Difference> diff = ObjectDifferences.compare(a, b, ObjectDifferencesTest::node);
    assertEquals(1, diff.size());
    assertEquals("alternate->value", diff.get(0).path);
    assertEquals("1", diff.get(0).actual);
    assertEquals("2", diff.get(0).expected);
    assertThrows(UnsupportedOperationException.class, diff::clear);
    assertTrue(ObjectDifferences.compare(a, a, ObjectDifferencesTest::node).isEmpty());
  }

  @Test void objectArraysReportNullsLengthsAndNestedPaths() {
    Node[] a = {new Node(1), null}, b = {new Node(2), new Node(3), new Node(4)};
    List<ObjectDifferences.Difference> diff = ObjectDifferences.compare(a, b,
        (v, x, y) -> v.objects("units", x, y, ObjectDifferencesTest::node));
    assertEquals(3, diff.size());
    assertEquals("units.length", diff.get(0).path);
    assertEquals("units[0]->value", diff.get(1).path);
    assertEquals("units[1]", diff.get(2).path);
    assertEquals(ObjectDifferences.Type.POINTER, diff.get(2).type);
  }

  @Test void primitiveArraysUseExactNumericComparisonAndSafeDimensions() {
    double[][] a = {{0, 1, Double.NaN}, {2}}, b = {{-0.0, 1.00001, Double.NaN}, {2, 3}};
    List<ObjectDifferences.Difference> diff = ObjectDifferences.compare(a, b,
        (v, x, y) -> v.array("matrix", x, y));
    assertEquals(3, diff.size());
    assertEquals("matrix[0][1]", diff.get(0).path);
    assertEquals("matrix[0][2]", diff.get(1).path);
    assertEquals("matrix[1].length", diff.get(2).path);
    assertThrows(IllegalArgumentException.class, () -> ObjectDifferences.compare(a, b,
        (v, x, y) -> v.field("bad", x, y)));
  }

  @Test void preservesDistinctUpstreamFloatingRulesWithoutChangingExactFields() {
    assertFalse(ObjectDifferences.floatingEqual(0, 0.0001));
    assertTrue(ObjectDifferences.floatingEqual(0, 0.000099));
    assertFalse(ObjectDifferences.floatingEqual(Double.NaN, Double.NaN));
    assertFalse(ObjectDifferences.floatingEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
    List<ObjectDifferences.Difference> diff = ObjectDifferences.compare(0.0, 0.0001, (v, a, b) -> {
      v.floatingField("tolerant", a, b);
      v.field("exact", a, b);
      v.floatingField("nan", Double.NaN, 1);
      v.floatingField("outside", 0, 0.000101);
    });
    assertEquals(2, diff.size());
    assertEquals("exact", diff.get(0).path);
    assertEquals("outside", diff.get(1).path);
  }

  @Test void cStringsStopAtNulAndRejectUnterminatedBuffers() {
    assertTrue(ObjectDifferences.cStringEqual(new byte[] {65, 0, 7}, new byte[] {65, 0, 8}));
    assertFalse(ObjectDifferences.cStringEqual(new byte[] {65, 0}, new byte[] {66, 0}));
    assertFalse(ObjectDifferences.cStringEqual(new byte[] {65, 0}, new byte[] {65, 66, 0}));
    assertThrows(IllegalArgumentException.class,
        () -> ObjectDifferences.cStringEqual(new byte[] {65}, new byte[] {65, 0}));
  }
}
