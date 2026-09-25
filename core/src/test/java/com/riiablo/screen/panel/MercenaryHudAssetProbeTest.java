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
  void probeNativeHirelingIconGeometry() {
    String home = System.getenv("D2_LOCALIZATION_HOME");
    Assumptions.assumeTrue(home != null && new File(home, "d2data.mpq").isFile());
    Gdx.app = new HeadlessApplication(new ApplicationAdapter() {});
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    String[] icons = {"rogueicon", "act2hireableicon", "act3hireableicon", "barbhirable_icon"};
    for (String icon : icons) {
      DC6 dc6 = DC6.loadFromFile(resolver.resolve(
          "data\\global\\ui\\HIREABLES\\" + icon + ".dc6"));
      assertEquals(1, dc6.getNumDirections(), icon);
      assertEquals(1, dc6.getNumFramesPerDir(), icon);
      BBox box = dc6.getBox(0, 0);
      assertEquals(41, box.height, icon);
      assertEquals(icon.equals("barbhirable_icon") ? 47 : 46, box.width, icon);
      dc6.dispose();
    }
  }
}
