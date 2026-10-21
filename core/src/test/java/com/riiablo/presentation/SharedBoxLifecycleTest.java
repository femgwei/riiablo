package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.Textures;
import com.riiablo.audio.Audio;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.Engine;
import com.riiablo.presentation.component.MenuWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.graphics.BorderedPaletteIndexedDrawable;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.save.CharData;
import com.riiablo.screen.panel.CharacterPanel;
import com.riiablo.screen.panel.VendorPanel;
import com.riiablo.widget.NpcDialogBox;
import com.riiablo.widget.NpcMenu;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

/** Real widgets, AssetManager, native tables and Artemis; GL/font/audio are fixtures. */
class SharedBoxLifecycleTest extends RiiabloTest {
  private static final String BOX = "data/global/ui/MENU/boxpieces.DC6";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final List<World> worlds = new ArrayList<>();
  private final List<Disposable> resources = new ArrayList<>();
  private AssetManager assets;
  private FontTBL.BitmapFont font;
  private Stage stage;
  private Audio.Instance speech;

  @BeforeEach void setupFixture() throws Exception {
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    }
    Riiablo.colors = new Colors();
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16;
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    for (Field field : Fonts.class.getFields()) {
      if (BitmapFont.class.isAssignableFrom(field.getType()) && !Modifier.isFinal(field.getModifiers())) field.set(Riiablo.fonts, font);
    }
    Riiablo.bundle = mock(I18NBundle.class); when(Riiablo.bundle.get(anyString())).thenReturn("fixture");
    Riiablo.charData = CharData.obtain().clear().set(Riiablo.NORMAL, false, "BoxOwner", Riiablo.AMAZON);
    Riiablo.audio = mock(Audio.class); speech = mock(Audio.Instance.class);
    when(Riiablo.audio.play(anyString(), eq(false), eq(Audio.Channel.SPEECH))).thenReturn(speech);
    assets = new AssetManager(FileHandle::new, false); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class);
        when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region);
        return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    stage = mock(Stage.class); Group root = new Group();
    when(stage.getRoot()).thenReturn(root); when(stage.getActors()).thenReturn(root.getChildren());
    doAnswer(call -> { root.addActor(call.getArgument(0)); return null; }).when(stage).addActor(any());
  }

  @AfterEach void restoreFixture() throws Exception {
    try {
      for (World world : worlds) world.dispose();
      for (Disposable resource : resources) resource.dispose();
      if (assets != null) assets.dispose();
    } finally {
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
    }
  }

  @Test void queuedSharedDrawableKeepsOtherConsumerAndReleasesCachedRegions() throws Exception {
    BorderedPaletteIndexedDrawable first = own(new BorderedPaletteIndexedDrawable());
    BorderedPaletteIndexedDrawable second = own(new BorderedPaletteIndexedDrawable());
    first.dispose(); assets.finishLoading(); assertEquals(1, assets.getReferenceCount(BOX));
    PaletteIndexedBatch batch = mock(PaletteIndexedBatch.class);
    second.draw(batch, 0, 0, 80, 80);
    assertNotNull(get(second, "boxpieces"));
    DC6 shared = assets.get(BOX, DC6.class);
    second.dispose(); second.dispose();
    assertNull(get(second, "boxpieces")); assertNull(get(second, "TOP")); assertNull(second.getRegion());
    verify(shared).dispose(); assertEquals(0, assets.getLoadedAssets());
    clearInvocations(batch); second.draw(batch, 0, 0, 80, 80);
    second.draw(batch, 0, 0, 0, 0, 80, 80, 1, 1, 0); verifyNoInteractions(batch);
  }

  @Test void drawableDrawAndDisposeUseOriginalManager() {
    BorderedPaletteIndexedDrawable drawable = own(new BorderedPaletteIndexedDrawable()); assets.finishLoading();
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    drawable.draw(mock(PaletteIndexedBatch.class), 0, 0, 80, 80); drawable.dispose();
    assertEquals(0, assets.getLoadedAssets()); verifyNoInteractions(replacement);
  }

  @Test void hiddenAndUnattachedSubMenusRemainOwnedUntilTreeDisposal() {
    NpcMenu root = own(new NpcMenu(mock(MenuManager.class), 0, "NPC"));
    NpcMenu child = root.createSubMenu(3381), grandchild = child.createSubMenu(3399);
    NpcMenu.CancellationListener cancel = mock(NpcMenu.CancellationListener.class);
    root.addCancel(cancel); root.addItem(3381, child); child.addItem(3399, grandchild);
    assets.finishLoading(); assertEquals(3, assets.getReferenceCount(BOX));
    root.setVisible(false); root.remove(); assertEquals(3, assets.getReferenceCount(BOX));
    root.dispose(); root.dispose(); child.cancel(); root.cancel();
    assertTrue(child.isDisposed()); assertTrue(grandchild.isDisposed()); assertEquals(0, assets.getLoadedAssets());
    verifyNoInteractions(cancel);
    assertThrows(IllegalStateException.class, () -> root.createSubMenu(1));
  }

  @Test void childCleanupErrorDoesNotSkipSiblingOrRoot() {
    NpcMenu root = own(new NpcMenu(mock(MenuManager.class), 0, "NPC"));
    NpcMenu failing = spy(new NpcMenu(mock(MenuManager.class), 1));
    root.addItem(1, failing); NpcMenu sibling = root.createSubMenu(2);
    Error failure = new AssertionError("child");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).when(failing).dispose();
    assertSame(failure, assertThrows(AssertionError.class, root::dispose));
    assertTrue(sibling.isDisposed()); assertEquals(0, assets.getLoadedAssets()); root.dispose();
  }

  @Test void menuRejectsMultipleParentsAndCyclesWithoutTakingOtherTreeOwnership() {
    NpcMenu first = own(new NpcMenu(mock(MenuManager.class), 0, "first"));
    NpcMenu second = own(new NpcMenu(mock(MenuManager.class), 1, "second"));
    NpcMenu child = first.createSubMenu(1);
    assertThrows(IllegalArgumentException.class, () -> second.addItem(1, child));
    assertThrows(IllegalArgumentException.class, () -> child.addItem(1, first));
    first.dispose(); assets.finishLoading(); assertEquals(1, assets.getReferenceCount(BOX));
  }

  @ParameterizedTest @ValueSource(strings = {"entity", "wrapper", "earlyEntity", "earlyWrapper"})
  void menuTreeRetiresOnEntityOrWrapperRemovalEvenBeforeFirstProcess(String kind) {
    MenuManager manager = new MenuManager(); World world = world(manager, new DialogManager());
    int entity = world.create(); NpcMenu root = manager.createMenu(entity, "NPC"); root.createSubMenu(1);
    if (!kind.startsWith("early")) { assets.finishLoading(); world.process(); }
    if (kind.toLowerCase().endsWith("entity")) world.delete(entity); else world.getMapper(MenuWrapper.class).remove(entity);
    world.process(); assertTrue(root.isDisposed()); assertFalse(assets.contains(BOX));
  }

  @Test void replacementReleasesPreviousTreeButDeferredRemovalKeepsNewRoot() {
    MenuManager manager = new MenuManager(); World world = world(manager, new DialogManager());
    int entity = world.create(); NpcMenu old = manager.createMenu(entity, "old"); old.createSubMenu(1);
    world.process();
    world.getMapper(MenuWrapper.class).remove(entity);
    NpcMenu next = manager.createMenu(entity, "next"); next.createSubMenu(1);
    assets.finishLoading(); world.process(); assertTrue(old.isDisposed()); assertFalse(next.isDisposed());
    assertEquals(2, assets.getReferenceCount(BOX)); world.dispose(); assertFalse(assets.contains(BOX));
  }

  @Test void navigationAndCloseRetainTreeButWorldExitRemovesVisibleChild() {
    MenuManager manager = new MenuManager(); World world = world(manager, new DialogManager());
    int entity = world.create();
    world.getMapper(com.riiablo.engine.client.component.Label.class).create(entity);
    world.getMapper(Position.class).create(entity);
    NpcMenu root = manager.createMenu(entity, "NPC"), child = root.createSubMenu(1); root.addItem(1, child);
    assets.finishLoading(); world.process();
    manager.setMenu(root, entity); manager.setMenu(child, entity); manager.setMenu(null, Engine.INVALID_ENTITY);
    assertEquals(2, assets.getReferenceCount(BOX)); assertTrue(stage.getActors().isEmpty());
    manager.setMenu(child, entity); world.dispose(); manager.dispose();
    assertNull(manager.getMenu()); assertTrue(stage.getActors().isEmpty()); assertFalse(assets.contains(BOX));
    assertThrows(IllegalStateException.class, () -> manager.createMenu(entity, "late"));
  }

  @Test void worldExitWithoutProcessingReleasesEntirePendingTree() {
    MenuManager manager = new MenuManager(); World world = world(manager, new DialogManager());
    manager.createMenu(world.create(), "NPC").createSubMenu(1);
    world.dispose(); assertFalse(assets.contains(BOX));
  }

  @Test void failedWrapperInstallationImmediatelyRollsBackNewTree() {
    MenuManager manager = new MenuManager(); world(manager, new DialogManager());
    manager.mMenuWrapper = mock(com.artemis.ComponentMapper.class);
    Error failure = new AssertionError("wrapper installation");
    when(manager.mMenuWrapper.create(anyInt())).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, () -> manager.createMenu(0, "NPC")));
    assertFalse(assets.contains(BOX));
  }

  @Test void managerCleanupErrorDoesNotSkipOtherEntityTrees() {
    MenuManager manager = new MenuManager(); World world = world(manager, new DialogManager());
    NpcMenu first = manager.createMenu(world.create(), "first");
    NpcMenu second = manager.createMenu(world.create(), "second");
    NpcMenu failing = spy(new NpcMenu(manager, 1)); first.addItem(1, failing);
    Error failure = new AssertionError("root cleanup");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).when(failing).dispose();
    assertSame(failure, assertThrows(AssertionError.class, manager::dispose));
    assertTrue(first.isDisposed()); assertTrue(second.isDisposed()); assertFalse(assets.contains(BOX));
  }

  @Test void dialogReplacementAndWorldExitOwnOnlyTheirOwnAudioAndBorder() {
    MenuManager menus = new MenuManager(); DialogManager dialogs = new DialogManager(); World world = world(menus, dialogs);
    NpcDialogBox first = dialog(), second = dialog();
    dialogs.setDialog(first); dialogs.setDialog(first); dialogs.setDialog(second);
    assets.finishLoading(); assertEquals(1, assets.getReferenceCount(BOX));
    world.dispose(); first.act(1000); second.act(1000);
    assertNull(dialogs.getDialog()); assertTrue(stage.getActors().isEmpty()); assertFalse(assets.contains(BOX));
    verify(speech, times(2)).stop();
    dialogs.setDialog(dialog()); assertFalse(assets.contains(BOX)); // late input is retired, never installed
  }

  @Test void dialogAudioErrorStillReleasesBorderAndDoesNotRetryOrComplete() {
    NpcDialogBox.DialogCompletionListener completion = mock(NpcDialogBox.DialogCompletionListener.class);
    NpcDialogBox dialog = own(new NpcDialogBox(speechKey(), completion));
    Error failure = new AssertionError("speech stop"); doThrow(failure).when(speech).stop();
    assertSame(failure, assertThrows(AssertionError.class, dialog::dispose));
    assertFalse(assets.contains(BOX)); dialog.dispose(); dialog.act(10000);
    verify(speech).stop(); verifyNoInteractions(completion);
  }

  @Test void replacementCleanupErrorDoesNotLoseOwnershipOfNewDialog() {
    MenuManager menus = new MenuManager(); DialogManager dialogs = new DialogManager(); World world = world(menus, dialogs);
    NpcDialogBox first = dialog(), second = dialog(); dialogs.setDialog(first);
    Error failure = new AssertionError("old speech stop"); doThrow(failure).doNothing().when(speech).stop();
    assertSame(failure, assertThrows(AssertionError.class, () -> dialogs.setDialog(second)));
    assertSame(second, dialogs.getDialog()); assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(BOX)); assertEquals(1, stage.getActors().size);
    world.dispose(); assertFalse(assets.contains(BOX)); assertTrue(stage.getActors().isEmpty());
  }

  @Test void dialogConstructorFailureRollsBackBorder() {
    Error failure = new AssertionError("play");
    when(Riiablo.audio.play(anyString(), eq(false), eq(Audio.Channel.SPEECH))).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, () -> new NpcDialogBox(speechKey(), d -> {})));
    assertFalse(assets.contains(BOX));
  }

  @Test void dialogAttachFailureRetiresNewDialog() {
    MenuManager menus = new MenuManager(); DialogManager dialogs = new DialogManager(); world(menus, dialogs);
    doThrow(new IllegalStateException("attach")).when(stage).addActor(any());
    assertThrows(IllegalStateException.class, () -> dialogs.setDialog(dialog()));
    assertNull(dialogs.getDialog()); assertFalse(assets.contains(BOX));
  }

  @ParameterizedTest @ValueSource(strings = {"character", "vendor"})
  void panelDisposalProtectsSharedBorderAndOriginalManager(String kind) {
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class)) {
      assets.load(BOX, DC6.class);
      Disposable panel = own(kind.equals("character") ? new CharacterPanel() : new VendorPanel()); assets.finishLoading();
      AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
      panel.dispose(); panel.dispose();
      assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(BOX));
      verifyNoInteractions(replacement);
    }
  }

  @Test void characterConstructorFailureRollsBackAfterBorderAcquisition() {
    Error failure = new AssertionError("font"); when(font.newFontCache()).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, CharacterPanel::new));
    assertEquals(0, assets.getLoadedAssets()); assertFalse(assets.contains(BOX));
  }

  @Test void vendorConstructorFailureRollsBackAfterBorderAcquisition() {
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class)) {
      Error failure = new AssertionError("prompt"); when(Riiablo.bundle.get("vendor_buy_title")).thenThrow(failure);
      assertSame(failure, assertThrows(AssertionError.class, VendorPanel::new));
      assertEquals(0, assets.getLoadedAssets()); assertFalse(assets.contains(BOX));
    }
  }

  private World world(MenuManager menus, DialogManager dialogs) {
    IsometricCamera iso = mock(IsometricCamera.class);
    World world = new World(new WorldConfigurationBuilder().with(menus, dialogs).build()
        .register("scaledStage", stage).register("iso", iso));
    worlds.add(world); return world;
  }

  private NpcDialogBox dialog() { return own(new NpcDialogBox(speechKey(), d -> {})); }
  private String speechKey() { return Riiablo.files.speech.iterator().next().sound; }
  private <T extends Disposable> T own(T resource) { resources.add(resource); return resource; }
  private static Object get(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
  }
  private static void set(Object owner, String name, Object value) throws Exception { set(owner.getClass(), owner, name, value); }
  private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
  }
}
