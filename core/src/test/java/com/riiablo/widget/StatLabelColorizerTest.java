package com.riiablo.widget;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatLabelColorizerTest extends RiiabloTest {
  private Colors previousColors;

  @BeforeEach
  void setUpColors() {
    previousColors = Riiablo.colors;
    if (Riiablo.colors == null) Riiablo.colors = new Colors();
  }

  @AfterEach
  void restoreColors() {
    Riiablo.colors = previousColors;
  }

  @Test
  void permanentPointIncreaseStaysWhite() {
    Attributes attrs = attributes(21, 21);
    assertSame(Riiablo.colors.white, StatLabel.Colorizer.BASE_DIFFERENCE.getColor(
        attrs, attrs.get(Stat.strength, StatRef.obtain())));
  }

  @Test
  void equipmentBonusIsBlueEvenAfterBaseValueChanges() {
    Attributes attrs = attributes(21, 26);
    assertSame(Riiablo.colors.blue, StatLabel.Colorizer.BASE_DIFFERENCE.getColor(
        attrs, attrs.get(Stat.strength, StatRef.obtain())));
  }

  private static Attributes attributes(int base, int aggregate) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.strength, base);
    attrs.reset();
    attrs.aggregate().put(Stat.strength, aggregate);
    return attrs;
  }
}
