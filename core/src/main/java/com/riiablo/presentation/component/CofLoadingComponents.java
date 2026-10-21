package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.riiablo.engine.Dirty;

@PooledWeaver
public class CofLoadingComponents extends Component {
  public int flags = Dirty.NONE;
}
