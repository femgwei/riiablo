package com.riiablo.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.save.CharData;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

/**
 * Native in-game gold amount dialog.  The background and button artwork are
 * the original MENU resources; only the editable number and callbacks are
 * provided by the client.
 */
public final class GoldAmountDialog extends WidgetGroup implements Disposable {
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private Stage focusStage;
  private boolean disposed;
  private java.util.function.BooleanSupplier sessionGuard;
  public void setSessionGuard(java.util.function.BooleanSupplier guard) {
    if (!disposed) sessionGuard = guard;
  }
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

  private TextureRegion background;
  private Button okButton;
  private Button cancelButton;
  private Button increaseButton;
  private Button decreaseButton;
  private Label title;
  private TextField amount;
  private final boolean allowNegative;
  private Listener listener;
  private MaximumProvider maximumProvider;
  private float dialogX;
  private float dialogY;

  public GoldAmountDialog(String titleText, Listener listener) {
    this(titleText, listener, false);
  }

  public GoldAmountDialog(String titleText, Listener listener, boolean allowNegative) {
    this.listener = listener;
    this.allowNegative = allowNegative;
    try { initialize(titleText); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize(String titleText) {
    setTouchable(Touchable.enabled);
    addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        return isCurrentSession();
      }

      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        if (!isCurrentSession() || !isVisible()) return false;
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
        if (!isCurrentSession() || !isVisible()) return false;
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

    references.load(backgroundDescriptor);
    references.load(okCancelDescriptor);
    references.load(goldButtonDescriptor);
    assets.finishLoadingAsset(backgroundDescriptor);
    assets.finishLoadingAsset(okCancelDescriptor);
    assets.finishLoadingAsset(goldButtonDescriptor);

    DC6 backgroundDc6 = assets.get(backgroundDescriptor);
    DC6 okCancelDc6 = assets.get(okCancelDescriptor);
    DC6 goldButtonDc6 = assets.get(goldButtonDescriptor);
    background = backgroundDc6.getTexture();

    TextureRegion cancelUp = frame(okCancelDc6, 10);
    TextureRegion cancelDown = frame(okCancelDc6, 11);
    TextureRegion okUp = frame(okCancelDc6, 16);
    TextureRegion okDown = frame(okCancelDc6, 17);
    okButton = new SessionButton(new Button.ButtonStyle(
        new TextureRegionDrawable(okUp), new TextureRegionDrawable(okDown)));
    cancelButton = new SessionButton(new Button.ButtonStyle(
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
        GoldAmountDialog.this.cancel();
      }
    });
    // Keep a direct touch path as well as ClickListener.  This is important
    // for the native square glyph: some Stage/input combinations deliver the
    // button release without synthesizing ClickListener.clicked.
    cancelButton.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      private boolean pressed;

      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (!isCurrentSession() || !isVisible()) return false;
        pressed = button == Input.Buttons.LEFT;
        return pressed;
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        if (!isCurrentSession() || !isVisible()) { pressed = false; return; }
        if (pressed && button == Input.Buttons.LEFT && cancelButton.hit(x, y, true) != null) {
          cancel();
        }
        pressed = false;
      }
    });
    increaseButton = new SessionButton(new Button.ButtonStyle(
        new TextureRegionDrawable(frame(goldButtonDc6, 0)),
        new TextureRegionDrawable(frame(goldButtonDc6, 1))));
    decreaseButton = new SessionButton(new Button.ButtonStyle(
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
    title.setSessionGuard(this::isCurrentSession);
    addActor(title);

    amount = new TextField("", new TextField.TextFieldStyle() {{
      font = Riiablo.fonts.fontformal11;
      fontColor = Riiablo.colors.white;
      cursor = new TextureRegionDrawable(Riiablo.textures.white);
    }}) {
      @Override public boolean isDisabled() { return !isCurrentSession() || super.isDisabled(); }
      @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }
      @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
      @Override public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
        return isCurrentSession() ? super.hit(x, y, touchable) : null;
      }
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
    amount.setTextFieldListener((textField, c) -> clampAmount());
    amount.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        if (!isCurrentSession() || !isVisible()) return false;
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
    if (!isCurrentSession()) return;
    close();
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
    focusAmount();
  }

  private void focusAmount() {
    try {
      Stage current = getStage();
      if (focusStage != current) {
        Stage previous = focusStage;
        focusStage = null;
        if (previous != null && previous.getKeyboardFocus() == amount) previous.setKeyboardFocus(null);
      }
      focusStage = current;
      if (focusStage != null && !focusStage.setKeyboardFocus(amount)) close();
    } catch (RuntimeException | Error failure) {
      try { close(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  public void close() {
    super.setVisible(false);
    Stage previous = focusStage;
    focusStage = null;
    try (Disposal cleanup = new Disposal()) {
      if (amount != null) cleanup.run(() -> amount.getDefaultInputListener().keyUp(null, 0));
      if (previous != null) cleanup.run(() -> {
        if (previous.getKeyboardFocus() == amount) previous.setKeyboardFocus(null);
      });
    }
  }

  public boolean isOpen() {
    return isVisible();
  }

  public void setListener(Listener listener) {
    if (!isCurrentSession()) return;
    this.listener = listener;
  }

  public void setTitle(String titleText) {
    if (!isCurrentSession()) return;
    title.setText(titleText);
  }

  public void setMaximumProvider(MaximumProvider maximumProvider) {
    if (!isCurrentSession()) return;
    this.maximumProvider = maximumProvider;
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
    if (!isCurrentSession() || !isVisible()) return;
    clampAmount();
    String value = amount.getText().trim();
    if (value.isEmpty()) return;
    close();
    if (isCurrentSession() && listener != null) listener.submitted(value);
  }

  private void adjust(int delta) {
    if (!isCurrentSession() || !isVisible()) return;
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
    clampAmount();
    focusAmount();
  }

  private void clampAmount() {
    if (!isCurrentSession() || maximumProvider == null) return;
    String text = amount.getText().trim();
    if (text.isEmpty() || "-".equals(text)) return;
    try {
      long value = Long.parseLong(text);
      boolean negative = value < 0;
      int maximum = Math.max(0, maximumProvider.maximum(negative));
      if ((!negative && value <= maximum) || (negative && -value <= maximum)) return;
      amount.setText(negative && maximum > 0 ? "-" + maximum : Integer.toString(maximum));
      amount.setCursorPosition(amount.getText().length());
    } catch (NumberFormatException ignored) {
      // The field length and digit filter normally make overflow impossible.
    }
  }

  private void cancel() {
    if (!isCurrentSession() || !isVisible()) return;
    close();
    if (isCurrentSession() && listener != null) listener.canceled();
  }

  private boolean isCurrentSession() {
    return !disposed && character == Riiablo.charData && assets == Riiablo.assets
        && (sessionGuard == null || sessionGuard.getAsBoolean());
  }

  private class SessionButton extends Button {
    SessionButton(ButtonStyle style) { super(style); setSessionGuard(GoldAmountDialog.this::isCurrentSession); }
    @Override public boolean isDisabled() { return !isCurrentSession() || super.isDisabled(); }
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    if (!visible) close();
    else super.setVisible(true);
  }

  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }

  @Override public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (!isCurrentSession()) return;
    if (isVisible()) batch.draw(background, getX() + dialogX, getY() + dialogY);
    super.draw(batch, parentAlpha);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    listener = null; maximumProvider = null; sessionGuard = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::close);
      cleanup.run(() -> setTouchable(Touchable.disabled));
      if (amount != null) cleanup.run(() -> {
        amount.setDisabled(true);
        amount.setTextFieldListener(null); amount.setTextFieldFilter(null);
      });
      cleanup.run(() -> ActorTree.clear(this));
      for (Button button : new Button[] {okButton, cancelButton, increaseButton, decreaseButton}) {
        cleanup.dispose(button);
        cleanup.run(() -> ActorTree.clear(button));
      }
      cleanup.dispose(references);
    } finally {
      background = null; okButton = null; cancelButton = null;
      increaseButton = null; decreaseButton = null; title = null; amount = null;
    }
  }

  private static TextureRegion frame(DC6 dc6, int index) {
    return dc6.getTexture(Math.min(index, dc6.getNumFramesPerDir() - 1));
  }

  public interface Listener {
    void submitted(String text);
    default void canceled() {}
  }

  public interface MaximumProvider {
    int maximum(boolean negative);
  }
}
