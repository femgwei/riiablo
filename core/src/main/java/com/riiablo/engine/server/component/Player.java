package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.save.CharData;

@Transient
@PooledWeaver
public class Player extends Component {
  public CharData data;
  /** Persistent native Vengeance skill Param1 (rotating HitClass 0..2). */
  public int vengeanceHitClass;
}
