package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.SnapshotArray;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.widget.Button;
import com.riiablo.widget.GoldAmountDialog;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PanelResourceLifecycleTest {
  @Test void realManagerRetainsTheExternalTabReferenceAfterInventoryDisposal() throws Exception {
    InventoryPanel panel = inventory();
    com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    com.badlogic.gdx.Gdx.app = mock(com.badlogic.gdx.Application.class);
    AssetManager owner = new AssetManager(FileHandle::new, false);
    try {
      owner.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
        @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) { return mock(DC6.class); }
        @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
      });
      set(panel, "assets", owner);
      for (Field field : InventoryPanel.class.getDeclaredFields()) {
        if (field.getType() == AssetDescriptor.class) {
          field.setAccessible(true);
          owner.load((AssetDescriptor) field.get(panel));
        }
      }
      owner.load(panel.invcharTabDescriptor); // an independent consumer of the same sheet
      owner.finishLoading();
      DC6 shared = owner.get(panel.invcharTabDescriptor);
      assertEquals(2, owner.getReferenceCount(panel.invcharTabDescriptor.fileName));
      panel.dispose();
      panel.dispose();
      assertEquals(1, owner.getLoadedAssets());
      assertEquals(1, owner.getReferenceCount(panel.invcharTabDescriptor.fileName));
      verify(shared, never()).dispose();
      owner.unload(panel.invcharTabDescriptor.fileName);
      verify(shared).dispose();
    } finally {
      try { owner.dispose(); }
      finally { com.badlogic.gdx.Gdx.app = previousApp; }
    }
  }

  @Test void inventoryReleasesAllTenReferencesAndOwnedFillOnlyOnce() throws Exception {
    InventoryPanel panel = inventory();
    AssetManager owner = (AssetManager) get(panel, "assets");
    AssetManager previous = Riiablo.assets;
    Riiablo.assets = mock(AssetManager.class);
    try {
      panel.dispose();
      panel.dispose();
      verify(owner, times(10)).unload(anyString());
      verify(owner).unload("invcharTabDescriptor");
      verify(panel.fill).dispose();
      verify(panel.btnExit).dispose();
      verify(panel.btnDropGold).dispose();
      verify(panel.goldDialog).dispose();
      verify(panel.grid).dispose();
      verifyNoInteractions(Riiablo.assets);
    } finally { Riiablo.assets = previous; }
  }

  @Test void inventoryFailureDoesNotSkipFillDialogOrAnyAssetAndIsNotRetried() throws Exception {
    InventoryPanel panel = inventory();
    AssetManager owner = (AssetManager) get(panel, "assets");
    RuntimeException first = new RuntimeException("button");
    RuntimeException second = new RuntimeException("fill");
    doThrow(first).when(panel.btnExit).dispose();
    doThrow(second).when(panel.fill).dispose();
    assertSame(first, assertThrows(RuntimeException.class, panel::dispose));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    verify(panel.goldDialog).dispose();
    verify(owner, times(10)).unload(anyString());
    panel.dispose();
    verify(panel.fill).dispose();
  }

  @Test void inventoryAssetFailureStillReleasesRemainingReferences() throws Exception {
    InventoryPanel panel = inventory();
    AssetManager owner = (AssetManager) get(panel, "assets");
    RuntimeException failure = new RuntimeException("inventory sheet");
    doThrow(failure).when(owner).unload("invcharDescriptor");
    assertSame(failure, assertThrows(RuntimeException.class, panel::dispose));
    verify(owner).unload("invcharTabDescriptor");
    verify(owner).unload("inv_weaponsDescriptor");
    verify(panel.fill).dispose();
  }

  @Test void controlReleasesAllFifteenReferencesAndBothOwnedTexturesOnlyOnce() throws Exception {
    ControlPanel panel = control();
    AssetManager owner = (AssetManager) get(panel, "assets");
    Texture background = (Texture) get(get(panel, "controlWidget"), "background");
    Texture experience = (Texture) get(get(panel, "experienceWidget"), "whitePixelTexture");
    AssetManager previous = Riiablo.assets;
    Riiablo.assets = mock(AssetManager.class);
    try {
      panel.dispose();
      panel.dispose();
      // 15 parent references plus the nested run/minipanel/menu sheets.
      verify(owner, times(18)).unload(anyString());
      for (int i = 0; i < 7; i++) verify(owner).unload("class-" + i);
      verify(background).dispose();
      verify(experience).dispose();
      verifyNoInteractions(Riiablo.assets);
    } finally { Riiablo.assets = previous; }
  }

  @Test void nestedControlFailureStillReleasesBackgroundEverySheetAndParentIcons() throws Exception {
    ControlPanel panel = control();
    Object widget = get(panel, "controlWidget");
    Button run = (Button) get(widget, "btnRun");
    Texture background = (Texture) get(widget, "background");
    AssetManager owner = (AssetManager) get(panel, "assets");
    RuntimeException first = new RuntimeException("run button");
    RuntimeException second = new RuntimeException("sheet");
    doThrow(first).when(run).dispose();
    doThrow(second).when(owner).unload("ctrlpnlDescriptor");
    assertSame(first, assertThrows(RuntimeException.class, panel::dispose));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    verify(background).dispose();
    verify(owner, times(18)).unload(anyString());
    panel.dispose();
    verify(run).dispose();
  }

  @Test void controlWithoutDesktopWidgetStillReleasesIconsAndExperience() throws Exception {
    ControlPanel panel = control();
    set(panel, "controlWidget", null);
    panel.dispose();
    verify((AssetManager) get(panel, "assets"), times(15)).unload(anyString());
    verify((Texture) get(get(panel, "experienceWidget"), "whitePixelTexture")).dispose();
  }

  private static InventoryPanel inventory() throws Exception {
    InventoryPanel panel = mock(InventoryPanel.class, CALLS_REAL_METHODS);
    doReturn(new SnapshotArray<>(Actor.class)).when(panel).getChildren();
    initAssets(panel);
    set(panel, "fill", mock(Texture.class));
    panel.btnExit = mock(Button.class);
    panel.btnDropGold = mock(Button.class);
    panel.goldDialog = mock(GoldAmountDialog.class);
    set(panel, "grid", mock(ItemGrid.class));
    return panel;
  }

  private static ControlPanel control() throws Exception {
    ControlPanel panel = mock(ControlPanel.class, CALLS_REAL_METHODS);
    doReturn(new SnapshotArray<>(Actor.class)).when(panel).getChildren();
    initAssets(panel);
    AssetDescriptor[] icons = new AssetDescriptor[7];
    for (int i = 0; i < icons.length; i++) icons[i] = new AssetDescriptor<>("class-" + i, DC6.class);
    set(panel, "CharSkilliconDescriptor", icons);
    panel.btnAddStats = mock(Button.class);
    panel.btnAddSkills = mock(Button.class);
    panel.btnQuestLog = mock(Button.class);
    Object xp = nested(panel, "experienceWidget");
    set(xp, "whitePixelTexture", mock(Texture.class));
    Object widget = nested(panel, "controlWidget");
    initDescriptors(widget);
    set(widget, "this$0", panel);
    set(widget, "background", mock(Texture.class));
    set(widget, "btnRun", mock(Button.class));
    set(widget, "btnMenu", mock(Button.class));
    return panel;
  }

  private static AssetManager initAssets(Object panel) throws Exception {
    AssetManager owner = mock(AssetManager.class);
    set(panel, "assets", owner);
    initDescriptors(panel);
    return owner;
  }

  private static void initDescriptors(Object panel) throws Exception {
    for (Field field : panel.getClass().getDeclaredFields()) {
      if (field.getType() == AssetDescriptor.class) {
        field.setAccessible(true);
        field.set(panel, new AssetDescriptor<>(field.getName(), DC6.class));
      }
    }
  }

  private static Object nested(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    Object child = mock(field.getType(), CALLS_REAL_METHODS);
    field.set(owner, child);
    return child;
  }

  private static Object get(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private static void set(Object owner, String name, Object value) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(owner, value);
  }
}
