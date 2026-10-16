package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.codec.util.BBox;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CharacterCreateButton extends Widget implements Disposable {
  private static final String TAG = "CharButton";
  private final AssetManager assets;
  private final AssetReferenceScope references;
  private final Set<String> requested = new HashSet<>();
  private boolean disposed;
  private boolean interactionEnabled = true;
  private long selectId = -1, deselectId = -1;

  public enum State { NU1, FW, NU3, BW }

  private final AssetDescriptor<DC6> bwDesc;
  private final AssetDescriptor<DC6> bwsDesc;
  private final AssetDescriptor<DC6> fwDesc;
  private final AssetDescriptor<DC6> fwsDesc;
  private final AssetDescriptor<DC6> nu1Desc;
  private final AssetDescriptor<DC6> nu2Desc;
  private final AssetDescriptor<DC6> nu3Desc;
  private final AssetDescriptor<DC6> nu3sDesc;

  private final AssetDescriptor<Sound> selectDesc;
  private final AssetDescriptor<Sound> deselectDesc;

  public final CharacterClass charClass;
  private State state = State.NU1;
  private float x, y;

  private Animation active;
  private Animation nu1; // state 0
  private Animation fw;  // state 1
  private Animation bw;  // state 3
  private Animation nu2; // state 0
  private Animation nu3; // state 2

  private Sound select;
  private Sound deselect;

  private final ClickListener clickListener;

  public CharacterCreateButton(final CharacterClass charClass) {
    this(charClass, Riiablo.assets);
  }

  public CharacterCreateButton(final CharacterClass charClass, AssetManager assets) {
    this.charClass = Objects.requireNonNull(charClass, "charClass");
    this.assets = Objects.requireNonNull(assets, "assets");
    references = new AssetReferenceScope(assets);
    bwDesc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "bw.dc6", DC6.class);
    bwsDesc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "bws.dc6", DC6.class);
    fwDesc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "fw.dc6", DC6.class);
    fwsDesc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "fws.dc6", DC6.class);
    nu1Desc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "nu1.dc6", DC6.class);
    nu2Desc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "nu2.dc6", DC6.class);
    nu3Desc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "nu3.dc6", DC6.class);
    nu3sDesc = new AssetDescriptor<>("data\\global\\ui\\FrontEnd\\" + charClass + "\\" + charClass.shortName + "nu3s.dc6", DC6.class);

    selectDesc = new AssetDescriptor<>("data\\global\\sfx\\cursor\\intro\\" + charClass + " select.wav", Sound.class);
    deselectDesc = new AssetDescriptor<>("data\\global\\sfx\\cursor\\intro\\" + charClass + " deselect.wav", Sound.class);

    try {
      load(nu1Desc);

      addListener(clickListener = new ClickListener(Input.Buttons.LEFT) {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!canInteract()) return;
          switch (state) {
            case NU1:
              select();
              break;
            case NU3:
              deselect();
              break;
            default:
              // do nothing
          }
        }

        @Override
        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
          if (!canInteract()) return;
          if (state == State.NU1) {
            loadRemainingAssets();
            if (nu2 == null) {
              assets.finishLoadingAsset(nu2Desc);
              nu2 = Animation.newAnimation(assets.get(nu2Desc));
              nu2.setFrameDuration(1 / 16f);
            }
            setActive(nu2);
            nu2.setFrame(nu1.getFrame());
          }
        }

        @Override
        public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
          if (!canInteract()) return;
          if (state == State.NU1) {
            setActive(nu1);
            if (nu2 != null) nu1.setFrame(nu2.getFrame());
          }
        }
      });

      assets.finishLoadingAsset(nu1Desc);
      nu1 = Animation.newAnimation(assets.get(nu1Desc));
      nu1.setFrameDuration(1 / 16f);
      setActive(nu1);
    } catch (RuntimeException | Error failure) {
      try { dispose(); } catch (RuntimeException | Error cleanup) {
        if (failure != cleanup) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void load(AssetDescriptor descriptor) {
    if (requested.contains(descriptor.fileName)) return;
    references.load(descriptor);
    requested.add(descriptor.fileName);
  }

  public void loadRemainingAssets() {
    if (disposed) throw new IllegalStateException("character button is disposed");
    load(bwDesc);
    load(fwDesc);
    load(nu2Desc);
    load(nu3Desc);
    if (charClass.bws)  load(bwsDesc);
    if (charClass.fws)  load(fwsDesc);
    if (charClass.nu3s) load(nu3sDesc);

    load(selectDesc);
    load(deselectDesc);
  }

  private boolean canInteract() { return !disposed && interactionEnabled; }

  public void setInteractionEnabled(boolean enabled) {
    if (disposed) return;
    interactionEnabled = enabled;
    setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    if (!enabled) {
      clickListener.cancel();
      try (Disposal cleanup = new Disposal()) {
        cleanup.run(this::stopSelect);
        cleanup.run(this::stopDeselect);
      }
    }
  }

  private void stopSelect() {
    long id = selectId;
    selectId = -1;
    if (select != null && id != -1) select.stop(id);
  }

  private void stopDeselect() {
    long id = deselectId;
    deselectId = -1;
    if (deselect != null && id != -1) deselect.stop(id);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    interactionEnabled = false;
    active = nu1 = nu2 = nu3 = fw = bw = null;
    setTouchable(Touchable.disabled);
    requested.clear();
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::stopSelect);
      cleanup.run(this::stopDeselect);
      cleanup.dispose(references);
    } finally {
      select = deselect = null;
    }
  }

  @Override
  public void draw(Batch batch, float a) {
    if (disposed) return;
    active.act();
    BBox offset = active.getBox();
    active.draw(batch, getX() - offset.xMin, getY() + offset.yMax);
  }

  @Override
  public void drawDebug(ShapeRenderer shapes) {
    if (disposed) return;
    if (state == State.NU1 || state == State.NU3) {
      super.drawDebug(shapes);
    }
  }

  private void setActive(Animation animation) {
    if (active != animation) {
      active = animation;
      animation.setFrame(0);
      BBox offset = animation.getBox();
      setSize(offset.width, offset.height);
      super.setPosition(x + offset.xMin, y - offset.yMax);
    }
  }

  @Override
  public void setPosition(float x, float y) {
    if (disposed) return;
    this.x = x;
    this.y = y;
    BBox offset = active.getBox();
    super.setPosition(x + offset.xMin, y - offset.yMax);
  }

  public boolean isSelected() {
    return state == State.NU3;
  }

  public boolean isAnimating() {
    return state == State.FW || state == State.BW;
  }

  public State getState() {
    return state;
  }

  public void select() {
    if (!canInteract()) return;
    loadRemainingAssets();
    state = State.FW;
    if (fw == null) {
      assets.finishLoadingAsset(fwDesc);
      Animation.Builder builder = Animation.builder()
          .layer(assets.get(fwDesc));
      if (charClass.fws) {
        assets.finishLoadingAsset(fwsDesc);
        builder.layer(assets.get(fwsDesc), charClass.blendSpecial);
      }
      fw = builder.build();
      fw.setMode(Animation.Mode.CLAMP);
      fw.addAnimationListener(-1, new Animation.AnimationListener() {
        @Override
        public void onTrigger(Animation fw, int frame) {
          if (disposed) return;
          state = State.NU3;
          if (nu3 == null) {
            assets.finishLoadingAsset(nu3Desc);
            Animation.Builder builder = Animation.builder()
                .layer(assets.get(nu3Desc));
            if (charClass.nu3s) {
              assets.finishLoadingAsset(nu3sDesc);
              builder.layer(assets.get(nu3sDesc), charClass.blendSpecial);
            }
            nu3 = builder.build();
          }
          setActive(nu3);
        }
      });
    }
    setActive(fw);
    stopSelect();
    stopDeselect();
    if (select == null) {
      assets.finishLoadingAsset(selectDesc);
      select = assets.get(selectDesc);
    }
    selectId = select.play();
    CharacterCreateButton.super.toFront();
  }

  public void deselect() {
    if (!canInteract()) return;
    loadRemainingAssets();
    state = State.BW;
    if (bw == null) {
      assets.finishLoadingAsset(bwDesc);
      Animation.Builder builder = Animation.builder()
          .layer(assets.get(bwDesc));
      if (charClass.bws) {
        assets.finishLoadingAsset(bwsDesc);
        builder.layer(assets.get(bwsDesc), charClass.blendSpecial);
      }
      bw = builder.build();
      bw.setMode(Animation.Mode.CLAMP);
      bw.addAnimationListener(-1, new Animation.AnimationListener() {
        @Override
        public void onTrigger(Animation bw, int frame) {
          if (disposed) return;
          state = State.NU1;
          if (clickListener.isOver() && nu2 != null) {
            setActive(nu2);
          } else {
            setActive(nu1);
          }
        }
      });
    }
    setActive(bw);
    stopSelect();
    stopDeselect();
    if (deselect == null) {
      assets.finishLoadingAsset(deselectDesc);
      deselect = assets.get(deselectDesc);
    }
    deselectId = deselect.play();
  }
}
