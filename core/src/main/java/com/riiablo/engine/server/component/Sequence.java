package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.server.NativeJabSequence;

@Transient
@PooledWeaver
public class Sequence extends Component {
  public byte mode1;
  public byte mode2;
  /** New actions must restart their animation even when mode1 equals the current mode. */
  public boolean started;

  /** Native player Jab sequence state.  Other sequences leave these fields disabled. */
  public boolean nativeJab;
  public byte nativeJabWeaponClass;
  public int nativeJabFrame;
  public int nativeJabFrameCount;
  public int nativeJabSpeed;

  public Sequence sequence(byte mode1, byte mode2) {
    this.mode1 = mode1;
    this.mode2 = mode2;
    this.started = false;
    nativeJab = false;
    nativeJabWeaponClass = 0;
    nativeJabFrame = 0;
    nativeJabFrameCount = 0;
    nativeJabSpeed = 256;
    return this;
  }

  public Sequence nativeJab(byte weaponClass) {
    nativeJab = true;
    nativeJabWeaponClass = weaponClass;
    nativeJabFrame = 0;
    nativeJabFrameCount = NativeJabSequence.frameCount(weaponClass);
    nativeJabSpeed = 256;
    return this;
  }
}
