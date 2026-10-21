package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.widget.NpcMenu;

/**
 * Presentation-owned menu tree attached only to client entities. Dedicated
 * entity composition does not create or depend on this transient UI state.
 */
@Transient
@PooledWeaver
public class MenuWrapper extends Component {
  public NpcMenu menu;
}
