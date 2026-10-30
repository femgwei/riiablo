package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;

/** Read-only diagnostics for requests that may remove Pathfind on rejection.
 * Never used by AI, path continuation or target selection. */
@Transient
public final class NativeTowardRequestTrace extends Component {
  public long revision;
  public int targetId = -1;
  public NativePathIdentity.Token targetIdentity;
  public boolean accepted;
}
