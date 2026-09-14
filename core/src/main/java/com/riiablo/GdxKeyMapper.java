package com.riiablo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.ObjectSet;
import com.riiablo.key.MappedKey;
import com.riiablo.key.SaveableKeyMapper;
import com.riiablo.serializer.IntArrayStringSerializer;
import com.riiablo.serializer.SerializeException;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

public class GdxKeyMapper extends SaveableKeyMapper {
  private static final String TAG = "GdxKeyMapper";

  private final Preferences PREFERENCES = Gdx.app.getPreferences(TAG);
  private final Trie<String, MappedKey> KEYS = new PatriciaTrie<>();
  private final Map<String, int[]> DEFAULTS = new HashMap<>();
  private boolean captureMode;

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
