package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofTransforms;
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
  protected ComponentMapper<CofTransforms> mCofTransforms;
  @Wire protected OverlayManager overlays;
  @Wire protected CofManager cofs;

  private final IntSet missingOverlayLogged = new IntSet();
  private final IntSet venomTransformActive = new IntSet();
  private final IntMap<byte[]> venomOriginalTransforms = new IntMap<>();

  @Override
  protected void process(int entityId) {
    UnitStates states = mUnitStates.get(entityId);
    if (states == null || states.stateList == null) return;

    reconcile(entityId, StateId.DIMVISION, states.stateList.getState(StateId.DIMVISION));
    reconcile(entityId, StateId.BLADESHIELD,
        states.stateList.getState(StateId.BLADESHIELD));
    // Barbarian states use the native States.txt overlay records.  The
    // server remains authoritative for state lifetime; this client system
    // only restores the corresponding visual from StateP snapshots.
    reconcile(entityId, StateId.FRENZY, states.stateList.getState(StateId.FRENZY));
    reconcile(entityId, StateId.BERSERK, states.stateList.getState(StateId.BERSERK));
    reconcile(entityId, StateId.BATTLEORDERS,
        states.stateList.getState(StateId.BATTLEORDERS));
    reconcile(entityId, StateId.BATTLECOMMAND,
        states.stateList.getState(StateId.BATTLECOMMAND));
    reconcile(entityId, StateId.SHOUT, states.stateList.getState(StateId.SHOUT));
    reconcile(entityId, StateId.BATTLECRY, states.stateList.getState(StateId.BATTLECRY));
    reconcileVenomTransform(entityId,
        states.stateList.getState(StateId.VENOMCLAWS));
  }

  /** States.txt venomclaws itemtrans=cgrn, applied to both weapon layers. */
  private void reconcileVenomTransform(int entityId, UnitState venom) {
    if (!mCofTransforms.has(entityId)) return;
    CofTransforms transforms = mCofTransforms.get(entityId);
    if (venom == null) {
      if (!venomTransformActive.remove(entityId)) return;
      byte[] original = venomOriginalTransforms.remove(entityId);
      if (original == null) return;
      int flags = Dirty.NONE;
      flags |= cofs.setTransform(entityId, COF.Component.RH, original[0]);
      flags |= cofs.setTransform(entityId, COF.Component.LH, original[1]);
      cofs.updateTransform(entityId, flags);
      return;
    }

    byte venomTransform = venomPackedTransform();
    if (venomTransform == CofTransforms.TRANSFORM_NULL) return;
    byte[] original = venomOriginalTransforms.get(entityId);
    if (original == null) {
      original = new byte[] {
          transforms.transform[COF.Component.RH],
          transforms.transform[COF.Component.LH]
      };
      venomOriginalTransforms.put(entityId, original);
    } else {
      // An equipment update may legitimately replace a hand transform while
      // Venom is active. Preserve that new base color before reapplying cgrn.
      if (transforms.transform[COF.Component.RH] != venomTransform) {
        original[0] = transforms.transform[COF.Component.RH];
      }
      if (transforms.transform[COF.Component.LH] != venomTransform) {
        original[1] = transforms.transform[COF.Component.LH];
      }
    }
    venomTransformActive.add(entityId);
    int flags = Dirty.NONE;
    flags |= cofs.setTransform(entityId, COF.Component.RH, venomTransform);
    flags |= cofs.setTransform(entityId, COF.Component.LH, venomTransform);
    cofs.updateTransform(entityId, flags);
  }

  static byte venomPackedTransform() {
    if (Riiablo.files == null || Riiablo.files.colors == null) {
      return CofTransforms.TRANSFORM_NULL;
    }
    int color = Riiablo.files.colors.index("cgrn") + 1;
    return color > 0 && color < 32
        ? (byte) color : CofTransforms.TRANSFORM_NULL;
  }

  @Override
  protected void removed(int entityId) {
    venomTransformActive.remove(entityId);
    venomOriginalTransforms.remove(entityId);
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
      case StateId.BLADESHIELD:
        candidates = new String[] {"bladeshield"};
        break;
      case StateId.FRENZY:
        candidates = new String[] {"frenzy"};
        break;
      case StateId.BERSERK:
        candidates = new String[] {"berserkfront", "berserkback"};
        break;
      case StateId.BATTLEORDERS:
        candidates = new String[] {"battleorders"};
        break;
      case StateId.BATTLECOMMAND:
        candidates = new String[] {"battlecommand"};
        break;
      case StateId.SHOUT:
        candidates = new String[] {"shout"};
        break;
      case StateId.BATTLECRY:
        candidates = new String[] {"battlecry"};
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
