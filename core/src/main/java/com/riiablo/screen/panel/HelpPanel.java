package com.riiablo.screen.panel;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Keys;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.key.MappedKey;
import com.riiablo.widget.Button;
import com.riiablo.widget.Label;

/** Diablo II's in-game help overlay, assembled from the original 640x480 resources. */
public class HelpPanel extends WidgetGroup implements Disposable {
  static final float LOGICAL_WIDTH = 640f;
  static final float LOGICAL_HEIGHT = 480f;
  private static final float HUD_X_SCALE = 0.8f;
  private static final float HUD_Y_OFFSET = -120f;

  private final AssetDescriptor<DC6> borderDescriptor = new AssetDescriptor<>(
      "data\\global\\ui\\MENU\\helpborder.DC6", DC6.class);
  private final AssetDescriptor<DC6> yellowBulletDescriptor = new AssetDescriptor<>(
      "data\\global\\ui\\MENU\\helpyellowbullet.DC6", DC6.class);
  private final AssetDescriptor<DC6> whiteBulletDescriptor = new AssetDescriptor<>(
      "data\\global\\ui\\MENU\\helpwhitebullet.DC6", DC6.class);
  private final AssetDescriptor<DC6> closeButtonDescriptor = new AssetDescriptor<>(
      "data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);

  private final WidgetGroup canvas = new WidgetGroup();
  private final DC6 border;
  private final TextureRegion yellowBullet;
  private final TextureRegion whiteBullet;

  public HelpPanel() {
    Riiablo.assets.load(borderDescriptor);
    Riiablo.assets.load(yellowBulletDescriptor);
    Riiablo.assets.load(whiteBulletDescriptor);
    Riiablo.assets.load(closeButtonDescriptor);
    Riiablo.assets.finishLoadingAsset(borderDescriptor);
    Riiablo.assets.finishLoadingAsset(yellowBulletDescriptor);
    Riiablo.assets.finishLoadingAsset(whiteBulletDescriptor);
    Riiablo.assets.finishLoadingAsset(closeButtonDescriptor);

    border = Riiablo.assets.get(borderDescriptor);
    yellowBullet = Riiablo.assets.get(yellowBulletDescriptor).getTexture(0);
    whiteBullet = Riiablo.assets.get(whiteBulletDescriptor).getTexture(0);

    setFillParent(true);
    setTouchable(Touchable.enabled);
    setVisible(false);
    addListener(new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        return true;
      }

      @Override
      public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
        return true;
      }
    });

    canvas.setSize(LOGICAL_WIDTH, LOGICAL_HEIGHT);
    canvas.addActor(new Artwork());
    addActor(canvas);

    addCenteredLabel(text("Strhelp1"), 283f, 2f, 260f, 24f,
        Riiablo.fonts.font16, Riiablo.colors.gold);
    addBulletedHelp();
    addHudCallouts();

    DC6 closeButton = Riiablo.assets.get(closeButtonDescriptor);
    Button close = new Button(new Button.ButtonStyle(
        new TextureRegionDrawable(closeButton.getTexture(10)),
        new TextureRegionDrawable(closeButton.getTexture(11))));
    close.setSize(32f, 32f);
    close.setPosition(525f, fromTop(25f, close.getHeight()));
    close.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        close();
      }
    });
    canvas.addActor(close);
    addCenteredLabel(text("strClose"), 542f, 60f, 100f, 20f,
        Riiablo.fonts.font16, Riiablo.colors.gold);
  }

  private void addBulletedHelp() {
    String[] entries = {
        format("StrHelp2", "Ctrl"),
        format("StrHelp3", "Alt"),
        format("StrHelp4", "Shift"),
        format("StrHelp5", keyName(Keys.Automap)),
        text("StrHelp6"),
        text("StrHelp7"),
        text("StrHelp8"),
        format("StrHelp8a", keyName(Keys.Help))
    };

    for (int i = 0; i < entries.length; i++) {
      addTopLabel(entries[i], 100f, 59f + i * 20f, 500f, 18f,
          Align.left, Riiablo.colors.gold);
    }
  }

  private void addHudCallouts() {
    addHudLabel(text("strlvlup"), 222f, 355f, 150f);
    addHudLabel(text("strnewskl"), 578f, 355f, 150f);

    addHudLabel(text("StrHelp10"), 135f, 382f, 180f);
    addHudLabel(text("StrHelp11"), 135f, 397f, 180f);
    addHudLabel(text("StrHelp12"), 135f, 412f, 180f);

    addHudLabel(text("StrHelp13"), 675f, 381f, 180f);
    addHudLabel(text("StrHelp11"), 675f, 396f, 180f);
    addHudLabel(text("StrHelp12"), 675f, 411f, 180f);

    addHudLabel(text("StrHelp17"), 450f, 371f, 180f);
    addHudLabel(text("StrHelp18"), 450f, 386f, 180f);
    addHudLabel(text("StrHelp19"), 450f, 401f, 180f);
    addHudLabel(text("StrHelp20"), 450f, 417f, 180f);

    addHudLabel(text("StrHelp9"), 65f, 451f, 130f);
    addHudLabel(text("StrHelp15"), 315f, 450f, 150f);
    addHudLabel(text("StrHelp22"), 745f, 451f, 130f);

    addHudLabel(text("StrHelp14"), 264f, 480f, 150f);
    addHudLabel(text("StrHelp14a"), 264f, 495f, 150f);
    addHudLabel(text("StrHelp16"), 370f, 476f, 150f);
    addHudLabel(text("StrHelp16a"), 370f, 493f, 150f);
    addHudLabel(text("StrHelp21"), 535f, 490f, 130f);
  }

  private void addHudLabel(String value, float sourceX, float sourceTop, float width) {
    addCenteredLabel(value, hudX(sourceX), hudTop(sourceTop), width, 18f,
        Riiablo.fonts.fontformal12, Riiablo.colors.white);
  }

  private void addTopLabel(String value, float x, float top, float width, float height,
      int alignment, Color color) {
    Label label = new Label(value, Riiablo.fonts.fontformal12, color);
    label.setAlignment(alignment);
    label.setBounds(x, fromTop(top, height), width, height);
    canvas.addActor(label);
  }

  private void addCenteredLabel(String value, float centerX, float top, float width, float height,
      com.badlogic.gdx.graphics.g2d.BitmapFont font, Color color) {
    Label label = new Label(value, font, color);
    label.setAlignment(Align.center);
    label.setBounds(centerX - width / 2f, fromTop(top, height), width, height);
    canvas.addActor(label);
  }

  private static float fromTop(float top, float height) {
    return LOGICAL_HEIGHT - top - height;
  }

  private static float hudX(float sourceX) {
    return sourceX * HUD_X_SCALE;
  }

  private static float hudTop(float sourceTop) {
    return sourceTop + HUD_Y_OFFSET;
  }

  private static String text(String key) {
    return Riiablo.string.lookup(key);
  }

  private static String format(String key, String value) {
    return Riiablo.string.format(key, value);
  }

  private static String keyName(MappedKey key) {
    int keycode = key.getPrimaryAssignment();
    return keycode == MappedKey.NOT_MAPPED ? "--" : Input.Keys.toString(keycode);
  }

  static float contentScale(float width, float height) {
    return Math.min(1f, Math.min(width / LOGICAL_WIDTH, height / LOGICAL_HEIGHT));
  }

  static float contentX(float width, float scale) {
    return (width - LOGICAL_WIDTH * scale) / 2f;
  }

  @Override
  public void layout() {
    float scale = contentScale(getWidth(), getHeight());
    canvas.setScale(scale);
    canvas.setPosition(contentX(getWidth(), scale), 0f);
  }

  public void open() {
    setVisible(true);
    toFront();
  }

  public void close() {
    setVisible(false);
  }

  public void toggle() {
    if (isVisible()) close();
    else open();
  }

  @Override
  public void dispose() {
    Riiablo.assets.unload(borderDescriptor.fileName);
    Riiablo.assets.unload(yellowBulletDescriptor.fileName);
    Riiablo.assets.unload(whiteBulletDescriptor.fileName);
    Riiablo.assets.unload(closeButtonDescriptor.fileName);
  }

  private final class Artwork extends Actor {
    private final TextureRegion[] frames = new TextureRegion[8];

    Artwork() {
      for (int i = 0; i < frames.length; i++) frames[i] = border.getTexture(i);
      setSize(LOGICAL_WIDTH, LOGICAL_HEIGHT);
      setTouchable(Touchable.disabled);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      float x = getX();
      float y = getY();
      drawTop(batch, frames[0], x, y, 0f);
      drawTop(batch, frames[1], x + 256f, y, 0f);
      drawTop(batch, frames[2], x, y, 256f);
      drawTop(batch, frames[3], x + 256f, y, 256f);
      drawTop(batch, frames[4], x + 320f, y, 0f);
      drawTop(batch, frames[5], x + 576f, y, 0f);
      drawTop(batch, frames[6], x + 320f, y, 256f);
      drawTop(batch, frames[7], x + 576f, y, 256f);

      for (int i = 0; i < 8; i++) {
        drawTop(batch, yellowBullet, x + 88f, y, 63f + i * 20f);
      }

      drawCallout(batch, x, y, hudX(222f), hudTop(378f), hudX(217f), hudTop(574f));
      drawCallout(batch, x, y, hudX(578f), hudTop(378f), hudX(573f), hudTop(574f));
      drawCallout(batch, x, y, hudX(135f), hudTop(435f), hudX(130f), hudTop(565f));
      drawCallout(batch, x, y, hudX(675f), hudTop(434f), hudX(670f), hudTop(562f));
      drawCallout(batch, x, y, hudX(450f), hudTop(440f), hudX(445f), hudTop(539f));
      drawCallout(batch, x, y, hudX(65f), hudTop(474f), hudX(60f), hudTop(538f));
      drawCallout(batch, x, y, hudX(315f), hudTop(473f), hudX(310f), hudTop(583f));
      drawCallout(batch, x, y, hudX(745f), hudTop(474f), hudX(740f), hudTop(538f));
      drawCallout(batch, x, y, hudX(264f), hudTop(518f), hudX(259f), hudTop(583f));
      drawCallout(batch, x, y, hudX(370f), hudTop(516f), hudX(365f), hudTop(565f));
      drawCallout(batch, x, y, hudX(535f), hudTop(513f), hudX(530f), hudTop(568f));
    }

    private void drawTop(Batch batch, TextureRegion region, float x, float parentY, float top) {
      batch.draw(region, x, parentY + fromTop(top, region.getRegionHeight()));
    }

    private void drawCallout(Batch batch, float parentX, float parentY,
        float lineX, float lineTop, float dotX, float dotTop) {
      float lineBottom = parentY + LOGICAL_HEIGHT - dotTop;
      float lineHeight = Math.max(1f, dotTop - lineTop);
      Color previous = batch.getColor().cpy();
      batch.setColor(Color.WHITE);
      batch.draw(Riiablo.textures.white, parentX + lineX, lineBottom, 1f, lineHeight);
      batch.setColor(previous);
      drawTop(batch, whiteBullet, parentX + dotX, parentY, dotTop);
    }
  }
}
