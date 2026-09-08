package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.codec.excel.Objects;

@Transient
@PooledWeaver
public class Object extends Component {
  public Objects.Entry base;
  /** Authoritative mode mirrored into ObjectP for reconnecting clients. */
  public byte mode;
  /** bit 0 opened, bit 1 activated, bit 2 currently interactable. */
  public byte stateFlags;
}
