package com.riiablo.engine.server.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnimationTraceWriterTest {
  @Test
  void writesCanonicalHeaderAndFrameJsonl() throws Exception {
    Path path = Files.createTempFile("animation-trace", ".jsonl");
    try {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("skill", 151);
      metadata.put("capabilities", Arrays.asList("mode", "animation_frame", "keyframes"));
      try (AnimationTraceWriter writer = new AnimationTraceWriter(path, metadata)) {
        writer.frame(0, 100, 18, "SQ", "BA", "1HS", 0, 16, 256,
            Arrays.asList("ATK"), 100, 200, Arrays.asList("DAMAGE"), null);
      }
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      String[] lines = content.trim().split("\\R");
      assertEquals(2, lines.length);
      assertTrue(lines[0].contains("\"type\":\"header\""));
      assertTrue(lines[0].contains("\"schema\":1"));
      assertTrue(lines[1].contains("\"animation_frame_raw\":0"));
      assertTrue(lines[1].contains("\"keyframes\":[\"ATK\"]"));
    } finally {
      Files.deleteIfExists(path);
    }
  }
}
