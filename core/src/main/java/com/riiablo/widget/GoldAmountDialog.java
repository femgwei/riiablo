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
  private static final String OK_PATH = "data\\global\\ui\\MENU\\buttontempok.dc6";
  private static final String CANCEL_PATH = "data\\global\\ui\\MENU\\buttontempcancel.dc6";

  private final AssetDescriptor<DC6> backgroundDescriptor =
      new AssetDescriptor<>(BACKGROUND_PATH, DC6.class);
  private final AssetDescriptor<DC6> okDescriptor =
      new AssetDescriptor<>(OK_PATH, DC6.class);
  private final AssetDescriptor<DC6> cancelDescriptor =
      new AssetDescriptor<>(CANCEL_PATH, DC6.class);

  private final TextureRegion background;
  private final Button okButton;
  private final Button cancelButton;
  private final TextField amount;
  private final boolean allowNegative;
  private Listener listener;
  private float dialogX;
  private float dialogY;

  public GoldAmountDialog(Listener listener) {
    this(listener, false);
  }

  public GoldAmountDialog(Listener listener, boolean allowNegative) {
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
    Riiablo.assets.load(okDescriptor);
    Riiablo.assets.load(cancelDescriptor);
    Riiablo.assets.finishLoadingAsset(backgroundDescriptor);
    Riiablo.assets.finishLoadingAsset(okDescriptor);
    Riiablo.assets.finishLoadingAsset(cancelDescriptor);

    DC6 backgroundDc6 = Riiablo.assets.get(backgroundDescriptor);
    DC6 okDc6 = Riiablo.assets.get(okDescriptor);
    DC6 cancelDc6 = Riiablo.assets.get(cancelDescriptor);
    background = backgroundDc6.getTexture();

    TextureRegion okUp = frame(okDc6, 0);
    TextureRegion okDown = frame(okDc6, 1);
    TextureRegion cancelUp = frame(cancelDc6, 0);
    TextureRegion cancelDown = frame(cancelDc6, 1);
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
    addActor(okButton);
    addActor(cancelButton);

    amount = new TextField("", new TextField.TextFieldStyle() {{
      font = Riiablo.fonts.fontformal11;
      fontColor = Riiablo.colors.white;
      cursor = new TextureRegionDrawable(Riiablo.textures.white);
    }});
    amount.setAlignment(Align.center);
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

    amount.setText("");
    amount.setBounds(dialogX + 29, dialogY + 61, 170, 27);
    placeButton(okButton, dialogX + 34, dialogY + 14);
    placeButton(cancelButton, dialogX + 112, dialogY + 14);
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
    Riiablo.assets.unload(backgroundDescriptor.fileName);
    Riiablo.assets.unload(okDescriptor.fileName);
    Riiablo.assets.unload(cancelDescriptor.fileName);
  }

  private static TextureRegion frame(DC6 dc6, int index) {
    return dc6.getTexture(Math.min(index, dc6.getNumFramesPerDir() - 1));
  }

  public interface Listener {
    void submitted(String text);
    default void canceled() {}
  }
}
