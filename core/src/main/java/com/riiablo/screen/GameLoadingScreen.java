package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientGameplayContext;
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
  private final ClientGameplayContext clientContext;
  private ScreenAssetScope visibleAssets;
  private int submittedDependencies;

  private Stage stage;
  private AnimationWrapper loadingscreenWrapper;

  private Map map;
  private int act;
  private long loadGeneration;
  private long visibleGeneration;
  private boolean disposed;

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets) {
    this(map, dependencies, sessionAssets, ClientGameplayContext.fromGlobals());
  }

  GameLoadingScreen(Map map, Array<AssetDescriptor> dependencies, ScreenAssetScope sessionAssets,
      ClientGameplayContext clientContext) {
    this.map = map;
    this.dependencies = new Array<>(dependencies);
    this.sessionAssets = sessionAssets;
    this.clientContext = clientContext == null ? ClientGameplayContext.fromGlobals() : clientContext;
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
    if (disposed) return;
    this.act = act;
  }

  @Override
  public void show() {
    if (disposed || visibleAssets != null) return;
    visibleGeneration = ++loadGeneration;
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
    // Client may dispose a popped screen. GameScreen reuses this loader for
    // act travel, so only its transient show/hide resources belong here.
    if (disposed) return;
    disposed = true;
    hide();
    map = null;
  }

  @Override
  public void render(float delta) {
    if (disposed || visibleAssets == null) return;
    if (sessionAssets.assets.update()) {
      if (disposed || visibleAssets == null || visibleGeneration != loadGeneration || map == null) return;
      map.finishLoading();
      map.generate();
      if (clientContext.game() == null) return;
      Riiablo.client.popScreen();
      return;
    }

    if (clientContext.batch() == null || clientContext.palettes() == null) return;
    clientContext.batch().setPalette(clientContext.palettes().loading);
    stage.act();
    stage.draw();
  }

  public interface Loadable {
    Array<AssetDescriptor> getDependencies();
  }
}
