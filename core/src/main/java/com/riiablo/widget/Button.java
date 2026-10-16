package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.util.Disposal;

public class Button extends com.badlogic.gdx.scenes.scene2d.ui.Button implements Disposable {

  private static final AssetDescriptor<Sound> buttonDescriptor = new AssetDescriptor<>("data\\global\\sfx\\cursor\\button.wav", Sound.class);

  int disabledBlendMode = BlendMode.DARKEN;
  Color disabledColor = Riiablo.colors.darken;

  int highlightedBlendMode = BlendMode.BRIGHTEN;
  Color highlightedColor = Riiablo.colors.highlight;

  int blendMode = BlendMode.ID;
  Color color = Riiablo.colors.white;

  private boolean clickSoundEnabled = true;
  private final AssetManager soundAssets;
  private Sound clickSound;
  private boolean soundDisposed;

  @Override
  public void setStyle(com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle style) {
    super.setStyle(style);
  }

  public Button(ButtonStyle style) {
    super(style);
    soundAssets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
    soundAssets.load(buttonDescriptor);
    addListener(new ClickListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int b) {
        if (event.getButton() != Input.Buttons.LEFT || isDisabled()) {
          return super.touchDown(event, x, y, pointer, b);
        }

        if (clickSoundEnabled && !soundDisposed) {
          if (clickSound == null) {
            soundAssets.finishLoadingAsset(buttonDescriptor);
            clickSound = soundAssets.get(buttonDescriptor);
          }
          clickSound.play();
        }
        Riiablo.input.vibrate(10);
        return super.touchDown(event, x, y, pointer, b);
      }
    });
  }

  public void setClickSoundEnabled(boolean enabled) {
    clickSoundEnabled = enabled;
  }

  public void setBlendMode(int blendMode, Color color) {
    this.blendMode = blendMode;
    this.color = color;
  }

  public void setDisabledBlendMode(int blendMode, Color color) {
    disabledBlendMode = blendMode;
    disabledColor = color;
  }

  public void setHighlightedBlendMode(int blendMode, Color color) {
    highlightedBlendMode = blendMode;
    highlightedColor = color;
  }

  @Override
  public void dispose() {
    disposeClickSound();
  }

  private void disposeClickSound() {
    if (soundDisposed) return;
    soundDisposed = true;
    clickSound = null;
    try (Disposal cleanup = new Disposal()) {
      // As with screen/map assets, do not cancel an active shared-name load.
      // This must run on the resource host's GL/main thread before its shutdown.
      cleanup.run(() -> {
        if (soundAssets.contains(buttonDescriptor.fileName)
            && !soundAssets.isLoaded(buttonDescriptor.fileName)) {
          soundAssets.finishLoadingAsset(buttonDescriptor);
        }
      });
      cleanup.run(() -> {
        if (soundAssets.contains(buttonDescriptor.fileName)) {
          soundAssets.unload(buttonDescriptor.fileName);
        }
      });
    }
  }

  /**
   * Retires only the click-sound references owned by Buttons in an owned subtree.
   * Does not call arbitrary child dispose methods or destroy shared styles/textures.
   * Call before removing/replacing that subtree; detach alone is not final disposal.
   */
  public static void disposeClickSounds(Actor root) {
    Array<Button> buttons = new Array<>();
    collectButtons(root, buttons);
    try (Disposal cleanup = new Disposal()) {
      // Snapshot before releasing anything: a resource callback may remove actors.
      for (Button button : buttons) cleanup.run(button::disposeClickSound);
    }
  }

  private static void collectButtons(Actor actor, Array<Button> buttons) {
    if (actor instanceof Button) buttons.add((Button) actor);
    if (actor instanceof Group) {
      for (Actor child : ((Group) actor).getChildren()) collectButtons(child, buttons);
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (batch instanceof PaletteIndexedBatch) {
      draw((PaletteIndexedBatch) batch, parentAlpha);
    } else {
      super.draw(batch, parentAlpha);
    }
  }

  // FIXME: super.draw(Batch,float) sets color and applies to batch, circumventing my own color -- workaround is to set actor's color
  public void draw(PaletteIndexedBatch batch, float parentAlpha) {
    final boolean disabled = isDisabled();
    final boolean over = isOver();
    if (disabled) {
      setColor(disabledColor);
      batch.setBlendMode(disabledBlendMode);
    } else if (over) {
      setColor(highlightedColor);
      batch.setBlendMode(highlightedBlendMode);
    } else {
      setColor(color);
      batch.setBlendMode(blendMode);
    }
    super.draw(batch, parentAlpha);
    batch.resetBlendMode();
  }

  public static class ButtonStyle extends com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle {
    public ButtonStyle() {}
    public ButtonStyle(Drawable up, Drawable down) {
      this(up, down, null);
    }
    public ButtonStyle(Drawable up, Drawable down, Drawable checked) {
      super(up, down, checked);
    }
    public ButtonStyle(com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle style) {
      super(style);
    }
  }
}
