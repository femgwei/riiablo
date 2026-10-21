package com.riiablo.engine;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.Map;
import com.riiablo.map.MapPopulationPort;

/** Host-side ECS adapter; map generation never sees the world or entity factory. */
public final class MapRuntimeBindings {
  private MapRuntimeBindings() {}

  public static void bind(Map map, World world, MapPopulationPort population,
      boolean remoteClient) {
    map.configureGeneration(remoteClient, population.hasClientPresentation());
    ComponentMapper<Warp> warps = world.getMapper(Warp.class);
    map.bindWarpIndex(entityId -> {
      if (!warps.has(entityId)) return Integer.MIN_VALUE;
      Warp warp = warps.get(entityId);
      return warp == null ? Integer.MIN_VALUE : warp.index;
    });
  }
}
