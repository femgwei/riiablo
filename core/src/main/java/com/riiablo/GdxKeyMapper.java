package com.riiablo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectSet;
import com.riiablo.key.MappedKey;
import com.riiablo.key.SaveableKeyMapper;
import com.riiablo.save.D2Key;
import com.riiablo.serializer.IntArrayStringSerializer;
import com.riiablo.serializer.SerializeException;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.io.IOException;

public class GdxKeyMapper extends SaveableKeyMapper {
  private static final String TAG = "GdxKeyMapper";

  private final Preferences PREFERENCES = Gdx.app.getPreferences(TAG);
  private final Trie<String, MappedKey> KEYS = new PatriciaTrie<>();
  private final Map<String, int[]> DEFAULTS = new HashMap<>();
  private boolean captureMode;

  /**
   * Slots in the native D2 key table that correspond to Riiablo's controls.
   * The slot, rather than the action number, is intentionally used here: the
   * original table contains a few shared action IDs, while the record order is
   * stable across 1.10f character files and is what the game rewrites.
   */
  private static final NativeBinding[] NATIVE_BINDINGS = {
      binding("character", 1, 0),
      binding("inventory", 3, 2),
      binding("party", 4),
      binding("quests", 8, 9),
      binding("enter", 10),
      binding("help", 12),
      binding("automap", 14),
      binding("spells", 26, 24),
      binding("skill1", 28),
      binding("skill2", 30),
      binding("skill3", 32),
      binding("skill4", 34),
      binding("skill5", 36),
      binding("skill6", 38),
      binding("skill7", 40),
      binding("skill8", 90),
      binding("belt1", 44),
      binding("belt2", 46),
      binding("belt3", 48),
      binding("belt4", 50),
      binding("run", 68),
      binding("swap", 86),
      binding("hireling", 108),
      binding("esc", 112)
  };

  private static NativeBinding binding(String alias, int... slots) {
    return new NativeBinding(alias, slots);
  }

  private static final class NativeBinding {
    final String alias;
    final int[] slots;

    NativeBinding(String alias, int[] slots) {
      this.alias = alias;
      this.slots = slots;
    }
  }

  @Override
  public boolean add(MappedKey key) {
    // Capture the constructor assignment before SaveableKeyMapper loads a persisted
    // override. This gives the controls screen a stable, per-process default.
    DEFAULTS.putIfAbsent(key.getAlias().toLowerCase(), key.getAssignments());
    return super.add(key);
  }

  /** Restores one mapping to its constructor default and persists it immediately. */
  public boolean reset(MappedKey key) {
    if (key == null) return false;
    int[] defaults = DEFAULTS.get(key.getAlias().toLowerCase());
    if (defaults == null) return false;
    key.assign(defaults);
    save(key);
    return true;
  }

  /** Restores all registered mappings to their constructor defaults. */
  public void resetAll() {
    for (MappedKey key : this) reset(key);
  }

  /**
   * Loads a character's native {@code .key} file over the global preferences.
   * Missing/unsupported native key codes are left unmapped in the runtime
   * mapper, but the original numeric value remains intact in the file until it
   * is explicitly saved.
   */
  public boolean loadCharacterKey(FileHandle file) {
    if (file == null || !file.exists()) return false;
    try {
      applyNativeKeyFile(D2Key.read(file));
      return true;
    } catch (IOException | RuntimeException e) {
      Gdx.app.error(TAG, "Failed to read character key file " + file.path(), e);
      return false;
    }
  }

  /** Saves the current Riiablo bindings to a character-form native key file. */
  public boolean saveCharacterKey(FileHandle file) {
    if (file == null) return false;
    try {
      D2Key nativeKeys = file.exists() ? D2Key.read(file) : D2Key.emptyCharacter();
      updateNativeKeyFile(nativeKeys);
      nativeKeys.write(file);
      return true;
    } catch (IOException | RuntimeException e) {
      Gdx.app.error(TAG, "Failed to write character key file " + file.path(), e);
      return false;
    }
  }

  private void applyNativeKeyFile(D2Key nativeKeys) {
    for (NativeBinding binding : NATIVE_BINDINGS) {
      MappedKey key = get(binding.alias);
      if (key == null || binding.slots.length == 0) continue;
      int[] assignments = new int[Math.max(2, binding.slots.length)];
      Arrays.fill(assignments, MappedKey.NOT_MAPPED);
      for (int i = 0; i < binding.slots.length && i < assignments.length; i++) {
        int slot = binding.slots[i];
        if (slot < nativeKeys.recordCount()) assignments[i] = fromNativeKey(nativeKeys.record(slot).keyCode());
      }
      // MappedKey requires unmapped entries to be trailing and rejects
      // duplicates. Native files use the same convention, so normalize here.
      compactAssignments(assignments);
      key.assign(assignments);
    }
  }

  private void updateNativeKeyFile(D2Key nativeKeys) {
    for (NativeBinding binding : NATIVE_BINDINGS) {
      MappedKey key = get(binding.alias);
      if (key == null) continue;
      int[] assignments = key.getAssignments();
      for (int i = 0; i < binding.slots.length && i < nativeKeys.recordCount(); i++) {
        int keycode = i < assignments.length ? toNativeKey(assignments[i]) : 0xFFFF;
        nativeKeys.record(binding.slots[i]).setKeyCode(keycode);
      }
    }
  }

  private static void compactAssignments(int[] assignments) {
    int out = 0;
    for (int assignment : assignments) {
      if (assignment != MappedKey.NOT_MAPPED) assignments[out++] = assignment;
    }
    while (out < assignments.length) assignments[out++] = MappedKey.NOT_MAPPED;
  }

  private static int fromNativeKey(int keycode) {
    if (keycode == 0xFFFF) return MappedKey.NOT_MAPPED;
    if (keycode >= 0x41 && keycode <= 0x5A) return Input.Keys.A + keycode - 0x41;
    if (keycode >= 0x30 && keycode <= 0x39) return Input.Keys.NUM_0 + keycode - 0x30;
    if (keycode >= 0x70 && keycode <= 0x7B) return Input.Keys.F1 + keycode - 0x70;
    switch (keycode) {
      case 0x09: return Input.Keys.TAB;
      case 0x0D: return Input.Keys.ENTER;
      case 0x10: return Input.Keys.SHIFT_LEFT;
      case 0x11: return Input.Keys.CONTROL_LEFT;
      case 0x12: return Input.Keys.ALT_LEFT;
      case 0x1B: return Input.Keys.ESCAPE;
      case 0x20: return Input.Keys.SPACE;
      case 0x2C: return Input.Keys.COMMA;
      case 0x2D: return Input.Keys.MINUS;
      case 0x2E: return Input.Keys.PERIOD;
      case 0x2F: return Input.Keys.SLASH;
      case 0x3B: return Input.Keys.SEMICOLON;
      case 0x3D: return Input.Keys.EQUALS;
      case 0x5B: return Input.Keys.LEFT_BRACKET;
      case 0x5D: return Input.Keys.RIGHT_BRACKET;
      case 0x60: return Input.Keys.GRAVE;
      default: return MappedKey.NOT_MAPPED;
    }
  }

  private static int toNativeKey(int keycode) {
    if (keycode == MappedKey.NOT_MAPPED) return 0xFFFF;
    if (keycode >= Input.Keys.A && keycode <= Input.Keys.Z) return 0x41 + keycode - Input.Keys.A;
    if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) return 0x30 + keycode - Input.Keys.NUM_0;
    if (keycode >= Input.Keys.F1 && keycode <= Input.Keys.F12) return 0x70 + keycode - Input.Keys.F1;
    switch (keycode) {
      case Input.Keys.TAB: return 0x09;
      case Input.Keys.ENTER: return 0x0D;
      case Input.Keys.SHIFT_LEFT: return 0x10;
      case Input.Keys.CONTROL_LEFT: return 0x11;
      case Input.Keys.ALT_LEFT: return 0x12;
      case Input.Keys.ESCAPE: return 0x1B;
      case Input.Keys.SPACE: return 0x20;
      case Input.Keys.COMMA: return 0x2C;
      case Input.Keys.MINUS: return 0x2D;
      case Input.Keys.PERIOD: return 0x2E;
      case Input.Keys.SLASH: return 0x2F;
      case Input.Keys.SEMICOLON: return 0x3B;
      case Input.Keys.EQUALS: return 0x3D;
      case Input.Keys.LEFT_BRACKET: return 0x5B;
      case Input.Keys.RIGHT_BRACKET: return 0x5D;
      case Input.Keys.GRAVE: return 0x60;
      default: return 0xFFFF;
    }
  }

  /** Temporarily prevents captured keys from also dispatching gameplay actions. */
  public void setCaptureMode(boolean captureMode) {
    this.captureMode = captureMode;
  }

  @NonNull
  public SortedMap<String, MappedKey> prefixMap(String alias) {
    if (alias == null) return (SortedMap<String, MappedKey>) MapUtils.EMPTY_SORTED_MAP;
    return KEYS.prefixMap(alias.toLowerCase());
  }

  @Nullable
  public MappedKey get(String alias) {
    if (alias == null) return null;
    return KEYS.get(alias.toLowerCase());
  }

  @Nullable
  @Override
  public int[] load(MappedKey key) {
    String alias = key.getAlias();
    String serializedValue = PREFERENCES.getString(alias);
    if (serializedValue == null || serializedValue.isEmpty()) return null;

    int[] assignments;
    try {
      assignments = IntArrayStringSerializer.INSTANCE.deserialize(serializedValue);
    } catch (SerializeException t) {
      Gdx.app.error(TAG, String.format("removing %s from preferences (invalid save format): %s", alias, t.getMessage()), t);
      PREFERENCES.remove(alias);
      PREFERENCES.flush();
      throw t;
    }

    if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG) {
      String[] keycodeNames = getKeycodeNames(assignments);
      Gdx.app.debug(TAG, String.format("%s [%s] loaded as %s (raw: \"%s\")",
          key.getName(), key.getAlias(), Arrays.toString(keycodeNames), serializedValue));
    }

    KEYS.put(alias.toLowerCase(), key);
    return assignments;
  }

  @Override
  public void save(MappedKey key) {
    if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG && !isManaging(key)) {
      Gdx.app.debug(TAG, String.format("key %s is being saved by a key mapper not managing it", key));
    }

    int[] assignments = key.getAssignments();
    String serializedValue = IntArrayStringSerializer.INSTANCE.serialize(assignments);
    PREFERENCES.putString(key.getAlias(), serializedValue);
    PREFERENCES.flush();
    if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG) {
      String[] keycodeNames = getKeycodeNames(assignments);
      Gdx.app.debug(TAG, String.format("%s [%s] saved as %s (raw: \"%s\")",
          key.getName(), key.getAlias(), Arrays.toString(keycodeNames), serializedValue));
    }
  }

  private String[] getKeycodeNames(int[] keycodes) {
    int i = 0;
    String[] keycodeNames = new String[keycodes.length];
    for (int keycode : keycodes) {
      if (keycode == MappedKey.NOT_MAPPED) {
        keycodeNames[i++] = "null(0)";
      } else {
        keycodeNames[i++] = Input.Keys.toString(keycode) + "(" + keycode + ")";
      }
    }

    return keycodeNames;
  }

  @Override
  public void onAssigned(MappedKey key, int assignment, int keycode) {
    super.onAssigned(key, assignment, keycode);
    if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG) {
      Gdx.app.debug(TAG, String.format("assigned [%s] to [%s]", Input.Keys.toString(keycode), key.getAlias()));
    }
  }

  @Override
  public void onUnassigned(MappedKey key, int assignment, int keycode) {
    super.onUnassigned(key, assignment, keycode);
    if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG) {
      Gdx.app.debug(TAG, String.format("unassigned [%s] from [%s]", Input.Keys.toString(keycode), key.getAlias()));
    }
  }

  public com.badlogic.gdx.InputProcessor newInputProcessor() {
    return new InputProcessor();
  }

  class InputProcessor extends InputAdapter {
    InputProcessor() {
      super();
    }

    @Override
    public boolean keyDown(int keycode) {
      if (captureMode) return false;
      ObjectSet<MappedKey> keys = lookup(keycode);
      if (keys != null) {
        for (MappedKey key : keys) {
          setPressed(key, keycode, true);
        }
      }

      return false;
    }

    @Override
    public boolean keyUp(int keycode) {
      if (captureMode) return false;
      ObjectSet<MappedKey> keys = lookup(keycode);
      if (keys != null) {
        for (MappedKey key : keys) {
          setPressed(key, keycode, false);
        }
      }

      return false;
    }
  }
}
