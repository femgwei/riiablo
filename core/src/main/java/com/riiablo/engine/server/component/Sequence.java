package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.server.NativeJabSequence;

@Transient
@PooledWeaver
public class Sequence extends Component {
  public com.riiablo.engine.server.NativeMonsterSequence nativeMonster;
  /** Stable MonSeq identity; nativeMonster is a derived table object. */
  public String nativeMonsterName;
  public int nativeMonsterCursor;
  public int nativeMonsterWaitFrames;

  public byte mode1;
  public byte mode2;
  /** New actions must restart their animation even when mode1 equals the current mode. */
  public boolean started;

  /** MonSeq seq_mummyres: SC points 0..15, missile action on point 13. */
  public boolean nativeMummy;
  public int nativeMummyFrame;

  /** seq_deathmaulerdig: 35 A2 points, attack action on point 13. */
  public boolean nativeDeathMaul;
  public int nativeDeathMaulFrame;

  /** Reverse S1 MonSeq used by Tentacle/TentacleHead Submerge. */
  public boolean nativeSubmerge;
  public int nativeSubmergeLastFrame;
  public int nativeSubmergeFrame;
  public int nativeSubmergeLastPoint;
  public boolean nativeSubmergeHeld;
  /** seq_froghidden: S1 0,1,2 with no action points; mode-update SrvDo94 holds at 0. */
  public boolean nativeFrogHidden;
  /** seq_diablorun has RN source points and independent looping/countdown. */
  public boolean nativeDiabRun;

  /** Native player Jab sequence state.  Other sequences leave these fields disabled. */
  public boolean nativeJab;
  public byte nativeJabWeaponClass;
  public int nativeJabFrame;
  public int nativeJabFrameCount;
  public int nativeJabSpeed;

  public Sequence sequence(byte mode1, byte mode2) {
    nativeMonster = null;
    nativeMonsterName = null;
    nativeMonsterCursor = 0;
    nativeMonsterWaitFrames = 0;
    this.mode1 = mode1;
    this.mode2 = mode2;
    this.started = false;
    nativeMummy = false;
    nativeMummyFrame = 0;
    nativeDeathMaul = false;
    nativeDeathMaulFrame = 0;
    nativeSubmerge = false;
    nativeSubmergeLastFrame = 0;
    nativeSubmergeFrame = 0;
    nativeSubmergeLastPoint = -1;
    nativeSubmergeHeld = false;
    nativeFrogHidden = false;
    nativeDiabRun = false;
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
