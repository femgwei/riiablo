package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;

/** Exercise retained controls directly, not only through the now-inactive parent. */
final class PanelSessionAssertions {
  static Array<Actor> snapshot(Actor root) {
    Array<Actor> actors = new Array<>(); collect(root, actors); return actors;
  }
  private static void collect(Actor actor, Array<Actor> actors) {
    actors.add(actor);
    if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, actors);
  }
  static void assertInactive(Actor root, Array<Actor> actors) {
    root.act(1); root.draw((Batch) null, 1); assertNull(root.hit(0, 0, true));
    for (Actor actor : actors) {
      if (actor instanceof Button || actor instanceof Label) {
        actor.act(1); actor.draw((Batch) null, 1); assertNull(actor.hit(0, 0, true));
      }
      for (EventListener listener : actor.getListeners().toArray(EventListener.class)) {
        if (listener instanceof ClickListener) ((ClickListener) listener).clicked(null, 0, 0);
      }
    }
  }
}
