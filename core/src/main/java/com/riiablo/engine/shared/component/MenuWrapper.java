package com.riiablo.engine.shared.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.widget.NpcMenu;

/**
 * Presentation-owned menu state attached to an entity.  The component lives
 * in the shared layer so server entity composition does not import the client
 * package merely to expose an ECS slot.
 */
@Transient
@PooledWeaver
public class MenuWrapper extends Component {
  public NpcMenu menu;
}
