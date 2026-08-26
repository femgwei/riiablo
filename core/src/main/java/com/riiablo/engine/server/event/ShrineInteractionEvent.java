package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Native shrine effect request published after the common safe effects run.
 *
 * <p>Combat state, portal, monster and item systems can consume this event
 * without making the map-object layer depend on their implementation.</p>
 */
public class ShrineInteractionEvent implements Event {
  @EntityId public int playerId;
  @EntityId public int entityId;
  public int shrineId;
  public int code;
  /** D2MOO Shrines.txt effect-class bucket used during selection. */
  public int effectClass;
  /** Native operation category; see NativeShrineEffectResolver. */
  public int effectKind;
  public int arg0;
  public int arg1;
  public int durationFrames;
  public int resetFrames;
  public boolean appliedLocally;

  public static ShrineInteractionEvent obtain(int playerId, int entityId,
      int shrineId, int code, int arg0, int arg1, int durationFrames,
      int resetFrames, boolean appliedLocally) {
    return obtain(playerId, entityId, shrineId, code, 0, 0, arg0, arg1,
        durationFrames, resetFrames, appliedLocally);
  }

  public static ShrineInteractionEvent obtain(int playerId, int entityId,
      int shrineId, int code, int effectClass, int effectKind, int arg0,
      int arg1, int durationFrames, int resetFrames, boolean appliedLocally) {
    ShrineInteractionEvent event = new ShrineInteractionEvent();
    event.playerId = playerId;
    event.entityId = entityId;
    event.shrineId = shrineId;
    event.code = code;
    event.effectClass = effectClass;
    event.effectKind = effectKind;
    event.arg0 = arg0;
    event.arg1 = arg1;
    event.durationFrames = durationFrames;
    event.resetFrames = resetFrames;
    event.appliedLocally = appliedLocally;
    return event;
  }
}
