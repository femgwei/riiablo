package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.codec.COF;

@Transient
@PooledWeaver
public class CofWrapper extends Component {
  public COF cof;
}
