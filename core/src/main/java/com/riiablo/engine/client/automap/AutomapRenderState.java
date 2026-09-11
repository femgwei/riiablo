package com.riiablo.engine.client.automap;

/** Validates the non-nested Automap renderer phase order. */
public final class AutomapRenderState {
  public enum Phase { SHAPES, SPRITES }

  private AutomapRenderState() {}

  public static Phase enterSprites(Phase current) {
    if (current != Phase.SHAPES) throw new IllegalStateException("Sprite pass requires active Shape pass");
    return Phase.SPRITES;
  }

  public static Phase leaveSprites(Phase current) {
    if (current != Phase.SPRITES) throw new IllegalStateException("Shape pass was not suspended");
    return Phase.SHAPES;
  }
}
