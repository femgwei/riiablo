package com.d2moo.testutil;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Test-only Java adaptation of D2MOO VisitorFramework at 14110b3.
 * Schemas explicitly select fields (and union branches); no reflection over object fields,
 * native addresses, or automatic tolerance in game assertions. */
public final class ObjectDifferences {
  public enum Type { FIELD, POINTER }

  public static final class Difference {
    public final Type type;
    public final String path, actual, expected;

    private Difference(Type type, String path, Object actual, Object expected) {
      this.type = type;
      this.path = path;
      this.actual = String.valueOf(actual);
      this.expected = String.valueOf(expected);
    }

    @Override public String toString() {
      return type + " " + path + ": " + actual + " != " + expected;
    }
  }

  @FunctionalInterface public interface Schema<T> {
    void visit(Visitor visitor, T actual, T expected);
  }

  private final List<Difference> differences = new ArrayList<>();
  private final IdentityHashMap<Object, Set<Object>> visited = new IdentityHashMap<>();

  private ObjectDifferences() {}

  public static <T> List<Difference> compare(T actual, T expected, Schema<T> schema) {
    ObjectDifferences context = new ObjectDifferences();
    context.new Visitor("").object("", actual, expected, schema);
    return Collections.unmodifiableList(new ArrayList<>(context.differences));
  }

  /** Upstream TestComparator<float/double>: strict threshold, including NaN/infinity behavior. */
  public static boolean floatingEqual(double actual, double expected) {
    return Math.abs(actual - expected) < 0.0001;
  }

  /** Bounded strcmp for Java fixture buffers. Unterminated buffers fail explicitly. */
  public static boolean cStringEqual(byte[] actual, byte[] expected) {
    int a = terminator(actual), b = terminator(expected);
    if (a != b) return false;
    for (int i = 0; i < a; i++) if (actual[i] != expected[i]) return false;
    return true;
  }

  private static int terminator(byte[] bytes) {
    for (int i = 0; i < bytes.length; i++) if (bytes[i] == 0) return i;
    throw new IllegalArgumentException("C string requires a NUL terminator");
  }

  public final class Visitor {
    private final String parent;
    private Visitor(String parent) { this.parent = parent; }
    private String path(String name) {
      return parent.isEmpty() ? name : name.isEmpty() ? parent : parent + "->" + name;
    }
    private void difference(Type type, String path, Object actual, Object expected) {
      differences.add(new Difference(type, path, actual, expected));
    }

    /** Exact scalar comparison. Floating fields have an explicit upstream-compatible method. */
    public void field(String name, Object actual, Object expected) {
      if ((actual != null && actual.getClass().isArray())
          || (expected != null && expected.getClass().isArray())) {
        throw new IllegalArgumentException("use array() for array contents");
      }
      if (!Objects.equals(actual, expected)) difference(Type.FIELD, path(name), actual, expected);
    }

    /** DiffVisitor scalar rule deliberately differs from floatingEqual at the boundary/NaN. */
    public void floatingField(String name, double actual, double expected) {
      if (Math.abs(actual - expected) > 0.0001) difference(Type.FIELD, path(name), actual, expected);
    }

    public <T> void object(String name, T actual, T expected, Schema<T> schema) {
      String path = path(name);
      if (actual == null || expected == null) {
        if (actual != expected) difference(Type.POINTER, path,
            actual == null ? "null" : "present", expected == null ? "null" : "present");
        return;
      }
      // Pair identity is a Java safety improvement over upstream's one-sided address set.
      Set<Object> peers = visited.computeIfAbsent(actual,
          ignored -> Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
      if (peers.add(expected)) schema.visit(new Visitor(path), actual, expected);
    }

    /** Primitive (including multidimensional) arrays; floating elements use numeric equality. */
    public void array(String name, Object actual, Object expected) {
      primitiveArray(path(name), actual, expected);
    }

    private void primitiveArray(String path, Object actual, Object expected) {
      if (actual == null || expected == null) {
        if (actual != expected) difference(Type.POINTER, path,
            actual == null ? "null" : "present", expected == null ? "null" : "present");
        return;
      }
      Class<?> type = actual.getClass();
      if (!type.isArray() || type != expected.getClass()) {
        throw new IllegalArgumentException("matching primitive array types required");
      }
      Class<?> component = type.getComponentType();
      if (!component.isPrimitive() && !component.isArray()) {
        throw new IllegalArgumentException("use objects() with an explicit element schema");
      }
      int a = Array.getLength(actual), b = Array.getLength(expected);
      if (a != b) difference(Type.FIELD, path + ".length", a, b);
      for (int i = 0; i < Math.min(a, b); i++) {
        Object av = Array.get(actual, i), bv = Array.get(expected, i);
        String element = path + "[" + i + "]";
        if (component.isArray()) primitiveArray(element, av, bv);
        else {
          boolean equal = component == float.class || component == double.class
              ? ((Number) av).doubleValue() == ((Number) bv).doubleValue() : av.equals(bv);
          if (!equal) difference(Type.FIELD, element, av, bv);
        }
      }
    }

    /** Java array length replaces native pointer/count; mismatched counts are reported safely. */
    public <T> void objects(String name, T[] actual, T[] expected, Schema<T> schema) {
      if (actual == null || expected == null) {
        object(name, actual, expected, (v, a, b) -> {});
        return;
      }
      if (actual.length != expected.length) field(name + ".length", actual.length, expected.length);
      for (int i = 0; i < Math.min(actual.length, expected.length); i++) {
        object(name + "[" + i + "]", actual[i], expected[i], schema);
      }
    }
  }
}
