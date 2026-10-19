package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.codec.Index;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.codec.excel.States;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Reconciles synchronized server states with client-side overlays.
 *
 * <p>The server remains authoritative: this system never creates a state and
 * only renders the state ids received in {@code StateP}. Native States.txt
 * overlay rows are reconciled here so reconnects and late entity snapshots
 * automatically restore presentation independently of damage events.</p>
 */
@All(UnitStates.class)
public class StateOverlaySystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(StateOverlaySystem.class);

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Monster> mMonster;
  @Wire protected OverlayManager overlays;
  @Wire protected CofManager cofs;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();

  private final IntSet missingOverlayLogged = new IntSet();
  private final IntSet venomTransformActive = new IntSet();
  private final IntMap<byte[]> venomOriginalTransforms = new IntMap<>();
  private final IntSet coldTransformActive = new IntSet();
  private final IntMap<IntSet> overlayStates = new IntMap<>();

  /** Native States.txt owns curse/buff visuals; damage is not required to display a state. */
  private void reconcileStateOverlays(int entity, UnitStates states) {
    IntSet previous = overlayStates.get(entity);
    if (previous == null) { previous = new IntSet(); overlayStates.put(entity, previous); }
    IntSet active = new IntSet();
    for (UnitState state : states.stateList.getStates()) {
      if (state.expired) continue;
      int id = state.stateId;
      active.add(id);
      States.Entry definition = clientContext.files() == null || clientContext.files().States == null
          ? null : clientContext.files().States.get(id);
      boolean mapped = false;
      if (definition != null) {
        for (String overlay : definition.overlays) {
          if (overlay == null || overlay.isEmpty()) continue;
          overlays.setPersistent(entity, id, overlay);
          mapped = true;
        }
        if (!previous.contains(id) && definition.castOverlay != null && !definition.castOverlay.isEmpty())
          overlays.set(entity, definition.castOverlay);
      }
      if (!mapped) {
        String fallback = resolveOverlayId(id);
        if (fallback != null) overlays.setPersistent(entity, id, fallback);
      }
    }
    for (IntSet.IntSetIterator it = previous.iterator(); it.hasNext;) {
      int id = it.next();
      if (active.contains(id)) continue;
      overlays.clearPersistent(entity, id);
      States.Entry definition = clientContext.files() == null || clientContext.files().States == null
          ? null : clientContext.files().States.get(id);
      if (definition != null && definition.removeOverlay != null && !definition.removeOverlay.isEmpty())
        overlays.set(entity, definition.removeOverlay);
    }
    overlayStates.put(entity, active);
  }

  @Override
  protected void process(int entityId) {
    UnitStates states = mUnitStates.get(entityId);
    if (states == null || states.stateList == null) return;

    reconcileStateOverlays(entityId, states);
    reconcileVenomTransform(entityId,
        states.stateList.getState(StateId.VENOMCLAWS));
    reconcileColdTransform(entityId, elementalColorShift(states));
  }

  /** Native elemental presentation: COLD/FREEZE blue or POISON green. */
  private void reconcileColdTransform(int entityId, int colorShift) {
    boolean active = colorShift >= 0;
    if (!active) {
      if (!coldTransformActive.remove(entityId)) return;
      byte[] baseTransforms = mCofTransforms.has(entityId)
          ? mCofTransforms.get(entityId).transform : null;
      restoreAnimationTransforms(entityId, baseTransforms);
      return;
    }

    // State colors are PL2 hue variations, not packed item transforms.
    // Writing cblu into CofTransforms dispatches a TransformChangeEvent that
    // can run after this system and replace the PL2 state tint with the
    // unit's ordinary component colors.  Leave the authoritative component
    // transforms untouched and apply the state palette only to rendered
    // animation layers.
    coldTransformActive.add(entityId);
    applyAnimationTransform(entityId, colorShift);
  }

  private int elementalColorShift(UnitStates states) {
    boolean cold = states.stateList.hasState(StateId.COLD);
    boolean freezeActive = states.stateList.hasState(StateId.FREEZE);
    boolean poison = states.stateList.hasState(StateId.POISON);
    if (!cold && !freezeActive && !poison) return -1;
    // The stock COLD/FREEZE rows carry blue=1 and colorshift=108.  The
    // renderer's HueVariations table is stored in the palette's BGR order;
    // row 100 therefore produces the orange-brown tint seen in-game, while
    // row 108 is purple.  Native unit blue state presentation uses the dark
    // blue HueVariations row 107.  Poison remains the native green row 104.
    int fallback = cold || freezeActive ? 107 : 104;
    if (cold || freezeActive || clientContext.files() == null || clientContext.files().States == null) {
      return fallback;
    }
    States.Entry best = null;
    if (poison) {
      States.Entry poisonEntry = clientContext.files().States.get("poison");
      if (poisonEntry != null && (best == null || poisonEntry.colorPriority > best.colorPriority)) {
        best = poisonEntry;
      }
    }
    return best == null ? fallback : best.colorShift;
  }

  /** Applies the native PL2 hue row directly to loaded animation layers. */
  private void applyAnimationTransform(int entityId, int colorShift) {
    if (mAnimationWrapper == null || !mAnimationWrapper.has(entityId)
        || Riiablo.colormaps == null) return;
    Index transform = Riiablo.colormaps.getStateTransform(colorShift);
    if (transform == null) return;
    Animation animation = mAnimationWrapper.get(entityId).animation;
    COF cof = animation.getCOF();
    if (cof == null) return;
    for (int i = 0; i < cof.getNumLayers(); i++) {
      int component = cof.getLayer(i).component;
      Animation.Layer layer = animation.getLayer(component);
      if (layer != null) layer.setTransform(transform, 1);
    }
  }

  private void restoreAnimationTransforms(int entityId, byte[] original) {
    if (mAnimationWrapper == null || !mAnimationWrapper.has(entityId)) return;
    Animation animation = mAnimationWrapper.get(entityId).animation;
    COF cof = animation.getCOF();
    if (cof == null) return;
    for (int i = 0; i < cof.getNumLayers(); i++) {
      int component = cof.getLayer(i).component;
      Animation.Layer layer = animation.getLayer(component);
      if (layer != null) {
        byte transform = original != null && component < original.length
            ? original[component] : CofTransforms.TRANSFORM_NULL;
        MonsterTransform.apply(layer,
            mMonster.has(entityId) ? mMonster.get(entityId) : null, transform);
      }
    }
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

    byte venomTransform = venomPackedTransform(clientContext);
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
    return venomPackedTransform(ClientGameplayContext.fromGlobals());
  }

  static byte venomPackedTransform(ClientGameplayContext context) {
    if (context == null || context.files() == null || context.files().colors == null) {
      return CofTransforms.TRANSFORM_NULL;
    }
    int color = context.files().colors.index("cgrn") + 1;
    return color > 0 && color < 32
        ? (byte) color : CofTransforms.TRANSFORM_NULL;
  }

  @Override
  protected void removed(int entityId) {
    IntSet previous = overlayStates.remove(entityId);
    if (previous != null && overlays != null) {
      for (IntSet.IntSetIterator it = previous.iterator(); it.hasNext;)
        overlays.clearPersistent(entityId, it.next());
    }
    venomTransformActive.remove(entityId);
    venomOriginalTransforms.remove(entityId);
    coldTransformActive.remove(entityId);
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
      case StateId.SLOWMISSILES:
        candidates = new String[] {"innersight"};
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
      case StateId.VALKYRIE:
        // The retail filename is misspelled "ValkarieGlow".  Keep the
        // correctly-spelled aliases for data packs that fixed the typo.
        candidates = new String[] {"ValkarieGlow", "ValkyrieGlow", "valkyrieglow"};
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
      if (clientContext.files() == null || clientContext.files().Overlay == null) return null;
      if (clientContext.files().Overlay.get(candidate) != null) return candidate;
      // Overlay.txt keys are case-sensitive in the Java index, while MPQ
      // filenames and some 1.10 table variants are not.  Resolve aliases by
      // key or filename before giving up on a valid native overlay.
      for (com.riiablo.codec.excel.Overlay.Entry row : clientContext.files().Overlay) {
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
