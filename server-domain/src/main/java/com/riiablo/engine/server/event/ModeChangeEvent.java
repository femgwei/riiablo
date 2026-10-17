package com.riiablo.engine.server.event;

public class ModeChangeEvent extends CofChangeEvent {
  public byte mode;
  /** True when the mode is unchanged and only the action animation restarts. */
  public boolean restart;

  public static ModeChangeEvent obtain(int entityId, byte mode) {
    return obtain(entityId, mode, false);
  }

  public static ModeChangeEvent obtain(int entityId, byte mode, boolean restart) {
    ModeChangeEvent event = new ModeChangeEvent();
    event.entityId = entityId;
    event.mode = mode;
    event.restart = restart;
    return event;
  }
}
