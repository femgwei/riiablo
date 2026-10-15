package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.server.component.NativeTargetNode;

/** Allocation-local shared membership. No static world/entity-ID registry. */
public final class NativeTargetNodes {
  private NativeTargetNodes() {}

  public static NativeTargetNode get(World world, int entityId) {
    NativeTargetNode node = world.getMapper(NativeTargetNode.class).create(entityId);
    node.usePlayerSlots(world.getSystem(NativePlayerTargetSlots.class));
    return node;
  }
}
