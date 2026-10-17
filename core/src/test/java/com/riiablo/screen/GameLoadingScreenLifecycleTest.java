package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class GameLoadingScreenLifecycleTest {
  private static final String LOADING = "data/local/ui/loadingscreen.dc6";

  @Test void unusedLoaderDoesNotAcquireOrReleaseAnything() {
    AssetManager owner = mock(AssetManager.class);
    GameLoadingScreen screen = new GameLoadingScreen(mock(Map.class), new Array<>(),
        new ScreenAssetScope(owner));
    screen.hide();
    screen.dispose();
    verifyNoInteractions(owner);
  }

  @Test void repeatedShowsRebuildTransientStageButSubmitSessionDependenciesOnlyOnce() {
    runWithLoader(false);
  }

  @Test void failureDuringMapLoadRetiresTransientResourcesAndAllowsCleanup() {
    runWithLoader(true);
  }

  private void runWithLoader(boolean fail) {
    Application previousApp = Gdx.app;
    AssetManager previousAssets = Riiablo.assets;
    com.riiablo.Colors previousColors = Riiablo.colors;
    com.badlogic.gdx.utils.viewport.Viewport previousViewport = Riiablo.viewport;
    Gdx.app = mock(Application.class);
    Riiablo.colors = new com.riiablo.Colors();
    AssetManager owner = mock(AssetManager.class);
    AssetManager replacement = mock(AssetManager.class);
    when(owner.contains(anyString())).thenReturn(true);
    when(owner.isLoaded(anyString())).thenReturn(true);
    DC6 dc6 = mock(DC6.class);
    when(owner.get(any(AssetDescriptor.class))).thenReturn(dc6);
    Animation animation = mock(Animation.class);
    ScreenAssetScope session = new ScreenAssetScope(owner);
    AssetDescriptor<Sound> dependency = new AssetDescriptor<>("session.wav", Sound.class);
    Array<AssetDescriptor> dependencies = new Array<>();
    dependencies.add(dependency);
    Map map = mock(Map.class);
    RuntimeException failure = new RuntimeException("map load");
    if (fail) doThrow(failure).when(map).load();
    try (MockedConstruction<Stage> stages = mockConstruction(Stage.class);
         MockedStatic<Animation> animations = mockStatic(Animation.class)) {
      animations.when(() -> Animation.newAnimation(dc6)).thenReturn(animation);
      GameLoadingScreen screen = new GameLoadingScreen(map, dependencies, session);
      Riiablo.assets = replacement;
      screen.loadAct(1);
      if (fail) {
        assertSame(failure, assertThrows(RuntimeException.class, screen::show));
      } else {
        screen.show();
        screen.show();
      }
      screen.hide();
      verify(stages.constructed().get(0)).dispose();
      verify(owner).unload(LOADING);
      verify(owner, never()).unload(dependency.fileName);
      if (!fail) {
        screen.loadAct(2);
        screen.show();
        screen.hide();
        verify(stages.constructed().get(1)).dispose();
        verify(owner, times(2)).unload(LOADING);
        verify(map).setAct(1);
        verify(map).setAct(2);

        screen.dispose();
        screen.loadAct(3);
        screen.show(); // a disposed loader must reject a new Act load
        assertEquals(2, stages.constructed().size());
        verify(map, never()).setAct(3);
      } else {
        screen.dispose();
      }
      session.dispose();
      session.dispose();
      verify(owner).load(dependency);
      verify(owner).unload(dependency.fileName);
      verifyNoInteractions(replacement);
    } finally {
      Gdx.app = previousApp;
      Riiablo.assets = previousAssets;
      Riiablo.colors = previousColors;
      Riiablo.viewport = previousViewport;
    }
  }
}
