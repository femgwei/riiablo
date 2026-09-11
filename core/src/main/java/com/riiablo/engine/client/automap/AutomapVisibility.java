package com.riiablo.engine.client.automap;

import com.riiablo.map.Map;

/** Pure visibility predicates shared by the Automap entity collector and tests. */
public final class AutomapVisibility {
  private AutomapVisibility() {}

  /**
   * Returns whether an entity in {@code room} is in the native client scope.
   * A room is visible only while it is CLIENT_IN_ROOM or CLIENT_IN_SIGHT.
   */
  public static boolean isRoomVisible(Map.RoomEx room) {
    return room != null && isActivationVisible(room.getActivationStatus());
  }

  /** D2MOO activation statuses 0/1 are visible; 2/3/COUNT remain hidden. */
  public static boolean isActivationVisible(int activationStatus) {
    return activationStatus <= Map.RoomEx.CLIENT_IN_SIGHT;
  }

  /**
   * Applies the native topology fallback used by AutomapRenderer. When a map
   * has no exported topology (or activation tracking has not started), retain
   * the legacy visibility behavior and allow the entity through.
   */
  public static boolean isEntityVisible(Map.Zone zone, float worldX, float worldY) {
    if (zone == null || !zone.hasNativeRoomTopology() || !zone.isRoomActivationTracking()) {
      return true;
    }
    return isRoomVisible(zone.findRoomEx(worldX, worldY));
  }
}
