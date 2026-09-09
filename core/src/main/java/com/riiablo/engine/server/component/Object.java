package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.codec.excel.Objects;

@Transient
@PooledWeaver
public class Object extends Component {
  public static final int STATE_OPENED = 1 << 0;
  public static final int STATE_ACTIVATED = 1 << 1;
  public static final int STATE_INTERACTABLE = 1 << 2;

  public Objects.Entry base;
  /** Authoritative mode mirrored into ObjectP for reconnecting clients. */
  public byte mode;
  /** Bit set composed from {@link #STATE_OPENED}, {@link #STATE_ACTIVATED}, and
   * {@link #STATE_INTERACTABLE}. */
  public byte stateFlags;
}
