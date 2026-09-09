package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Verifies the 1.10f rows and formulas consumed by native Bone Wall/Prison. */
class NativeNecromancerBoneWallDataTest extends RiiabloTest {
  @Test
  void nativeRowsSelectSrvDoFunctionsSummonAndLifetime() {
    Skills.Entry wall = Riiablo.files.skills.get(SkillId.BONE_WALL);
    Skills.Entry prison = Riiablo.files.skills.get(SkillId.BONE_PRISON);
    assertNotNull(wall);
    assertNotNull(prison);
    assertTrue(NecromancerSkills.isBoneWall(wall));
    assertTrue(NecromancerSkills.isBonePrison(prison));
    assertEquals("bonewall", wall.summon);
    assertEquals("bonewall", prison.summon);
    assertEquals("S1", wall.summode);
    assertEquals("NU", prison.summode);
    assertEquals(600, NecromancerSkills.getBoneWallDurationFrames(wall));
    assertEquals(600, NecromancerSkills.getBoneWallDurationFrames(prison));
    assertEquals(4, NecromancerSkills.getBoneWallSegmentsPerSide(wall, 1));
    assertEquals(4, NecromancerSkills.getBoneWallSegmentsPerSide(wall, 20));
    assertEquals(12, NecromancerSkills.getBonePrisonSegmentCount());

    MonStats.Entry summon = Riiablo.files.monstats.get("bonewall");
    assertNotNull(summon);
    assertEquals("BoneWall", summon.AI);
    assertTrue(summon.killable);
  }

  @Test
  void lifeSynergiesAndPar34FollowNativeCalcSemantics() {
    Skills.Entry wall = Riiablo.files.skills.get(SkillId.BONE_WALL);
    Skills.Entry prison = Riiablo.files.skills.get(SkillId.BONE_PRISON);
    assertEquals(8, SkillFormula.evaluate("par34", wall, 1));
    assertEquals(8, SkillFormula.evaluate("par34", wall, 20));
    assertEquals(45, NecromancerSkills.getBoneWallLifePercent(
        wall, 2, name -> "Bone Armor".equals(name) ? 1
            : "Bone Prison".equals(name) ? 1 : 0));
    assertEquals(41, NecromancerSkills.getBoneWallLifePercent(
        prison, 2, name -> "Bone Armor".equals(name) ? 1
            : "Bone Wall".equals(name) ? 1 : 0));
  }

  @Test
  void prisonOffsetsAndWallDirectionMatchNativeGeometry() {
    int[] out = new int[2];
    int[][] expected = {
        {-1, -4}, {1, -4}, {3, -3}, {4, -1}, {4, 1}, {3, 3},
        {-1, 4}, {1, 4}, {-3, 3}, {-4, -1}, {-4, 1}, {-3, -3}
    };
    for (int i = 0; i < expected.length; i++) {
      NecromancerSkills.getBonePrisonOffset(i, out);
      assertEquals(expected[i][0], out[0]);
      assertEquals(expected[i][1], out[1]);
    }
    Vector2 direction = ServerSkillSystem.boneWallDirection(
        new Vector2(0, 0), new Vector2(4, 0), new Vector2());
    assertEquals(0f, direction.x, 0.0001f);
    assertEquals(1f, direction.y, 0.0001f);
  }
}
