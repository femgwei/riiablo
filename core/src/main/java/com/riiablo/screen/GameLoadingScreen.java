package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.presentation.ClientGameplayContext;
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
  private final LoadingScreenHost host;
  private ScreenAssetScope visibleAssets;
  private int submittedDependencies;

  private Stage stage;
  private AnimationWrapper loadingscreenWrapper;

  private final Map map;
  private int act;
  private long loadGeneration;
  private long visibleGeneration;
  private boolean disposed;

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets) {
    this(map, dependencies, sessionAssets, ClientGameplayContext.fromGlobals());
  }

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets,
      ClientGameplayContext clientContext) {
    this(map, dependencies, sessionAssets, LoadingScreenHost.fromGlobals(
        clientContext == null ? ClientGameplayContext.fromGlobals() : clientContext));
  }

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets,
      LoadingScreenHost host) {
    this.map = map;
    this.dependencies = new Array<>(dependencies);
    this.sessionAssets = sessionAssets;
    this.host = java.util.Objects.requireNonNull(host, "host");
  }

  /** Client retires popped views; every Act transition therefore needs a fresh one. */
  GameLoadingScreen nextLoad() {
    dispose();
    return new GameLoadingScreen(map,
        dependencies, sessionAssets, host);
  }

  private void createStage() {
    stage = new Stage(host.viewport, host.batch);

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
    if (disposed) return;
    this.act = act;
  }

  @Override
  public void show() {
    if (disposed || visibleAssets != null) return;
    visibleGeneration = ++loadGeneration;
    Gdx.app.log(TAG, "Loading act " + (act + 1));
    host.activate();

    visibleAssets = new ScreenAssetScope(sessionAssets.assets);
    try {
      createStage();
      while (submittedDependencies < dependencies.size) {
        sessionAssets.loadOnce(dependencies.get(submittedDependencies));
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
    ++loadGeneration;
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
    // Client permanently retires a popped view. The next Act gets a fresh view.
    if (disposed) return;
    disposed = true;
    hide();
  }

  @Override
  public void render(float delta) {
    if (disposed || visibleAssets == null) return;
    if (sessionAssets.assets.update()) {
      if (disposed || visibleAssets == null || visibleGeneration != loadGeneration || map == null) return;
      map.finishLoading();
      if (disposed || visibleAssets == null || visibleGeneration != loadGeneration) return;
      map.generate();
      if (disposed || visibleAssets == null || visibleGeneration != loadGeneration) return;
      host.resume();
      return;
    }

    if (host.batch == null || host.palettes == null) return;
    host.batch.setPalette(host.palettes.loading);
    stage.act();
    stage.draw();
  }

  public interface Loadable {
    Array<AssetDescriptor> getDependencies();
  }
}

