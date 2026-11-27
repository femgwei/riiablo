package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.CorpseFragment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class NativeCorpseFragmentPresentationTest extends RiiabloTest {
  @Test void nativeBloodDisplacementMatchesRetainedDrawSample() {
    Missile missile = new Missile();
    missile.missile = Riiablo.files.Missiles.get("bigblood2");
    Position at = new Position();
    CorpseFragment clock = new CorpseFragment();
    Vector2 velocity = NativeCorpseFragmentPresentation.velocity(
        missile.missile, new Vector2(1, -1), new Vector2());
    NativeCorpseFragmentPresentation.advance(missile, clock, at, velocity, 1f / 25);
    float firstX = 16 * (at.position.x - at.position.y);
    for (int i = 1; i < 8; i++)
      NativeCorpseFragmentPresentation.advance(missile, clock, at, velocity, 1f / 25);
    // Original run 090136: class 21, frame 1 at x=244, frame 8 at x=273.
    // Compare relative displacement, not camera/seed-dependent birth position.
    assertEquals(273 - 244, 16 * (at.position.x - at.position.y) - firstX, 1f);
    assertEquals(0f, at.position.x + at.position.y, .0001f);
    assertTrue(velocity.isZero());
    Vector2 landed = new Vector2(at.position);
    for (int i = 8; i < 80; i++)
      NativeCorpseFragmentPresentation.advance(missile, clock, at, velocity, 1f / 25);
    assertEquals(landed, at.position);
    assertEquals(8, NativeCorpseFragmentPresentation.frame(missile, 9));
  }

  @Test void coldDccStillLandsThenLoadsTerminalFrameAndReclaimsResidue() {
    AssetManager previousAssets = Riiablo.assets;
    com.riiablo.Colors previousColors = Riiablo.colors;
    AssetManager assets = mock(AssetManager.class);
    Riiablo.assets = assets;
    Riiablo.colors = new com.riiablo.Colors();
    boolean[] warm = {false};
    when(assets.isLoaded(any(AssetDescriptor.class))).thenAnswer(call -> warm[0]);
    when(assets.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      AssetDescriptor<?> descriptor = call.getArgument(0);
      ByteBuffer header = ByteBuffer.wrap(Riiablo.mpqs.resolve(descriptor.fileName).readBytes())
          .order(ByteOrder.LITTLE_ENDIAN);
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(header.get(2) & 255);
      when(dcc.getNumFramesPerDir()).thenReturn(header.getInt(3));
      when(dcc.getBox(anyInt())).thenReturn(new com.riiablo.codec.util.BBox());
      return dcc;
    });
    com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new MissileLoader(), new MissileImpactPresentationSystem(), new AnimationStepper(),
            new OverlayManager(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0))
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int gore = fragment(world, "corpseexplosion", Vector2.Zero);
      int blood = fragment(world, "bigblood2", new Vector2(1, -1));
      world.setDelta(1f / 25);
      for (int i = 0; i < 25; i++) world.process();
      assertFalse(world.getMapper(AnimationWrapper.class).has(gore));
      assertEquals(25, world.getMapper(Missile.class).get(gore).nativeFrame);
      Vector2 landed = new Vector2(world.getMapper(Position.class).get(blood).position);
      warm[0] = true;
      world.process();
      assertEquals(12, world.getMapper(AnimationWrapper.class).get(gore).animation.getFrame());
      assertEquals(8, world.getMapper(AnimationWrapper.class).get(blood).animation.getFrame());
      for (int i = 26; i < 127; i++) world.process();
      assertTrue(world.getEntityManager().isActive(gore));
      assertTrue(world.getEntityManager().isActive(blood));
      assertEquals(landed, world.getMapper(Position.class).get(blood).position);
      world.process();
      world.process();
      assertFalse(world.getEntityManager().isActive(gore));
      assertFalse(world.getEntityManager().isActive(blood));
    } finally {
      world.dispose();
      Riiablo.assets = previousAssets;
      Riiablo.colors = previousColors;
    }
  }

  @Test void residueClockIsIndependentOfRenderFrequencyAndZeroDelta() {
    for (int hz : new int[] {25, 60, 120}) {
      Missile missile = new Missile();
      missile.missile = Riiablo.files.Missiles.get("bigblood2");
      CorpseFragment clock = new CorpseFragment();
      Position at = new Position();
      Vector2 velocity = NativeCorpseFragmentPresentation.velocity(missile.missile,
          new Vector2(1, -1), new Vector2());
      NativeCorpseFragmentPresentation.advance(missile, clock, at, velocity, 0);
      assertEquals(0, missile.nativeFrame);
      for (int i = 0; i < hz * 2; i++)
        assertFalse(NativeCorpseFragmentPresentation.advance(missile, clock, at, velocity, 1f / hz));
      assertEquals(50, missile.nativeFrame);
      assertEquals(8, NativeCorpseFragmentPresentation.frame(missile, 9));
      assertEquals(33.9375f, 16 * (at.position.x - at.position.y), .01f);
    }
  }

  private static int fragment(World world, String name, Vector2 facing) {
    int id = world.create();
    Missile missile = world.getMapper(Missile.class).create(id);
    missile.missile = Riiablo.files.Missiles.get(name);
    missile.authoritative = false;
    missile.presentationOnly = true;
    world.getMapper(Position.class).create(id).position.set(100, 100);
    world.getMapper(CorpseFragment.class).create(id);
    NativeCorpseFragmentPresentation.velocity(missile.missile, facing,
        world.getMapper(Velocity.class).create(id).velocity);
    return id;
  }
}
