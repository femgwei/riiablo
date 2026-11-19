package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.animation.AnimationTraceWriter;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnimationTraceSystemTest {
  @Test
  void writesCanonicalPlayerFrameFromAuthoritativeComponents() throws Exception {
    Path tracePath = Files.createTempFile("riiablo-animation-trace", ".jsonl");
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("skill", 151);
    metadata.put("capabilities", Arrays.asList("mode", "animation_frame", "keyframes"));

    AnimationTraceWriter writer = new AnimationTraceWriter(tracePath, metadata);
    AnimationTraceSystem trace = new AnimationTraceSystem(writer).gameFrame(100);
    World world = new World(new WorldConfigurationBuilder().with(trace).build());
    try {
      com.artemis.Entity entity = world.createEntity();
      Class type = entity.edit().create(Class.class);
      type.type = Class.Type.PLR;
      CofReference cof = entity.edit().create(CofReference.class)
          .set("BA", Engine.Player.MODE_S1);
      cof.wclass = Engine.WEAPON_1HS;
      AnimData anim = entity.edit().create(AnimData.class);
      anim.frame = 2 << 8;
      anim.numFrames = 16 << 8;
      anim.speed = 256;
      anim.keyframes = new byte[16];
      anim.keyframes[2] = Engine.KEYFRAME_ATK;
      Position position = entity.edit().create(Position.class);
      position.position.set(10.5f, 20.25f);

      world.process();
    } finally {
      trace.close();
      world.dispose();
    }

    List<String> lines = Files.readAllLines(tracePath);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).contains("\"type\":\"header\""));
    assertTrue(lines.get(1).contains("\"tick\":0"));
    assertTrue(lines.get(1).contains("\"game_frame\":100"));
    assertTrue(lines.get(1).contains("\"mode\":\"S1\""));
    assertTrue(lines.get(1).contains("\"token\":\"BA\""));
    assertTrue(lines.get(1).contains("\"wclass\":\"1HS\""));
    assertTrue(lines.get(1).contains("\"animation_frame_raw\":512"));
    assertTrue(lines.get(1).contains("\"animation_frame_count\":16"));
    assertTrue(lines.get(1).contains("\"keyframes\":[\"ATK\"]"));
  }
}
