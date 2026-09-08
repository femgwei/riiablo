package com.riiablo.engine.server.item;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;

import com.riiablo.engine.server.component.Item;

/**
 * Removes pickup ownership claims exactly at the Artemis deletion boundary.
 *
 * <p>{@link GroundDropOwnership#clear(int)} intentionally keeps a claim while
 * a consumed entity is pending deletion, preventing a late duplicate request
 * from consuming it twice.  This system observes the actual component removal
 * and releases the claim so recycled entity ids cannot inherit stale state.</p>
 */
@All(Item.class)
public final class GroundDropCleanupSystem extends BaseEntitySystem {
  protected ComponentMapper<Item> mItem;

  protected void process(int entityId) {
    // Ground items have no periodic work; cleanup is performed by removed().
  }

  @Override
  protected void processSystem() {
    // Intentionally empty; BaseEntitySystem still invokes removed() when an
    // Item entity leaves the world.
  }

  @Override
  protected void removed(int entityId) {
    GroundDropOwnership.discard(entityId);
  }
}
