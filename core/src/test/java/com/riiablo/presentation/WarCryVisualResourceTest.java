package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarCryVisualResourceTest extends RiiabloTest {
  @org.junit.jupiter.api.BeforeEach void initializeAnimationColors() {
    savedColors = Riiablo.colors;
    if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
  }

  private com.riiablo.Colors savedColors;

  @org.junit.jupiter.api.AfterEach void restoreAnimationColors() {
    Riiablo.colors = savedColors;
  }
  @Test void remainingBarbarianVisualsUseNativeRows() {
    for (String name : new String[] {"frenzy", "berserkfront", "berserkback", "whirlwind",
        "shoutstart", "battleorderscast", "battlecommandcast", "bash", "doubledamage1"}) {
      com.riiablo.codec.excel.Overlay.Entry row = Riiablo.files.Overlay.get(name);
      assertNotNull(row, name);
      if ("whirlwind".equals(name)) {
        assertEquals(0, row.Trans, "native whirlwind is translucent, not additive");
        assertEquals(.25f, AuraRenderPolicy.alpha(row.Trans));
      }
      if ("berserkfront".equals(name)) assertFalse(row.PreDraw);
      if ("berserkback".equals(name)) assertTrue(row.PreDraw);
      System.out.println("BARB_OVERLAY " + name + " rate=" + row.AnimRate + " trans="
          + row.Trans + " pre=" + row.PreDraw + " height2=" + row.Height2);
      if (!"bash".equals(name) && !"doubledamage1".equals(name)) {
        com.riiablo.presentation.component.Overlay effect = new com.riiablo.presentation.component.Overlay();
        effect.set(row);
        assertTrue(effect.usesNativeRendering(), name);
        assertFalse(effect.persistent, "drawing policy must not invent a permanent state");
        assertTrue(row.AnimRate > 0, name);
        assertTrue(row.Trans >= 0 && row.Trans <= 3 || row.Trans == 5,
            "supported native draw mode " + name);
      }
    }
    for (int id = 126; id <= 155; id++) {
      com.riiablo.codec.excel.NativeSkills.Entry row = Riiablo.files.NativeSkills.get(id);
      StringBuilder line = new StringBuilder("BARB_VISUAL " + id);
      for (String col : new String[] {"skill", "anim", "seqtrans", "cltstfunc", "cltdofunc",
          "cltmissilea", "cltmissileb", "castoverlay", "srvoverlay", "aurastate", "auratargetstate"})
        line.append(" ").append(col).append("=").append(row.string(col));
      System.out.println(line);
      for (String column : new String[] {"aurastate", "auratargetstate"}) {
        com.riiablo.codec.excel.States.Entry state = Riiablo.files.States.get(row.string(column));
        if (state == null) continue;
        System.out.println("BARB_STATE " + row.string(column) + " overlays="
            + java.util.Arrays.toString(state.overlays) + " cast=" + state.castOverlay);
      }
    }
  }
  @Test void waveDirectionsUseNativeIntegerTargets() {
    com.badlogic.gdx.math.Vector2 direction = new com.badlogic.gdx.math.Vector2();
    assertEquals(new com.badlogic.gdx.math.Vector2(29, 2).nor(),
        SkillCastHandler.warCryDirection(1, direction));
    assertEquals(new com.badlogic.gdx.math.Vector2(-2, 29).nor(),
        SkillCastHandler.warCryDirection(17, direction));
    for (int i = 0; i < 32; i++) {
      com.badlogic.gdx.math.Vector2 opposite = SkillCastHandler.warCryDirection(i + 32,
          new com.badlogic.gdx.math.Vector2());
      assertTrue(SkillCastHandler.warCryDirection(i, direction).add(opposite).isZero(.000001f));
    }
  }
  @Test void nativeWarCryPresentationRowsUseTheVerifiedRenderingPolicy() {
    for (int id : new int[] {130, 137, 138, 146, 149, 154, 155}) {
      com.riiablo.codec.excel.NativeSkills.Entry skill = Riiablo.files.NativeSkills.get(id);
      assertNotNull(skill);
      StringBuilder line = new StringBuilder("WARCRY " + id);
      for (String col : new String[] {"skill", "srvdofunc", "cltstfunc", "cltdofunc",
          "cltmissilea", "cltmissileb", "cltmissilec", "srvmissilea", "srvmissile",
          "castoverlay", "aurastate", "auratargetstate"}) {
        line.append(" ").append(col).append("=").append(skill.string(col));
      }
      System.out.println(line);
      String missileName = skill.string("cltmissilea");
      if (!missileName.isEmpty()) {
        com.riiablo.codec.excel.Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
        assertNotNull(missile, missileName);
        assertEquals(32, missile.NumDirections, "DCC directions are not the 64-way wave count");
        com.riiablo.codec.excel.Skills.Entry projected = new com.riiablo.codec.excel.Skills.Entry();
        projected.Id = id;
        assertTrue(SkillCastHandler.isWarCryWave(projected));
        System.out.println("WARCRY_MISSILE " + missileName + " cltdo=" + missile.pCltDoFunc
            + " trans=" + missile.Trans + " rate=" + missile.animrate + " speed=" + missile.AnimSpeed
            + " dirs=" + missile.NumDirections + " range=" + missile.Range + " vel=" + missile.Vel
            + " cel=" + missile.CelFile);
      }
    }
    com.riiablo.codec.excel.Overlay.Entry cast = Riiablo.files.Overlay.get("warcry");
    assertNotNull(cast);
    com.riiablo.presentation.component.Overlay effect = new com.riiablo.presentation.component.Overlay();
    effect.set(cast);
    assertTrue(effect.usesNativeRendering());
    assertFalse(effect.persistent, "cast animation remains one-shot");
    effect.clearEffect();
    assertFalse(effect.usesNativeRendering(), "pooled reuse must clear drawing policy");
    assertFalse(AuraRenderPolicy.isWarCryOverlay(null));
    assertFalse(AuraRenderPolicy.isWarCryOverlay("might"));
    System.out.println("WARCRY_CAST trans=" + cast.Trans + " rate=" + cast.AnimRate
        + " offsets=" + cast.Xoffset + "," + cast.Yoffset + " height2=" + cast.Height2);
    for (String name : new String[] {"terror", "taunt", "battlecry", "stunned"}) {
      com.riiablo.codec.excel.States.Entry state = Riiablo.files.States.get(name);
      assertNotNull(state, name);
      assertFalse(state.aura, name);
      System.out.println("WARCRY_STATE " + name + " aura=" + state.aura
          + " overlays=" + java.util.Arrays.toString(state.overlays)
          + " cast=" + state.castOverlay + " rem=" + state.removeOverlay);
      for (String overlay : state.overlays) {
        if (overlay == null || overlay.isEmpty()) continue;
        com.riiablo.codec.excel.Overlay.Entry row = Riiablo.files.Overlay.get(overlay);
        assertNotNull(row, overlay);
        effect.set(row);
        assertTrue(effect.usesNativeRendering(), overlay);
        assertEquals(3, row.Trans);
        assertEquals("battlecry".equals(name) ? .08f : .04f,
            AuraRenderPolicy.frameDuration(row.AnimRate), .000001f);
        System.out.println("WARCRY_OVERLAY " + overlay + " trans=" + row.Trans
            + " rate=" + row.AnimRate + " offsets=" + row.Xoffset + "," + row.Yoffset
            + " heights=" + row.Height1 + "," + row.Height2 + "," + row.Height3
            + "," + row.Height4 + " predraw=" + row.PreDraw);
      }
    }
  }
}
