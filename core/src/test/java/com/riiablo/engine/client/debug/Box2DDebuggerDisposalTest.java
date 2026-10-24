package com.riiablo.presentation.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.RenderSystem;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class Box2DDebuggerDisposalTest {
  @Test
  void releasesOnlyOwnedRendererOnce() throws Exception {
    Box2DDebugger system = new Box2DDebugger();
    Box2DDebugRenderer renderer = mock(Box2DDebugRenderer.class);
    system.renderSystem = mock(RenderSystem.class);
    system.physicsSystem = mock(Box2DPhysics.class);
    setRenderer(system, renderer);
    system.dispose();
    system.dispose();
    verify(renderer).dispose();
    verifyNoInteractions(system.renderSystem, system.physicsSystem);
    assertNull(getRenderer(system));
  }

  @Test
  void failedReleaseDoesNotRetryAndUninitializedDisposalIsSafe() throws Exception {
    Box2DDebugger system = new Box2DDebugger();
    system.dispose();
    Box2DDebugRenderer renderer = mock(Box2DDebugRenderer.class);
    setRenderer(system, renderer);
    RuntimeException failure = new RuntimeException("renderer");
    doThrow(failure).when(renderer).dispose();
    assertSame(failure, assertThrows(RuntimeException.class, system::dispose));
    assertDoesNotThrow(system::dispose);
    verify(renderer).dispose();
  }

  @Test
  void worldDisposesDisabledDebuggerUsingProductionCleanup() {
    Box2DDebugRenderer renderer = mock(Box2DDebugRenderer.class);
    Box2DDebugger system = new DebuggerFixture(renderer);
    World world = new World(new WorldConfiguration().setSystem(system));
    assertFalse(system.isEnabled());
    world.dispose();
    verify(renderer).dispose();
  }

  // No rendering is initialized here: exercise World's lifecycle callback, not GL wiring.
  @Wire(injectInherited = false)
  private static class DebuggerFixture extends Box2DDebugger {
    private final Box2DDebugRenderer ownedRenderer;
    DebuggerFixture(Box2DDebugRenderer renderer) { ownedRenderer = renderer; }
    @Override protected void initialize() {
      try { setRenderer(this, ownedRenderer); }
      catch (Exception e) { throw new AssertionError(e); }
      setEnabled(false);
    }
  }

  private static void setRenderer(Box2DDebugger system, Box2DDebugRenderer renderer) throws Exception {
    Field field = Box2DDebugger.class.getDeclaredField("renderer");
    field.setAccessible(true);
    field.set(system, renderer);
  }

  private static Object getRenderer(Box2DDebugger system) throws Exception {
    Field field = Box2DDebugger.class.getDeclaredField("renderer");
    field.setAccessible(true);
    return field.get(system);
  }
}
