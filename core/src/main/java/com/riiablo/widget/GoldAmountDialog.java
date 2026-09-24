package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;

/**
 * Native in-game gold amount dialog.  The background and button artwork are
 * the original MENU resources; only the editable number and callbacks are
 * provided by the client.
 */
public final class GoldAmountDialog extends WidgetGroup implements Disposable {
  private static final String BACKGROUND_PATH = "data\\global\\ui\\MENU\\dialogbackground.dc6";
  // The in-game panel sheet contains the native square cancel/confirm glyphs.
  // Frames 10/11 are cancel (normal/pressed), 16/17 are confirm.
  private static final String OK_CANCEL_PATH = "data\\global\\ui\\PANEL\\buysellbtn.dc6";
  private static final String GOLD_BUTTON_PATH = "data\\global\\ui\\BIGMENU\\numberarrows.dc6";

  private final AssetDescriptor<DC6> backgroundDescriptor =
      new AssetDescriptor<>(BACKGROUND_PATH, DC6.class);
  private final AssetDescriptor<DC6> okCancelDescriptor =
      new AssetDescriptor<>(OK_CANCEL_PATH, DC6.class);
  private final AssetDescriptor<DC6> goldButtonDescriptor =
      new AssetDescriptor<>(GOLD_BUTTON_PATH, DC6.class);

  private final TextureRegion background;
  private final Button okButton;
  private final Button cancelButton;
  private final Button increaseButton;
  private final Button decreaseButton;
  private final Label title;
  private final TextField amount;
  private final boolean allowNegative;
  private Listener listener;
  private float dialogX;
  private float dialogY;

  public GoldAmountDialog(String titleText, Listener listener) {
    this(titleText, listener, false);
  }

  public GoldAmountDialog(String titleText, Listener listener, boolean allowNegative) {
    this.listener = listener;
    this.allowNegative = allowNegative;
    setTouchable(Touchable.enabled);
    addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        return true;
      }

      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
          cancel();
          return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
          submit();
          return true;
        }
        return false;
      }
    });
    // TextField handles most key events on its own and may consume ESC before
    // the event bubbles back to this group. Capture it on the dialog so ESC
    // always has the native modal-dialog close behavior.
    addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
          cancel();
          return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
          submit();
          return true;
        }
        return false;
      }
    });

    Riiablo.assets.load(backgroundDescriptor);
    Riiablo.assets.load(okCancelDescriptor);
    Riiablo.assets.load(goldButtonDescriptor);
    Riiablo.assets.finishLoadingAsset(backgroundDescriptor);
    Riiablo.assets.finishLoadingAsset(okCancelDescriptor);
    Riiablo.assets.finishLoadingAsset(goldButtonDescriptor);

    DC6 backgroundDc6 = Riiablo.assets.get(backgroundDescriptor);
    DC6 okCancelDc6 = Riiablo.assets.get(okCancelDescriptor);
    DC6 goldButtonDc6 = Riiablo.assets.get(goldButtonDescriptor);
    background = backgroundDc6.getTexture();

    TextureRegion cancelUp = frame(okCancelDc6, 10);
    TextureRegion cancelDown = frame(okCancelDc6, 11);
    TextureRegion okUp = frame(okCancelDc6, 16);
    TextureRegion okDown = frame(okCancelDc6, 17);
    okButton = new Button(new Button.ButtonStyle(
        new TextureRegionDrawable(okUp), new TextureRegionDrawable(okDown)));
    cancelButton = new Button(new Button.ButtonStyle(
        new TextureRegionDrawable(cancelUp), new TextureRegionDrawable(cancelDown)));
    okButton.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        submit();
      }
    });
    cancelButton.addListener(new ClickListener(Input.Buttons.LEFT) {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        cancel();
      }
    });
    // Keep a direct touch path as well as ClickListener.  This is important
    // for the native square glyph: some Stage/input combinations deliver the
    // button release without synthesizing ClickListener.clicked.
    cancelButton.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      private boolean pressed;

      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        pressed = button == Input.Buttons.LEFT;
        return pressed;
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        if (pressed && button == Input.Buttons.LEFT && cancelButton.hit(x, y, true) != null) {
          cancel();
        }
        pressed = false;
      }
    });
    increaseButton = new Button(new Button.ButtonStyle(
        new TextureRegionDrawable(frame(goldButtonDc6, 0)),
        new TextureRegionDrawable(frame(goldButtonDc6, 1))));
    decreaseButton = new Button(new Button.ButtonStyle(
        new TextureRegionDrawable(frame(goldButtonDc6, 2)),
        new TextureRegionDrawable(frame(goldButtonDc6, 3))));
    increaseButton.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) { adjust(1); }
    });
    decreaseButton.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) { adjust(-1); }
    });
    addActor(okButton);
    addActor(cancelButton);
    addActor(increaseButton);
    addActor(decreaseButton);

    title = new Label(titleText, Riiablo.fonts.fontformal11, Riiablo.colors.gold);
    title.setAlignment(Align.center);
    title.setTouchable(Touchable.disabled);
    addActor(title);

    amount = new TextField("", new TextField.TextFieldStyle() {{
      font = Riiablo.fonts.fontformal11;
      fontColor = Riiablo.colors.white;
      cursor = new TextureRegionDrawable(Riiablo.textures.white);
    }}) {
      @Override
      protected void drawCursor(Drawable cursor, Batch batch, BitmapFont font, float x, float y) {
        // LibGDX positions the cursor from the text baseline. Raise the
        // blinking line four pixels in total to match the native dialog.
        super.drawCursor(cursor, batch, font, x, y + 4f);
      }
    };
    amount.setAlignment(Align.left);
    amount.setMaxLength(9);
    amount.setTextFieldFilter((textField, c) -> Character.isDigit(c)
        || allowNegative && c == '-' && textField.getText().isEmpty());
    amount.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
          submit();
          return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
          cancel();
          return true;
        }
        return false;
      }
    });
    addActor(amount);

    setVisible(false);
  }

  public void open() {
    sizeToStage();
    dialogX = (getWidth() - background.getRegionWidth()) / 2f;
    dialogY = (getHeight() - background.getRegionHeight()) / 2f;

    amount.setText("");
    // dialogbackground already paints the textbox border. Keep the editable
    // text/cursor inset from both inner edges like the native dialog.
    amount.setBounds(dialogX + 45, dialogY + 61, 139, 26);
    title.setBounds(dialogX + 8, dialogY + 108, background.getRegionWidth() - 16, 20);
    placeButton(increaseButton, dialogX + 7, dialogY + 79);
    placeButton(decreaseButton, dialogX + 7, dialogY + 61);
    placeButton(okButton, dialogX + 35, dialogY + 10);
    placeButton(cancelButton, dialogX + 140, dialogY + 10);
    setVisible(true);
    toFront();
    if (getStage() != null) getStage().setKeyboardFocus(amount);
  }

  public void close() {
    setVisible(false);
    if (getStage() != null && getStage().getKeyboardFocus() == amount) {
      getStage().setKeyboardFocus(null);
    }
  }

  public boolean isOpen() {
    return isVisible();
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  private void placeButton(Button button, float x, float y) {
    float width = button.getStyle().up.getMinWidth();
    float height = button.getStyle().up.getMinHeight();
    button.setBounds(x, y, width, height);
  }

  private void sizeToStage() {
    if (getStage() != null && getParent() != null) {
      // This actor belongs to InventoryPanel/StashPanel, whose origin is at a
      // side of the viewport. Convert the Stage bounds into that parent's
      // coordinates so the modal and its artwork are centered on the screen,
      // not merely within the owning panel.
      Vector2 bottomLeft = getParent().stageToLocalCoordinates(new Vector2(0, 0));
      Vector2 topRight = getParent().stageToLocalCoordinates(
          new Vector2(getStage().getWidth(), getStage().getHeight()));
      setBounds(bottomLeft.x, bottomLeft.y,
          topRight.x - bottomLeft.x, topRight.y - bottomLeft.y);
    } else if (getParent() != null) {
      setBounds(0, 0, getParent().getWidth(), getParent().getHeight());
    }
  }

  private void submit() {
    if (!isVisible()) return;
    String value = amount.getText().trim();
    if (value.isEmpty()) return;
    close();
    if (listener != null) listener.submitted(value);
  }

  private void adjust(int delta) {
    int value;
    try {
      value = Integer.parseInt(amount.getText().trim());
    } catch (NumberFormatException e) {
      value = 0;
    }
    long next = (long) value + delta;
    if (next > 999_999_999L) next = 999_999_999L;
    // Native arrow controls never cross below zero. Stash withdrawal remains
    // available by typing a negative value when allowNegative is enabled.
    if (next < 0) next = 0;
    amount.setText(Long.toString(next));
    if (getStage() != null) getStage().setKeyboardFocus(amount);
  }

  private void cancel() {
    if (!isVisible()) return;
    close();
    if (listener != null) listener.canceled();
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (isVisible()) batch.draw(background, getX() + dialogX, getY() + dialogY);
    super.draw(batch, parentAlpha);
  }

  @Override
  public void dispose() {
    okButton.dispose();
    cancelButton.dispose();
    increaseButton.dispose();
    decreaseButton.dispose();
    Riiablo.assets.unload(backgroundDescriptor.fileName);
    Riiablo.assets.unload(okCancelDescriptor.fileName);
    Riiablo.assets.unload(goldButtonDescriptor.fileName);
  }

  private static TextureRegion frame(DC6 dc6, int index) {
    return dc6.getTexture(Math.min(index, dc6.getNumFramesPerDir() - 1));
  }

  public interface Listener {
    void submitted(String text);
    default void canceled() {}
  }
}
