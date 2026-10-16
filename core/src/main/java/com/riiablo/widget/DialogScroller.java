package com.riiablo.widget;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.excel.Speech;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.util.Disposal;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class DialogScroller extends Table implements Disposable {
  private static final Logger log = LogManager.getLogger(DialogScroller.class);

  private final FontTBL.BitmapFont font = Riiablo.fonts.fontformal11;
  private final Audio audioHost = java.util.Objects.requireNonNull(Riiablo.audio, "audio");
  public static final float PADDING = 8;

  DialogCompletionListener listener;
  TextArea textArea;
  ScrollPane scrollPane;
  float scrollSpeed;
  Audio.Instance audio;
  private boolean disposed;
  private boolean completed = true;

  public DialogScroller(DialogCompletionListener listener) {
    this.listener = listener;
    try {
      initialize();
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    setTouchable(Touchable.disabled);
    //setDebug(true, true);

    textArea = new TextArea("", new TextArea.TextFieldStyle() {{
      font = DialogScroller.this.font;
      fontColor = Riiablo.colors.white;
    }}) {
      int count;
      float prefHeight;

      @Override
      public float getPrefHeight() {
        return prefHeight;
      }

      @Override
      public void setText(String str) {
        super.setText(str);
        count = StringUtils.countMatches(str, '\n') + 1;
        prefHeight = count * getStyle().font.getLineHeight();
        setHeight(getPrefHeight());
      }
    };

    scrollPane = new ScrollPane(textArea);
    scrollPane.setTouchable(Touchable.disabled);
    scrollPane.setSmoothScrolling(false);
    scrollPane.setFlickScroll(false);
    scrollPane.setFlingTime(0);
    scrollPane.setOverscroll(false, false);
    scrollPane.setClamp(false);
    scrollPane.setScrollX(-4); // FIXME: actual preferred width of text isn't calculated anywhere, this is best guess
    add(scrollPane).pad(PADDING).grow();
    pack();
  }

  public void setTextId(String str) {
    if (disposed) return;
    setText(Riiablo.string.lookup(str));
  }

  public void setText(String str) {
    if (disposed) return;
    stop();
    textArea.setText(str);
    scrollPane.layout();
    scrollSpeed = 0;
    scrollPane.setScrollY(0);
  }

  public void play(String dialog) {
    if (disposed) return;
    stop();
    // FIXME: scrollSpeed should be in pixels/sec, but timing is off by about 10-15%
    //        problem seems to be with fontformat11 metrics, applying scalar to line height
    final float lineScalar = 0.85f;
    Speech.Entry speech = Riiablo.files.speech.get(dialog);
    if (speech == null) {
      log.error("Missing speech mapping: {}", dialog);
      textArea.setText(Riiablo.bundle.get("unknown"));
      scrollPane.layout();
      scrollSpeed = 0;
      scrollPane.setScrollY(0);
      audio = null;
      return;
    }
    String text = Riiablo.string.lookup(speech.soundstr);
    String[] parts = text.split("\n", 2);
    scrollSpeed = parts.length > 1
        ? NumberUtils.toFloat(parts[0]) / 60 * font.getLineHeight() * lineScalar
        : font.getLineHeight();
    textArea.setText(parts.length > 1 ? parts[1] : parts[0]);
    scrollPane.layout();

    scrollPane.setScrollY(-scrollPane.getScrollHeight() + textArea.getStyle().font.getLineHeight() / 2);
    audio = audioHost.play(dialog, false, Audio.Channel.SPEECH);
    completed = false;
  }

  @Override
  public void act(float delta) {
    if (disposed || completed || scrollPane == null) return;
    scrollPane.setScrollY(scrollPane.getScrollY() + (scrollSpeed * delta));
    scrollPane.act(delta);
    if (scrollPane.getScrollY() > textArea.getPrefHeight()) {
      stop();
      if (listener != null) listener.onCompleted(this);
    }
  }

  /** Ends the current playback without retiring this reusable scroller. */
  public void stop() {
    completed = true;
    scrollSpeed = 0;
    Audio.Instance closing = audio;
    audio = null;
    if (closing != null) closing.stop();
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    listener = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::stop);
      cleanup.run(() -> clearActorTree(this));
      cleanup.run(this::remove);
    } finally {
      textArea = null;
      scrollPane = null;
    }
  }

  private static void clearActorTree(Actor actor) {
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(actor::clearActions);
      cleanup.run(actor::clearListeners);
      if (actor instanceof Group) {
        Group group = (Group) actor;
        for (Actor child : group.getChildren().toArray(Actor.class)) {
          cleanup.run(() -> clearActorTree(child));
        }
        cleanup.run(group::clearChildren);
      }
    }
  }

  @Override public void draw(Batch batch, float parentAlpha) {
    if (!disposed) super.draw(batch, parentAlpha);
  }

  public interface DialogCompletionListener {
    void onCompleted(DialogScroller d);
  }
}
