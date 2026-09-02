package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Reconciles synchronized server states with client-side overlays.
 *
 * <p>The server remains authoritative: this system never creates a state and
 * only renders the state ids received in {@code StateP}.  A small mapping is
 * kept here instead of in the network codec so reconnects and late entity
 * snapshots automatically restore the presentation.</p>
 */
@All(UnitStates.class)
public class StateOverlaySystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(StateOverlaySystem.class);

  protected ComponentMapper<UnitStates> mUnitStates;
  @Wire protected OverlayManager overlays;

  private final IntSet missingOverlayLogged = new IntSet();

  @Override
  protected void process(int entityId) {
    UnitStates states = mUnitStates.get(entityId);
    if (states == null || states.stateList == null) return;

    reconcile(entityId, StateId.DIMVISION, states.stateList.getState(StateId.DIMVISION));
  }

  private void reconcile(int entityId, int stateId, UnitState state) {
    String overlayId = resolveOverlayId(stateId);
    if (overlayId == null) return;
    if (state != null) {
      overlays.setPersistent(entityId, stateId, overlayId);
      log.debug("[STATE_PRESENTATION] entity={} state={} duration={} level={} overlay={}",
          entityId, StateId.getName(stateId), state.duration, state.level, overlayId);
    } else {
      overlays.clearPersistent(entityId, stateId);
    }
  }

  /** Resolve only overlays present in the loaded native table. */
  private String resolveOverlayId(int stateId) {
    String[] candidates;
    switch (stateId) {
      case StateId.DIMVISION:
        candidates = new String[] {"dimvision", "dimvisionoverlay", "curse"};
        break;
      default:
        return null;
    }
    for (String candidate : candidates) {
      if (Riiablo.files.Overlay.get(candidate) != null) return candidate;
    }
    if (!missingOverlayLogged.contains(stateId)) {
      missingOverlayLogged.add(stateId);
      log.warn("[STATE_PRESENTATION] state={} result=no_overlay_mapping candidates={}",
          StateId.getName(stateId), java.util.Arrays.toString(candidates));
    }
    return null;
  }
}
