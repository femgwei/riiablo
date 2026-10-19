package com.riiablo.engine.client.component;

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
  /** State id that owns a persistent overlay, or -1 for cast visuals. */
  public int stateId = -1;

  public Overlay set(com.riiablo.codec.excel.Overlay.Entry overlay) {
    this.entry = overlay;
    this.assetDescriptor = new AssetDescriptor<>("data\\global\\overlays\\" + overlay.Filename + ".dcc", DCC.class);
    this.isLoaded = false;
    this.minimumDisplayFrames = 3;
    this.persistent = false;
    this.stateId = -1;
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
    stateId = -1;
  }
}
