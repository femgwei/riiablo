package com.riiablo.engine.server;

import com.badlogic.gdx.utils.IntMap;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Exact, detached images of system-owned retry clocks. No tick or scheduling callbacks. */
final class RuntimeCooldowns {
  private RuntimeCooldowns() {}

  /** Checkpoint overlays can precede the component-aspect insertion notification.
   * Deleting that entity must still clear its clocks, including during rollback.
   */
  static void clearOnDeletion(World world, IntMap<?>... timers) {
    world.getAspectSubscriptionManager().get(Aspect.all())
        .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
          @Override public void inserted(IntBag entities) {}
          @Override public void removed(IntBag entities) {
            for (int i = 0; i < entities.size(); i++) {
              for (IntMap<?> timer : timers) timer.remove(entities.get(i));
            }
          }
        });
  }

  static Map<Integer, Float> capture(IntMap<Float> timers) {
    Map<Integer, Float> result = new TreeMap<>();
    for (IntMap.Entry<Float> entry : timers) result.put(entry.key, entry.value);
    validate(result);
    return Collections.unmodifiableMap(result);
  }

  static void validate(Map<Integer, Float> timers) {
    if (timers == null) throw new IllegalArgumentException("null runtime timers");
    for (Map.Entry<Integer, Float> entry : timers.entrySet()) {
      if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null
          || !Float.isFinite(entry.getValue()) || entry.getValue() < 0f) {
        throw new IllegalArgumentException("invalid runtime timer");
      }
    }
  }

  static void restore(IntMap<Float> target, Map<Integer, Float> timers) {
    validate(timers); // Validate every entry before clearing live state.
    target.clear();
    for (Map.Entry<Integer, Float> entry : timers.entrySet()) target.put(entry.getKey(), entry.getValue());
  }
}
