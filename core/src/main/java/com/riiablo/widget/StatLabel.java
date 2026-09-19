package com.riiablo.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;

public class StatLabel extends Label {
  Attributes attrs;
  short stat;
  int value;
  boolean initialized;
  Colorizer colorizer;

  public StatLabel(Attributes attrs, short stat) {
    this(attrs, stat, Colorizer.DEFAULT);
  }

  public StatLabel(Attributes attrs, short stat, Colorizer colorizer) {
    super(Riiablo.fonts.font16);
    this.attrs = attrs;
    this.stat = stat;
    this.colorizer = colorizer;
    updateSize = false;
  }

  @Override
  public void act(float delta) {
    updateValue();
    super.act(delta);
  }

  private void updateValue() {
    StatRef s = attrs.get(stat, StatRef.obtain());
    int curValue = s == null ? 0 : s.asInt();
    if (!initialized || value != curValue) {
      initialized = true;
      value = curValue;
      setAlignment(Align.center);
      setText(Integer.toString(value));
    }
    setColor(colorizer.getColor(attrs, s));
  }

  @Override
  public void setText(CharSequence newText) {
    BitmapFont font = getFont(newText.length());
    if (font != getStyle().font) {
      getStyle().font = font;
      setStyle(getStyle()); // hacky, but only way to correct update style with changes
    }

    super.setText(newText);
  }

  private static BitmapFont getFont(int len) {
    if (len > 6) {
      return Riiablo.fonts.ReallyTheLastSucker;
    } else if (len > 3) {
      return Riiablo.fonts.font8;
    } else {
      return Riiablo.fonts.font16;
    }
  }

  public enum Colorizer {
    DEFAULT {
      @Override
      Color getColor(Attributes attrs, StatRef stat) {
        return stat != null && stat.modified()
            ? Riiablo.colors.blue
            : Riiablo.colors.white;
      }
    },
    BASE_DIFFERENCE {
      @Override
      Color getColor(Attributes attrs, StatRef stat) {
        if (stat == null) return Riiablo.colors.white;
        StatRef base = attrs.base().get(stat.id(), StatRef.obtain());
        return base != null && base.encodedValues() != stat.encodedValues()
            ? Riiablo.colors.blue
            : Riiablo.colors.white;
      }
    },
    RESISTANCE {
      @Override
      Color getColor(Attributes attrs, StatRef stat) {
        if (stat == null) return Riiablo.colors.white;
        int value = stat.asInt();
        if (value < 0) {
          return Riiablo.colors.red;
        }

        String maxstat = stat.entry().maxstat;
        if (maxstat != null && !maxstat.isEmpty()) {
          StatRef maximum = attrs.get(Stat.index(maxstat), StatRef.obtain());
          if (maximum != null && value >= maximum.asInt()) return Riiablo.colors.gold;
        }

        StatRef base = attrs.base().get(stat.id(), StatRef.obtain());
        return base != null && base.encodedValues() != stat.encodedValues()
            ? Riiablo.colors.blue
            : Riiablo.colors.white;
      }
    };

    abstract Color getColor(Attributes attrs, StatRef stat);
  }
}
