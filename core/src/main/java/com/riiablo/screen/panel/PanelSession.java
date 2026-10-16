package com.riiablo.screen.panel;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.screen.GameScreen;
import com.riiablo.util.ActorTree;
import com.riiablo.util.Disposal;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;

/** Borrowed session identity plus ownership of returned UI trees, including detached controls. */
final class PanelSession implements Disposable {
  private final Object assets = Riiablo.assets, character = Riiablo.charData;
  private final Object fonts = Riiablo.fonts, colors = Riiablo.colors;
  private final Object textures = Riiablo.textures, strings = Riiablo.string, bundle = Riiablo.bundle;
  private final Array<Actor> actors = new Array<>();
  private GameScreen owner;
  private World world;
  private boolean assembling = true, disposed;

  PanelSession(GameScreen owner) {
    this.owner = owner;
    world = owner == Riiablo.game ? Riiablo.engine : null;
  }

  GameScreen owner() { return owner; }
  World world() { return world; }

  void bindWorld(World world) {
    if (disposed) return;
    java.util.Objects.requireNonNull(world, "world");
    if (this.world != null && this.world != world) throw new IllegalStateException("world already bound");
    this.world = world;
  }

  boolean isCurrent() {
    return !disposed && assets == Riiablo.assets && character == Riiablo.charData
        && fonts == Riiablo.fonts && colors == Riiablo.colors && textures == Riiablo.textures
        && strings == Riiablo.string && bundle == Riiablo.bundle
        && (assembling || owner == Riiablo.game && world == Riiablo.engine);
  }

  void finish(Actor root) { own(root); assembling = false; }

  <T extends Actor> T own(T actor) {
    if (actor == null) return null;
    if (!actors.contains(actor, true)) actors.add(actor);
    if (actor instanceof Button) ((Button) actor).setSessionGuard(this::isCurrent);
    if (actor instanceof Label) ((Label) actor).setSessionGuard(this::isCurrent);
    if (actor instanceof com.riiablo.widget.GoldAmountDialog)
      ((com.riiablo.widget.GoldAmountDialog) actor).setSessionGuard(this::isCurrent);
    if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) own(child);
    return actor;
  }

  /** Also collect a partially constructed tree before any cleanup removes its links. */
  void dispose(Actor root) { if (!disposed) { own(root); dispose(); } }

  static void guardTree(Actor actor, java.util.function.BooleanSupplier guard) {
    if (actor instanceof Button) ((Button) actor).setSessionGuard(guard);
    if (actor instanceof Label) ((Label) actor).setSessionGuard(guard);
    if (actor instanceof com.riiablo.widget.GoldAmountDialog)
      ((com.riiablo.widget.GoldAmountDialog) actor).setSessionGuard(guard);
    if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) guardTree(child, guard);
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    owner = null; world = null;
    Actor[] retiring = actors.toArray(Actor.class);
    actors.clear();
    try (Disposal cleanup = new Disposal()) {
      for (Actor actor : retiring) {
        if (actor instanceof Button) cleanup.run(() -> Button.disposeClickSounds(actor));
        cleanup.run(() -> ActorTree.clear(actor));
      }
    }
  }
}
