package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Overlay;
import org.junit.jupiter.api.Test;

/** Native data and client/server presentation gates for Barbarian skills. */
class NativeBarbarianPresentationTest extends RiiabloTest {
  @Test
  void printNativePresentationColumns() {
    String[] names = {"Frenzy", "Whirlwind", "Berserk", "Shout",
        "Battle Orders", "Battle Command", "War Cry"};
    for (String name : names) {
      Skills.Entry skill = Riiablo.files.skills.get(name);
      assertNotNull(skill, name);
      System.out.println("[BARBARIAN_PRESENTATION_DATA] skill=" + name
          + " anim=" + skill.anim + " seqtrans=" + skill.seqtrans
          + " monanim=" + skill.monanim + " stsound=" + skill.stsound
          + " dosound=" + skill.dosound + " castoverlay=" + skill.castoverlay
          + " resultFlags=" + skill.ResultFlags + " hitClass=" + skill.HitClass);
    }
    int index = 0;
    for (Overlay.Entry overlay : Riiablo.files.Overlay) {
      String id = overlay.overlay == null ? "" : overlay.overlay.toLowerCase();
      if (index == 54 || id.contains("barb") || id.contains("warcry")
          || id.contains("battle") || id.contains("frenzy") || id.contains("berserk")
          || id.contains("shout") || id.contains("master")) {
        System.out.println("[BARBARIAN_OVERLAY_DATA] index=" + index + " id="
            + overlay.overlay + " file=" + overlay.Filename + " frames=" + overlay.Frames);
      }
      index++;
    }
  }
}
