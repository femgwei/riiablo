package com.riiablo.engine.server.animation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSONL writer for cross-runtime animation traces.
 *
 * The first line is a {@code header}; subsequent lines are {@code frame}
 * records.  Keeping this writer in server-domain makes it usable by headless
 * tests and by the dedicated server without pulling a JSON framework into the
 * authoritative simulation.
 */
public final class AnimationTraceWriter implements AutoCloseable {
  public static final int SCHEMA = 1;

  private final BufferedWriter output;
  private boolean closed;

  public AnimationTraceWriter(Path path, Map<String, ?> metadata) throws IOException {
    if (path.getParent() != null) Files.createDirectories(path.getParent());
    output = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("type", "header");
    header.put("schema", SCHEMA);
    header.put("metadata", metadata == null ? new LinkedHashMap<String, Object>() : metadata);
    write(header);
  }

  public synchronized void frame(long tick, Map<String, ?> values) throws IOException {
    if (closed) throw new IOException("animation trace is closed");
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("type", "frame");
    record.put("tick", tick);
    if (values != null) record.putAll(values);
    write(record);
  }

  /** Writes one semantic client-animation event between frame records. */
  public synchronized void event(long tick, int gameFrame, int entityId,
      String kind, Map<String, ?> values) throws IOException {
    if (closed) throw new IOException("animation trace is closed");
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("type", "event");
    record.put("tick", tick);
    record.put("game_frame", gameFrame);
    record.put("entity_id", entityId);
    record.put("kind", kind);
    if (values != null) record.putAll(values);
    write(record);
  }

  /** Writes the standard server/client fields used by animation_compare.py. */
  public synchronized void frame(long tick, int gameFrame, int modeId, String mode,
      String token, String wclass, int animationFrameRaw, int frameCount, int speed,
      Collection<String> keyframes, Integer xFp16, Integer yFp16,
      Collection<String> combatEvents, String visualSha256) throws IOException {
    frame(tick, gameFrame, -1, modeId, mode, token, wclass, animationFrameRaw,
        frameCount, speed, keyframes, xFp16, yFp16, combatEvents, visualSha256);
  }

  /** Standard frame with an optional authoritative entity identity. */
  public synchronized void frame(long tick, int gameFrame, int entityId, int modeId, String mode,
      String token, String wclass, int animationFrameRaw, int frameCount, int speed,
      Collection<String> keyframes, Integer xFp16, Integer yFp16,
      Collection<String> combatEvents, String visualSha256) throws IOException {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("game_frame", gameFrame);
    if (entityId >= 0) values.put("entity_id", entityId);
    values.put("mode_id", modeId);
    values.put("mode", mode);
    values.put("token", token);
    values.put("wclass", wclass);
    values.put("animation_frame_raw", animationFrameRaw < 0 ? null : animationFrameRaw);
    values.put("animation_frame_count", frameCount < 0 ? null : frameCount);
    values.put("animation_speed", speed < 0 ? null : speed);
    values.put("keyframes", keyframes == null ? null : new ArrayList<>(keyframes));
    values.put("x_fp16", xFp16);
    values.put("y_fp16", yFp16);
    values.put("combat_events", combatEvents == null ? new ArrayList<String>() : new ArrayList<>(combatEvents));
    values.put("visual_sha256", visualSha256);
    frame(tick, values);
  }

  private void write(Map<String, ?> record) throws IOException {
    output.write(json(record));
    output.newLine();
    output.flush();
  }

  @SuppressWarnings("unchecked")
  private static String json(Object value) {
    if (value == null) return "null";
    if (value instanceof String || value instanceof Character) {
      return "\"" + escape(String.valueOf(value)) + "\"";
    }
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    if (value instanceof Map) {
      StringBuilder builder = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
        if (!first) builder.append(',');
        first = false;
        builder.append(json(String.valueOf(entry.getKey()))).append(':').append(json(entry.getValue()));
      }
      return builder.append('}').toString();
    }
    if (value instanceof Iterable) {
      StringBuilder builder = new StringBuilder("[");
      boolean first = true;
      for (Object item : (Iterable<?>) value) {
        if (!first) builder.append(',');
        first = false;
        builder.append(json(item));
      }
      return builder.append(']').toString();
    }
    return json(String.valueOf(value));
  }

  private static String escape(String value) {
    StringBuilder builder = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\': builder.append("\\\\"); break;
        case '"': builder.append("\\\""); break;
        case '\n': builder.append("\\n"); break;
        case '\r': builder.append("\\r"); break;
        case '\t': builder.append("\\t"); break;
        default:
          if (c < 0x20) builder.append(String.format("\\u%04x", (int) c));
          else builder.append(c);
      }
    }
    return builder.toString();
  }

  @Override public synchronized void close() throws IOException {
    if (!closed) {
      closed = true;
      output.close();
    }
  }
}
