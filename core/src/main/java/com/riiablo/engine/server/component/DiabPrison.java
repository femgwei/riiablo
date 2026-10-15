package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.utils.IntArray;

/** MonsterSpawn's self-owned root and its three minions; never owned by Diablo. */
@Transient
public class DiabPrison extends Component {
  public IntArray members;
}
