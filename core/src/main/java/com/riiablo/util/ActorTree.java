package com.riiablo.util;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

/** Retires an owned actor tree's callbacks and links, never its borrowed styles or assets. */
public final class ActorTree {
  private ActorTree() {}

  public static void clear(Actor actor) {
    if (actor == null) return;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(actor::clearListeners);
      cleanup.run(actor::clearActions);
      if (actor instanceof Group) {
        Group group = (Group) actor;
        for (Actor child : group.getChildren().toArray(Actor.class)) cleanup.run(() -> clear(child));
        cleanup.run(group::clearChildren);
      }
      cleanup.run(actor::remove);
    }
  }
}
