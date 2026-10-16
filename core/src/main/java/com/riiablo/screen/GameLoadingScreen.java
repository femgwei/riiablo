package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.map.Map;
import com.riiablo.widget.AnimationWrapper;
import com.riiablo.util.Disposal;

public class GameLoadingScreen extends ScreenAdapter {
  private static final String TAG = "GameLoadingScreen";

  private final AssetDescriptor<DC6> loadingscreenDescriptor = new AssetDescriptor<>("data\\local\\ui\\loadingscreen.dc6", DC6.class);

  private final Array<AssetDescriptor> dependencies;
  private final ScreenAssetScope sessionAssets;
  private ScreenAssetScope visibleAssets;
  private int submittedDependencies;

  private Stage stage;
  private AnimationWrapper loadingscreenWrapper;

  private Map map;
  private int act;

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets) {
    this.map = map;
    this.dependencies = new Array<>(dependencies);
    this.sessionAssets = sessionAssets;
  }

  private void createStage() {
    stage = new Stage(Riiablo.defaultViewport, Riiablo.batch);

    visibleAssets.load(loadingscreenDescriptor);
    sessionAssets.assets.finishLoadingAsset(loadingscreenDescriptor);
    final Animation loadingscreen = Animation.newAnimation(sessionAssets.assets.get(loadingscreenDescriptor));
    loadingscreen.setFrameDuration(Float.MAX_VALUE);
    loadingscreenWrapper = new AnimationWrapper(loadingscreen) {
      @Override
      public void act(float delta) {
        super.act(delta);
        for (Animation animation : animations) {
          animation.setFrame((int) (sessionAssets.assets.getProgress() * (animation.getNumFramesPerDir() - 1)));
        }
      }
    };
    loadingscreenWrapper.setPosition(
        (stage.getWidth()  / 2) - (loadingscreen.getMinWidth()  / 2),
        (stage.getHeight() / 2) - (loadingscreen.getMinHeight() / 2));
    stage.addActor(loadingscreenWrapper);
  }

  public void loadAct(int act) {
    this.act = act;
  }

  @Override
  public void show() {
    if (visibleAssets != null) return;
    Gdx.app.log(TAG, "Loading act " + (act + 1));
    Riiablo.viewport = Riiablo.defaultViewport;

    visibleAssets = new ScreenAssetScope(sessionAssets.assets);
    try {
      createStage();
      while (submittedDependencies < dependencies.size) {
        sessionAssets.load(dependencies.get(submittedDependencies));
        submittedDependencies++;
      }
      map.setAct(act);
      map.load();
    } catch (RuntimeException | Error failure) {
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(() -> { throw failure; });
        cleanup.run(this::hide);
      }
    }
  }

  @Override
  public void hide() {
    Stage previousStage = stage;
    ScreenAssetScope previousAssets = visibleAssets;
    stage = null;
    loadingscreenWrapper = null;
    visibleAssets = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.dispose(previousStage); // borrows Client's batch
      cleanup.dispose(previousAssets);
    }
  }

  @Override
  public void dispose() {
    // Client may dispose a popped screen. GameScreen reuses this loader for
    // act travel, so only its transient show/hide resources belong here.
    hide();
  }

  @Override
  public void render(float delta) {
    if (sessionAssets.assets.update()) {
      map.finishLoading();
      map.generate();
      Riiablo.client.popScreen();
      return;
    }

    Riiablo.batch.setPalette(Riiablo.palettes.loading);
    stage.act();
    stage.draw();
  }

  public interface Loadable {
    Array<AssetDescriptor> getDependencies();
  }
}
