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
    reconcile(entityId, StateId.CONVERSION,
        states.stateList.getState(StateId.CONVERSION));
    reconcile(entityId, StateId.FROZENARMOR,
        states.stateList.getState(StateId.FROZENARMOR));
    reconcile(entityId, StateId.SHIVERARMOR,
        states.stateList.getState(StateId.SHIVERARMOR));
    reconcile(entityId, StateId.CHILLINGARMOR,
        states.stateList.getState(StateId.CHILLINGARMOR));
    // D2's shrine buffs are native timed states with the same overhead
    // presentation used by the original client.  Keep these in the client
    // reconciliation path so StateP snapshots and local games behave alike.
    reconcile(entityId, StateId.SHRINE_ARMOR,
        states.stateList.getState(StateId.SHRINE_ARMOR));
    reconcile(entityId, StateId.SHRINE_COMBAT,
        states.stateList.getState(StateId.SHRINE_COMBAT));
    reconcile(entityId, StateId.SHRINE_RESIST_LIGHTNING,
        states.stateList.getState(StateId.SHRINE_RESIST_LIGHTNING));
    reconcile(entityId, StateId.SHRINE_RESIST_FIRE,
        states.stateList.getState(StateId.SHRINE_RESIST_FIRE));
    reconcile(entityId, StateId.SHRINE_RESIST_COLD,
        states.stateList.getState(StateId.SHRINE_RESIST_COLD));
    reconcile(entityId, StateId.SHRINE_RESIST_POISON,
        states.stateList.getState(StateId.SHRINE_RESIST_POISON));
    reconcile(entityId, StateId.SHRINE_SKILL,
        states.stateList.getState(StateId.SHRINE_SKILL));
    reconcile(entityId, StateId.SHRINE_MANA_REGEN,
        states.stateList.getState(StateId.SHRINE_MANA_REGEN));
    reconcile(entityId, StateId.SHRINE_STAMINA,
        states.stateList.getState(StateId.SHRINE_STAMINA));
    reconcile(entityId, StateId.SHRINE_EXPERIENCE,
        states.stateList.getState(StateId.SHRINE_EXPERIENCE));
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
        // Overlay.txt's native key is "cursedimvision"; its Filename is
        // CurseDimVisionEffect. Keep filename/mod aliases after the native
        // key because Excel lookups are case-sensitive.
        candidates = new String[] {
            "cursedimvision", "CurseDimVisionEffect", "CurseDimVision",
            "dimvision", "dimvisionoverlay", "curse"
        };
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
      case StateId.CONVERSION:
        // The native row is "conversionaura" and points at Conversion.dcc.
        // Keep filename/lower-case aliases as compatibility fallbacks.
        candidates = new String[] {"conversionaura", "Conversion", "conversion"};
        break;
      case StateId.FROZENARMOR:
        candidates = new String[] {"frozenarmor"};
        break;
      case StateId.SHIVERARMOR:
        candidates = new String[] {"shiverarmor"};
        break;
      case StateId.CHILLINGARMOR:
        candidates = new String[] {"chillarmor", "chillingarmor"};
        break;
      case StateId.SHRINE_ARMOR:
        candidates = new String[] {"shrine_armor"};
        break;
      case StateId.SHRINE_COMBAT:
        candidates = new String[] {"shrine_combat"};
        break;
      case StateId.SHRINE_RESIST_LIGHTNING:
        candidates = new String[] {"shrine_resist_lightning"};
        break;
      case StateId.SHRINE_RESIST_FIRE:
        candidates = new String[] {"shrine_resist_fire"};
        break;
      case StateId.SHRINE_RESIST_COLD:
        candidates = new String[] {"shrine_resist_cold"};
        break;
      case StateId.SHRINE_RESIST_POISON:
        candidates = new String[] {"shrine_resist_poison"};
        break;
      case StateId.SHRINE_SKILL:
        candidates = new String[] {"shrine_skill"};
        break;
      case StateId.SHRINE_MANA_REGEN:
        candidates = new String[] {"shrine_mana_regen"};
        break;
      case StateId.SHRINE_STAMINA:
        candidates = new String[] {"shrine_stamina"};
        break;
      case StateId.SHRINE_EXPERIENCE:
        candidates = new String[] {"shrine_experience"};
        break;
      default:
        return null;
    }
    for (String candidate : candidates) {
      if (Riiablo.files.Overlay.get(candidate) != null) return candidate;
      // Overlay.txt keys are case-sensitive in the Java index, while MPQ
      // filenames and some 1.10 table variants are not.  Resolve aliases by
      // key or filename before giving up on a valid native overlay.
      for (com.riiablo.codec.excel.Overlay.Entry row : Riiablo.files.Overlay) {
        if (row == null) continue;
        if ((row.overlay != null && row.overlay.equalsIgnoreCase(candidate))
            || (row.Filename != null && row.Filename.equalsIgnoreCase(candidate))) {
          return row.overlay;
        }
      }
      // The stock shrine DCCs are present in the MPQ even in a few trimmed
      // Overlay.txt packs. OverlayManager supplies the canonical row in that
      // case, so do not suppress the request here.
      if (candidate.regionMatches(true, 0, "shrine_", 0, 7)) return candidate;
    }
    if (!missingOverlayLogged.contains(stateId)) {
      missingOverlayLogged.add(stateId);
      log.warn("[STATE_PRESENTATION] state={} result=no_overlay_mapping candidates={}",
          StateId.getName(stateId), java.util.Arrays.toString(candidates));
    }
    return null;
  }
}
