package com.riiablo.profiler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProfilerSystemDisposalTest {
  @Test
  void disposesOwnedResourcesInOrderAndOnlyOnce() {
    ProfilerSystem system = fixture();
    Stage stage = system.stage;
    ShapeRenderer renderer = system.renderer;
    Skin skin = system.skin;
    system.dispose();
    system.dispose();
    InOrder order = inOrder(stage, renderer, skin);
    order.verify(stage).dispose();
    order.verify(renderer).dispose();
    order.verify(skin).dispose();
    verifyNoMoreInteractions(stage, renderer, skin);
    assertReleased(system);
  }

  @Test
  void failureStillReleasesOtherResourcesAndPreservesAllFailures() {
    ProfilerSystem system = fixture();
    Stage stage = system.stage;
    ShapeRenderer renderer = system.renderer;
    Skin skin = system.skin;
    RuntimeException first = new RuntimeException("stage");
    Error second = new AssertionError("renderer");
    doThrow(first).when(stage).dispose();
    doThrow(second).when(renderer).dispose();
    assertSame(first, assertThrows(RuntimeException.class, system::dispose));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    verify(skin).dispose();
    assertReleased(system);
    assertDoesNotThrow(system::dispose);
    verify(stage).dispose();
    verify(renderer).dispose();
  }

  @Test
  void uninitializedOrPartiallyInitializedResourcesCanBeDisposed() {
    ProfilerSystem system = new ProfilerSystem();
    system.dispose();
    ShapeRenderer renderer = mock(ShapeRenderer.class);
    system.renderer = renderer;
    system.dispose();
    verify(renderer).dispose();
    assertReleased(system);
  }

  @Test
  void worldDisposesDisabledProfilerUsingProductionCleanup() {
    Stage ownedStage = mock(Stage.class);
    ShapeRenderer ownedRenderer = mock(ShapeRenderer.class);
    Skin ownedSkin = mock(Skin.class);
    ProfilerSystem system = new ProfilerSystem() {
      @Override protected void initialize() {
        stage = ownedStage;
        renderer = ownedRenderer;
        skin = ownedSkin;
        setEnabled(false);
      }
    };
    World world = new World(new WorldConfiguration()
        .setSystem(mock(ProfilerManager.class)).setSystem(system));
    assertFalse(system.isEnabled());
    world.dispose();
    verify(ownedStage).dispose();
    verify(ownedRenderer).dispose();
    verify(ownedSkin).dispose();
    assertReleased(system);
  }

  private static ProfilerSystem fixture() {
    ProfilerSystem system = new ProfilerSystem();
    system.stage = mock(Stage.class);
    system.renderer = mock(ShapeRenderer.class);
    system.skin = mock(Skin.class);
    return system;
  }

  private static void assertReleased(ProfilerSystem system) {
    assertNull(system.stage);
    assertNull(system.renderer);
    assertNull(system.skin);
    assertNull(system.camera);
    assertNull(system.gui);
  }
}
