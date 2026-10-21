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
@All(com.riiablo.engine.client.component.Overlay.class)
public class OverlayManager extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(OverlayManager.class);
  static final int SHRINE_ICON_STATE = Integer.MIN_VALUE + 0x5249;
  protected ComponentMapper<com.riiablo.engine.client.component.Overlay> mOverlay;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();
  private AssetManager assets;
  private final IntMap<Array<OwnedOverlay>> owned = new IntMap<>();
  private boolean disposed;

  private static final class OwnedOverlay {
    final com.riiablo.engine.client.component.Overlay root, component;
    final AssetDescriptor descriptor;
    final AssetReferenceScope references;
    OwnedOverlay(com.riiablo.engine.client.component.Overlay root,
        com.riiablo.engine.client.component.Overlay component, AssetReferenceScope references) {
      this.root = root;
      this.component = component;
      descriptor = component.assetDescriptor;
      this.references = references;
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
      com.riiablo.engine.client.component.Overlay overlay = effect.component;
      if (!assets.isLoaded(effect.descriptor)) continue;
      Animation animation = overlay.animation;
      boolean loadedBefore = overlay.isLoaded;
      if (!loadedBefore) {
        DC dc = assets.get(overlay.assetDescriptor);
        animation.edit().layer(dc, overlay.entry.Trans == 3
            ? BlendMode.LUMINOSITY : BlendMode.ID).build();
        if (overlay.entry.AnimRate > 0) animation.setFrameDuration(1f / overlay.entry.AnimRate);
        animation.setMode(overlay.persistent ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
        overlay.isLoaded = true;
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

  public void setPersistent(int entity, int stateId, String overlayId) {
    ensureOpen();
    if (overlayId == null || overlayId.isEmpty()) return;
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects != null) for (OwnedOverlay effect : effects) {
      com.riiablo.engine.client.component.Overlay current = effect.component;
      if (current.persistent && current.stateId == stateId && current.entry != null
          && same(overlayId, current.entry.overlay)
          && mOverlay.get(entity) == effect.root && current.assetDescriptor == effect.descriptor)
        return;
    }
    Overlay.Entry entry = resolveEntry(overlayId);
    if (entry != null) install(entity, entry, true, stateId);
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
    if (assets == null) assets = java.util.Objects.requireNonNull(clientContext.assets(), "assets");
    Array<OwnedOverlay> effects = owned.get(entity);
    if (effects == null) { effects = new Array<>(false, 4); owned.put(entity, effects); }
    // Same state/overlay refresh is idempotent; each different native overlay gets a layer.
    for (int i = effects.size - 1; i >= 0; i--) {
      OwnedOverlay effect = effects.get(i);
      com.riiablo.engine.client.component.Overlay current = effect.component;
      if (current.persistent == persistent && current.stateId == stateId
          && current.entry != null && same(current.entry.overlay, entry.overlay)) retire(entity, effect);
    }
    // retire may remove the map entry when replacing the final owned effect.
    owned.put(entity, effects);
    com.riiablo.engine.client.component.Overlay root = mOverlay.create(entity);
    com.riiablo.engine.client.component.Overlay component = root.entry == null
        ? root : new com.riiablo.engine.client.component.Overlay();
    component.set(entry);
    component.persistent = persistent;
    component.stateId = stateId;
    if (component != root) root.layers.add(component);
    AssetReferenceScope references = new AssetReferenceScope(assets);
    try {
      references.load(component.assetDescriptor);
      effects.add(new OwnedOverlay(root, component, references));
    } catch (RuntimeException | Error failure) {
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
    com.riiablo.engine.client.component.Overlay root = mOverlay.get(entity);
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

