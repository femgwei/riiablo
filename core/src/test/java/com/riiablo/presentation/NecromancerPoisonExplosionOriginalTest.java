package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.missile.NativePoisonMissileMotion;
import com.riiablo.presentation.component.AnimationWrapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class NecromancerPoisonExplosionOriginalTest extends RiiabloTest {
  @Test void retainedOriginalFramesMatchProductionIntroLoopAndExit() {
    com.riiablo.Colors previous = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      JsonValue fixture = fixture();
      int id = world.create();
      Missile cloud = world.getMapper(Missile.class).create(id);
      cloud.missile = Riiablo.files.Missiles.get("poisonexplosioncloud");
      cloud.nativeLifetimeFrames = 60;
      ByteBuffer header = ByteBuffer.wrap(Riiablo.mpqs.resolve(
          "data/global/missiles/" + cloud.missile.CelFile + ".dcc").readBytes())
          .order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(8, header.get(2) & 255);
      assertEquals(31, header.getInt(3));
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(8);
      when(dcc.getNumFramesPerDir()).thenReturn(31);
      when(dcc.getBox(anyInt())).thenReturn(new com.riiablo.codec.util.BBox());
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.edit().layer(dcc).build();
      animation.setMode(Animation.Mode.LOOP);
      animation.setSubLoop(cloud.missile.SubStart, cloud.missile.SubStop);
      animation.setFrameDelta(MissileLoader.nativeFrameDelta(null, cloud));
      world.setDelta(1f / 25f);
      for (JsonValue sample : fixture.get("samples")) {
        cloud.nativeFrame = sample.getInt("inferred_age");
        MissileLoader.initializePlayback(cloud, cloud.missile, animation, 0);
        world.process();
        world.process(); // A render pass must not increment an authority-owned age.
        assertEquals(sample.getInt("frame"), animation.getFrame(),
            "raw line " + sample.getInt("raw_line"));
      }
      assertEquals(23, animation.getFrame(), "retail exit is no longer trapped in SubLoop");
    } finally {
      world.dispose();
      Riiablo.colors = previous;
    }
  }

  @Test void nativeDriftReplaysRetainedRelativeScreenPositions() {
    JsonValue fixture = fixture();
    Position at = new Position();
    Vector2 velocity = NativePoisonMissileMotion.velocity(Vector2.Zero,
        new Vector2(2, -2), (2 << 7) * 75 / 100, new Vector2());
    int previous = 0;
    float initialX = 0, initialY = 0;
    JsonValue first = fixture.get("samples").child;
    for (JsonValue sample : fixture.get("samples")) {
      int age = sample.getInt("inferred_age");
      NativePoisonMissileMotion.advance(at, velocity, age - previous);
      previous = age;
      float x = 16 * (at.position.x - at.position.y);
      float y = 8 * (at.position.x + at.position.y);
      if (sample == first) { initialX = x; initialY = y; }
      assertEquals(sample.getInt("screen_x") - first.getInt("screen_x"), x - initialX, 1f,
          "relative x at raw line " + sample.getInt("raw_line"));
      assertEquals(sample.getInt("screen_y") - first.getInt("screen_y"), y - initialY, 1f);
    }
  }

  private static JsonValue fixture() {
    return new JsonReader().parse(NecromancerPoisonExplosionOriginalTest.class.getResourceAsStream(
        "/original/necromancer-poison-explosion-draw-1.10f.json"));
  }
}
