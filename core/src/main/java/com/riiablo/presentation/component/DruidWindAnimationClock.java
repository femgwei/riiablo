package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;

/** Local replica animation time, independent of DCC loading and combat clocks. */
@Transient
public class DruidWindAnimationClock extends Component {
  public double time;
}
