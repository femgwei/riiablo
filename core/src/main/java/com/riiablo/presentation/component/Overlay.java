package com.riiablo.presentation.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

import com.badlogic.gdx.assets.AssetDescriptor;

import com.riiablo.codec.Animation;
import com.riiablo.codec.DC;
import com.riiablo.codec.DCC;

@Transient
@PooledWeaver
public class Overlay extends PooledComponent {
  /** Additional simultaneous effects; the first effect remains on this component for compatibility. */
  public final com.badlogic.gdx.utils.Array<Overlay> layers = new com.badlogic.gdx.utils.Array<>(false, 4);
  public final Animation animation = Animation.newAnimation();
  public AssetDescriptor<? extends DC> assetDescriptor;
  public com.riiablo.codec.excel.Overlay.Entry entry;
  public boolean isLoaded;
  /** Minimum simulation frames to keep a transient overlay after loading. */
  public int minimumDisplayFrames;
  /** True when this overlay is driven by a synchronized unit state. */
  public boolean persistent;
  /** Native aura-specific drawing; other overlay consumers retain their existing policy. */
  public boolean nativeAura;
  /** Enchant's native 25 Hz activation clock, including asset-load delays. */
  public int nativeFrame;
  /** Client birth clock for the Arctic Blast ONCE layer, including cold assets. */
  public double nativeClockTime;
  public boolean nativeClockStarted;
  /** State id that owns a persistent overlay, or -1 for cast visuals. */
  public int stateId = -1;
  /** Independent Bone Armor chain; equal resources in different chains must coexist. */
  public int boneArmorGroup;

  public boolean usesNativeRendering() {
    return nativeAura || entry != null
        && (persistent && com.riiablo.presentation.NativeFeralMaulPresentation.handles(stateId)
            || persistent && stateId == com.riiablo.engine.server.state.StateId.CYCLONEARMOR
            || boneArmorGroup > 0
            || persistent && stateId == com.riiablo.engine.server.state.StateId.FENRIS_RAGE
            || persistent && stateId == com.riiablo.engine.server.state.StateId.RABIES
            || com.riiablo.presentation.AuraRenderPolicy.isBarbarianOverlay(entry.overlay)
            || "enchant".equalsIgnoreCase(entry.overlay)
            || "fire_hit".equalsIgnoreCase(entry.overlay)
            || "steallife".equalsIgnoreCase(entry.overlay)
            || "stealmana".equalsIgnoreCase(entry.overlay)
            || "arctic_blast_cast".equalsIgnoreCase(entry.overlay)
            || com.riiablo.presentation.AuraRenderPolicy.isPaladinTargetOverlay(entry.overlay));
  }

  public Overlay set(com.riiablo.codec.excel.Overlay.Entry overlay) {
    this.entry = overlay;
    this.assetDescriptor = new AssetDescriptor<>("data\\global\\overlays\\" + overlay.Filename + ".dcc", DCC.class);
    this.isLoaded = false;
    this.minimumDisplayFrames = 3;
    this.persistent = false;
    this.nativeAura = false;
    this.nativeFrame = 0;
    this.nativeClockTime = 0; this.nativeClockStarted = false;
    this.stateId = -1;
    this.boneArmorGroup = 0;
    return this;
  }

  @Override
  protected void reset() {
    for (Overlay layer : layers) layer.clearEffect();
    layers.clear();
    clearEffect();
  }

  /** Clears this effect without discarding other state/impact layers. */
  public void clearEffect() {
    animation.reset();
    assetDescriptor = null;
    entry = null;
    isLoaded = false;
    minimumDisplayFrames = 0;
    persistent = false;
    nativeAura = false;
    nativeFrame = 0;
    nativeClockTime = 0; nativeClockStarted = false;
    stateId = -1;
    boneArmorGroup = 0;
  }
}
