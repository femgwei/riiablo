package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

@Transient
@PooledWeaver
public class Sequence extends Component {
  public byte mode1;
  public byte mode2;
  /** New actions must restart their animation even when mode1 equals the current mode. */
  public boolean started;

  public Sequence sequence(byte mode1, byte mode2) {
    this.mode1 = mode1;
    this.mode2 = mode2;
    this.started = false;
    return this;
  }
}
