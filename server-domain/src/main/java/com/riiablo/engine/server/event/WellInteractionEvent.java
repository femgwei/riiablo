package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Synchronous native well effect request.
 *
 * <p>The object system applies the safe attribute restoration first. State
 * and pet systems may then consume this event and set {@link #appliedByConsumer}
 * when poison/freeze/curse removal or pet healing actually changed gameplay.
 * That acknowledgement makes the well consume a charge, matching D2Game.</p>
 */
public class WellInteractionEvent implements Event {
  public static final int RESTORED_LIFE = 1;
  public static final int RESTORED_MANA = 1 << 1;
  public static final int RESTORED_STAMINA = 1 << 2;

  @EntityId public int playerId;
  @EntityId public int entityId;
  public int objectClassId;
  /** Objects.txt Parm1, an 8.8 fixed-point fraction with denominator 256. */
  public int restoreFraction256;
  /** Bit mask of RESTORED_* values already applied by the object system. */
  public int localRestorationMask;
  public boolean cleansePoison;
  public boolean cleanseFreeze;
  public boolean cleanseCurses;
  public boolean healAndCleansePets;
  /** Set by a synchronous subscriber only when it applied an external effect. */
  public boolean appliedByConsumer;

  public static WellInteractionEvent obtain(int playerId, int entityId,
      int objectClassId, int restoreFraction256, int localRestorationMask) {
    WellInteractionEvent event = new WellInteractionEvent();
    event.playerId = playerId;
    event.entityId = entityId;
    event.objectClassId = objectClassId;
    event.restoreFraction256 = Math.max(0, restoreFraction256);
    event.localRestorationMask = localRestorationMask;
    event.cleansePoison = true;
    event.cleanseFreeze = true;
    event.cleanseCurses = true;
    event.healAndCleansePets = true;
    return event;
  }

  public boolean applied() {
    return localRestorationMask != 0 || appliedByConsumer;
  }
}
