package com.riiablo.screen.panel;

import com.artemis.World;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;
import com.riiablo.util.ActorTree;
import com.riiablo.util.Disposal;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;

/** Shared ownership boundary for the text-only party/trade views; all host resources are borrowed. */
abstract class SessionNetworkPanel extends Table implements Disposable {
  private final AssetManager assets = Riiablo.assets;
  private final CharData character = Riiablo.charData;
  private final com.riiablo.Textures textures = Riiablo.textures;
  protected final Fonts fonts = Riiablo.fonts;
  protected final Colors colors = Riiablo.colors;
  private final I18NBundle bundle = Riiablo.bundle;
  private final Array<Actor> ownedActors = new Array<>();
  protected GameScreen owner;
  private World world;
  private boolean disposed;
  private boolean networkBound;
  protected ClientNetworkSynchronizer synchronizer;
  protected ClientNetworkReceiver receiver;

  SessionNetworkPanel(GameScreen owner) {
    this.owner = owner;
    world = owner == null ? Riiablo.engine : null;
  }

  /** Called by assembly before publication. Rebinding the same world is harmless. */
  public final void bindWorld(World world) {
    if (disposed) return;
    java.util.Objects.requireNonNull(world, "world");
    if (this.world != null && this.world != world) throw new IllegalStateException("world already bound");
    this.world = world;
  }

  public final void setNetworkSystems(ClientNetworkSynchronizer synchronizer, ClientNetworkReceiver receiver) {
    if (disposed) return;
    java.util.Objects.requireNonNull(synchronizer, "synchronizer");
    java.util.Objects.requireNonNull(receiver, "receiver");
    if (world == null || world.getSystem(ClientNetworkSynchronizer.class) != synchronizer
        || world.getSystem(ClientNetworkReceiver.class) != receiver) {
      throw new IllegalArgumentException("network systems must belong to the bound world");
    }
    if (networkBound && (this.synchronizer != synchronizer || this.receiver != receiver)) {
      throw new IllegalStateException("network systems already bound");
    }
    this.synchronizer = synchronizer;
    this.receiver = receiver;
    networkBound = true;
  }

  protected final boolean isCurrentSession() {
    return !disposed && owner == Riiablo.game && world == Riiablo.engine
        && assets == Riiablo.assets && character == Riiablo.charData
        && fonts == Riiablo.fonts && colors == Riiablo.colors && bundle == Riiablo.bundle
        && textures == Riiablo.textures;
  }

  protected final boolean isInteractive() { return isCurrentSession() && isVisible(); }
  protected final String text(String key) { return bundle.get(key); }
  protected final String format(String key, Object... args) { return bundle.format(key, args); }
  protected final <T extends Actor> T own(T actor) { ownedActors.add(actor); return actor; }

  protected final boolean isCurrentControl(Actor actor) {
    return isCurrentSession() && ownedActors.contains(actor, true) && actor.isDescendantOf(this);
  }

  protected final Table table() {
    return own(new Table() {
      @Override public void act(float delta) { if (isCurrentControl(this)) super.act(delta); }
      @Override public void draw(Batch batch, float alpha) { if (isCurrentControl(this)) super.draw(batch, alpha); }
      @Override public Actor hit(float x, float y, boolean touchable) { return isCurrentControl(this) ? super.hit(x, y, touchable) : null; }
    });
  }

  protected final Label label(String text, BitmapFont font, Color color) {
    return own(new Label(text, font, color) {
      @Override public void act(float delta) { if (isCurrentControl(this)) super.act(delta); }
      @Override public void draw(Batch batch, float alpha) { if (isCurrentControl(this)) super.draw(batch, alpha); }
      @Override public void draw(PaletteIndexedBatch batch, float alpha) { if (isCurrentControl(this)) super.draw(batch, alpha); }
      @Override public Actor hit(float x, float y, boolean touchable) { return isCurrentControl(this) ? super.hit(x, y, touchable) : null; }
    });
  }

  protected final LabelButton actionButton(String text, Runnable action) {
    LabelButton button = own(new LabelButton(text, fonts.fontformal10, colors.white) {
      @Override public void act(float delta) { if (isCurrentControl(this)) super.act(delta); }
      @Override public void draw(Batch batch, float alpha) { if (isCurrentControl(this)) super.draw(batch, alpha); }
      @Override public void draw(PaletteIndexedBatch batch, float alpha) { if (isCurrentControl(this)) super.draw(batch, alpha); }
      @Override public Actor hit(float x, float y, boolean touchable) { return isCurrentControl(this) ? super.hit(x, y, touchable) : null; }
    });
    button.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) {
        if (isInteractive() && isCurrentControl(button)) action.run();
      }
    });
    return button;
  }

  /** Retire every returned dynamic actor, including one whose attachment failed or was removed externally. */
  protected final void retireActors(Array<Actor> actors) {
    Actor[] retiring = actors.toArray(Actor.class);
    actors.clear();
    for (Actor actor : retiring) ownedActors.removeValue(actor, true);
    try (Disposal cleanup = new Disposal()) {
      for (Actor actor : retiring) cleanup.run(() -> ActorTree.clear(actor));
    }
  }

  protected final void constructionFailed(Throwable failure) {
    try { dispose(); }
    catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    super.setVisible(visible);
  }
  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }
  @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
  @Override public Actor hit(float x, float y, boolean touchable) { return isCurrentSession() ? super.hit(x, y, touchable) : null; }

  protected abstract void clearView();

  @Override public final void dispose() {
    if (disposed) return;
    disposed = true;
    synchronizer = null; receiver = null; owner = null; world = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearView);
      cleanup.run(() -> retireActors(ownedActors));
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null));
    }
  }
}
