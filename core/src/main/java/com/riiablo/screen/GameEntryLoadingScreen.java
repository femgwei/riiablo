package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.save.CharData;
import com.riiablo.widget.AnimationWrapper;
import com.riiablo.util.Disposal;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Displays a loading frame while staged gameplay fonts are loaded by the
 * AssetManager's asynchronous loader. This keeps the character-selection
 * screen responsive when a native CJK atlas is first built.
 */
public final class GameEntryLoadingScreen extends ScreenAdapter {
  private static final String TAG = "GameEntryLoadingScreen";
  private static final AssetDescriptor<DC6> LOADING = new AssetDescriptor<>(
      "data\\local\\ui\\loadingscreen.dc6", DC6.class);

  private final AssetManager assets;
  private final Fonts fonts;
  private final LoadingScreenHost host;
  private final Supplier<? extends Screen> nextScreen;
  private Stage stage;
  private ScreenAssetScope visibleAssets;
  private boolean disposed;
  private boolean entered;
  private boolean showEntryLoading;

  public GameEntryLoadingScreen(CharData charData) {
    this(charData, null, false);
  }

  public GameEntryLoadingScreen(CharData charData, com.badlogic.gdx.net.Socket socket,
      boolean localHost) {
    // The caller/game screen owns the socket and hosted server, not this
    // temporary font-loading view. Preserve that ownership during handoff.
    this(() -> socket == null ? new GameScreen(charData)
        : new NetworkedGameScreen(charData, socket, localHost));
  }

  GameEntryLoadingScreen(Supplier<? extends Screen> nextScreen) {
    this(nextScreen, Riiablo.assets, Riiablo.fonts, LoadingScreenHost.fromGlobals());
  }

  GameEntryLoadingScreen(Supplier<? extends Screen> nextScreen, AssetManager assets,
      Fonts fonts, LoadingScreenHost host) {
    this.nextScreen = Objects.requireNonNull(nextScreen, "nextScreen");
    this.assets = Objects.requireNonNull(assets, "assets");
    this.fonts = Objects.requireNonNull(fonts, "fonts");
    this.host = Objects.requireNonNull(host, "host");
  }

  private void createStage() {
    stage = new Stage(host.viewport, host.batch);

    visibleAssets.load(LOADING);
    assets.finishLoadingAsset(LOADING);
    Animation loading = Animation.newAnimation(assets.get(LOADING));
    loading.setFrameDuration(Float.MAX_VALUE);
    AnimationWrapper image = new AnimationWrapper(loading) {
      @Override public void act(float delta) {
        super.act(delta);
        loading.setFrame(Math.max(0, Math.min(loading.getNumFramesPerDir() - 1,
            (int) (assets.getProgress() * (loading.getNumFramesPerDir() - 1)))));
      }
    };
    image.setPosition((stage.getWidth() - loading.getMinWidth()) / 2f,
        (stage.getHeight() - loading.getMinHeight()) / 2f);
    stage.addActor(image);
  }

  @Override public void show() {
    if (disposed || entered) throw new IllegalStateException("entry loading screen is finished");
    if (visibleAssets != null) return;
    host.activate();
    // Once staged fonts are indexed, the following GameLoadingScreen is the
    // only visible progress screen. Keeping this screen black for the tiny
    // handoff prevents the loading animation from appearing twice.
    visibleAssets = new ScreenAssetScope(assets);
    try {
      createStage();
      showEntryLoading = !fonts.hasIndexedGameplayFontCache();
      fonts.queueGameplayFonts();
    } catch (RuntimeException | Error failure) {
      fail(failure);
    }
  }

  @Override public void hide() {
    Stage previousStage = stage;
    ScreenAssetScope previousAssets = visibleAssets;
    stage = null;
    visibleAssets = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.dispose(previousStage); // borrows Client's batch
      cleanup.dispose(previousAssets);
    }
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    hide();
  }

  @Override public void render(float delta) {
    if (disposed || visibleAssets == null || entered) return;
    try {
      if (!fonts.finishQueueGameplayFonts()) {
        drawLoading(delta);
        return;
      }
      if (assets.update()) {
        entered = true; // before initialization/factory/Client callbacks can re-enter
        fonts.initializeGameplayFonts();
        Gdx.app.log(TAG, "Gameplay fonts ready; entering game");
        host.enter(Objects.requireNonNull(nextScreen.get(), "next screen"));
        return;
      }
      drawLoading(delta);
    } catch (RuntimeException | Error failure) {
      fail(failure);
    }
  }

  private void fail(Throwable failure) {
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> {
        if (failure instanceof Error) throw (Error) failure;
        throw (RuntimeException) failure;
      });
      cleanup.run(this::dispose);
    }
  }

  private void drawLoading(float delta) {
    if (!showEntryLoading) return;
    host.batch.setPalette(host.palettes.loading);
    stage.act(delta);
    stage.draw();
  }
}
