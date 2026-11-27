package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC;
import com.riiablo.codec.excel.Overlay;
import com.riiablo.graphics.BlendMode;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

/** Owns independent state and impact layers, including each layer's asset lifetime. */
@All(com.riiablo.presentation.component.Overlay.class)
public class OverlayManager extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(OverlayManager.class);
  static final int SHRINE_ICON_STATE = Integer.MIN_VALUE + 0x5249;
  protected ComponentMapper<com.riiablo.presentation.component.Overlay> mOverlay;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;
  private AssetManager assets;
  private long nextOverlayOrder;
  private final IntMap<Array<OwnedOverlay>> owned = new IntMap<>();
  private boolean disposed;

  private static final class OwnedOverlay {
    final com.riiablo.presentation.component.Overlay root, component;
    final AssetDescriptor descriptor;
    final AssetReferenceScope references;
    final long order;
    OwnedOverlay(com.riiablo.presentation.component.Overlay root,
        com.riiablo.presentation.component.Overlay component, AssetReferenceScope references, long order) {
      this.root = root;
      this.component = component;
      descriptor = component.assetDescriptor;
      this.references = references;
      this.order = order;
    }
  }

  @Override protected void initialize() { assets = clientContext.assets(); }

  @Override protected void begin() {
    if (disposed) return;
    // Covers removal before first insertion, entity reuse and external replacement.
    int[] entities = owned.keys().toArray().toArray();
    try (Disposal cleanup = new Disposal()) {
      for (int entity : entities) {
        Array<OwnedOverlay> effects = owned.get(entity);
        for (int i = effects.size - 1; i >= 0; i--) {
          OwnedOverlay effect = effects.get(i);
          if (!isCurrent(entity, effect)) cleanup.run(() -> retire(entity, effect));
        }
      }
    }
  }

  private boolean isCurrent(int entity, OwnedOverlay effect) {
    return subscription.getActiveEntityIds().get(entity) && mOverlay.has(entity)
        && mOverlay.get(entity) == effect.root
        && (effect.component == effect.root || effect.root.layers.contains(effect.component, true))
        && effect.component.assetDescriptor == effect.descriptor;
  }

  @Override protected void process(int entity) {
    if (disposed || assets == null) return;
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) return;
    for (int i = effects.size - 1; i >= 0; i--) {
      OwnedOverlay effect = effects.get(i);
      com.riiablo.presentation.component.Overlay overlay = effect.component;
      boolean arctic = NativeArcticCastOverlay.handles(overlay);
      if (arctic && !NativeArcticCastOverlay.advance(overlay, world.delta)) {
        retire(entity, effect);
        continue;
      }
      if (!assets.isLoaded(effect.descriptor)) continue;
      Animation animation = overlay.animation;
      boolean loadedBefore = overlay.isLoaded;
      if (!loadedBefore) loadAnimation(overlay);
      if (arctic) {
        NativeArcticCastOverlay.apply(overlay);
        continue; // No asset-dependent minimum-display extension of the native ONCE lifetime.
      }
      if (NativeBoneArmorPresentation.handles(overlay)) {
        if (!NativeBoneArmorPresentation.advance(overlay, world.delta)) {
          int group = overlay.boneArmorGroup;
          boolean emerge = "bonearmor_cast".equalsIgnoreCase(overlay.entry.overlay);
          retire(entity, effect);
          com.riiablo.codec.excel.States.Entry state = clientContext.files().States.get(
              com.riiablo.engine.server.state.StateId.BONEARMOR);
          if (emerge) {
            continueBoneArmorStage(entity, group, state.overlays[0]);
            continueBoneArmorStage(entity, group, state.overlays[1]);
          } else continueBoneArmorStage(entity, group, state.castOverlay);
        }
        continue;
      }
      if (!overlay.persistent && loadedBefore && overlay.minimumDisplayFrames > 0)
        overlay.minimumDisplayFrames--;
      if (!overlay.persistent && loadedBefore && overlay.minimumDisplayFrames <= 0
          && animation.getFrame() >= animation.getNumFramesPerDir() - 1) retire(entity, effect);
    }
    removeEmptyComponent(entity);
  }

  public void set(int entity, String overlayId) {
    ensureOpen();
    Overlay.Entry entry = resolveEntry(overlayId);
    if (entry == null) {
      log.warn("[OVERLAY] entity={} id={} result=missing", entity, overlayId);
      return;
    }
    install(entity, entry, false, -1);
  }

  @net.mostlyoriginal.api.event.common.Subscribe
  public void onUnitOverlay(com.riiablo.engine.server.event.UnitOverlayEvent event) {
    if (event == null || event.entityId < 0 || event.overlay == null
        || event.overlay.isEmpty() || !world.getEntityManager().isActive(event.entityId)) return;
    set(event.entityId, event.overlay);
  }

  public void setPersistent(int entity, int stateId, String overlayId) {
    ensureOpen();
    if (overlayId == null || overlayId.isEmpty()) return;
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects != null) for (OwnedOverlay effect : effects) {
      com.riiablo.presentation.component.Overlay current = effect.component;
      if (current.persistent && current.stateId == stateId && current.entry != null
          && same(overlayId, current.entry.overlay)
          && mOverlay.get(entity) == effect.root && current.assetDescriptor == effect.descriptor)
        return;
    }
    Overlay.Entry entry = resolveEntry(overlayId);
    if (entry != null) install(entity, entry, true, stateId);
  }

  /** Replaces a state's selected layers while retaining matching animation clocks. */
  public void reconcilePersistent(int entity, int stateId, String... overlayIds) {
    ensureOpen();
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects != null) for (int i = effects.size - 1; i >= 0; i--) {
      OwnedOverlay effect = effects.get(i);
      com.riiablo.presentation.component.Overlay current = effect.component;
      if (!current.persistent || current.stateId != stateId) continue;
      boolean retain = false;
      if (overlayIds != null && current.entry != null) for (String name : overlayIds) {
        if (same(current.entry.overlay, name)) { retain = true; break; }
      }
      if (!retain) retire(entity, effect);
    }
    if (overlayIds != null) for (String name : overlayIds) setPersistent(entity, stateId, name);
    removeEmptyComponent(entity);
  }

  /** Includes pending asset loads: a chained state overlay waits for retirement. */
  public boolean isOneShotActive(int entity, String overlayId) {
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null || overlayId == null || overlayId.isEmpty()) return false;
    for (OwnedOverlay effect : effects) {
      com.riiablo.presentation.component.Overlay overlay = effect.component;
      if (!overlay.persistent && overlay.entry != null
          && same(overlayId, overlay.entry.overlay) && isCurrent(entity, effect)) return true;
    }
    return false;
  }

  /** Clears only this state's layers; unrelated buffs and impact flashes survive. */
  public void clearPersistent(int entity, int stateId) {
    if (disposed) return;
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) return;
    for (int i = effects.size - 1; i >= 0; i--) {
      OwnedOverlay effect = effects.get(i);
      if (effect.component.persistent && effect.component.stateId == stateId) retire(entity, effect);
    }
    removeEmptyComponent(entity);
  }

  void setBoneArmorStage(int entity, int group, String name) {
    ensureOpen();
    Overlay.Entry entry = resolveEntry(name);
    if (entry != null) install(entity, entry, true,
        com.riiablo.engine.server.state.StateId.BONEARMOR, group);
  }

  private void continueBoneArmorStage(int entity, int group, String name) {
    setBoneArmorStage(entity, group, name);
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) return;
    for (OwnedOverlay effect : effects) {
      com.riiablo.presentation.component.Overlay layer = effect.component;
      if (layer.boneArmorGroup != group || layer.entry == null || !same(layer.entry.overlay, name)) continue;
      // Retail restarts its linked-list scan after replacement, advancing the
      // new stage in the same native tick. Original three-pair draws preserve
      // offsets 0,8,15; holding a birth cel here instead produces 0,8,16.
      layer.nativeClockTime = com.riiablo.engine.SimulationClock.STEP_SECONDS;
      layer.nativeClockStarted = false;
      if (layer.isLoaded) NativeBoneArmorPresentation.advance(layer, 0);
    }
  }

  private void loadAnimation(com.riiablo.presentation.component.Overlay overlay) {
    Animation animation = overlay.animation;
    DC dc = assets.get(overlay.assetDescriptor);
    animation.edit().layer(dc, overlay.usesNativeRendering()
        ? AuraRenderPolicy.blendMode(overlay.entry.Trans)
        : overlay.entry.Trans == 3 ? BlendMode.LUMINOSITY : BlendMode.ID).build();
    if (overlay.usesNativeRendering()) {
      animation.setFrameDuration(AuraRenderPolicy.frameDuration(overlay.entry.AnimRate));
      animation.getLayer(0).setAlpha(AuraRenderPolicy.alpha(overlay.entry.Trans));
    } else if (overlay.entry.AnimRate > 0) animation.setFrameDuration(1f / overlay.entry.AnimRate);
    animation.setMode(overlay.persistent ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
    overlay.isLoaded = true;
  }

  /** Retail RemoveOverlay also matches mode4 emergence's front/back continuations. */
  void removeBoneArmorPair(int entity) {
    for (String name : new String[] {"bonearmor_front", "bonearmor_back"}) {
      Array<OwnedOverlay> effects = owned.get(entity);
      OwnedOverlay newest = null;
      if (effects != null) for (OwnedOverlay effect : effects) {
        com.riiablo.presentation.component.Overlay layer = effect.component;
        if (!NativeBoneArmorPresentation.handles(layer) || layer.entry == null) continue;
        boolean match = same(layer.entry.overlay, name) || same(layer.entry.overlay, "bonearmor_cast");
        // The ledger is unordered for fast removal. Use allocation order to
        // reproduce the native head-first linked list, never its array slot.
        if (match && (newest == null || effect.order > newest.order)) newest = effect;
      }
      if (newest != null) retire(entity, newest);
    }
    removeEmptyComponent(entity);
  }

  public void setShrineIcon(int entity, String overlayId) {
    setPersistent(entity, SHRINE_ICON_STATE, overlayId);
  }

  public void clearShrineIcon(int entity) { clearPersistent(entity, SHRINE_ICON_STATE); }

  /**
   * Finds an overlay using the native table key, its filename, or (for the
   * stock shrine glyphs) the canonical DCC filename.  Several 1.10 data sets
   * differ only in the case/shape of the Overlay.txt key; the filesystem is
   * not guaranteed to make that distinction when the MPQ is mounted.  The
   * old exact lookup silently dropped the icon in those installations.
   */
  private Overlay.Entry resolveEntry(String overlayId) {
    if (clientContext.files() == null || clientContext.files().Overlay == null) return null;

    Overlay.Entry exact = clientContext.files().Overlay.get(overlayId);
    if (exact != null) return exact;

    for (Overlay.Entry candidate : clientContext.files().Overlay) {
      if (candidate == null) continue;
      if (same(overlayId, candidate.overlay)
          || same(overlayId, candidate.Filename)) return candidate;
    }

    // Retail Overlay.txt contains the shrine rows, but modded/partial data
    // packs occasionally omit them while still shipping the DCCs.  Keep the
    // stock shrine presentation usable without inventing fallbacks for
    // arbitrary gameplay overlays.
    if (overlayId.regionMatches(true, 0, "shrine_", 0, 7)) {
      Overlay.Entry shrine = new Overlay.Entry();
      shrine.overlay = overlayId;
      shrine.Filename = shrineFilename(overlayId);
      shrine.PreDraw = false;
      shrine.Trans = 0;
      return shrine;
    }
    return null;
  }

  private static boolean same(String a, String b) {
    return a != null && b != null && a.equalsIgnoreCase(b);
  }

  private static String shrineFilename(String overlayId) {
    StringBuilder filename = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < overlayId.length(); i++) {
      char c = overlayId.charAt(i);
      if (c == '_') {
        upper = true;
      } else {
        filename.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return filename.toString();
  }


  private void ensureOpen() {
    if (disposed) throw new IllegalStateException("overlay manager is disposed");
  }

  private void install(int entity, Overlay.Entry entry, boolean persistent, int stateId) {
    install(entity, entry, persistent, stateId, 0);
  }

  private void install(int entity, Overlay.Entry entry, boolean persistent, int stateId, int boneArmorGroup) {
    if (assets == null) assets = java.util.Objects.requireNonNull(clientContext.assets(), "assets");
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) { effects = new Array<>(false, 4); owned.put(entity, effects); }
    // Same state/overlay refresh is idempotent; each different native overlay gets a layer.
    for (int i = effects.size - 1; i >= 0; i--) {
      OwnedOverlay effect = effects.get(i);
      com.riiablo.presentation.component.Overlay current = effect.component;
      if (current.persistent == persistent && current.stateId == stateId
          && current.boneArmorGroup == boneArmorGroup
          && current.entry != null && same(current.entry.overlay, entry.overlay)) retire(entity, effect);
    }
    // retire may remove the map entry when replacing the final owned effect.
    owned.put(entity, effects);
    com.riiablo.presentation.component.Overlay root = mOverlay.create(entity);
    com.riiablo.presentation.component.Overlay component = root.entry == null
        ? root : new com.riiablo.presentation.component.Overlay();
    component.set(entry);
    component.persistent = persistent;
    component.stateId = stateId;
    component.boneArmorGroup = boneArmorGroup;
    com.riiablo.codec.excel.States.Entry state = clientContext.files() == null
        || clientContext.files().States == null ? null : clientContext.files().States.get(stateId);
    component.nativeAura = state != null && state.aura;
    if (component != root) root.layers.add(component);
    AssetReferenceScope references = new AssetReferenceScope(assets);
    try {
      references.load(component.assetDescriptor);
      effects.add(new OwnedOverlay(root, component, references, ++nextOverlayOrder));
      if (boneArmorGroup > 0 && assets.isLoaded(component.assetDescriptor)) {
        loadAnimation(component);
        component.nativeClockStarted = true;
      }
    } catch (RuntimeException | Error failure) {
      for (int i = effects.size - 1; i >= 0; i--)
        if (effects.get(i).component == component) effects.removeIndex(i);
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(() -> {
          if (failure instanceof Error) throw (Error) failure;
          throw (RuntimeException) failure;
        });
        cleanup.run(component::clearEffect);
        cleanup.run(() -> root.layers.removeValue(component, true));
        cleanup.run(() -> removeEmptyComponent(entity));
        if (effects.size == 0) owned.remove(entity);
        cleanup.dispose(references);
      }
    }
  }

  private void removeEmptyComponent(int entity) {
    com.riiablo.presentation.component.Overlay root = mOverlay.get(entity);
    if (root != null && root.entry == null && root.layers.size == 0) mOverlay.remove(entity);
  }

  @Override protected void removed(int entity) {
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) return;
    try (Disposal cleanup = new Disposal()) {
      for (int i = effects.size - 1; i >= 0; i--) {
        OwnedOverlay effect = effects.get(i);
        if (!isCurrent(entity, effect)) cleanup.run(() -> retire(entity, effect));
      }
    }
  }

  private void retire(int entity, OwnedOverlay effect) {
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null || !effects.removeValue(effect, true)) return;
    if (effects.size == 0) owned.remove(entity);
    try (Disposal cleanup = new Disposal()) {
      if (effect.component.assetDescriptor == effect.descriptor) {
        cleanup.run(effect.component::clearEffect);
        if (effect.component != effect.root)
          cleanup.run(() -> effect.root.layers.removeValue(effect.component, true));
      }
      cleanup.dispose(effect.references);
    }
  }

  @Override protected void dispose() {
    if (disposed) return;
    disposed = true;
    int[] entities = owned.keys().toArray().toArray();
    try (Disposal cleanup = new Disposal()) {
      for (int entity : entities) {
        Array<OwnedOverlay> effects = owned.get(entity);
        for (int i = effects.size - 1; i >= 0; i--) {
          OwnedOverlay effect = effects.get(i);
          cleanup.run(() -> retire(entity, effect));
        }
      }
    }
  }
}

