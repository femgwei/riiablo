package com.riiablo.engine;

import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.VelocityModeChanger;

/**
 * Mode-specific assembly policy, not mutable session state or a new tick schedule.
 *
 * <p>These settings preserve the existing GameScreen/D2GS split. In particular,
 * remote clients still apply local run input, but do not own networked COF modes
 * or dynamic unit footprints. The local skill adapter retains its existing
 * division of work with client skill presentation.
 */
public enum SimulationMode {
  LOCAL(true, true, true),
  DEDICATED_SERVER(true, false, true),
  REMOTE_CLIENT(false, true, false);

  private final boolean authoritative;
  private final boolean applyLocalRunInput;
  private final boolean updateNetworkedModes;

  SimulationMode(boolean authoritative, boolean applyLocalRunInput, boolean updateNetworkedModes) {
    this.authoritative = authoritative;
    this.applyLocalRunInput = applyLocalRunInput;
    this.updateNetworkedModes = updateNetworkedModes;
  }

  /** Whether authority-only groups may be installed in this world. */
  public boolean isAuthoritative() {
    return authoritative;
  }

  /** Always returns a fresh, session-owned system, including the inert replica variant. */
  public DynamicUnitCollisionSystem createDynamicUnitCollisionSystem() {
    return new DynamicUnitCollisionSystem(authoritative);
  }

  public VelocityModeChanger createVelocityModeChanger() {
    return new VelocityModeChanger(applyLocalRunInput, updateNetworkedModes);
  }

  /** Must only be installed in an authoritative world, at the host's existing tick position. */
  public ServerSkillSystem createServerSkillSystem() {
    if (!authoritative) {
      throw new IllegalStateException("Remote clients must not install authoritative skill execution");
    }
    return new ServerSkillSystem(this == LOCAL);
  }
}
