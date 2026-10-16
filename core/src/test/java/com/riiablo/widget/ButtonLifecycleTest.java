package com.riiablo.widget;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Client;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ButtonLifecycleTest {
  private static final String PATH = "data/global/sfx/cursor/button.wav";
  private AssetManager previousAssets;
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private Colors previousColors;
  private Client.InputProcessor previousInput;
  private AssetManager assets;
  private Sound sound;

  @BeforeEach void setup() {
    previousAssets = Riiablo.assets;
    previousApp = Gdx.app;
    previousFiles = Gdx.files;
    previousColors = Riiablo.colors;
    previousInput = Riiablo.input;
    Gdx.app = mock(Application.class);
    Gdx.files = mock(com.badlogic.gdx.Files.class);
    Riiablo.colors = new Colors();
    Riiablo.input = mock(Client.InputProcessor.class);
    sound = mock(Sound.class);
    assets = new AssetManager(FileHandle::new, false);
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return sound; }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    Riiablo.assets = assets;
  }

  @AfterEach void restore() {
    try { assets.dispose(); }
    finally {
      Riiablo.assets = previousAssets;
      Gdx.app = previousApp;
      Gdx.files = previousFiles;
      Riiablo.colors = previousColors;
      Riiablo.input = previousInput;
    }
  }

  @Test void sharedReferencesRemainUntilTheirActualOwnerReleasesThem() {
    assets.load(PATH, Sound.class);
    Button first = button();
    Button second = button();
    assets.finishLoading();
    assertEquals(3, assets.getReferenceCount(PATH));
    first.dispose();
    first.dispose();
    assertEquals(2, assets.getReferenceCount(PATH));
    second.dispose();
    assertEquals(1, assets.getReferenceCount(PATH));
    verify(sound, never()).dispose();
    assets.unload(PATH);
    verify(sound).dispose();
  }

  @Test void queuedDisposalDoesNotCancelAnExternalReference() {
    Button first = button();
    Button second = button();
    assets.load(PATH, Sound.class);
    first.dispose();
    second.dispose();
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(PATH));
    verify(sound, never()).dispose();
  }

  @Test void queuedOwnReferenceToAlreadyLoadedSoundIsBalanced() {
    assets.load(PATH, Sound.class);
    assets.finishLoading();
    Button button = button();
    button.dispose();
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(PATH));
    verify(sound, never()).dispose();
  }

  @Test void activeAsyncDisposalPreservesQueuedExternalConsumer() throws Exception {
    CountDownLatch decoded = new CountDownLatch(1);
    assets.setLoader(Sound.class, new AsynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public void loadAsync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { decoded.countDown(); }
      @Override public Sound loadSync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return sound; }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    Button button = button();
    assets.load(PATH, Sound.class);
    assertFalse(assets.update());
    assertTrue(decoded.await(5, TimeUnit.SECONDS));
    assertFalse(assets.isLoaded(PATH));
    button.dispose();
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(PATH));
    verify(sound, never()).dispose();
  }

  @Test void playbackAndReleaseUseOriginalManagerAfterGlobalReplacement() {
    Button button = button();
    Riiablo.assets = mock(AssetManager.class);
    press(button, Input.Buttons.LEFT);
    verify(sound).play();
    button.dispose();
    assertFalse(assets.contains(PATH));
    verifyNoInteractions(Riiablo.assets);
    verify(sound).dispose();
  }

  @Test void disposedButtonNeverPlaysItsCachedSoundAgain() {
    Button button = button();
    press(button, Input.Buttons.LEFT);
    button.dispose();
    press(button, Input.Buttons.LEFT);
    verify(sound).play();
  }

  @Test void disabledMutedAndRightClicksDoNotStartSound() {
    Button button = button();
    press(button, Input.Buttons.RIGHT);
    button.setDisabled(true);
    press(button, Input.Buttons.LEFT);
    button.setDisabled(false);
    button.setClickSoundEnabled(false);
    press(button, Input.Buttons.LEFT);
    verify(sound, never()).play();
    button.dispose();
    assertFalse(assets.contains(PATH));
  }

  @Test void nestedHiddenButtonsReleaseOnlySoundNotSubclassOrArbitraryDisposables() {
    Group root = new Group();
    Group hidden = new Group();
    hidden.setVisible(false);
    root.addActor(hidden);
    SpecialButton nested = new SpecialButton();
    OwnedActor unrelated = new OwnedActor();
    hidden.addActor(nested);
    hidden.addActor(unrelated);
    Button.disposeClickSounds(root);
    Button.disposeClickSounds(root);
    assertFalse(assets.contains(PATH));
    assertEquals(0, nested.disposals);
    assertEquals(0, unrelated.disposals);
    assertEquals(2, hidden.getChildren().size);
    assertNotNull(nested.getStyle());
  }

  @Test void detachingAndReaddingIsNotDisposal() {
    Group first = new Group();
    Group second = new Group();
    Button button = button();
    first.addActor(button);
    button.remove();
    second.addActor(button);
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(PATH));
    verify(sound, never()).dispose();
    Button.disposeClickSounds(second);
    assertFalse(assets.contains(PATH));
  }

  @Test void unloadFailuresContinueAndAreNotRetried() {
    AssetManager owner = mock(AssetManager.class);
    Riiablo.assets = owner;
    when(owner.contains(PATH)).thenReturn(true);
    when(owner.isLoaded(PATH)).thenReturn(true);
    RuntimeException first = new RuntimeException("first");
    RuntimeException second = new RuntimeException("second");
    doThrow(first).doThrow(second).doNothing().when(owner).unload(PATH);
    Group root = new Group();
    for (int i = 0; i < 3; i++) root.addActor(button());
    assertSame(first, assertThrows(RuntimeException.class, () -> Button.disposeClickSounds(root)));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    Button.disposeClickSounds(root);
    verify(owner, times(3)).unload(PATH);
  }

  @Test void releaseCallbacksRemovingActorsCannotSkipSnapshottedButtons() {
    AssetManager owner = mock(AssetManager.class);
    Riiablo.assets = owner;
    when(owner.contains(PATH)).thenReturn(true);
    when(owner.isLoaded(PATH)).thenReturn(true);
    Group root = new Group();
    for (int i = 0; i < 3; i++) root.addActor(button());
    doAnswer(call -> { root.clearChildren(); return null; }).when(owner).unload(PATH);
    Button.disposeClickSounds(root);
    verify(owner, times(3)).unload(PATH);
  }

  private static Button button() { return new Button(new Button.ButtonStyle()); }

  private static void press(Button button, int mouseButton) {
    InputEvent event = new InputEvent();
    event.setButton(mouseButton);
    event.setListenerActor(button);
    // The first listener belongs to Scene2D's Button; the last is our sound listener.
    ClickListener listener = (ClickListener) button.getListeners().peek();
    listener.touchDown(event, 0, 0, 0, mouseButton);
  }

  private static class SpecialButton extends Button {
    int disposals;
    SpecialButton() { super(new ButtonStyle()); }
    @Override public void dispose() { disposals++; super.dispose(); }
  }

  private static class OwnedActor extends Actor implements Disposable {
    int disposals;
    @Override public void dispose() { disposals++; }
  }
}
