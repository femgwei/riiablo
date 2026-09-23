package com.riiablo.widget;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.excel.Speech;
import com.riiablo.graphics.BorderedPaletteIndexedDrawable;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

// FIXME: should extend DialogScroller
public class NpcDialogBox extends Table implements Disposable {
  private static final Logger log = LogManager.getLogger(NpcDialogBox.class);

  DialogCompletionListener listener;
  TextArea textArea;
  ScrollPane scrollPane;
  float scrollSpeed;
  Audio.Instance audio;

  public NpcDialogBox(String sound, DialogCompletionListener listener) {
    this.listener = listener;
    setBackground(new BorderedPaletteIndexedDrawable());
    setTouchable(Touchable.disabled);
    //setDebug(true, true);

    // FIXME: scrollSpeed should be in pixels/sec, but timing is off by about 10-15%
    //        problem seems to be with fontformat11 metrics, applying scalar to line height
    final float lineScalar = 0.85f;
    final FontTBL.BitmapFont FONT = Riiablo.fonts.fontformal11;
    Speech.Entry speech = Riiablo.files.speech.get(sound);
    if (speech == null) {
      // Speech IDs are data keys and must not be derived from localized NPC
      // display names. Keep malformed/missing rows from taking down the client.
      log.error("Missing speech mapping: {}", sound);
    }
    String text = speech != null && speech.soundstr != null
        ? Riiablo.string.lookup(speech.soundstr)
        : Riiablo.bundle.get("unknown");
    String[] parts = text.split("\n", 2);
    String body = parts.length > 1 ? parts[1] : parts[0];
    scrollSpeed = parts.length > 1
        ? NumberUtils.toFloat(parts[0]) / 60 * FONT.getLineHeight() * lineScalar
        : FONT.getLineHeight();
    final int count = StringUtils.countMatches(body, '\n');
    textArea = new TextArea(body, new TextArea.TextFieldStyle() {{
      font = FONT;
      fontColor = Riiablo.colors.white;
    }}) {
      final float prefHeight = count * getStyle().font.getLineHeight();

      @Override
      public float getPrefHeight() {
        return prefHeight;
      }
    };

    scrollPane = new ScrollPane(textArea);
    scrollPane.setTouchable(Touchable.disabled);
    scrollPane.setSmoothScrolling(false);
    scrollPane.setFlickScroll(false);
    scrollPane.setFlingTime(0);
    scrollPane.setOverscroll(false, false);
    scrollPane.setClamp(false);
    scrollPane.setScrollX(-15); // FIXME: actual preferred width of text isn't calculated anywhere, this is best guess
    add(scrollPane).size(330, 128);
    pack();

    scrollPane.setScrollY(-scrollPane.getScrollHeight() + textArea.getStyle().font.getLineHeight() / 2);
    audio = speech == null ? null : Riiablo.audio.play(sound, false);
  }

  @Override
  public void act(float delta) {
    scrollPane.setScrollY(scrollPane.getScrollY() + (scrollSpeed * delta));
    scrollPane.act(delta);
    if (scrollPane.getScrollY() > textArea.getPrefHeight()) {
      listener.onCompleted(this);
    }
  }

  @Override
  public void dispose() {
    if (audio != null) audio.stop();
  }

  public interface DialogCompletionListener {
    void onCompleted(NpcDialogBox d);
  }
}
