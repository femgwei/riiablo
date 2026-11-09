package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;

/** Separate visual RNG and elapsed time for an authoritative carrier or storm state. */
@Transient
public class ReferenceElementalEmitter extends Component {
  public float age, next;
  public int seed = 1;
  public boolean initialized, hurricane;
  /** Reference Firestorm's three slightly diverging flame streams. */
  public float firestormLaneAngle;
  /** Last boulder ground-fire phase, used to avoid visually locked cells. */
  public int lastBoulderFirePhase = -1;
}
