package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.Engine;

/** Minimal D2AiCmdStrc projection used by native leader/minion AI. */
@Transient
@PooledWeaver
public class NativeAiCommand extends Component {
  public static final int NONE = 0;
  public static final int ATTACK = 1;

  @EntityId public int ownerId = Engine.INVALID_ENTITY;
  @EntityId public int targetId = Engine.INVALID_ENTITY;
  public int command;

  public NativeAiCommand set(int ownerId, int command, int targetId) {
    this.ownerId = ownerId;
    this.command = command;
    this.targetId = targetId;
    return this;
  }

  public boolean isAttack() {
    return command == ATTACK;
  }
}
