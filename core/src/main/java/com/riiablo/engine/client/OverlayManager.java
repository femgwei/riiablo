package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntArray;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC;
import com.riiablo.codec.excel.Overlay;
import com.riiablo.graphics.BlendMode;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

@All(com.riiablo.engine.client.component.Overlay.class)
public class OverlayManager extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(OverlayManager.class);
  /** Reserved owner id for an unactivated shrine's world-space glyph. */
  static final int SHRINE_ICON_STATE = Integer.MIN_VALUE + 0x5249;

  protected ComponentMapper<com.riiablo.engine.client.component.Overlay> mOverlay;
  private AssetManager assets;
  private final IntMap<OwnedOverlay> owned = new IntMap<>();
  private final IntArray retired = new IntArray();
  private boolean disposed;

  private static final class OwnedOverlay {
    final com.riiablo.engine.client.component.Overlay component;
    final AssetDescriptor descriptor;
    final AssetReferenceScope references;

    OwnedOverlay(com.riiablo.engine.client.component.Overlay component, AssetReferenceScope references) {
      this.component = component;
      descriptor = component.assetDescriptor;
      this.references = references;
    }
  }

  @Override protected void initialize() { assets = Riiablo.assets; }

  @Override protected void begin() {
    if (disposed) return;
    // An overlay removed before its first subscription insertion has no removed callback.
    // Also reconcile external component replacement without touching the new pooled value.
    retired.clear();
    for (IntMap.Entry<OwnedOverlay> entry : owned) {
      if (!isCurrent(entry.key, entry.value)) retired.add(entry.key);
    }
    try (Disposal cleanup = new Disposal()) {
      for (int i = 0; i < retired.size; i++) {
        final int entityId = retired.get(i);
        cleanup.run(() -> retire(entityId));
      }
    }
  }

  private boolean isCurrent(int entityId, OwnedOverlay overlay) {
    // EntityManager stays active until the end-of-frame purge even after deletion.
    return subscription.getActiveEntityIds().get(entityId) && mOverlay.has(entityId)
        && mOverlay.get(entityId) == overlay.component
        && overlay.component.assetDescriptor == overlay.descriptor;
  }

  @Override
  protected void process(int entityId) {
    if (disposed || assets == null || !owned.containsKey(entityId)) return;
    com.riiablo.engine.client.component.Overlay overlay = mOverlay.get(entityId);
    if (overlay == null || overlay.assetDescriptor == null || !assets.isLoaded(overlay.assetDescriptor)) return;

    Animation animation = overlay.animation;
    // A newly loaded one-frame DCC starts at frame 0, which is also its
    // terminal frame.  Do not dispose it in the same pass that installs its
    // layer: RenderSystem has not had a chance to draw that first frame yet.
    // Remembering the previous loaded state also keeps the normal multi-frame
    // path unchanged while allowing the transient hit flash to survive one
    // render frame.
    boolean loadedBefore = overlay.isLoaded;
    if (!overlay.isLoaded) {
      DC dc = assets.get(overlay.assetDescriptor);
      animation.edit()
          .layer(dc, overlay.entry.Trans == 3 ? BlendMode.LUMINOSITY : BlendMode.ID)
          .build();
      // Overlay.txt stores AnimRate as the number of overlay frames played
      // per second.  The generic Animation default is the 25 Hz game tick;
      // using it for ValkarieGlow (native AnimRate=8) makes the gold ring
      // pulse more than three times faster than the retail client.  Preserve
      // the native rate for every overlay that provides one and retain the
      // 25 Hz fallback for legacy/partial rows with AnimRate=0.
      if (overlay.entry.AnimRate > 0) {
        animation.setFrameDuration(1f / overlay.entry.AnimRate);
      }
      // Keep transient effects clamped to their last valid frame.  ONCE
      // advances to endIndex (one past the final frame), and the simulation
      // OverlayStepper runs before RenderSystem; a one-frame hit DCC would
      // therefore be loaded successfully but skipped by Animation.draw().
      // OverlayManager removes the effect after its hold period below.
      animation.setMode(overlay.persistent ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
      // FIXME: set frame to elapsed time since creation
      overlay.isLoaded = true;
      log.debug("Loaded {}", overlay.assetDescriptor.fileName);
    }

    if (!overlay.persistent && loadedBefore
        && overlay.minimumDisplayFrames > 0) {
      overlay.minimumDisplayFrames--;
    }
    if (!overlay.persistent && loadedBefore
        && overlay.minimumDisplayFrames <= 0
        && animation.getFrame() >= animation.getNumFramesPerDir() - 1) {
      removeOverlay(entityId);
    }
  }

  public void set(int entityId, String overlayId) {
    ensureOpen();
    Overlay.Entry overlay = resolveEntry(overlayId);
    if (overlay == null) {
      log.warn("[OVERLAY] entity={} id={} result=missing", entityId, overlayId);
      return;
    }
    install(entityId, overlay, false, -1);
  }

  /** Installs a looping overlay owned by a synchronized state. */
  public void setPersistent(int entityId, int stateId, String overlayId) {
    ensureOpen();
    if (overlayId == null || overlayId.isEmpty()) return;
    com.riiablo.engine.client.component.Overlay current =
        mOverlay.has(entityId) ? mOverlay.get(entityId) : null;
    if (current != null && current.persistent && current.stateId == stateId
        && current.entry != null && same(overlayId, current.entry.overlay)) return;
    Overlay.Entry overlay = resolveEntry(overlayId);
    if (overlay == null) {
      log.warn("[STATE_OVERLAY] entity={} state={} id={} result=missing", entityId, stateId, overlayId);
      return;
    }
    install(entityId, overlay, true, stateId);
    log.info("[STATE_OVERLAY] entity={} state={} id={} result=queued", entityId, stateId, overlayId);
  }

  public void clearPersistent(int entityId, int stateId) {
    if (disposed) return;
    if (!mOverlay.has(entityId)) return;
    com.riiablo.engine.client.component.Overlay overlay = mOverlay.get(entityId);
    if (!overlay.persistent || overlay.stateId != stateId) return;
    removeOverlay(entityId);
    log.info("[STATE_OVERLAY] entity={} state={} result=cleared", entityId, stateId);
  }

  /** Installs the stock shrine glyph above an unused shrine object. */
  public void setShrineIcon(int entityId, String overlayId) {
    setPersistent(entityId, SHRINE_ICON_STATE, overlayId);
  }

  /** Removes the shrine glyph after the one-shot activation begins. */
  public void clearShrineIcon(int entityId) {
    clearPersistent(entityId, SHRINE_ICON_STATE);
  }

  /**
   * Finds an overlay using the native table key, its filename, or (for the
   * stock shrine glyphs) the canonical DCC filename.  Several 1.10 data sets
   * differ only in the case/shape of the Overlay.txt key; the filesystem is
   * not guaranteed to make that distinction when the MPQ is mounted.  The
   * old exact lookup silently dropped the icon in those installations.
   */
  private Overlay.Entry resolveEntry(String overlayId) {
    if (Riiablo.files == null || Riiablo.files.Overlay == null) return null;

    Overlay.Entry exact = Riiablo.files.Overlay.get(overlayId);
    if (exact != null) return exact;

    for (Overlay.Entry candidate : Riiablo.files.Overlay) {
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

  private void install(int entityId, Overlay.Entry entry, boolean persistent, int stateId) {
    if (assets == null) assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
    removeOverlay(entityId);
    com.riiablo.engine.client.component.Overlay component = mOverlay.create(entityId).set(entry);
    component.persistent = persistent;
    component.stateId = stateId;
    AssetReferenceScope references = new AssetReferenceScope(assets);
    try {
      references.load(component.assetDescriptor);
      owned.put(entityId, new OwnedOverlay(component, references));
    } catch (RuntimeException | Error failure) {
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(() -> {
          if (failure instanceof Error) throw (Error) failure;
          throw (RuntimeException) failure;
        });
        cleanup.run(() -> mOverlay.remove(entityId));
        cleanup.dispose(references);
      }
    }
  }

  private void removeOverlay(int entityId) {
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> retire(entityId));
      cleanup.run(() -> mOverlay.remove(entityId));
    }
  }

  @Override protected void removed(int entityId) {
    OwnedOverlay current = owned.get(entityId);
    // A deferred removal notification may follow an installation on the same entity.
    if (current != null && !isCurrent(entityId, current)) retire(entityId);
  }

  private void retire(int entityId) {
    // Component removal/reset may already have run; the ledger does not depend on its fields.
    OwnedOverlay closing = owned.remove(entityId);
    if (closing == null) return;
    try (Disposal cleanup = new Disposal()) {
      // Never clear a pooled component which has since been reused for another overlay.
      if (closing.component.assetDescriptor == closing.descriptor) {
        cleanup.run(closing.component.animation::reset);
        closing.component.assetDescriptor = null;
        closing.component.isLoaded = false;
      }
      cleanup.dispose(closing.references);
    }
  }

  @Override protected void dispose() {
    if (disposed) return;
    disposed = true;
    int[] entities = owned.keys().toArray().toArray();
    try (Disposal cleanup = new Disposal()) {
      for (int entityId : entities) cleanup.run(() -> retire(entityId));
    }
  }
}
