package com.riiablo.engine.server.npc;

import com.artemis.ComponentMapper;
import com.riiablo.engine.server.ai.Npc;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.ZoneChangeEvent;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Refreshes normal NPC inventories when a town becomes empty.
 *
 * <p>D2 keeps a town's trade inventory shared by all players in the game. A
 * panel close does not reroll it; the inventory is discarded only after the
 * last player has left that town (or the native refresh timer fires). This
 * listener implements the level-empty part for both local and dedicated
 * server worlds.</p>
 */
public final class NpcVendorSessionSystem extends PassiveSystem {
  private final NpcVendorSessionManager sessions;

  protected ComponentMapper<Player> mPlayer;

  public NpcVendorSessionSystem() {
    this(null);
  }

  public NpcVendorSessionSystem(NpcVendorSessionManager sessions) {
    this.sessions = sessions;
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.isTown()) return;
    if (townStillOccupied()) return;

    if (sessions != null) sessions.clearAll();
    Npc.clearAllVendorStocks();
  }

  private boolean townStillOccupied() {
    com.artemis.utils.IntBag players = world.getAspectSubscriptionManager()
        .get(com.artemis.Aspect.all(Player.class, MapWrapper.class)).getEntities();
    ComponentMapper<MapWrapper> wrappers = world.getMapper(MapWrapper.class);
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      MapWrapper wrapper = wrappers.get(ids[i]);
      if (wrapper != null && wrapper.zone != null && wrapper.zone.isTown()) return true;
    }
    return false;
  }
}
