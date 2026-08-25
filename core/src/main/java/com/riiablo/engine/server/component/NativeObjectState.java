package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.map.NativePresetObjectResolver;

/** Runtime state and D2MOO provenance for a native preset object. */
@Transient
@PooledWeaver
public class NativeObjectState extends Component {
  public int presetIndex;
  public int originalClassId;
  public int currentClassId;
  public byte initialMode;
  public boolean ds1Raw;
  public boolean spawned;
  public boolean opened;
  public boolean activated;
  public NativePresetObjectResolver.Kind kind = NativePresetObjectResolver.Kind.ORDINARY;

  public NativeObjectState set(int presetIndex, int originalClassId, int currentClassId,
      int mode, boolean ds1Raw, boolean spawned,
      NativePresetObjectResolver.Kind kind) {
    this.presetIndex = presetIndex;
    this.originalClassId = originalClassId;
    this.currentClassId = currentClassId;
    this.initialMode = (byte) mode;
    this.ds1Raw = ds1Raw;
    this.spawned = spawned;
    this.opened = false;
    this.activated = false;
    this.kind = kind == null ? NativePresetObjectResolver.Kind.ORDINARY : kind;
    return this;
  }
}
