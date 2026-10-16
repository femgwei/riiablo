package com.riiablo.screen;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Client;
import com.riiablo.util.Disposal;
import com.riiablo.widget.Button;
import java.util.Objects;

/** Front-end screen ownership, separate from its reusable visible subscriptions. */
final class MenuScreenResources implements Disposable {
  final AssetManager assets;
  final Client.InputProcessor input;
  private final ScreenAssetScope references;
  private final ScreenSubscriptionScope subscriptions = new ScreenSubscriptionScope();
  private final Array<Button> buttons = new Array<>();
  private final Array<Disposable> children = new Array<>();
  private Stage stage;
  private boolean visible;
  private boolean disposed;
  private boolean platformDisposed;

  MenuScreenResources(AssetManager assets, Client.InputProcessor input) {
    this.assets = Objects.requireNonNull(assets, "assets");
    this.input = Objects.requireNonNull(input, "input");
    references = new ScreenAssetScope(assets);
  }

  void load(AssetDescriptor asset) { references.load(asset); }

  Stage own(Stage stage) {
    if (this.stage != null || disposed) throw new IllegalStateException("stage already owned or disposed");
    this.stage = Objects.requireNonNull(stage, "stage");
    return stage;
  }

  void useStageInput() {
    Stage ownedStage = Objects.requireNonNull(stage, "stage");
    subscribe(() -> input.addProcessor(ownedStage), () -> input.removeProcessor(ownedStage));
  }

  <T extends Button> T own(T button) {
    if (disposed) throw new IllegalStateException("menu is disposed");
    buttons.add(Objects.requireNonNull(button, "button"));
    return button;
  }

  void subscribe(Runnable attach, Runnable detach) { subscriptions.add(attach, detach); }

  <T extends Disposable> T ownResource(T child) {
    if (disposed) throw new IllegalStateException("menu is disposed");
    children.add(Objects.requireNonNull(child, "child"));
    return child;
  }

  void releaseResource(Disposable child) {
    // Remove before calling user code: failure/re-entry cannot dispose this owner twice.
    if (children.removeValue(child, true)) child.dispose();
  }

  void useController(ControllerListener listener) {
    Application application = Objects.requireNonNull(Gdx.app, "app");
    LifecycleListener lifecycle = new LifecycleListener() {
      @Override public void pause() {}
      @Override public void resume() {}
      @Override public void dispose() { platformDisposed = true; }
    };
    subscribe(() -> application.addLifecycleListener(lifecycle),
        () -> application.removeLifecycleListener(lifecycle));
    subscribe(() -> Controllers.addListener(listener), () -> {
      // LWJGL disposes platform lifecycle listeners before Client.dispose().
      // Calling Controllers here after its registry was removed creates a NEW manager.
      if (!platformDisposed) Controllers.removeListener(listener);
    });
  }

  void show() {
    if (disposed || platformDisposed) throw new IllegalStateException("menu or platform is disposed");
    if (visible) return;
    subscriptions.activate(); // rolls back even a partially registered listener
    visible = true;
  }

  void hide() {
    visible = false;
    subscriptions.deactivate();
  }

  boolean isVisible() { return visible; }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    visible = false;
    Stage closingStage = stage;
    stage = null;
    Button[] closingButtons = buttons.toArray(Button.class);
    buttons.clear();
    Disposable[] closingChildren = children.toArray(Disposable.class);
    children.clear();
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(subscriptions::dispose);
      for (Disposable child : closingChildren) cleanup.dispose(child);
      // Explicit registration also covers a constructor failing before parenting its buttons.
      for (Button button : closingButtons) cleanup.run(() -> Button.disposeClickSounds(button));
      cleanup.dispose(closingStage); // borrows the Client's batch
      cleanup.dispose(references);
    }
  }

  void fail(Throwable failure) {
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> {
        if (failure instanceof Error) throw (Error) failure;
        throw (RuntimeException) failure;
      });
      cleanup.run(this::dispose);
    }
  }
}
