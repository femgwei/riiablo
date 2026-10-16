package com.riiablo.screen.panel;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.event.InteractEvent;
import com.riiablo.engine.client.NetworkedActionSender;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;
import com.riiablo.widget.Button;
import com.riiablo.widget.HotkeyButton;
import com.riiablo.util.Disposal;
import com.riiablo.util.AssetReferenceScope;

public class MobileControls extends WidgetGroup implements Disposable {
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private boolean disposed;
  private final PanelSession session;
  public void bindWorld(com.artemis.World world) { session.bindWorld(world); }
  private ActorGestureListener gestureListener;
  private SpellsQuickPanel observedPanel;
  final AssetDescriptor<DC6> SkilliconDescriptor = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\Skillicon.DC6", DC6.class);
  DC6 Skillicon;

  final AssetDescriptor<DC6> SoSkilliconDescriptor = new AssetDescriptor<>("data\\global\\ui\\SPELLS\\SoSkillicon.DC6", DC6.class);
  DC6 SoSkillicon;

  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Position> mPosition;
  private final Vector2 tmpVec2 = new Vector2();

  protected EventSystem events;
  protected Actioneer actioneer;

  @com.artemis.annotations.Wire(name = "client.socket", failOnNull = false)
  protected Socket socket;

  Button interact;
  HotkeyButton skills[];

  final float SIZE = 64;

  public MobileControls() {
    this(Riiablo.game);
  }

  public MobileControls(com.riiablo.screen.GameScreen owner) {
    session = new PanelSession(owner);
    try {
      initialize();
      session.finish(this);
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(SkilliconDescriptor);
    assets.finishLoadingAsset(SkilliconDescriptor);
    Skillicon = assets.get(SkilliconDescriptor);

    references.load(SoSkilliconDescriptor);
    assets.finishLoadingAsset(SoSkilliconDescriptor);
    SoSkillicon = assets.get(SoSkilliconDescriptor);

    interact = new Button(new Button.ButtonStyle() {{
      up   = new TextureRegionDrawable(Skillicon.getTexture(8));
      down = new TextureRegionDrawable(Skillicon.getTexture(9));
    }});
    interact.setSize(SIZE, SIZE);
    interact.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession() || events == null) return;
        events.dispatch(InteractEvent.obtain());
      }
    });

    skills = new HotkeyButton[5];
    for (int i = 0; i < skills.length; i++) {
      skills[i] = new HotkeyButton(Skillicon, 0, -1);
      skills[i].setSize(SIZE, SIZE);
    }

    gestureListener = new ActorGestureListener() {
      private final Vector2 tmpVec2 = new Vector2();

      @Override
      public boolean longPress(Actor actor, float x, float y) {
        if (!isCurrentSession() || session.owner() == null) return false;
        SpellsQuickPanel spellsQuickPanel = session.owner().spellsQuickPanelR;
        final boolean visible = !spellsQuickPanel.isVisible();
        if (visible) {
          HotkeyButton dst = (HotkeyButton) actor;
          spellsQuickPanel.setObserver(dst);
          observedPanel = spellsQuickPanel;
          // FIXME: Scale this better
          tmpVec2.set(actor.getWidth(), actor.getHeight());
          actor.localToStageCoordinates(tmpVec2);
          spellsQuickPanel.setPosition(tmpVec2.x, tmpVec2.y, Align.bottomRight);
        }
        spellsQuickPanel.setVisible(visible);
        return true;
      }

      @Override
      public void tap(InputEvent event, float x, float y, int count, int button) {
        if (!isCurrentSession() || session.owner() == null) return;
        if (session.owner().spellsQuickPanelR.isVisible()) {
          session.owner().spellsQuickPanelR.setVisible(false);
          return;
        }

        HotkeyButton actor = (HotkeyButton) event.getListenerActor();
        // Button's disabled drawable is not sufficient here: the gesture
        // listener receives taps independently of Scene2D's Button listener.
        // Respect the same availability state used by the HUD so a red
        // Throw/Attack icon cannot still start an animation or packet.
        actor.refreshDisabled();
        if (actor.isDisabled()) return;
        final int skillId = actor.getSkill();
        if (skillId == -1) return;
        final int player = session.owner().player;
        if (player < 0 || mAngle == null || mPosition == null
            || !mAngle.has(player) || !mPosition.has(player)) return;
        tmpVec2.set(mAngle.get(player).target).scl(12).add(mPosition.get(player).position);
        if (socket == null) {
          if (actioneer != null) actioneer.cast(player, skillId, Engine.INVALID_ENTITY, tmpVec2);
        } else {
          NetworkedActionSender.cast(socket, skillId, Engine.INVALID_ENTITY, tmpVec2);
        }
        // TODO: above target is placeholder
      }
    };
    gestureListener.getGestureDetector().setLongPressSeconds(0.5f);
    for (Actor button : skills) button.addListener(gestureListener);

    addActor(interact);
    for (Actor button : skills) addActor(button);

    setSize(256, 256);
    setTouchable(Touchable.childrenOnly);

    interact.setPosition(getWidth(), 0, Align.bottomRight);
    skills[0].setPosition(getWidth(), 72, Align.bottomRight);
    skills[1].setPosition(getWidth() - 72, 72, Align.bottomRight);
    skills[2].setPosition(getWidth() - 72, 0, Align.bottomRight);
    skills[3].setPosition(getWidth() - 144, 72, Align.bottomRight);
    skills[4].setPosition(getWidth() - 144, 0, Align.bottomRight);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      if (gestureListener != null) cleanup.run(() -> gestureListener.getGestureDetector().cancel());
      // The quick chooser only borrows a skill slot; do not clear a newer observer.
      if (observedPanel != null && skills != null) {
        for (HotkeyButton skill : skills) {
          if (skill != null && observedPanel.observer == skill) {
            cleanup.run(() -> observedPanel.setObserver(null));
            cleanup.run(() -> observedPanel.setVisible(false));
            break;
          }
        }
      }
      cleanup.run(this::clearListeners);
      if (interact != null) {
        cleanup.run(interact::clearListeners);
        cleanup.run(interact::remove);
        cleanup.dispose(interact);
      }
      if (skills != null) for (HotkeyButton skill : skills) {
        if (skill == null) continue;
        cleanup.run(skill::clearListeners);
        cleanup.run(skill::remove);
        cleanup.dispose(skill);
      }
      cleanup.run(this::clearChildren);
      cleanup.run(this::remove);
      cleanup.run(() -> session.dispose(this));
      cleanup.dispose(references);
    } finally {
      Skillicon = null;
      SoSkillicon = null;
      interact = null;
      skills = null;
      events = null;
      actioneer = null;
      socket = null;
      mAngle = null;
      mPosition = null;
      gestureListener = null;
      observedPanel = null;
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (!isCurrentSession()) return;
    super.draw(batch, parentAlpha);
  }

  @Override
  public void act(float delta) {
    if (isCurrentSession()) super.act(delta);
  }

  private boolean isCurrentSession() { return !disposed && session.isCurrent(); }
  @Override public Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }
}
