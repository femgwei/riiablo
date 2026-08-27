package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.codec.DC6;

@Transient
@PooledWeaver
public class Item extends Component {
  public com.riiablo.item.Item item;
  public AssetDescriptor<DC6> flippyDescriptor;
  /** Temporary server-side pickup ownership for monster/player drops. */
  public int dropOwnerId = -1;
  public long dropOwnerUntilMillis;

  public Item set(com.riiablo.item.Item item) {
    this.item = item;
    this.dropOwnerId = -1;
    this.dropOwnerUntilMillis = 0L;
    this.flippyDescriptor = new AssetDescriptor<>(Class.Type.ITM.PATH + '\\' + item.getFlippyFile() + ".dc6", DC6.class);
    return this;
  }
}
