package com.riiablo.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;

class ResistanceDisplayValueTest extends RiiabloTest {
  @Test
  void fourElementsUseTheirOwnResolvedMaximumWithoutMutatingAuthority() {
    Attributes attrs = Attributes.obtainStandard();
    short[] resistances = {Stat.fireresist, Stat.coldresist, Stat.lightresist, Stat.poisonresist};
    short[] maxima = {Stat.maxfireresist, Stat.maxcoldresist, Stat.maxlightresist, Stat.maxpoisonresist};
    for (int i = 0; i < resistances.length; i++) {
      attrs.base().put(resistances[i], 140 + i);
      attrs.base().put(maxima[i], 75 + i * 5);
    }
    attrs.reset();
    for (int i = 0; i < resistances.length; i++) {
      ResistanceDisplayValue value = ResistanceDisplayValue.capture(attrs, resistances[i]);
      assertEquals(75 + i * 5, value.displayed);
      assertTrue(value.capped);
      assertEquals(140 + i, attrs.getValue(resistances[i], 0));
      assertEquals(140 + i, attrs.base().getValue(resistances[i], 0));
    }
  }

  @Test
  void retainedSnapshotAndOtherPlayerSurviveLaterUpdatesAndSourceRemoval() {
    Attributes owner = attributes(120, 85);
    Attributes observer = attributes(31, 75);
    ResistanceDisplayValue before = ResistanceDisplayValue.capture(owner, Stat.fireresist);
    owner.aggregate().put(Stat.maxfireresist, 75);
    assertEquals(75, ResistanceDisplayValue.capture(owner, Stat.fireresist).displayed);
    owner.aggregate().put(Stat.fireresist, -40);
    ResistanceDisplayValue after = ResistanceDisplayValue.capture(owner, Stat.fireresist);
    assertEquals(-40, after.displayed);
    assertFalse(after.capped);
    assertEquals(120, before.raw);
    assertEquals(85, before.maximum);
    assertEquals(85, before.displayed);
    assertEquals(31, ResistanceDisplayValue.capture(observer, Stat.fireresist).displayed);
  }

  @Test
  void missingMaximumDoesNotInventACapAndOrdinaryStatsAreRejected() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.fireresist, 82);
    attrs.reset();
    ResistanceDisplayValue value = ResistanceDisplayValue.capture(attrs, Stat.fireresist);
    assertEquals(82, value.displayed);
    assertFalse(value.capped);
    assertThrows(IllegalArgumentException.class,
        () -> ResistanceDisplayValue.capture(attrs, Stat.strength));
  }

  private static Attributes attributes(int raw, int maximum) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.fireresist, raw);
    attrs.base().put(Stat.maxfireresist, maximum);
    attrs.reset();
    return attrs;
  }
}
