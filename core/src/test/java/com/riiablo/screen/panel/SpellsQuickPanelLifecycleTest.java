package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.Keys;
import com.riiablo.codec.DC6;
import com.riiablo.key.MappedKey;
import com.riiablo.key.MappedKeyStateAdapter;
import com.riiablo.widget.Button;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SpellsQuickPanelLifecycleTest {
  @Test void clearingUsesAllTableChildrenNotTheLossySkillIndexAndIsRepeatable() throws Exception {
    AssetManager previousAssets = Riiablo.assets;
    Colors previousColors = Riiablo.colors;
    com.badlogic.gdx.Files previousFiles = com.badlogic.gdx.Gdx.files;
    com.badlogic.gdx.Gdx.files = mock(com.badlogic.gdx.Files.class);
    AssetManager owner = mock(AssetManager.class);
    Riiablo.assets = owner;
    Riiablo.colors = new Colors();
    String path = "data/global/sfx/cursor/button.wav";
    when(owner.contains(path)).thenReturn(true);
    when(owner.isLoaded(path)).thenReturn(true);
    try {
      SpellsQuickPanel panel = mock(SpellsQuickPanel.class, CALLS_REAL_METHODS);
      panel.tables = new Table[] {new Table(), new Table()};
      panel.buttons = new IntMap<>(); // Includes neither duplicate-id nor unindexed children.
      panel.keyMappings = new ObjectMap<>();
      for (Table table : panel.tables) {
        table.add(new Button(new Button.ButtonStyle()));
        table.add(new Button(new Button.ButtonStyle()));
      }
      Method clear = SpellsQuickPanel.class.getDeclaredMethod("clearButtons");
      clear.setAccessible(true);
      clear.invoke(panel);
      clear.invoke(panel);
      verify(owner, times(4)).unload(path);
      for (Table table : panel.tables) {
        assertEquals(0, table.getChildren().size);
        assertEquals(0, table.getCells().size);
      }
      assertEquals(0, panel.buttons.size);
      assertEquals(0, panel.keyMappings.size);
    } finally {
      Riiablo.assets = previousAssets;
      Riiablo.colors = previousColors;
      com.badlogic.gdx.Gdx.files = previousFiles;
    }
  }

  @Test void failedSoundReleaseStillClearsEveryRowKeyListenerAndIcon() throws Exception {
    AssetManager previousAssets = Riiablo.assets;
    Colors previousColors = Riiablo.colors;
    com.badlogic.gdx.Files previousFiles = com.badlogic.gdx.Gdx.files;
    com.badlogic.gdx.Gdx.files = mock(com.badlogic.gdx.Files.class);
    Riiablo.colors = new Colors();
    AssetManager owner = mock(AssetManager.class);
    Riiablo.assets = owner;
    String path = "data/global/sfx/cursor/button.wav";
    when(owner.contains(path)).thenReturn(true);
    when(owner.isLoaded(path)).thenReturn(true);
    RuntimeException failure = new RuntimeException("button release");
    doThrow(failure).doNothing().when(owner).unload(path);
    SpellsQuickPanel panel = mock(SpellsQuickPanel.class, CALLS_REAL_METHODS);
    panel.mappedKeyListener = new MappedKeyStateAdapter();
    try {
      panel.tables = new Table[] {new Table(), new Table()};
      panel.buttons = new IntMap<>();
      panel.keyMappings = new ObjectMap<>();
      for (Table table : panel.tables) table.add(new Button(new Button.ButtonStyle()));
      set(panel, "SkilliconDescriptor", new AssetDescriptor<>("icons", DC6.class));
      set(panel, "CharSkilliconDescriptor", new AssetDescriptor[] {new AssetDescriptor<>("class-icons", DC6.class)});
      com.riiablo.util.AssetReferenceScope references = new com.riiablo.util.AssetReferenceScope(owner);
      references.load(new AssetDescriptor<>("icons", DC6.class));
      references.load(new AssetDescriptor<>("class-icons", DC6.class));
      when(owner.contains("icons")).thenReturn(true);
      when(owner.isLoaded("icons")).thenReturn(true);
      when(owner.contains("class-icons")).thenReturn(true);
      when(owner.isLoaded("class-icons")).thenReturn(true);
      set(panel, "references", references);
      set(panel, "character", mock(com.riiablo.save.CharData.class));
      // This legacy sound-error fixture bypasses the Actor constructor.
      doNothing().when(panel).clearChildren();
      doNothing().when(panel).clearListeners();
      for (MappedKey key : Keys.Skill) key.addStateListener(panel.mappedKeyListener);
      assertSame(failure, assertThrows(RuntimeException.class, panel::dispose));
      verify(owner, times(2)).unload(path);
      verify(owner).unload("icons");
      verify(owner).unload("class-icons");
      for (Table table : panel.tables) assertEquals(0, table.getChildren().size);
      for (MappedKey key : Keys.Skill) assertFalse(key.containsStateListener(panel.mappedKeyListener));
    } finally {
      for (MappedKey key : Keys.Skill) key.removeStateListener(panel.mappedKeyListener);
      Riiablo.assets = previousAssets;
      Riiablo.colors = previousColors;
      com.badlogic.gdx.Gdx.files = previousFiles;
    }
  }

  private static void set(SpellsQuickPanel panel, String name, Object value) throws Exception {
    java.lang.reflect.Field field = SpellsQuickPanel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(panel, value);
  }
}
