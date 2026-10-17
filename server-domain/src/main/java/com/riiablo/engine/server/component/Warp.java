package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.engine.Engine;

@Transient
@PooledWeaver
public class Warp extends PooledComponent {
  public int           index;
  public LvlWarp.Entry warp;
  public Levels.Entry  dstLevel;
  /** Non-negative owner for an ordinary player Town Portal endpoint. */
  public int townPortalOwner = Engine.INVALID_ENTITY;
  /** The paired endpoint entity for an ordinary player Town Portal. */
  public int linkedTownPortal = Engine.INVALID_ENTITY;

  public final IntIntMap substs = new IntIntMap();

  @Override
  protected void reset() {
    index = 0;
    warp = null;
    dstLevel = null;
    townPortalOwner = Engine.INVALID_ENTITY;
    linkedTownPortal = Engine.INVALID_ENTITY;
    substs.clear();
  }

  public Warp set(int index, LvlWarp.Entry warp, Levels.Entry dstLevel) {
    this.index = index;
    this.warp = warp;
    this.dstLevel = dstLevel;
    return this;
  }
}
