package com.riiablo.save;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemDataResourceRefreshTest extends RiiabloTest {

  @Test
  void progressionSynchronizationClampsAndPersistsCurrentLife() {
    CharData character = new CharData();
    Attributes stats = character.getStats();
    stats.base().put(Stat.hitpoints, 69f);
    stats.base().put(Stat.maxhp, 62f);
    stats.base().put(Stat.mana, 21f);
    stats.base().put(Stat.maxmana, 21f);
    stats.base().put(Stat.stamina, 89f);
    stats.base().put(Stat.maxstamina, 89f);
    stats.reset();

    character.synchronizeCurrentResources();

    assertEquals(62f, stats.aggregate().get(Stat.hitpoints).asFixed());
    assertEquals(62f, stats.base().get(Stat.hitpoints).asFixed());
  }
  @Test
  void aggregateRefreshDoesNotRestoreStaleBaseLifeAboveMaximum() {
    CharData data = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "ResourceRefresh", Riiablo.AMAZON);
    Attributes stats = data.getStats();
    stats.base().put(Stat.hitpoints, 69f);
    stats.base().put(Stat.maxhp, 60f);
    stats.base().put(Stat.mana, 30f);
    stats.base().put(Stat.maxmana, 30f);
    stats.base().put(Stat.stamina, 40f);
    stats.base().put(Stat.maxstamina, 40f);
    stats.reset();

    // Combat changes aggregate life while the serialized base value is
    // still stale. This is the state that previously became 69/60.
    stats.aggregate().put(Stat.hitpoints, 60f);
    data.getItems().updateStats();

    assertEquals(60f, stats.aggregate().get(Stat.hitpoints).asFixed(), 0.001f);
    assertEquals(60f, stats.aggregate().get(Stat.maxhp).asFixed(), 0.001f);
    assertEquals(60f, stats.base().get(Stat.hitpoints).asFixed(), 0.001f);
  }

  @Test
  void aggregateRefreshPreservesWoundsInsteadOfRefillingLife() {
    CharData data = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "WoundedRefresh", Riiablo.AMAZON);
    Attributes stats = data.getStats();
    stats.base().put(Stat.hitpoints, 60f);
    stats.base().put(Stat.maxhp, 60f);
    stats.base().put(Stat.mana, 30f);
    stats.base().put(Stat.maxmana, 30f);
    stats.base().put(Stat.stamina, 40f);
    stats.base().put(Stat.maxstamina, 40f);
    stats.reset();
    stats.aggregate().put(Stat.hitpoints, 42f);

    data.getItems().updateStats();

    assertEquals(42f, stats.aggregate().get(Stat.hitpoints).asFixed(), 0.001f);
    assertEquals(42f, stats.base().get(Stat.hitpoints).asFixed(), 0.001f);
  }
}
