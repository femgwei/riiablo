package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.CharData;
import com.riiablo.widget.AnimationWrapper;

/**
 * Displays a loading frame while staged gameplay fonts are loaded by the
 * AssetManager's asynchronous loader. This keeps the character-selection
 * screen responsive when a native CJK atlas is first built.
 */
public final class GameEntryLoadingScreen extends ScreenAdapter {
  private static final String TAG = "GameEntryLoadingScreen";
  private static final AssetDescriptor<DC6> LOADING = new AssetDescriptor<>(
      "data\\local\\ui\\loadingscreen.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);

  private final CharData charData;
  private final com.badlogic.gdx.net.Socket socket;
  private final boolean localHost;
  private final Stage stage;
  private final AnimationWrapper image;
  private boolean entered;

  public GameEntryLoadingScreen(CharData charData) {
    this(charData, null, false);
  }

  public GameEntryLoadingScreen(CharData charData, com.badlogic.gdx.net.Socket socket,
      boolean localHost) {
    this.charData = charData;
    this.socket = socket;
    this.localHost = localHost;
    stage = new Stage(Riiablo.defaultViewport, Riiablo.batch);

    Riiablo.assets.load(LOADING);
    Riiablo.assets.finishLoadingAsset(LOADING);
    Animation loading = Animation.newAnimation(Riiablo.assets.get(LOADING));
    loading.setFrameDuration(Float.MAX_VALUE);
    image = new AnimationWrapper(loading) {
      @Override public void act(float delta) {
        super.act(delta);
        loading.setFrame(Math.max(0, Math.min(loading.getNumFramesPerDir() - 1,
            (int) (Riiablo.assets.getProgress() * (loading.getNumFramesPerDir() - 1)))));
      }
    };
    image.setPosition((stage.getWidth() - loading.getMinWidth()) / 2f,
        (stage.getHeight() - loading.getMinHeight()) / 2f);
    stage.addActor(image);
  }

  @Override public void show() {
    Riiablo.viewport = Riiablo.defaultViewport;
    Riiablo.fonts.queueGameplayFonts();
  }

  @Override public void hide() {
    Riiablo.assets.unload(LOADING.fileName);
  }

  @Override public void render(float delta) {
    if (!entered && Riiablo.assets.update()) {
      entered = true;
      Riiablo.fonts.initializeGameplayFonts();
      Gdx.app.log(TAG, "Gameplay fonts ready; entering game");
      if (socket == null) Riiablo.client.clearAndSet(new GameScreen(charData));
      else Riiablo.client.clearAndSet(new NetworkedGameScreen(charData, socket, localHost));
      return;
    }

    Riiablo.batch.setPalette(Riiablo.palettes.loading);
    stage.act(delta);
    stage.draw();
  }
}
