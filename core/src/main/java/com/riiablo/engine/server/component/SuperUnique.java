package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** Preserves the SuperUniques.txt identity lost when resolving its MonClass. */
@Transient
@PooledWeaver
public class SuperUnique extends Component {
  public int id = -1;
  public String key;

  public SuperUnique set(int id, String key) {
    this.id = id;
    this.key = key;
    return this;
  }
}
