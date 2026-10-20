package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import net.mostlyoriginal.api.event.common.Event;

/**
 * Authoritative sound intent consumed by a client presentation layer.
 *
 * <p>The server selects the native sound key and the entity that owns the
 * sound, but never resolves an audio asset or touches a local audio device.
 * Clients apply their own listener, zone and distance policy.</p>
 */
public final class SoundEvent implements Event {
  @EntityId
  public int entityId;
  public String sound;
  public boolean global;

  public static SoundEvent obtain(int entityId, String sound, boolean global) {
    SoundEvent event = new SoundEvent();
    event.entityId = entityId;
    event.sound = sound;
    event.global = global;
    return event;
  }
}
