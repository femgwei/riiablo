package com.riiablo.codec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional, process-local semantic trace sink for client animation work.
 *
 * The renderer and presentation systems do not depend on a file format. A
 * caller (the offscreen capture or a diagnostic build) installs a sink and
 * receives ordered events. With no sink installed this is a zero-cost no-op
 * apart from the volatile check.
 */
public final class AnimationTraceProbe {
  public interface Sink {
    void event(long tick, int gameFrame, int entityId, String kind,
        Map<String, ?> values);
  }

  private static volatile Sink sink;
  private static final ThreadLocal<Context> context = new ThreadLocal<>();

  private AnimationTraceProbe() {}

  public static void install(Sink value) { sink = value; }
  public static void clear() { sink = null; context.remove(); }

  public static void beginFrame(long tick, int gameFrame) {
    Context current = context.get();
    if (current == null) current = new Context();
    current.tick = tick;
    current.gameFrame = gameFrame;
    current.entityId = -1;
    context.set(current);
  }

  public static void beginEntity(int entityId) {
    Context current = context.get();
    if (current == null) {
      current = new Context();
      context.set(current);
    }
    current.entityId = entityId;
  }

  public static void endEntity() {
    Context current = context.get();
    if (current != null) current.entityId = -1;
  }

  public static void record(String kind) { record(kind, Collections.emptyMap()); }

  public static void record(String kind, Map<String, ?> values) {
    Sink target = sink;
    if (target == null) return;
    Context current = context.get();
    if (current == null) return;
    target.event(current.tick, current.gameFrame, current.entityId, kind,
        values == null ? Collections.emptyMap() : values);
  }

  public static Map<String, Object> values(Object... pairs) {
    if ((pairs.length & 1) != 0) throw new IllegalArgumentException("pairs");
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      result.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return result;
  }

  private static final class Context {
    long tick;
    int gameFrame;
    int entityId = -1;
  }
}
