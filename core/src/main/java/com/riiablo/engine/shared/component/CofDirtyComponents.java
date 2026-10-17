package com.riiablo.engine.shared.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.riiablo.engine.Dirty;

@PooledWeaver
public class CofDirtyComponents extends Component {
  public int flags = Dirty.NONE;
}
