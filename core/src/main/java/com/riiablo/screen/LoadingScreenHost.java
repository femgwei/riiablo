package com.riiablo.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.riiablo.Client;
import com.riiablo.Palettes;
import com.riiablo.Riiablo;
import com.riiablo.presentation.ClientGameplayContext;
import com.riiablo.graphics.PaletteIndexedBatch;
import java.util.Objects;
import java.util.function.Consumer;

/** Borrowed presentation resources and navigation for a loading view. */
final class LoadingScreenHost {
  final Viewport viewport;
  final PaletteIndexedBatch batch;
  final Palettes palettes;
  private final Runnable activateViewport;
  private final Runnable resumeGame;
  private final Consumer<Screen> enterGame;

  LoadingScreenHost(Viewport viewport, PaletteIndexedBatch batch, Palettes palettes,
      Runnable activateViewport, Runnable resumeGame, Consumer<Screen> enterGame) {
    this.viewport = viewport;
    this.batch = batch;
    this.palettes = palettes;
    this.activateViewport = Objects.requireNonNull(activateViewport, "activateViewport");
    this.resumeGame = Objects.requireNonNull(resumeGame, "resumeGame");
    this.enterGame = Objects.requireNonNull(enterGame, "enterGame");
  }

  void activate() { activateViewport.run(); }
  void resume() { resumeGame.run(); }
  void enter(Screen screen) { enterGame.accept(screen); }

  static LoadingScreenHost fromGlobals() {
    return fromGlobals(ClientGameplayContext.fromGlobals());
  }

  static LoadingScreenHost fromGlobals(ClientGameplayContext context) {
    final Viewport viewport = Riiablo.defaultViewport;
    final Client client = Riiablo.client;
    return new LoadingScreenHost(viewport, context.batch(), context.palettes(),
        () -> Riiablo.viewport = viewport,
        () -> { if (client != null) client.popScreen(); },
        screen -> Objects.requireNonNull(client, "client").clearAndSet(screen));
  }
}

