package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Data-driven, headless audit for the native shaman projectile chain. */
public class ShamanFireballAuditTest extends RiiabloTest {
  @Test
  void fallenShamanSkillEventReachesMissileFactory() {
    MonStats.Entry row = Riiablo.files.monstats.get("fallenshaman3");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    assertNotNull(skill);

    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem();
    Map map = new Map(0, 0);
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(skills, factory)
        .build()
        .register("factory", factory)
        .register("map", map);
    World world = new World(config);
    try {
      int shamanId = world.create();
      MonStats2.Entry stats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(shamanId).set(row, stats2);
      world.getMapper(Position.class).create(shamanId).position.set(10, 10);

      skills.onSkillDo(SkillDoEvent.obtain(shamanId, skill.Id, -1,
          new Vector2(20, 10), skill.srvdofunc, skill.cltdofunc));

      Missiles.Entry expected = Riiablo.files.Missiles.get("shafire3");
      assertNotNull(expected);
      assertEquals(expected.Id, factory.missileId);
      assertEquals(shamanId, factory.ownerId);
      assertEquals(1, factory.creations);
      System.out.println("[SHAMAN_FIREBALL_AUDIT] phase=factory_created monster=" + row.Id
          + " skill=" + skill.skill + " missile=" + expected.Missile
          + " missileId=" + factory.missileId + " owner=" + factory.ownerId);
    } finally {
      world.dispose();
    }
  }

  @Test
  void shamanRowsResolveSkillsAndMissiles() {
    int matched = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      String id = row.Id == null ? "" : row.Id.toLowerCase(Locale.ROOT);
      String ai = row.AI == null ? "" : row.AI.toLowerCase(Locale.ROOT);
      String name = row.NameStr == null ? "" : row.NameStr.toLowerCase(Locale.ROOT);
      if (!(id.contains("shaman") || ai.contains("shaman") || name.contains("shaman")
          || id.contains("pygmy"))) continue;

      matched++;
      System.out.println("[SHAMAN_FIREBALL_AUDIT] monster=" + row.Id + " base=" + row.BaseId
          + " ai=" + row.AI + " skill1=" + row.Skill1 + " mode1=" + row.Sk1mode
          + " skill2=" + row.Skill2 + " mode2=" + row.Sk2mode
          + " skill3=" + row.Skill3 + " missA1=" + row.MissA1 + " missS1=" + row.MissS1);

      boolean hasProjectileSkill = false;
      String[] skillNames = {row.Skill1, row.Skill2, row.Skill3, row.Skill4,
          row.Skill5, row.Skill6, row.Skill7, row.Skill8};
      for (String skillName : skillNames) {
        if (skillName == null || skillName.isEmpty()) continue;
        Skills.Entry skill = Riiablo.files.skills.get(skillName);
        assertNotNull(skill, "missing skill row " + skillName + " for " + row.Id);
        System.out.println("[SHAMAN_FIREBALL_AUDIT] skill=" + skillName + " id=" + skill.Id
            + " srvDoFunc=" + skill.srvdofunc + " srvA=" + skill.srvmissilea
            + " srvB=" + skill.srvmissileb + " cltA=" + skill.cltmissilea
            + " cltB=" + skill.cltmissileb + " monanim=" + skill.monanim);
        String[] missiles = {skill.srvmissilea, skill.srvmissileb, skill.srvmissilec,
            skill.srvmissiled, skill.cltmissilea, skill.cltmissileb,
            skill.cltmissilec, skill.cltmissiled};
        for (String missileName : missiles) {
          if (missileName == null || missileName.isEmpty()) continue;
          hasProjectileSkill = true;
          Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
          assertNotNull(missile, "missing missile row " + missileName + " for skill " + skillName);
          System.out.println("[SHAMAN_FIREBALL_AUDIT] skill=" + skillName + " id=" + skill.Id
              + " srvDoFunc=" + skill.srvdofunc + " srvMissile=" + missileName
              + " missileId=" + missile.Id + " vel=" + missile.Vel + " range=" + missile.Range
              + " srvDmgFunc=" + missile.pSrvDmgFunc + " dmg=" + missile.DmgCalc1);
        }
      }
      if (id.contains("fallen") || id.contains("pygmy") || ai.contains("shaman")) {
        String effective = row.AI != null && row.AI.equalsIgnoreCase("FallenShaman")
            ? row.Skill2 : row.Skill1;
        Skills.Entry effectiveSkill = effective == null ? null : Riiablo.files.skills.get(effective);
        boolean effectiveProjectile = hasProjectileSkill;
        if (!effectiveProjectile && effectiveSkill != null) {
          effectiveProjectile = hasProjectile(effectiveSkill);
        }
        System.out.println("[SHAMAN_FIREBALL_AUDIT] result="
            + (effectiveProjectile ? "projectile-configured" : "NO_PROJECTILE")
            + " monster=" + row.Id + " effectiveSkill=" + effective);
        if (row.AI != null && row.AI.equalsIgnoreCase("FallenShaman")
            && "ShamanFire".equalsIgnoreCase(row.Skill2)) {
          assertTrue(effectiveProjectile, "fallen shaman fire skill cannot resolve: " + row.Id);
          Monster monster = new Monster();
          monster.monstats = row;
          String resolvedName = ServerSkillSystem.resolveMonsterChainMissile(
              monster, effectiveSkill.srvmissilea);
          Missiles.Entry resolved = Riiablo.files.Missiles.get(resolvedName);
          assertNotNull(resolved, "native shaman chain missile missing for " + row.Id);
          System.out.println("[SHAMAN_FIREBALL_AUDIT] phase=chain monster=" + row.Id
              + " chainId=" + ServerSkillSystem.getMonsterChainId(row)
              + " missile=" + resolved.Missile + " missileId=" + resolved.Id
              + " celFile=" + resolved.CelFile);
        }
      }
    }
    assertTrue(matched > 0, "D2 data did not contain any shaman rows");
  }

  private static boolean hasProjectile(Skills.Entry skill) {
    return skill != null && (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
        || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
        || hasText(skill.cltmissilec) || hasText(skill.cltmissiled));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  private static final class RecordingFactory extends EntityFactory {
    int creations;
    int missileId = -1;
    int ownerId = -1;

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position) {
      return createMissile(missileId, angle, position, -1);
    }

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position, int ownerId) {
      creations++;
      this.missileId = missileId;
      this.ownerId = ownerId;
      return 9001;
    }

    @Override public int createPlayer(CharData charData, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int monPresetId, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int objId, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int objectId, float x, float y) { return -1; }
    @Override public int createMonster(int monsterId, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
