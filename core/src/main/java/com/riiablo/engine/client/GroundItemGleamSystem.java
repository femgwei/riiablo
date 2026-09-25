package com.riiablo.engine.client;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Position;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.profiler.GpuSystem;

/**
 * Renders the native ground-item gleam.  D2 does not put dropped items on the
 * Automap; it periodically plays the small {@code overlays/Gleam.dcc} overlay
 * at the item's ground anchor instead.
 */
@GpuSystem
@All({Item.class, Position.class})
public final class GroundItemGleamSystem extends BaseEntitySystem {
  private static final AssetDescriptor<DCC> GLEAM_DESCRIPTOR =
      new AssetDescriptor<>("data\\global\\overlays\\Gleam.dcc", DCC.class);
  private static final float PERIOD_SECONDS = 5f;

  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Position> mPosition;

  @com.artemis.annotations.Wire(name = "iso")
  protected IsometricCamera iso;
  @com.artemis.annotations.Wire(name = "batch")
  protected PaletteIndexedBatch batch;

  private final IntMap<GleamState> states = new IntMap<>();
  private final Vector2 screen = new Vector2();
  private boolean loadQueued;

  private static final class GleamState {
    final Animation animation;
    float nextAt;
    boolean playing;

    GleamState(Animation animation, float nextAt) {
      this.animation = animation;
      this.nextAt = nextAt;
    }
  }

  @Override
  protected void begin() {
    if (!loadQueued && Riiablo.assets != null) {
      Riiablo.assets.load(GLEAM_DESCRIPTOR);
      loadQueued = true;
    }
  }

  @Override
  protected void inserted(int entityId) {
    states.remove(entityId);
  }

  @Override
  protected void removed(int entityId) {
    states.remove(entityId);
  }

  @Override
  protected void processSystem() {
    if (Riiablo.assets == null || !Riiablo.assets.isLoaded(GLEAM_DESCRIPTOR)
        || iso == null || batch == null) return;

    DCC dcc = Riiablo.assets.get(GLEAM_DESCRIPTOR);
    float now = world.getDelta();
    // Render systems are invoked once per visible frame; retain a monotonic
    // local clock rather than using the fixed simulation tick.
    elapsed += now;
    float delta = now;
    batch.begin();
    batch.setColor(1f, 1f, 1f, 1f);
    for (int i = 0, size = getEntityIds().size(); i < size; i++) {
      int entityId = getEntityIds().get(i);
      Item component = mItem.get(entityId);
      if (component == null || component.item == null
          || component.item.location != com.riiablo.item.Location.GROUND) continue;

      GleamState state = states.get(entityId);
      if (state == null) {
        Animation animation = Animation.builder().layer(dcc, BlendMode.LUMINOSITY).build();
        animation.setMode(Animation.Mode.CLAMP);
        state = new GleamState(animation, elapsed + PERIOD_SECONDS);
        states.put(entityId, state);
      }

      if (!state.playing && elapsed >= state.nextAt) {
        state.animation.restart();
        state.playing = true;
        state.nextAt = elapsed + PERIOD_SECONDS;
      }
      if (!state.playing) continue;

      state.animation.act(delta);
      Position position = mPosition.get(entityId);
      if (position != null) {
        iso.toScreen(screen.set(position.position));
        state.animation.draw(batch, screen.x, screen.y);
      }
      if (state.animation.isFinished()) state.playing = false;
    }
    batch.end();
  }

  private float elapsed;
}
