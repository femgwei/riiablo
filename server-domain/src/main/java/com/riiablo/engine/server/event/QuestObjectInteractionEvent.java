package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Synchronous request sent to the owning quest before a quest object changes.
 *
 * <p>A quest subscriber calls {@link #accept()} or {@link #accept(byte)} only
 * after checking the player's quest record and required quest item. This
 * keeps generic map-object code independent from individual quest scripts.</p>
 */
public class QuestObjectInteractionEvent implements Event {
  @EntityId public int playerId;
  @EntityId public int entityId;
  public int objectClassId;
  public NativeQuestObjectResolver.Type type;
  public boolean accepted;
  public boolean oneShot;
  public byte targetMode;

  public static QuestObjectInteractionEvent obtain(int playerId, int entityId,
      int objectClassId, NativeQuestObjectResolver.Type type) {
    QuestObjectInteractionEvent event = new QuestObjectInteractionEvent();
    event.playerId = playerId;
    event.entityId = entityId;
    event.objectClassId = objectClassId;
    event.type = type == null ? NativeQuestObjectResolver.Type.NONE : type;
    event.oneShot = event.type.oneShot;
    event.targetMode = event.type.suggestedMode;
    if (event.type.defaultActivation) event.accept();
    return event;
  }

  public void accept() {
    accepted = true;
  }

  public void accept(byte targetMode) {
    this.targetMode = targetMode;
    accepted = true;
  }
}
