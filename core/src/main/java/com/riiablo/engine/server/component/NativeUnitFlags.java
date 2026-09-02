package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/**
 * Authoritative subset of D2Common's {@code D2UnitStrc::dwFlags}.
 *
 * <p>These values intentionally match {@code D2C_UnitFlags}; they must not be
 * mixed with {@code MonStats} flags or the server's monster-AI flags. Spawn
 * sites apply the native policy that belongs to that particular D2Game call
 * path because native summons do not all receive the same flags.</p>
 */
@Transient
@PooledWeaver
public class NativeUnitFlags extends Component {
  public static final int TARGETABLE = 0x00000002;
  public static final int CAN_BE_ATTACKED = 0x00000004;
  public static final int IS_VALID_TARGET = 0x00000008;
  public static final int IS_MERCENARY = 0x00000200;
  public static final int NO_TREASURE_CLASS = 0x00020000;
  public static final int IS_INITIALIZED = 0x01000000;
  public static final int IS_RESURRECT = 0x02000000;
  public static final int NO_EXPERIENCE = 0x04000000;
  public static final int IS_REVIVE = 0x80000000;

  public static final int MONSTER_TARGET = TARGETABLE | CAN_BE_ATTACKED | IS_VALID_TARGET;
  /** Final anti-farming state shared by native external and self resurrection. */
  public static final int NO_RESURRECTION_REWARD = NO_EXPERIENCE | NO_TREASURE_CLASS;
  /** D2Game {@code SKILLS_ResurrectUnit} restores target bits and suppresses rewards. */
  public static final int MONSTER_RESURRECTION = MONSTER_TARGET | NO_RESURRECTION_REWARD;
  /** SrvSt61 clears target bits again while the self-resurrection animation runs. */
  public static final int SELF_RESURRECTION = NO_RESURRECTION_REWARD;
  /** D2Game {@code MONSTERAI_InitializeHireling}. */
  public static final int MERCENARY = NO_RESURRECTION_REWARD | IS_MERCENARY;
  /** D2Game {@code SKILLS_SrvDo087_MaggotLay}. */
  public static final int MAGGOT_LAY_SUMMON = NO_EXPERIENCE;
  /** D2Game {@code SKILLS_SrvDo091_Nest_EvilHutSpawner}. */
  public static final int NEST_SUMMON = NO_EXPERIENCE | NO_TREASURE_CLASS;

  private int flags;

  public NativeUnitFlags reset() {
    flags = 0;
    return this;
  }

  public NativeUnitFlags set(int mask) {
    flags |= mask;
    return this;
  }

  public NativeUnitFlags clear(int mask) {
    flags &= ~mask;
    return this;
  }

  public boolean has(int mask) {
    return (flags & mask) == mask;
  }

  public int flags() {
    return flags;
  }

  public NativeUnitFlags markMonsterResurrection() {
    return set(MONSTER_RESURRECTION);
  }

  public NativeUnitFlags markSelfResurrection() {
    return set(MONSTER_RESURRECTION).clear(MONSTER_TARGET);
  }

  public NativeUnitFlags markMercenary() {
    return set(MERCENARY);
  }

  /** Preset/persisted resurrection marker; not used by ordinary skill resurrection. */
  public NativeUnitFlags markPresetResurrect() {
    return set(IS_RESURRECT);
  }
}
