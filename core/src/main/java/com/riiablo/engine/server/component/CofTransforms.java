package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.riiablo.codec.COF;

import java.util.Arrays;

@PooledWeaver
public class CofTransforms extends PooledComponent {
  public static final byte TRANSFORM_NULL = -1;
  /**
   * Resolves a monster's MonStats.TransLvl to its native palshift.dat row.
   * The first two rows are reserved; TransLvl 0 therefore selects row 2.
   */
  public static byte monsterPaletteRow(int transLvl) {
    return transLvl >= 0 && transLvl <= 7
        ? (byte) (transLvl + 2)
        : TRANSFORM_NULL;
  }

  public static final byte[] DEFAULT_TRANSFORM;
  static {
    DEFAULT_TRANSFORM = new byte[COF.Component.NUM_COMPONENTS];
    Arrays.fill(DEFAULT_TRANSFORM, TRANSFORM_NULL);
  }

  public final byte[] transform = new byte[COF.Component.NUM_COMPONENTS]; {
    reset();
  }

  @Override
  protected void reset() {
    System.arraycopy(DEFAULT_TRANSFORM, 0, transform, 0, COF.Component.NUM_COMPONENTS);
  }
}
