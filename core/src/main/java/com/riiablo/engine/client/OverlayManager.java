package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.badlogic.gdx.assets.AssetDescriptor;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC;
import com.riiablo.codec.excel.Overlay;
import com.riiablo.graphics.BlendMode;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All(com.riiablo.engine.client.component.Overlay.class)
public class OverlayManager extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(OverlayManager.class);
  /** Reserved owner id for an unactivated shrine's world-space glyph. */
  static final int SHRINE_ICON_STATE = Integer.MIN_VALUE + 0x5249;

  protected ComponentMapper<com.riiablo.engine.client.component.Overlay> mOverlay;

  @Override
  protected void process(int entityId) {
    com.riiablo.engine.client.component.Overlay overlay = mOverlay.get(entityId);
    if (!Riiablo.assets.isLoaded(overlay.assetDescriptor)) return;

    Animation animation = overlay.animation;
    if (!overlay.isLoaded) {
      DC dc = Riiablo.assets.get(overlay.assetDescriptor);
      animation.edit()
          .layer(dc, overlay.entry.Trans == 3 ? BlendMode.LUMINOSITY : BlendMode.ID)
          .build();
      animation.setMode(overlay.persistent ? Animation.Mode.LOOP : Animation.Mode.ONCE);
      // FIXME: set frame to elapsed time since creation
      overlay.isLoaded = true;
      log.debug("Loaded {}", overlay.assetDescriptor.fileName);
    }

    if (!overlay.persistent && animation.isFinished()) {
      dispose(overlay);
      mOverlay.remove(entityId);
    }
  }

  public void set(int entityId, String overlayId) {
    if (mOverlay.has(entityId)) {
      dispose(mOverlay.get(entityId));
    }

    Overlay.Entry overlay = resolveEntry(overlayId);
    if (overlay == null) {
      log.warn("[OVERLAY] entity={} id={} result=missing", entityId, overlayId);
      return;
    }
    com.riiablo.engine.client.component.Overlay overlayC = mOverlay.create(entityId).set(overlay);
    Riiablo.assets.load(overlayC.assetDescriptor);
  }

  /** Installs a looping overlay owned by a synchronized state. */
  public void setPersistent(int entityId, int stateId, String overlayId) {
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
    if (current != null) dispose(current);
    com.riiablo.engine.client.component.Overlay overlayC = mOverlay.create(entityId).set(overlay);
    overlayC.persistent = true;
    overlayC.stateId = stateId;
    Riiablo.assets.load(overlayC.assetDescriptor);
    log.info("[STATE_OVERLAY] entity={} state={} id={} result=queued", entityId, stateId, overlayId);
  }

  public void clearPersistent(int entityId, int stateId) {
    if (!mOverlay.has(entityId)) return;
    com.riiablo.engine.client.component.Overlay overlay = mOverlay.get(entityId);
    if (!overlay.persistent || overlay.stateId != stateId) return;
    dispose(overlay);
    mOverlay.remove(entityId);
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

  void dispose(com.riiablo.engine.client.component.Overlay overlay) {
    AssetDescriptor assetDescriptor = overlay.assetDescriptor;
    Riiablo.assets.unload(assetDescriptor.fileName);
    log.debug("Unloaded {}", assetDescriptor.fileName);
  }
}
