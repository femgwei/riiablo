package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
    cancelButton.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        cancel();
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
    }});
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
    if (getParent() != null) {
      setSize(getParent().getWidth(), getParent().getHeight());
    }
    dialogX = (getWidth() - background.getRegionWidth()) / 2f;
    dialogY = (getHeight() - background.getRegionHeight()) / 2f;

    amount.setText("0");
    amount.setBounds(dialogX + 30, dialogY + 61, 169, 26);
    title.setBounds(dialogX + 8, dialogY + 108, background.getRegionWidth() - 16, 20);
    placeButton(increaseButton, dialogX + 7, dialogY + 79);
    placeButton(decreaseButton, dialogX + 7, dialogY + 61);
    placeButton(okButton, dialogX + 35, dialogY + 14);
    placeButton(cancelButton, dialogX + 140, dialogY + 14);
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
    if (next < -999_999_999L) next = -999_999_999L;
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
