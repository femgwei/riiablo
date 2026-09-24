package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.codec.DC6;
import com.riiablo.codec.util.BBox;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MercenaryHudAssetProbeTest {
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
