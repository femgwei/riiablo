package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarCryVisualResourceTest extends RiiabloTest {
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
