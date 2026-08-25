package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;

/** Runtime state and D2MOO provenance for a native preset object. */
@Transient
@PooledWeaver
public class NativeObjectState extends Component {
  public int presetIndex;
  public int originalClassId;
  public int currentClassId;
  public byte initialMode;
  public byte currentMode;
  public boolean ds1Raw;
  public boolean spawned;
  public boolean opened;
  public boolean activated;
  public NativePresetObjectResolver.Kind kind = NativePresetObjectResolver.Kind.ORDINARY;
  /** Map-owned record that survives destruction/recreation of the ECS entity. */
  public Map.NativeObject source;

  public NativeObjectState set(int presetIndex, int originalClassId, int currentClassId,
      int mode, boolean ds1Raw, boolean spawned,
      NativePresetObjectResolver.Kind kind) {
    return set(null, presetIndex, originalClassId, currentClassId, mode,
        ds1Raw, spawned, kind);
  }

  public NativeObjectState set(Map.NativeObject source, int presetIndex,
      int originalClassId, int currentClassId, int mode,
      boolean ds1Raw, boolean spawned, NativePresetObjectResolver.Kind kind) {
    this.presetIndex = presetIndex;
    this.originalClassId = originalClassId;
    this.currentClassId = currentClassId;
    this.source = source;
    this.initialMode = source == null ? (byte) mode : source.currentMode();
    this.currentMode = this.initialMode;
    this.ds1Raw = ds1Raw;
    this.spawned = spawned;
    this.opened = source != null && source.opened();
    this.activated = source != null && source.activated();
    this.kind = kind == null ? NativePresetObjectResolver.Kind.ORDINARY : kind;
    return this;
  }

  public void persistMode(byte mode) {
    currentMode = mode;
    if (source != null) source.persistMode(mode);
  }

  public void persistOpened(boolean opened) {
    this.opened = opened;
    if (source != null) source.persistOpened(opened);
  }

  public void persistActivated(boolean activated) {
    this.activated = activated;
    if (source != null) source.persistActivated(activated);
  }
}
