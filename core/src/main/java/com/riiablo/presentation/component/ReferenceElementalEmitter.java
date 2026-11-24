package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;

/** Separate visual RNG and elapsed time for an authoritative carrier or storm state. */
@Transient
public class ReferenceElementalEmitter extends Component {
  public float age, next;
  public int seed = 1;
  public boolean initialized, hurricane;
  public com.d2moo.common.drlg.D2Seed hurricaneSeed;
  public double hurricaneTime;
  public int hurricaneFrame;
  /** Reference Firestorm's three slightly diverging flame streams. */
  public float firestormLaneAngle;
  public com.d2moo.common.drlg.D2Seed boulderSeed;
  public int boulderFrame, boulderCellX, boulderCellY;
  public double boulderTime;
  public com.d2moo.common.drlg.D2Seed volcanoSeed;
  public int volcanoFrame;
}
