package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.riiablo.codec.DC6;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MercenaryHudAssetProbeTest {
  @Test
  void usesNativeLifeBarColorThresholds() {
    assertSame(Color.GREEN, MercenaryHud.healthBarColor(1f));
    assertSame(Color.GREEN, MercenaryHud.healthBarColor(0.67f));
    assertSame(Color.YELLOW, MercenaryHud.healthBarColor(2f / 3f));
    assertSame(Color.YELLOW, MercenaryHud.healthBarColor(0.34f));
    assertSame(Color.RED, MercenaryHud.healthBarColor(1f / 3f));
    assertSame(Color.RED, MercenaryHud.healthBarColor(0f));
  }

  @Test
  void laysOutValkyrieBesideMercenary() {
    assertEquals(0, MercenaryHud.companionSlotCount(false, false));
    assertEquals(1, MercenaryHud.companionSlotCount(true, false));
    assertEquals(1, MercenaryHud.companionSlotCount(false, true));
    assertEquals(2, MercenaryHud.companionSlotCount(true, true));
    assertEquals(0f, MercenaryHud.companionSlotX(false, true));
    assertEquals(56f, MercenaryHud.companionSlotX(true, true));
    org.junit.jupiter.api.Assertions.assertTrue(
        MercenaryHud.isValkyriePetType("Valkyrie"));
    org.junit.jupiter.api.Assertions.assertTrue(
        MercenaryHud.isValkyriePetType(" valkyrie "));
    org.junit.jupiter.api.Assertions.assertFalse(
        MercenaryHud.isValkyriePetType("decoy"));
    assertEquals(5, MercenaryHud.druidSummonPriority("raven"));
    assertEquals(10, MercenaryHud.druidSummonPriority("spiritwolf"));
    assertEquals(20, MercenaryHud.druidSummonPriority("oak sage"));
    assertEquals(30, MercenaryHud.druidSummonPriority("poison creeper"));
    assertEquals(40, MercenaryHud.druidSummonPriority("grizzly"));
    assertEquals(12, MercenaryHud.summonSortPriority("skeleton", 12));

    SummonedPet living = new SummonedPet().set(7, "valkyrie", 32, 5, false, 0);
    assertEquals(true, MercenaryHud.isLivingSummon(living, null, 10f, 20f));
    living.deathPending = true;
    assertEquals(false, MercenaryHud.isLivingSummon(living, null, 10f, 20f));
    living.deathPending = false;
    UnitLifecycle dead = new UnitLifecycle().reset();
    dead.transition(UnitLifecycle.Phase.DEATH);
    assertEquals(false, MercenaryHud.isLivingSummon(living, dead, 10f, 20f));
    assertEquals(false, MercenaryHud.isLivingSummon(living, null, 0f, 20f));
  }

  @Test
  void probeNativeHirelingIconGeometry() {
    String home = System.getenv("D2_LOCALIZATION_HOME");
    Assumptions.assumeTrue(home != null && new File(home, "d2data.mpq").isFile());
    Gdx.app = new HeadlessApplication(new ApplicationAdapter() {});
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    String[] icons = {"rogueicon", "act2hireableicon", "act3hireableicon", "barbhirable_icon",
        "valkarieicon", "Raven", "Wolf", "Bear", "Vines", "OakSage", "skeletonicon",
        "skeletonmageicon", "golemicon", "revivedicon", "ShadowAssassin"};
    for (String icon : icons) {
      DC6 dc6 = DC6.loadFromFile(resolver.resolve(
          "data\\global\\ui\\HIREABLES\\" + icon + ".dc6"));
      assertEquals(1, dc6.getNumDirections(), icon);
      assertEquals(1, dc6.getNumFramesPerDir(), icon);
      BBox box = dc6.getBox(0, 0);
      if (icon.equals("rogueicon") || icon.equals("act2hireableicon")
          || icon.equals("act3hireableicon") || icon.equals("barbhirable_icon")
          || icon.equals("valkarieicon")) {
        assertEquals(41, box.height, icon);
        assertEquals(icon.equals("barbhirable_icon") ? 47 : 46, box.width, icon);
      } else {
        org.junit.jupiter.api.Assertions.assertTrue(box.width > 0 && box.height > 0, icon);
      }
      dc6.dispose();
    }
  }
}
