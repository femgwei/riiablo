package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Skills;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native-data inventory for the Assassin skill tree before runtime porting. */
class AssassinSkillSpecializationTest extends RiiabloTest {
  @Test
  void auditNativeAssassinRows() {
    int rows = 0;
    for (int id = CharacterClass.ASSASSIN.firstSpell; id < CharacterClass.ASSASSIN.lastSpell; id++) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "assassin skill id=" + id);
      rows++;
      System.out.println("[ASSASSIN_SKILL] id=" + skill.Id + " name=" + skill.skill
          + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc
          + " cltDo=" + skill.cltdofunc + " srvA=" + skill.srvmissilea
          + " srvB=" + skill.srvmissileb + " cltA=" + skill.cltmissilea
          + " summon=" + skill.summon + " petType=" + skill.pettype
          + " petMax=" + skill.petmax + " auraLen=" + skill.auralencalc
          + " calc4=" + skill.calc4 + " params=" + java.util.Arrays.toString(skill.Param));
      if (skill.summon != null && !skill.summon.isEmpty()) {
        com.riiablo.codec.excel.MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
        assertNotNull(summon, skill.skill + " summon=" + skill.summon);
        System.out.println("[ASSASSIN_SUMMON] skill=" + skill.skill + " monster=" + summon.Id
            + " ai=" + summon.AI + " skill1=" + summon.Skill1 + " level1=" + summon.Sk1lvl
            + " skill2=" + summon.Skill2 + " level2=" + summon.Sk2lvl
            + " missA1=" + summon.MissA1 + " missA2=" + summon.MissA2
            + " missS1=" + summon.MissS1 + " missS2=" + summon.MissS2
            + " missS3=" + summon.MissS3 + " missS4=" + summon.MissS4
            + " ai1=" + java.util.Arrays.toString(summon.aip1)
            + " ai2=" + java.util.Arrays.toString(summon.aip2)
            + " ai3=" + java.util.Arrays.toString(summon.aip3)
            + " ai4=" + java.util.Arrays.toString(summon.aip4));
        Skills.Entry attack = summon.Skill1 == null || summon.Skill1.isEmpty()
            ? null : Riiablo.files.skills.get(summon.Skill1);
        if (attack != null) {
          System.out.println("[ASSASSIN_SUMMON_SKILL] monster=" + summon.Id + " skill="
              + attack.skill + " srvDo=" + attack.srvdofunc + " srvA=" + attack.srvmissilea
              + " srvB=" + attack.srvmissileb + " cltA=" + attack.cltmissilea
              + " calc1=" + attack.calc1 + " calc2=" + attack.calc2
              + " calc3=" + attack.calc3 + " calc4=" + attack.calc4
              + " auraRange=" + attack.aurarangecalc + " eType=" + attack.EType + " params="
              + java.util.Arrays.toString(attack.Param));
          Missiles.Entry serverMissile = attack.srvmissilea == null
              || attack.srvmissilea.isEmpty() ? null
              : Riiablo.files.Missiles.get(attack.srvmissilea);
          if (serverMissile != null) {
            System.out.println("[ASSASSIN_SUMMON_MISSILE] skill=" + attack.skill
                + " missile=" + serverMissile.Missile
                + " srvDo=" + serverMissile.pSrvDoFunc
                + " srvHit=" + serverMissile.pSrvHitFunc
                + " velocity=" + serverMissile.Vel + " range=" + serverMissile.Range
                + " params=" + java.util.Arrays.toString(serverMissile.Param)
                + " sub=" + java.util.Arrays.toString(serverMissile.SubMissile));
            String subName = serverMissile.SubMissile != null
                && serverMissile.SubMissile.length > 0 ? serverMissile.SubMissile[0] : null;
            Missiles.Entry subMissile = subName == null || subName.isEmpty()
                ? null : Riiablo.files.Missiles.get(subName);
            if (subMissile != null) {
              System.out.println("[ASSASSIN_SUMMON_SUBMISSILE] parent=" + serverMissile.Missile
                  + " missile=" + subMissile.Missile
                  + " srvDo=" + subMissile.pSrvDoFunc
                  + " srvHit=" + subMissile.pSrvHitFunc
                  + " velocity=" + subMissile.Vel + " range=" + subMissile.Range
                  + " size=" + subMissile.Size + " damageRate=" + subMissile.DamageRate
                  + " nextHit=" + subMissile.NextHit
                  + " nextDelay=" + subMissile.NextDelay);
            }
          }
        }
      }
    }
    System.out.println("[ASSASSIN_SKILL_SUMMARY] rows=" + rows);
  }

  @Test
  void fireTraumaUsesNativeAirGroundChainAndSkillSynergy() {
    Skills.Entry fire = Riiablo.files.skills.get("Fire Trauma");
    Missiles.Entry air = Riiablo.files.Missiles.get("bomb in air");
    Missiles.Entry ground = Riiablo.files.Missiles.get("bomb on ground");
    Missiles.Entry explosion = Riiablo.files.Missiles.get("bomb explosion");
    assertNotNull(fire);
    assertNotNull(air);
    assertNotNull(ground);
    assertNotNull(explosion);
    assertEquals(251, fire.Id);
    assertEquals(36, air.pSrvHitFunc,
        "SrvHit36 must convert air contact into a ground child only on null-hit");
    assertEquals("bomb on ground", air.HitSubMissile[0]);
    assertEquals(3, ground.pSrvHitFunc,
        "SrvHit03 ground row must defer damage until null-target expiry");
    assertEquals("bomb explosion", ground.ExplosionMissile);
    assertEquals(5, fire.Param[0], "Fire Trauma aurarangecalc=par1 radius");

    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(false), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(fire.Id, 3);
      for (String synergy : new String[] {"Shock Field", "Death Sentry", "Charged Bolt Sentry",
          "Lightning Sentry", "Wake of Fire Sentry", "Inferno Sentry"}) {
        Skills.Entry row = Riiablo.files.skills.get(synergy);
        if (row != null) data.setSkillLevel(row.Id, 1);
      }
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, fire.Id, Engine.INVALID_ENTITY, new Vector2(8, 0), fire.srvdofunc, fire.cltdofunc));
      assertEquals(1, factory.missiles);
      Missile projectile = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(projectile);
      assertEquals("bomb in air", projectile.missile.Missile);
      assertEquals(fire.Id, projectile.skillId);
      assertEquals(3, projectile.damageLevel);
      assertTrue(projectile.damageSnapshot);
      assertTrue(projectile.damage.get(Stat.firemaxdam).asInt() >=
          projectile.damage.get(Stat.firemindam).asInt());
      int synergyPercent = com.riiablo.engine.server.skill.SkillFormula.evaluate(
          fire.EDmgSymPerCalc, fire, 3,
          name -> {
            Skills.Entry row = Riiablo.files.skills.get(name);
            return row != null ? data.getSkill(row.Id) : 0;
          });
      assertTrue(synergyPercent > 0, "Fire Trauma must evaluate sentry synergies");
    } finally {
      world.dispose();
    }
  }

  @Test
  void fireTraumaGroundIgnoresUnitContactAndExplodesOnceAtSkillRadius() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry fire = Riiablo.files.skills.get("Fire Trauma");
      Missiles.Entry ground = Riiablo.files.Missiles.get("bomb on ground");
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(fire.Id, 1);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      Attributes ownerAttrs = attributes(1000);
      world.getMapper(Position.class).create(owner).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = ownerAttrs;

      int near = world.create();
      world.getMapper(Monster.class).create(near);
      world.getMapper(Position.class).create(near).position.set(3, 0);
      Attributes nearAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(near).attrs = nearAttrs;
      int far = world.create();
      world.getMapper(Monster.class).create(far);
      world.getMapper(Position.class).create(far).position.set(8, 0);
      Attributes farAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(far).attrs = farAttrs;

      int groundId = factory.createMissile(ground.Id, Vector2.X, new Vector2(0, 0), owner);
      Missile source = world.getMapper(Missile.class).get(groundId);
      source.skillId = fire.Id;
      source.damageLevel = 1;
      MissileDamageResolver.initializeSkill(source, fire, ownerAttrs, 1,
          name -> 0);
      assertTrue(source.damageSnapshot);
      world.setDelta(1f / 25f);
      for (int i = 0; i < 4; i++) {
        world.process();
        assertEquals(1000f, nearAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
            "SrvHit03 must not damage a unit on contact before expiry");
      }
      world.process();
      assertTrue(nearAttrs.get(Stat.hitpoints).asFixed() < 1000f,
          "ground expiry must apply Fire Trauma's center AoE");
      assertEquals(1000f, farAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "targets outside aurarangecalc=par1 remain untouched");
      assertFalse(world.getEntityManager().isActive(groundId));
      assertTrue(java.util.Collections.frequency(factory.missileNames, "bomb explosion") == 1,
          "ground expiry emits one presentation explosion");
      world.process();
      assertTrue(java.util.Collections.frequency(factory.missileNames, "bomb explosion") == 1,
          "expired ground source cannot explode twice");
    } finally {
      world.dispose();
    }
  }

  /**
   * Locks the ten dark-magic trap-family IDs to their 1.10f Native rows.
   *
   * <p>The display names differ between the 1.14d dark-magic fixture and the
   * 1.10f MPQ (for example Fire Blast/Fire Trauma and Shock Web/Shock Field),
   * so this gate keys on the exact ID and native server dispatcher instead of
   * copying expansion-era names or formulas.</p>
   */
  @Test
  void auditDarkMagicTrapExactIdsAgainstLegacyRows() {
    java.util.Map<Integer, String> expectedNames = new java.util.LinkedHashMap<>();
    expectedNames.put(251, "Fire Trauma");
    expectedNames.put(256, "Shock Field");
    expectedNames.put(257, "Blade Sentinel");
    expectedNames.put(261, "Charged Bolt Sentry");
    expectedNames.put(262, "Wake of Fire Sentry");
    expectedNames.put(266, "Blade Fury");
    expectedNames.put(271, "Lightning Sentry");
    expectedNames.put(272, "Inferno Sentry");
    expectedNames.put(276, "Death Sentry");
    expectedNames.put(277, "Blade Shield");

    java.util.Map<Integer, Integer> expectedSrvDo = new java.util.LinkedHashMap<>();
    expectedSrvDo.put(251, 0); // lobbed Fire Trauma uses the generic missile path.
    expectedSrvDo.put(256, 43); // Shock Field progressive scatter.
    expectedSrvDo.put(257, 44); // Blade Sentinel owned returning weapon.
    expectedSrvDo.put(261, 45); // Charged Bolt Sentry owned trap.
    expectedSrvDo.put(262, 45); // Wake of Fire Sentry owned trap.
    expectedSrvDo.put(266, 48); // Blade Fury held-input missile release.
    expectedSrvDo.put(271, 45); // Lightning Sentry owned trap.
    expectedSrvDo.put(272, 45); // Inferno Sentry owned trap.
    expectedSrvDo.put(276, 45); // Death Sentry owned trap/corpse transaction.
    expectedSrvDo.put(277, 54); // Blade Shield periodic weapon state.

    assertEquals(expectedNames.size(), expectedSrvDo.size());
    for (Integer id : expectedNames.keySet()) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "dark-magic trap exact-ID=" + id + " missing from 1.10f Skills.txt");
      assertEquals(expectedNames.get(id), skill.skill,
          "1.10f row name for dark-magic trap exact-ID=" + id);
      assertEquals(expectedSrvDo.get(id), skill.srvdofunc,
          "native SrvDoFunc for dark-magic trap exact-ID=" + id);
    }

    assertEquals("assassintrap", Riiablo.files.skills.get(257).pettype);
    assertEquals("assassintrap", Riiablo.files.skills.get(261).pettype);
    assertEquals("assassintrap", Riiablo.files.skills.get(262).pettype);
    assertEquals("assassintrap", Riiablo.files.skills.get(271).pettype);
    assertEquals("assassintrap", Riiablo.files.skills.get(272).pettype);
    assertEquals("assassintrap", Riiablo.files.skills.get(276).pettype);
    assertEquals("bladefragment1", Riiablo.files.skills.get(266).srvmissilea);
    assertEquals("blade shield attachment", Riiablo.files.skills.get(277).srvmissilea);
  }

  @Test
  void auditTrapSummonSkillInheritanceRows() {
    java.util.Map<String, String[]> expectedSumSkills = new java.util.LinkedHashMap<>();
    expectedSumSkills.put("Blade Sentinel", new String[] {"Blade Sentinel", "", "", "", ""});
    expectedSumSkills.put("Charged Bolt Sentry", new String[] {
        "BoltSentry", "Fire Trauma", "Shock Field", "Lightning Sentry", "Death Sentry"});
    expectedSumSkills.put("Wake of Fire Sentry", new String[] {
        "Wake Of Destruction Sentry", "Fire Trauma", "Inferno Sentry", "", ""});
    expectedSumSkills.put("Lightning Sentry", new String[] {
        "sentry lightning", "Shock Field", "Charged Bolt Sentry", "Death Sentry", ""});
    expectedSumSkills.put("Inferno Sentry", new String[] {
        "mon inferno sentry", "Fire Trauma", "Wake of Fire Sentry", "Death Sentry", ""});
    expectedSumSkills.put("Death Sentry", new String[] {
        "mon death sentry", "death sentry ltng", "Fire Trauma", "Lightning Sentry", ""});
    java.util.Map<String, String[]> expectedSumSkCalcs = new java.util.LinkedHashMap<>();
    expectedSumSkCalcs.put("Blade Sentinel", new String[] {"lvl", "", "", "", ""});
    expectedSumSkCalcs.put("Charged Bolt Sentry", new String[] {
        "lvl", "skill('Fire Trauma'.blvl)", "skill('Shock Field'.blvl)",
        "skill('Lightning Sentry'.blvl)", "skill('Death Sentry'.blvl)"});
    expectedSumSkCalcs.put("Wake of Fire Sentry", new String[] {
        "lvl", "skill('Fire Trauma'.blvl)", "skill('Inferno Sentry'.blvl)", "", ""});
    expectedSumSkCalcs.put("Lightning Sentry", new String[] {
        "lvl", "skill('Shock Field'.blvl)", "skill('Charged Bolt Sentry'.blvl)",
        "skill('Death Sentry'.blvl)", ""});
    expectedSumSkCalcs.put("Inferno Sentry", new String[] {
        "lvl", "skill('Fire Trauma'.blvl)", "skill('Wake of Fire Sentry'.blvl)",
        "skill('Death Sentry'.blvl)", ""});
    expectedSumSkCalcs.put("Death Sentry", new String[] {
        "lvl", "lvl", "skill('Fire Trauma'.blvl)", "skill('Lightning Sentry'.blvl)", ""});
    for (String placementName : expectedSumSkills.keySet()) {
      Skills.Entry placement = Riiablo.files.skills.get(placementName);
      assertNotNull(placement, placementName);
      System.out.println("[ASSASSIN_SUMMON_INHERIT] placement=" + placementName
          + " sumskill=" + java.util.Arrays.toString(placement.sumskill)
          + " sumskcalc=" + java.util.Arrays.toString(placement.sumskcalc));
      assertTrue(placement.summon != null && !placement.summon.isEmpty(),
          placementName + " must select a summon row");
      assertArrayEquals(expectedSumSkills.get(placementName), placement.sumskill,
          placementName + " SumSkill mapping");
      assertArrayEquals(expectedSumSkCalcs.get(placementName), placement.sumskcalc,
          placementName + " SumSkCalc mapping");
    }
  }

  @Test
  void trapMissileResolutionRejectsClientOnlyHelperRows() {
    Skills.Entry visualOnly = new Skills.Entry();
    visualOnly.cltmissilea = "visual-only-trap";
    visualOnly.cltmissileb = "visual-only-trap-b";
    assertEquals(null, AssassinTrapSystem.resolveMissile(visualOnly, null),
        "client-only missile columns must not become authoritative trap damage");

    visualOnly.srvmissilea = "authoritative-trap";
    assertEquals("authoritative-trap",
        AssassinTrapSystem.resolveMissile(visualOnly, null));
  }

  @Test
  void trapAttackResolutionFailsClosedWhenSummonHasNoAttackSkill() {
    Monster malformedTrap = new Monster();
    malformedTrap.monstats = new com.riiablo.codec.excel.MonStats.Entry();
    Skills.Entry placement = Riiablo.files.skills.get("Lightning Sentry");
    assertEquals(null, AssassinTrapSystem.resolveAttackSkill(malformedTrap, placement),
        "a trap summon without MonStats Skill1/Skill2 must not reuse its placement skill");
  }

  @Test
  void sentryAiResolvesNativeAttackRowsInsteadOfPlacementSkills() {
    for (String placementName : new String[] {
        "Wake of Fire Sentry", "Inferno Sentry", "Death Sentry"}) {
      Skills.Entry placement = Riiablo.files.skills.get(placementName);
      assertNotNull(placement, placementName);
      assertTrue(placement.summon != null && !placement.summon.isEmpty(),
          placementName + " must declare its native summon row");

      com.riiablo.codec.excel.MonStats.Entry summon =
          Riiablo.files.monstats.get(placement.summon);
      assertNotNull(summon, placementName + " summon=" + placement.summon);
      assertTrue(summon.Skill1 != null && !summon.Skill1.isEmpty(),
          placementName + " must provide MonStats.Skill1 for sentry AI");
      Skills.Entry skill1 = Riiablo.files.skills.get(summon.Skill1);
      assertNotNull(skill1, placementName + " Skill1=" + summon.Skill1);

      Monster monster = new Monster();
      monster.monstats = summon;
      Skills.Entry resolved = AssassinTrapSystem.resolveAttackSkill(monster, placement);
      assertNotNull(resolved, placementName + " must resolve an attack skill");
      assertTrue(placement.Id != resolved.Id,
          placementName + " must not recursively execute its placement row");

      if ("Death Sentry".equals(placementName)
          && summon.Skill2 != null && !summon.Skill2.isEmpty()) {
        assertEquals(summon.Skill2, resolved.skill,
            "Death Sentry uses Skill2 for its ordinary lightning fallback");
      } else {
        assertEquals(summon.Skill1, resolved.skill,
            placementName + " uses MonStats.Skill1 for its attack AI");
      }
    }
  }

  @Test
  void assassinSentryAiRowsDoNotUseGenericMonsterFallback() {
    com.riiablo.engine.server.ai.AI wake =
        com.riiablo.engine.server.ai.AI.findAI(101, "AssassinSentry");
    com.riiablo.engine.server.ai.AI death =
        com.riiablo.engine.server.ai.AI.findAI(102, "DeathSentry");
    assertEquals("AssassinSentry", wake.getClass().getSimpleName());
    assertEquals("DeathSentry", death.getClass().getSimpleName());
    assertEquals("TRAP", wake.getState());
    assertEquals("TRAP", death.getState());
  }

  @Test
  void shadowWarriorUsesNativeSrvDo049AndOwnedPetState() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry shadow = Riiablo.files.skills.get("Shadow Warrior");
      assertNotNull(shadow);
      data.setSkillLevel(shadow.Id, 5);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = Attributes.obtainStandard();

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, shadow.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), shadow.srvdofunc, 0));

      assertEquals(1, factory.created);
      assertEquals("shadowwarrior", factory.petType);
      assertTrue(factory.skillLevel == 5);
      assertTrue(world.getMapper(UnitStates.class).get(factory.entityId).stateList
          .hasState(StateId.SHADOWWARRIOR));
    } finally {
      world.dispose();
    }
  }

  @Test
  void sentrySrvDo045CreatesOwnedTrapWithNativeShotBudget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      assertNotNull(sentry);
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = Attributes.obtainStandard();
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), sentry.srvdofunc, 0));
      assertEquals(1, factory.created);
      assertEquals("assassintrap", factory.petType);
      assertEquals(5, factory.petMaximum);
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId));
      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertEquals("assassintrap", trap.petType);
      assertEquals(10, trap.maxShots);

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(9, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(100);
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      trap.attackCooldownFrames = 0;
      trap.maxShots = 1;
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.missiles);
      assertEquals("sentry lightning", factory.attackSkill);
      assertEquals(1, trap.shotsFired);
      Missile lightning = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(lightning);
      assertEquals(sentry.Id, lightning.skillId,
          "Missiles.txt links the trap projectile to Lightning Sentry damage data");
      assertTrue(lightning.damageSnapshot,
          "Lightning Sentry creates an authoritative elemental damage snapshot");
      world.process();
      world.process();
      assertTrue(!world.getMapper(SummonedPet.class).has(factory.entityId),
          "a native sentry must be removed after its shot budget is exhausted");
    } finally {
      world.dispose();
    }
  }

  @Test
  void sentryProjectileCompletesAfterTrapShotBudgetRetiresController() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new SummonedPetSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(10, 0), sentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(14, 0);
      Attributes targetAttrs = attributes(10000);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;
      world.setDelta(1f / 25f);
      world.process(); // fire the one permitted shot
      assertEquals(1, factory.missiles);
      world.process(); // retire the trap controller before the missile arrives
      assertFalse(world.getEntityManager().isActive(factory.entityId));
      for (int i = 0; i < 30 && targetAttrs.get(Stat.hitpoints).asFixed() >= 10000f; i++) {
        world.process();
      }
      assertTrue(targetAttrs.get(Stat.hitpoints).asFixed() < 10000f,
          "an in-flight sentry projectile must retain its owner after trap retirement");
    } finally {
      world.dispose();
    }
  }

  @Test
  void sentryRetargetsAfterTargetRemovalAndRetiresAtShotBudget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new SummonedPetSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      assertNotNull(sentry);
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(0, 0), sentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 2;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      int firstTarget = world.create();
      world.getMapper(Monster.class).create(firstTarget);
      world.getMapper(Position.class).create(firstTarget).position.set(4, 0);
      world.getMapper(AttributesWrapper.class).create(firstTarget).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.missiles);
      assertTrue(factory.missileDirections.get(0).x > 0,
          "the first sentry shot must target the original hostile");
      assertEquals(1, trap.shotsFired);

      world.delete(firstTarget);
      world.process(); // flush the removed target before the next search
      int replacement = world.create();
      world.getMapper(Monster.class).create(replacement);
      world.getMapper(Position.class).create(replacement).position.set(-4, 0);
      world.getMapper(AttributesWrapper.class).create(replacement).attrs = attributes(10000);
      trap.attackCooldownFrames = 0;
      world.process();
      assertEquals(2, factory.missiles);
      assertTrue(factory.missileDirections.get(1).x < 0,
          "the second sentry shot must retarget the replacement hostile");
      assertEquals(2, trap.shotsFired);

      world.process();
      assertFalse(world.getEntityManager().isActive(factory.entityId),
          "the sentry controller retires after its native shot budget");
    } finally {
      world.dispose();
    }
  }

  @Test
  void inactiveSentryPausesAndResumesItsCheckpointedSchedule() {
    RecordingFactory factory = new RecordingFactory();
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0);
    com.riiablo.map.Map.Zone zone = nativeThreeRoomZoneForTrap();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new RoomActivationSystem(), new ServerSkillSystem(true),
            new AssassinTrapSystem(), new SummonedPetSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", map));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      assertNotNull(sentry);
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(owner).set(map, zone);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(90, 10), sentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.attackCooldownFrames = 0;
      world.getMapper(MapWrapper.class).create(factory.entityId).set(map, zone);
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(95, 10);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);

      world.process(); // owner anchors room 0; trap in room 2 remains inactive
      assertEquals(0, factory.missiles,
          "an out-of-sight sentry must pause without consuming its shot schedule");
      assertEquals(0, trap.shotsFired);

      world.getMapper(Position.class).get(owner).position.set(50, 10);
      world.process(); // room 1 activates its direct sight ring, including room 2
      assertTrue(zone.isRoomActiveForAI(90, 10), "room 2 must be active from room 1 sight");
      assertTrue(world.getEntityManager().isActive(factory.entityId));
      assertEquals(1, factory.missiles,
          "the sentry must resume and fire when its room becomes active");
      assertEquals(1, trap.shotsFired);
    } finally {
      world.dispose();
    }
  }

  @Test
  void playerDepartureRemovesOwnedAssassinSentry() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new SummonedPetSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry sentry = Riiablo.files.skills.get("Lightning Sentry");
      assertNotNull(sentry);
      data.setSkillLevel(sentry.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, sentry.Id, Engine.INVALID_ENTITY, new Vector2(4, 0), sentry.srvdofunc, 0));

      assertTrue(factory.entityId >= 0, "the skill must create an owned sentry before departure");
      assertTrue(world.getEntityManager().isActive(factory.entityId));
      world.delete(owner);
      world.process(); // flush the player's deferred entity deletion
      world.process(); // SummonedPetSystem observes the missing owner
      assertFalse(world.getEntityManager().isActive(factory.entityId),
          "owned sentries must be removed when their player leaves the world");
    } finally {
      world.dispose();
    }
  }

  @Test
  void chargedBoltSentrySrvDo017EmitsNativeBoltBurst() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry charged = Riiablo.files.skills.get("Charged Bolt Sentry");
      Skills.Entry shockField = Riiablo.files.skills.get("Shock Field");
      assertNotNull(charged);
      assertNotNull(shockField);
      data.setSkillLevel(charged.Id, 3);
      data.setSkillLevel(shockField.Id, 6);
      data.setSkillLevel(Riiablo.files.skills.get("Fire Trauma").Id, 3);
      data.setSkillLevel(Riiablo.files.skills.get("Lightning Sentry").Id, 1);
      data.setSkillLevel(Riiablo.files.skills.get("Death Sentry").Id, 2);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      Attributes ownerAttrs = attributes(100);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = ownerAttrs;
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, charged.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), charged.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      assertEquals(5, trap.maxShots);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(10, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      world.process();

      assertEquals(7, factory.missiles,
          "SrvDo017 calc1 emits five base bolts plus one per three Shock Field base levels");
      assertEquals(7, java.util.Collections.frequency(factory.missileNames, "sentrychargedbolt"));
      Skills.Entry boltSentry = Riiablo.files.skills.get("BoltSentry");
      assertNotNull(boltSentry);
      assertEquals(1, trap.shotsFired,
          "the burst consumes one sentry attack, not one shot per projectile");
      float firstX = factory.missileDirections.get(0).x;
      boolean spread = false;
      for (Vector2 direction : factory.missileDirections) {
        if (Math.abs(direction.x - firstX) > 0.01f) {
          spread = true;
          break;
        }
      }
      assertTrue(spread, "charged bolts use independent native fan paths");
      for (int i = 0; i < factory.missileEntityIds.size(); i++) {
        Missile bolt = world.getMapper(Missile.class).get(factory.missileEntityIds.get(i));
        assertNotNull(bolt);
        assertEquals(factory.entityId, bolt.ownerId,
            "sentry bolts retain the trap as native missile owner for hostile filtering");
        assertEquals(charged.Id, bolt.skillId,
            "Missiles.txt links each bolt to Charged Bolt Sentry damage data");
        assertTrue(bolt.damageSnapshot,
            "each charged bolt carries authoritative lightning damage");
        assertTrue(bolt.chargedBoltPath);
        assertEquals(77f, bolt.range, 0.0001f,
            "native Charged Bolt path length is capped at 77");
      }

      // D2MOO evaluates the attack missile's Skills.txt damage expression at
      // cast time.  The reference packet below must match the trap packet;
      // omitting the owner hard-skill resolver silently drops Fire Trauma /
      // Lightning Sentry / Death Sentry synergies.
      Missile firstBolt = world.getMapper(Missile.class).get(factory.missileEntityIds.get(0));
      Skills.Entry boltSkill = firstBolt.missile.Skill != null
          && !firstBolt.missile.Skill.isEmpty()
          ? Riiablo.files.skills.get(firstBolt.missile.Skill) : charged;
      Missile expected = new Missile().set(firstBolt.missile, Vector2.Zero, firstBolt.range);
      MissileDamageResolver.initializeSkill(expected, boltSkill, ownerAttrs, trap.skillLevel,
          name -> {
            Skills.Entry row = Riiablo.files.skills.get(name);
            return row != null ? data.getBaseSkillLevel(row.Id) : 0;
          });
      assertEquals(expected.damage.get(Stat.lightmaxdam).asInt(),
          firstBolt.damage.get(Stat.lightmaxdam).asInt(),
          "Charged Bolt trap packets retain localized hard-skill synergies");

      int initialSeed = firstBolt.chargedBoltSeedLow;
      world.delete(target);
      for (int i = 0; i < 5; i++) world.process();
      assertTrue(firstBolt.chargedBoltSeedLow != initialSeed,
          "PATHTYPE_CHARGEDBOLT rolls a new left/straight/right segment every two tiles");
      assertTrue(firstBolt.chargedBoltNextTurnDistance >= 4f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void shockFieldSrvDo043UsesProgressiveCountAndScattersAuthoritativeMissiles() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry shock = Riiablo.files.skills.get("Shock Field");
      assertNotNull(shock);
      data.setSkillLevel(shock.Id, 1);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, shock.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), shock.srvdofunc, 0));

      assertEquals(6, factory.missiles,
          "SrvDo043 uses par1 + lvl/par2 at level one when Fire Trauma is unskilled");
      assertEquals(6, java.util.Collections.frequency(factory.missileNames, "shock field in air"));
      boolean varied = false;
      Vector2 first = factory.missileDirections.get(0);
      for (Vector2 direction : factory.missileDirections) {
        assertTrue(direction.len() > 0.99f && direction.len() < 1.01f);
        if (Math.abs(direction.x - first.x) > 0.01f
            || Math.abs(direction.y - first.y) > 0.01f) varied = true;
      }
      assertTrue(varied, "native Shock Field scatters each landing point independently");
      for (int id : factory.missileEntityIds) {
        Missile projectile = world.getMapper(Missile.class).get(id);
        assertNotNull(projectile);
        assertEquals(shock.Id, projectile.skillId);
        assertEquals(1, projectile.damageLevel);
        assertTrue(projectile.damageSnapshot,
            "Shock Field missiles retain the authoritative lightning damage snapshot");
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void bladeSentinelSrvDo044StartsAtCasterAndLaunchesTowardTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry blade = Riiablo.files.skills.get("Blade Sentinel");
      assertNotNull(blade);
      data.setSkillLevel(blade.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      Attributes ownerAttrs = attributes(100);
      ownerAttrs.base().put(Stat.mindamage, 10);
      ownerAttrs.base().put(Stat.maxdamage, 20);
      ownerAttrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = ownerAttrs;
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, blade.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), blade.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertTrue(trap.bladeSentinel);
      assertEquals(1, trap.maxShots);
      assertTrue(trap.durationFrames > 0);
      assertEquals(2f, world.getMapper(Position.class).get(factory.entityId).position.x);
      assertEquals(3f, world.getMapper(Position.class).get(factory.entityId).position.y);
      assertEquals(8f, trap.trapTargetX);
      assertEquals(3f, trap.trapTargetY);
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      trap.attackCooldownFrames = 0;
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1, factory.missiles);
      assertEquals("blade creeper", factory.missileName);
      Missile bladeMissile = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(bladeMissile);
      assertTrue(bladeMissile.attached);
      assertEquals(factory.entityId, bladeMissile.attachedEntityId);
      assertEquals(owner, bladeMissile.ownerId,
          "the player remains the authoritative damage owner");
      assertEquals(blade.Id, bladeMissile.skillId);
      assertTrue(bladeMissile.damageSnapshot,
          "Blade Sentinel must inherit the caster's weapon damage snapshot");
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Blade Sentinel controller survives until its native duration expires");

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(5, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);

      boolean reachedTarget = false;
      boolean startedReturn = false;
      for (int i = 0; i < 80; i++) {
        world.process();
        float controllerX = world.getMapper(Position.class).get(factory.entityId).position.x;
        float missileX = world.getMapper(Position.class).get(factory.missileEntityId).position.x;
        assertEquals(controllerX, missileX, 0.0001f,
            "SrvDo20 keeps the blade missile attached to its controller");
        if (controllerX >= 7.99f) reachedTarget = true;
        if (reachedTarget && controllerX < 7f) startedReturn = true;
      }
      assertTrue(reachedTarget, "Blade Creeper must reach the selected endpoint");
      assertTrue(startedReturn, "Blade Creeper must switch back toward its cast origin");
      assertEquals(1, factory.missiles, "Blade Creeper creates one attached missile only");
      assertTrue(world.getMapper(UnitStates.class).has(target),
          "a NextHit collision creates the target's native JUSTHIT state list");
      assertTrue(world.getMapper(UnitStates.class).get(target).stateList.hasState(StateId.JUSTHIT),
          "the attached blade applies target-wide native JUSTHIT suppression");
      assertTrue(world.getEntityManager().isActive(factory.missileEntityId),
          "SrvHit37 unit collisions must not destroy Blade Creeper");

      world.delete(factory.entityId);
      world.process();
      world.process();
      assertFalse(world.getEntityManager().isActive(factory.missileEntityId),
          "SrvDo20 removes the blade when its controller disappears");
    } finally {
      world.dispose();
    }
  }

  @Test
  void wakeOfFireSrvDo125CreatesMakerThenOppositeFireWaves() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry wake = Riiablo.files.skills.get("Wake of Fire Sentry");
      assertNotNull(wake);
      data.setSkillLevel(wake.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, wake.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), wake.srvdofunc, 0));
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      world.getMapper(SummonedPet.class).get(factory.entityId).maxShots = 1;

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      // Wake traps are deployed at the clicked point (8,3); keep the hostile
      // unit a few tiles away so the maker has a real travel segment.
      world.getMapper(Position.class).create(target).position.set(14, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      for (int i = 0; i < 30; i++) world.process();

      assertEquals(3, factory.missileNames.size(),
          "SrvDo125 creates one maker and two SrvDo31 wave missiles");
      assertEquals("wake of destruction maker", factory.missileNames.get(0));
      assertEquals("wake of destruction", factory.missileNames.get(1));
      assertEquals("wake of destruction", factory.missileNames.get(2));
      assertEquals(-factory.missileDirections.get(1).x, factory.missileDirections.get(2).x, 0.0001f);
      assertEquals(-factory.missileDirections.get(1).y, factory.missileDirections.get(2).y, 0.0001f);
      assertFalse(world.getEntityManager().isActive(factory.missileEntityIds.get(0)),
          "the maker is consumed after spawning its waves");
      assertTrue(factory.missileEntityIds.get(1) != factory.missileEntityIds.get(2),
          "the two wave missiles are distinct authoritative entities");
      assertFalse(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Wake of Fire controller retires after its one-shot budget");
    } finally {
      world.dispose();
    }
  }

  @Test
  void infernoSentrySrvDo095RepeatsMissilesAndTracksTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry inferno = Riiablo.files.skills.get("Inferno Sentry");
      Skills.Entry wake = Riiablo.files.skills.get("Wake of Fire Sentry");
      assertNotNull(inferno);
      assertNotNull(wake);
      data.setSkillLevel(inferno.Id, 4);
      data.setSkillLevel(wake.Id, 3);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, inferno.Id, Engine.INVALID_ENTITY, new Vector2(8, 3), inferno.srvdofunc, 0));
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1, factory.missiles);
      assertEquals("inferno sentry 1", factory.missileName);
      Missile channel = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(channel);
      assertFalse(channel.persistent,
          "SrvDo95 emits separate missiles instead of one synthetic persistent area");
      assertEquals(23f, channel.range, 0.0001f,
          "calc1 = ln34/2 + Wake of Fire synergy controls each missile path");
      assertTrue(trap.infernoChanneling);
      assertEquals(18, trap.infernoRemainingFrames,
          "calc2 = par1 + Wake of Fire synergy controls the repeat window");
      assertEquals(3, trap.infernoPulseFrames,
          "calc3 controls the native inferno repeat cadence");

      world.getMapper(Position.class).get(target).position.set(12, 8);
      for (int i = 0; i < 3; i++) world.process();
      assertEquals(2, factory.missiles,
          "SrvDo95 creates another missile on the calc3 animation event");
      assertTrue(factory.missileDirections.get(1).y > 0f,
          "each inferno pulse updates its direction toward the moving target");
      for (int i = 0; i < 25 && world.getMapper(SummonedPet.class).has(factory.entityId); i++) {
        world.process();
      }
      assertFalse(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Inferno controller retires after its native channel and shot budget");
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathSentrySrvDo055ConsumesOneCorpseAndDamagesNearbyEnemies() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      assertEquals(7, trap.maxShots,
          "calc4 includes one shot per three base Fire Blast levels");
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      int corpseId = world.create();
      Monster corpseMonster = world.getMapper(Monster.class).create(corpseId);
      corpseMonster.monstats = fallen;
      corpseMonster.monstats2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      assertNotNull(corpseMonster.monstats2);
      assertTrue(corpseMonster.monstats2.corpseSel);
      world.getMapper(Position.class).create(corpseId).position.set(11, 3);
      Attributes corpseAttrs = attributes(100);
      corpseAttrs.get(Stat.hitpoints).set(0);
      world.getMapper(AttributesWrapper.class).create(corpseId).attrs = corpseAttrs;
      Corpse corpse = world.getMapper(Corpse.class).create(corpseId).reset(
          Corpse.DEFAULT_DURATION, true);
      world.getMapper(UnitStates.class).create(corpseId).init(corpseId);

      int target = world.create();
      world.getMapper(Monster.class).create(target).monstats = fallen;
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1000);
      int outerTarget = world.create();
      world.getMapper(Monster.class).create(outerTarget).monstats = fallen;
      // Native ln34 is par3 + (level - 1) * par4, so level four yields a
      // seven-tile elemental radius and a 6.5-tile physical radius.
      world.getMapper(Position.class).create(outerTarget).position.set(17.8f, 3);
      world.getMapper(AttributesWrapper.class).create(outerTarget).attrs = attributes(1000);

      world.setDelta(1f / 25f);
      world.process();

      assertFalse(corpse.usable, "SrvDo55 reserves and consumes the selected corpse");
      assertTrue(world.getMapper(UnitStates.class).get(corpseId).stateList
          .hasState(StateId.CORPSE_NODRAW));
      assertEquals(corpseId, trap.deathLastCorpseId);
      assertEquals(1, trap.shotsFired, "one corpse explosion consumes one sentry shot");
      float innerHp = world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed();
      assertTrue(innerHp >= 920f && innerHp <= 960f,
          "the inner radius receives the native 40%-80% corpse-life roll");
      float outerHp = world.getMapper(AttributesWrapper.class).get(outerTarget).attrs
          .get(Stat.hitpoints).asFixed();
      assertTrue(outerHp >= 960f && outerHp <= 980f,
          "the outer half-tile ring retains the native elemental portion");
      assertEquals(1, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"),
          "the consumed corpse creates one synchronized explosion visual");

      Skills.Entry fallback = AssassinTrapSystem.resolveAttackSkill(
          world.getMapper(Monster.class).get(factory.entityId), deathSentry);
      assertNotNull(fallback);
      assertEquals("death sentry ltng", fallback.skill,
          "without a legal corpse Fn104 falls back to Skill2 lightning");
      assertTrue(hasText(fallback.srvmissile) || hasText(fallback.srvmissilea)
              || hasText(fallback.cltmissile) || hasText(fallback.cltmissilea),
          "the fallback row must provide an authoritative lightning missile");

      for (int i = 0; i < 5; i++) {
        trap.attackCooldownFrames = 0;
        world.process();
      }
      assertEquals(1, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"),
          "an already hidden corpse cannot be selected or exploded again");
      assertFalse(world.getMapper(SummonedPet.class).has(factory.entityId),
          "Death Sentry controller retires after its one-shot corpse budget");
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathSentrySrvDo055RejectsCorpseAtNativeDistanceBoundary() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      int target = world.create();
      world.getMapper(Monster.class).create(target).monstats = fallen;
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);

      Skills.Entry corpseSkill = Riiablo.files.skills.get("mon death sentry");
      assertNotNull(corpseSkill);
      assertTrue(corpseSkill.Param != null && corpseSkill.Param.length >= 4,
          "Death Sentry must expose native corpse-distance parameters");
      int nativeRange = corpseSkill.Param[2] + (4 - 1) * corpseSkill.Param[3];
      assertTrue(nativeRange > 0, "native corpse-distance range must be positive");
      int corpseId = world.create();
      Monster corpseMonster = world.getMapper(Monster.class).create(corpseId);
      corpseMonster.monstats = fallen;
      corpseMonster.monstats2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      assertNotNull(corpseMonster.monstats2);
      assertTrue(corpseMonster.monstats2.corpseSel);
      world.getMapper(Position.class).create(corpseId).position.set(
          12f + nativeRange / 2f, 3f);
      Attributes corpseAttrs = attributes(100);
      corpseAttrs.get(Stat.hitpoints).set(0);
      world.getMapper(AttributesWrapper.class).create(corpseId).attrs = corpseAttrs;
      Corpse corpse = world.getMapper(Corpse.class).create(corpseId).reset(
          Corpse.DEFAULT_DURATION, true);
      world.getMapper(UnitStates.class).create(corpseId).init(corpseId);

      world.setDelta(1f / 25f);
      world.process();

      assertTrue(corpse.usable,
          "Fn104 uses a strict '< nativeRange / 2' corpse-to-hostile gate");
      assertEquals(0, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"),
          "a corpse exactly on the native boundary must not trigger SrvDo055");
      assertFalse(world.getMapper(UnitStates.class).get(corpseId).stateList
          .hasState(StateId.CORPSE_NOSELECT));
      assertFalse(world.getMapper(UnitStates.class).get(corpseId).stateList
          .hasState(StateId.CORPSE_NODRAW));
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathSentrySrvDo055UsesNativeCorpseInsertionOrderAcrossNormalShotBudget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 2;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);

      com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      int target = world.create();
      world.getMapper(Monster.class).create(target).monstats = fallen;
      world.getMapper(Position.class).create(target).position.set(12, 3);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(10000);
      int firstCorpse = createSelectableFallenCorpse(world, fallen, 11, 3, 100);
      // The newer corpse is farther from the hostile but still inside the
      // native corpse-to-hostile gate. D2MOO returns it first because the
      // RoomEx unit list inserts new units at pUnitFirst; distance is not a
      // ranking criterion.
      int secondCorpse = createSelectableFallenCorpse(world, fallen, 14, 3, 100);

      world.setDelta(1f / 25f);
      world.process();

      boolean firstConsumed = !world.getMapper(Corpse.class).get(firstCorpse).usable;
      boolean secondConsumed = !world.getMapper(Corpse.class).get(secondCorpse).usable;
      assertTrue(firstConsumed ^ secondConsumed,
          "the first SrvDo055 transaction consumes exactly one of two legal corpses");
      int firstSelected = firstConsumed ? firstCorpse : secondCorpse;
      assertEquals(firstSelected, trap.deathLastCorpseId);
      assertEquals(secondCorpse, firstSelected,
          "native room-list insertion order beats corpse distance within one RoomEx");
      assertEquals(1, trap.shotsFired);

      trap.attackCooldownFrames = 0;
      world.process();

      assertFalse(world.getMapper(Corpse.class).get(firstCorpse).usable);
      assertFalse(world.getMapper(Corpse.class).get(secondCorpse).usable,
          "the second shot must consume the other legal corpse");
      assertEquals(2, trap.shotsFired);
      assertEquals(2, java.util.Collections.frequency(factory.missileNames, "corpseexplosion"));

      world.process();
      assertFalse(world.getMapper(SummonedPet.class).has(factory.entityId),
          "the normal two-shot budget retires after both corpse transactions");
    } finally {
      world.dispose();
    }
  }

  @Test
  void deathSentryUsesSkill2LightningWhenNoCorpseIsInRange() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    com.riiablo.codec.excel.MonStats.Entry summon = null;
    int[] originalAttackChance = null;
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      Skills.Entry fallback = Riiablo.files.skills.get("death sentry ltng");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      assertNotNull(fallback);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      Monster sentry = world.getMapper(Monster.class).get(factory.entityId);
      assertNotNull(sentry);
      summon = sentry.monstats;
      assertNotNull(summon);
      originalAttackChance = summon.aip3;
      summon.aip3 = new int[] {100, 100, 100};

      int target = world.create();
      world.getMapper(Monster.class).create(target);
      world.getMapper(Position.class).create(target).position.set(12, 3);
      Attributes targetAttrs = attributes(10000);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;

      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1, factory.missiles);
      String resolvedMissile = AssassinTrapSystem.resolveMissile(fallback, sentry);
      Missiles.Entry helper = Riiablo.files.Missiles.get(resolvedMissile);
      assertNotNull(helper);
      Missile lightning = world.getMapper(Missile.class).get(factory.missileEntityId);
      assertNotNull(lightning);
      assertEquals(helper.Id, lightning.missile.Id,
          "Death Sentry Skill2 must use the native lightning helper missile row");
      assertTrue(lightning.damageSnapshot,
          "Skill2 fallback must initialize an authoritative damage snapshot");
      assertEquals(resolvedMissile, lightning.missile.Missile,
          "fallback missile row remains sourced from Skill2");
      for (int i = 0; i < 30 && targetAttrs.get(Stat.hitpoints).asFixed() >= 10000f; i++) {
        world.process();
      }
      assertTrue(targetAttrs.get(Stat.hitpoints).asFixed() < 10000f,
          "Skill2 lightning must damage the hostile after authoritative collision");
    } finally {
      if (summon != null) summon.aip3 = originalAttackChance;
      world.dispose();
    }
  }

  @Test
  void deathSentryDoesNotFireSkill2WithoutHostileTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    com.riiablo.codec.excel.MonStats.Entry summon = null;
    int[] originalAttackChance = null;
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      Monster sentry = world.getMapper(Monster.class).get(factory.entityId);
      assertNotNull(sentry);
      summon = sentry.monstats;
      originalAttackChance = summon.aip3;
      summon.aip3 = new int[] {100, 100, 100};

      world.setDelta(1f / 25f);
      world.process();

      assertEquals(0, factory.missiles,
          "a Death Sentry with no hostile target must not emit Skill2 lightning");
      assertEquals(0, trap.shotsFired,
          "a null target must not consume the sentry shot budget");
      assertTrue(world.getMapper(SummonedPet.class).has(factory.entityId));
    } finally {
      if (summon != null) summon.aip3 = originalAttackChance;
      world.dispose();
    }
  }

  @Test
  void deathSentryRejectsNativeInvalidAndTownTargetsBeforeSkill2() {
    RecordingFactory factory = new RecordingFactory();
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", map));
    com.riiablo.codec.excel.MonStats.Entry summon = null;
    int[] originalAttackChance = null;
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      Monster sentry = world.getMapper(Monster.class).get(factory.entityId);
      summon = sentry.monstats;
      originalAttackChance = summon.aip3;
      summon.aip3 = new int[] {100, 100, 100};

      com.riiablo.codec.excel.MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      int invalid = world.create();
      world.getMapper(Monster.class).create(invalid).monstats = fallen;
      world.getMapper(Position.class).create(invalid).position.set(10, 3);
      Attributes invalidAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(invalid).attrs = invalidAttrs;
      world.getMapper(NativeUnitFlags.class).create(invalid).reset();

      int townTarget = world.create();
      world.getMapper(Monster.class).create(townTarget).monstats = fallen;
      world.getMapper(Position.class).create(townTarget).position.set(11, 3);
      Attributes townAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(townTarget).attrs = townAttrs;
      com.riiablo.map.Map.Zone town = new com.riiablo.map.Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      world.getMapper(MapWrapper.class).create(townTarget).set(map, town);

      int valid = world.create();
      world.getMapper(Monster.class).create(valid).monstats = fallen;
      world.getMapper(Position.class).create(valid).position.set(12, 3);
      Attributes validAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(valid).attrs = validAttrs;

      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1, factory.missiles,
          "Death Sentry must continue to the next valid hostile target");
      assertEquals(1000f, invalidAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "a target without CAN_BE_ATTACKED/IS_VALID_TARGET is a native null-hit");
      assertEquals(1000f, townAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "Town units are excluded before the Skill2 fallback is fired");
      assertEquals(1000f, validAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "the same-tick missile has not collided yet; creation proves the valid target was selected");
    } finally {
      if (summon != null) summon.aip3 = originalAttackChance;
      world.dispose();
    }
  }

  @Test
  void deathSentryRejectsHostileBehindMissileBarrier() {
    RecordingFactory factory = new RecordingFactory();
    BarrierMap map = new BarrierMap();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new AssassinTrapSystem(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", map));
    com.riiablo.codec.excel.MonStats.Entry summon = null;
    int[] originalAttackChance = null;
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry deathSentry = Riiablo.files.skills.get("Death Sentry");
      Skills.Entry fireBlast = Riiablo.files.skills.get("Fire Trauma");
      assertNotNull(deathSentry);
      assertNotNull(fireBlast);
      data.setSkillLevel(deathSentry.Id, 4);
      data.setSkillLevel(fireBlast.Id, 6);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(100);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, deathSentry.Id, Engine.INVALID_ENTITY, new Vector2(8, 3),
          deathSentry.srvdofunc, 0));

      SummonedPet trap = world.getMapper(SummonedPet.class).get(factory.entityId);
      assertNotNull(trap);
      trap.maxShots = 1;
      trap.attackCooldownFrames = 0;
      world.getMapper(AttributesWrapper.class).create(factory.entityId).attrs = attributes(100);
      world.getMapper(MapWrapper.class).create(factory.entityId).set(map, map.zone);
      Monster sentry = world.getMapper(Monster.class).get(factory.entityId);
      summon = sentry.monstats;
      originalAttackChance = summon.aip3;
      summon.aip3 = new int[] {100, 100, 100};

      int target = world.create();
      world.getMapper(Monster.class).create(target).monstats = Riiablo.files.monstats.get("fallen1");
      world.getMapper(Position.class).create(target).position.set(12, 3);
      Attributes targetAttrs = attributes(1000);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;
      world.getMapper(MapWrapper.class).create(target).set(map, map.zone);

      world.setDelta(1f / 25f);
      world.process();

      assertEquals(0, factory.missiles,
          "COLLIDE_MISSILE_BARRIER must prevent Death Sentry target acquisition");
      assertEquals(0, trap.shotsFired,
          "a wall-blocked target must not consume the trap shot budget");
      assertEquals(1000f, targetAttrs.get(Stat.hitpoints).asFixed(), 0.001f);
    } finally {
      if (summon != null) summon.aip3 = originalAttackChance;
      world.dispose();
    }
  }

  @Test
  void bladeShieldAndVenomExposeNativeSkillData() {
    Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
    assertNotNull(blade);
    assertEquals(277, blade.Id);
    assertEquals(28, blade.srvstfunc);
    assertEquals(54, blade.srvdofunc);
    assertEquals("bladeshield", blade.aurastate);
    assertEquals("ln12", blade.auralencalc);
    assertEquals("par4", blade.aurarangecalc);
    assertEquals(33667, blade.aurafilter);
    assertTrue(blade.periodic);
    assertEquals("par3", blade.perdelay);
    assertEquals(32, blade.ResultFlags);
    assertEquals(32, blade.SrcDam);
    assertNotNull(Riiablo.files.Overlay.get("bladeshield"));
    assertEquals(500, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        blade.auralencalc, blade, 1));
    assertEquals(25, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        blade.perdelay, blade, 1));
    assertEquals(6, AssassinSkills.bladeShieldRange(blade, 1));
    assertTrue(java.util.Arrays.equals(
        new int[] {1, 30}, AssassinSkills.bladeShieldDamageRange(blade, 1)));

    Skills.Entry venom = Riiablo.files.skills.get("Venom");
    assertNotNull(venom);
    assertEquals(278, venom.Id);
    assertEquals(18, venom.srvdofunc);
    assertEquals("venomclaws", venom.aurastate);
    assertEquals("poisonmindam", venom.aurastat[0]);
    assertEquals("poisonmaxdam", venom.aurastat[1]);
    assertEquals("skill_poison_override_length", venom.aurastat[2]);
    assertTrue(Riiablo.files.colors.index("cgrn") >= 0);
    assertEquals(3000, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        venom.auralencalc, venom, 1));
    assertTrue(java.util.Arrays.equals(
        new int[] {6, 8}, AssassinSkills.venomDamageRange(venom, 1)));
    assertEquals(10, AssassinSkills.venomPoisonLength(venom, 1));

    Attributes attacker = attributes(100);
    attacker.base().put(Stat.mindamage, 20);
    attacker.base().put(Stat.maxdamage, 20);
    attacker.base().put(Stat.firemindam, 8);
    attacker.base().put(Stat.firemaxdam, 8);
    attacker.reset();
    CombatSystem.CombatResult scaled = CombatSystem.INSTANCE.calculateBladeShieldAttack(
        attacker, attributes(100), true, false,
        20, 20, blade.SrcDam, 0, true, null, null, false);
    assertEquals(10, scaled.physicalDamage,
        "SrcDam 32 scales the complete skill-plus-weapon physical packet to 25%");
    assertEquals(2, scaled.elementalDamage[CombatSystem.DAMAGE_FIRE],
        "SrcDam also scales source elemental damage to 25%");
  }

  @Test
  void venomAddsDamageAndOverridesItemPoisonLength() {
    RecordingFactory factory = new RecordingFactory();
    StateUpdater updater = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), updater, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry venomSkill = Riiablo.files.skills.get("Venom");
      int assassin = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(venomSkill.Id, 1);
      world.getMapper(com.riiablo.engine.server.component.Player.class)
          .create(assassin).data = data;
      world.getMapper(Position.class).create(assassin).position.set(0, 0);
      Attributes attacker = attributes(100);
      attacker.base().put(Stat.mindamage, 1);
      attacker.base().put(Stat.maxdamage, 1);
      attacker.base().put(Stat.poisonmindam, 4);
      attacker.base().put(Stat.poisonmaxdam, 8);
      attacker.base().put(Stat.poisonlength, 100);
      attacker.reset();
      world.getMapper(AttributesWrapper.class).create(assassin).attrs = attacker;
      world.getMapper(UnitStates.class).create(assassin).init(assassin);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, venomSkill.Id, Engine.INVALID_ENTITY, new Vector2(),
          venomSkill.srvdofunc, venomSkill.cltdofunc));

      com.riiablo.engine.server.state.UnitState venom = world.getMapper(UnitStates.class)
          .get(assassin).stateList.getState(StateId.VENOMCLAWS);
      assertNotNull(venom);
      assertEquals(3000, venom.duration);
      assertEquals(6, venom.poisonMinDamage);
      assertEquals(8, venom.poisonMaxDamage);
      assertEquals(10, venom.poisonLengthOverride);

      Attributes defender = attributes(1000);
      CombatSystem.CombatResult hit = CombatSystem.INSTANCE.calculateAttack(
          attacker, defender, true, false, false,
          0, 0, 1000, true, null, null, 0, 0,
          world.getMapper(UnitStates.class).get(assassin).stateList, null, false);
      assertEquals(10, hit.poisonDuration,
          "Venom replaces the item's longer poison duration with 0.4 seconds");
      assertTrue(hit.elementalDamage[CombatSystem.DAMAGE_POISON] >= 10);
      assertTrue(hit.elementalDamage[CombatSystem.DAMAGE_POISON] <= 16,
          "item poison and Venom per-frame damage remain additive");

      com.riiablo.engine.server.state.UnitState stronger = AssassinSkills.applyVenomState(
          world.getMapper(UnitStates.class).get(assassin).stateList, venomSkill, 5, assassin);
      assertEquals(5, stronger.level);
      com.riiablo.engine.server.state.UnitState replaced = AssassinSkills.applyVenomState(
          world.getMapper(UnitStates.class).get(assassin).stateList, venomSkill, 1, assassin);
      assertEquals(1, replaced.level,
          "SrvDo018 replaces the prior stat list instead of retaining a stale higher level");
      assertEquals(3000, replaced.duration);
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldPulsesAtNativeRangeAndCadence() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes closeAttrs = attributes(1000);
      int close = createBladeShieldMonster(world, 5.5f, closeAttrs);
      Attributes secondCloseAttrs = attributes(1000);
      createBladeShieldMonster(world, 4f, secondCloseAttrs);
      Attributes farAttrs = attributes(1000);
      createBladeShieldMonster(world, 6.5f, farAttrs);

      com.badlogic.gdx.math.MathUtils.random.setSeed(0xB1ADE51EL);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();
      float firstHp = closeAttrs.get(Stat.hitpoints).asFixed();
      assertTrue(firstHp < 1000f, "the keyframe arms and resolves the first pulse");
      assertTrue(secondCloseAttrs.get(Stat.hitpoints).asFixed() < 1000f,
          "each hostile target in range receives an independent hit");
      assertEquals(1000f, farAttrs.get(Stat.hitpoints).asFixed(),
          "AuraRangeCalc par4 limits Blade Shield to six subtiles");
      assertTrue(world.getMapper(UnitStates.class).get(close).stateList.size() >= 0);

      for (int i = 0; i < 24; i++) world.process();
      assertEquals(firstHp, closeAttrs.get(Stat.hitpoints).asFixed(),
          "no extra hit may occur before PerDelay par3 expires");
      world.process();
      assertTrue(closeAttrs.get(Stat.hitpoints).asFixed() < firstHp,
          "the next periodic event resolves after 25 frames");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldStateRemainsVisibleButDealsNoTownDamage() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new TownMap());
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes targetAttrs = attributes(1000);
      createBladeShieldMonster(world, 2f, targetAttrs);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(1000f, targetAttrs.get(Stat.hitpoints).asFixed());
      assertTrue(world.getMapper(UnitStates.class).get(assassin).stateList
          .hasState(StateId.BLADESHIELD));
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldAuraCrossesAdjacentRoomWithoutWallRaycast() {
    RecordingFactory factory = new RecordingFactory();
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0);
    World world = bladeShieldWorld(factory, map);
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      com.riiablo.map.Map.Zone zone = bladeShieldThreeRoomZone(4);
      int assassin = createBladeShieldPlayer(world, blade);
      world.getMapper(Position.class).get(assassin).position.set(3f, 0f);
      world.getMapper(MapWrapper.class).create(assassin).set(map, zone);
      Attributes targetAttrs = attributes(1000);
      int target = createBladeShieldMonster(world, 4.5f, targetAttrs);
      world.getMapper(MapWrapper.class).create(target).set(map, zone);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();

      assertTrue(targetAttrs.get(Stat.hitpoints).asFixed() < 1000f,
          "D2MOO scans the current RoomEx and its direct neighbors; walls do not add a raycast gate");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldSkipsNonAdjacentRoomsAndDifferentZones() {
    RecordingFactory factory = new RecordingFactory();
    com.riiablo.map.Map map = new com.riiablo.map.Map(0, 0);
    World world = bladeShieldWorld(factory, map);
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      com.riiablo.map.Map.Zone zone = bladeShieldThreeRoomZone(4);
      com.riiablo.map.Map.Zone otherZone = bladeShieldThreeRoomZone(4);
      int assassin = createBladeShieldPlayer(world, blade);
      world.getMapper(Position.class).get(assassin).position.set(3f, 0f);
      world.getMapper(MapWrapper.class).create(assassin).set(map, zone);

      Attributes nonAdjacentAttrs = attributes(1000);
      int nonAdjacent = createBladeShieldMonster(world, 8.5f, nonAdjacentAttrs);
      world.getMapper(MapWrapper.class).create(nonAdjacent).set(map, zone);
      Attributes otherZoneAttrs = attributes(1000);
      int otherZoneTarget = createBladeShieldMonster(world, 3.5f, otherZoneAttrs);
      world.getMapper(MapWrapper.class).create(otherZoneTarget).set(map, otherZone);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1000f, nonAdjacentAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "a coordinate-overlapping aura must not cross a non-adjacent RoomEx");
      assertEquals(1000f, otherZoneAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "a complete MapWrapper must reject targets in another zone");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldSkipsNativeInvalidAndNullHitTargets() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes invalidAttrs = attributes(1000);
      int invalid = createBladeShieldMonster(world, 2f, invalidAttrs);
      world.getMapper(NativeUnitFlags.class).create(invalid).reset();
      Attributes validAttrs = attributes(1000);
      createBladeShieldMonster(world, 2.5f, validAttrs);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();

      assertEquals(1000f, invalidAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "D2MOO target filtering rejects null/invalid combat flags before damage");
      assertTrue(validAttrs.get(Stat.hitpoints).asFixed() < 1000f,
          "a valid hostile target remains eligible");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldStopsWhenTheStateExpiresOrTheSkillIsLost() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes targetAttrs = attributes(1000);
      createBladeShieldMonster(world, 2f, targetAttrs);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      UnitState state = world.getMapper(UnitStates.class).get(assassin)
          .stateList.getState(StateId.BLADESHIELD);
      assertNotNull(state);
      state.periodicCountdownFrames = 0;
      world.getMapper(com.riiablo.engine.server.component.Player.class).get(assassin)
          .data.setSkillLevel(blade.Id, 0);
      world.process();
      assertEquals(1000f, targetAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "losing the skill cancels a pending periodic pulse");
      assertFalse(world.getMapper(UnitStates.class).get(assassin).stateList
          .hasState(StateId.BLADESHIELD));

      world.getMapper(com.riiablo.engine.server.component.Player.class).get(assassin)
          .data.setSkillLevel(blade.Id, 1);
      UnitState expiring = AssassinSkills.applyBladeShieldState(
          world.getMapper(UnitStates.class).get(assassin).stateList,
          blade, 1, assassin);
      expiring.duration = 1;
      expiring.periodicCountdownFrames = 0;
      world.process();
      float hpAfterFinalPulse = targetAttrs.get(Stat.hitpoints).asFixed();
      assertFalse(world.getMapper(UnitStates.class).get(assassin).stateList
          .hasState(StateId.BLADESHIELD));
      for (int i = 0; i < 30; i++) world.process();
      assertEquals(hpAfterFinalPulse, targetAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "an expired Blade Shield cannot schedule later pulses");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldStopsWhenThePlayerDiesBeforeTheNextPulse() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes targetAttrs = attributes(1000);
      createBladeShieldMonster(world, 2f, targetAttrs);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      UnitState state = world.getMapper(UnitStates.class).get(assassin)
          .stateList.getState(StateId.BLADESHIELD);
      assertNotNull(state);
      state.periodicCountdownFrames = 0;
      world.getMapper(AttributesWrapper.class).get(assassin).attrs
          .get(Stat.hitpoints).set(0);
      world.process();
      assertEquals(1000f, targetAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "a dead player cannot emit a pending Blade Shield pulse");
      assertFalse(world.getMapper(UnitStates.class).get(assassin).stateList
          .hasState(StateId.BLADESHIELD),
          "player death interrupts the periodic weapon state");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldStopsWhenItsStateIsRemovedBeforeTheNextPulse() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes targetAttrs = attributes(1000);
      createBladeShieldMonster(world, 2f, targetAttrs);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      UnitState state = world.getMapper(UnitStates.class).get(assassin)
          .stateList.getState(StateId.BLADESHIELD);
      assertNotNull(state);
      state.periodicCountdownFrames = 0;
      assertTrue(world.getMapper(UnitStates.class).get(assassin).stateList
          .removeState(StateId.BLADESHIELD));
      world.process();
      assertEquals(1000f, targetAttrs.get(Stat.hitpoints).asFixed(), 0.001f,
          "removing Blade Shield state cancels a pending periodic pulse");
      assertFalse(world.getMapper(UnitStates.class).get(assassin).stateList
          .hasState(StateId.BLADESHIELD));
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldPulseUsesNativeWeaponDurabilityPath() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      CharData data = world.getMapper(com.riiablo.engine.server.component.Player.class)
          .get(assassin).data;
      Item weapon = new Item();
      weapon.reset();
      weapon.setBase(Riiablo.files.weapons.get("clw"));
      assertNotNull(weapon.base, "native Assassin claw fixture is required");
      weapon.attrs.base().put(Stat.durability, 20);
      weapon.attrs.base().put(Stat.maxdurability, 20);
      weapon.attrs.reset();
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon));

      Attributes targetAttrs = attributes(1_000_000);
      createBladeShieldMonster(world, 2f, targetAttrs);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      UnitState state = world.getMapper(UnitStates.class).get(assassin)
          .stateList.getState(StateId.BLADESHIELD);
      assertNotNull(state);
      for (long seed = 1; seed <= 512 && weapon.attrs.get(Stat.durability).asInt() == 20; seed++) {
        state.periodicCountdownFrames = 0;
        MathUtils.random.setSeed(seed);
        world.process();
      }

      assertEquals(19, weapon.attrs.get(Stat.durability).asInt(),
          "a confirmed Blade Shield pulse drains one native weapon durability point");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeShieldPulseUsesNativeTargetArmorDurabilityPath() {
    RecordingFactory factory = new RecordingFactory();
    World world = bladeShieldWorld(factory, new com.riiablo.map.Map(0, 0));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Shield");
      int assassin = createBladeShieldPlayer(world, blade);
      Attributes targetAttrs = attributes(1_000_000);
      int target = createBladeShieldMonster(world, 2f, targetAttrs);
      CharData targetData = CharData.createRemote("target", (byte) Riiablo.ASSASSIN);
      Item armor = new Item();
      armor.reset();
      armor.setBase(Riiablo.files.armor.get("lbt"));
      assertNotNull(armor.base, "native armor fixture is required");
      armor.attrs.base().put(Stat.durability, 20);
      armor.attrs.base().put(Stat.maxdurability, 20);
      armor.attrs.reset();
      targetData.getItems().equipItem(BodyLoc.FEET, targetData.getItems().add(armor));
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(target).data = targetData;

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(),
          blade.srvdofunc, blade.cltdofunc));
      world.setDelta(1f / 25f);
      UnitState state = world.getMapper(UnitStates.class).get(assassin)
          .stateList.getState(StateId.BLADESHIELD);
      assertNotNull(state);
      for (long seed = 1; seed <= 512 && armor.attrs.get(Stat.durability).asInt() == 20; seed++) {
        state.periodicCountdownFrames = 0;
        MathUtils.random.setSeed(seed);
        world.process();
      }

      assertEquals(19, armor.attrs.get(Stat.durability).asInt(),
          "a confirmed Blade Shield hit drains one native target armor durability point");
    } finally {
      world.dispose();
      com.riiablo.engine.server.combat.StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void bladeFuryEmitsOneTimedWeaponBladeAndConsumesPerBladeMana() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Fury");
      int assassin = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(blade.Id, 1);
      for (String prerequisite : new String[] {blade.reqskill1, blade.reqskill2, blade.reqskill3}) {
        if (prerequisite != null && !prerequisite.isEmpty()) {
          Skills.Entry required = Riiablo.files.skills.get(prerequisite);
          if (required != null) data.setSkillLevel(required.Id, 1);
        }
      }
      world.getMapper(com.riiablo.engine.server.component.Player.class)
          .create(assassin).data = data;
      world.getMapper(Position.class).create(assassin).position.set(0, 0);
      Attributes attrs = attributes(1000);
      attrs.base().put(Stat.mindamage, 40);
      attrs.base().put(Stat.maxdamage, 40);
      attrs.base().put(Stat.mana, 30);
      attrs.base().put(Stat.maxmana, 30);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(assassin).attrs = attrs;
      world.getMapper(UnitStates.class).create(assassin).init(assassin);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(8, 0),
          blade.srvdofunc, blade.cltdofunc));
      assertEquals(1, factory.missiles, "SrvDo048 creates one authoritative blade");
      Missile first = world.getMapper(Missile.class).get(factory.missileEntityIds.get(0));
      assertEquals(blade.Id, first.skillId);
      assertEquals(1, first.damageLevel);
      assertTrue(first.damageSnapshot, "Blade Fury keeps the SrcDam weapon snapshot");
      assertEquals(38, first.damage.get(Stat.mindamage).asInt(),
          "SrcDam=96 scales the 40 damage weapon and adds Blade Fury MinDam");
      float afterFirst = attrs.get(Stat.mana).asFixed();

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(8, 0),
          blade.srvdofunc, blade.cltdofunc));
      assertEquals(1, factory.missiles, "Param4 blocks an early held-input blade");

      for (int i = 0; i < 5; i++) {
        world.setDelta(1f / 25f);
        world.process();
      }
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2(8, 0),
          blade.srvdofunc, blade.cltdofunc));
      assertEquals(2, factory.missiles, "held input may emit the next blade after Param4");
      assertTrue(attrs.get(Stat.mana).asFixed() < afterFirst,
          "each accepted blade consumes mana, not the cast-start event");
    } finally {
      world.dispose();
    }
  }

  @Test
  void bladeFuryUsesStartManaGateWithoutChargingAtCastSubmission() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry blade = Riiablo.files.skills.get("Blade Fury");
      int assassin = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(blade.Id, 1);
      for (String prerequisite : new String[] {blade.reqskill1, blade.reqskill2, blade.reqskill3}) {
        if (prerequisite != null && !prerequisite.isEmpty()) {
          Skills.Entry required = Riiablo.files.skills.get(prerequisite);
          if (required != null) data.setSkillLevel(required.Id, 1);
        }
      }
      world.getMapper(com.riiablo.engine.server.component.Player.class)
          .create(assassin).data = data;
      Attributes attrs = attributes(30);
      attrs.base().put(Stat.level, 30);
      attrs.base().put(Stat.mana, 5);
      attrs.base().put(Stat.maxmana, 5);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(assassin).attrs = attrs;

      SkillCastEvent rejected = SkillCastEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2());
      world.getSystem(EventSystem.class).dispatch(rejected);
      assertFalse(rejected.accepted, "startmana rejects an underfunded initial cast");
      assertEquals(5f, attrs.get(Stat.mana).asFixed());

      attrs.base().put(Stat.mana, blade.startmana);
      attrs.base().put(Stat.maxmana, blade.startmana);
      attrs.reset();
      SkillCastEvent accepted = SkillCastEvent.obtain(
          assassin, blade.Id, Engine.INVALID_ENTITY, new Vector2());
      world.getSystem(EventSystem.class).dispatch(accepted);
      assertTrue(accepted.accepted, "resultCode=" + accepted.resultCode
          + " mana=" + attrs.get(Stat.mana).asFixed()
          + " start=" + blade.startmana);
      assertEquals(0f, accepted.manaCost,
          "usemanaondo defers the ordinary 8-mana cost until a blade is accepted");
      assertEquals(blade.startmana, attrs.get(Stat.mana).asFixed());
    } finally {
      world.dispose();
    }
  }

  private static World bladeShieldWorld(RecordingFactory factory, com.riiablo.map.Map map) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", map));
  }

  private static int createBladeShieldPlayer(World world, Skills.Entry blade) {
    int id = world.create();
    CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
    data.setSkillLevel(blade.Id, 1);
    world.getMapper(com.riiablo.engine.server.component.Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    Attributes attrs = attributes(1000);
    attrs.base().put(Stat.mindamage, 40);
    attrs.base().put(Stat.maxdamage, 40);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int createBladeShieldMonster(World world, float x, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static com.riiablo.map.Map.Zone nativeThreeRoomZoneForTrap() {
    com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone();
    com.riiablo.map.Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
    com.riiablo.map.Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
    com.riiablo.map.Map.RoomEx third = zone.addRoomEx(80, 0, 40, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});
    return zone;
  }

  private static com.riiablo.map.Map.Zone bladeShieldThreeRoomZone(int roomWidth) {
    com.riiablo.map.Map.Zone zone = new com.riiablo.map.Map.Zone();
    com.riiablo.map.Map.RoomEx first = zone.addRoomEx(0, 0, roomWidth, roomWidth);
    com.riiablo.map.Map.RoomEx second = zone.addRoomEx(roomWidth, 0, roomWidth, roomWidth);
    com.riiablo.map.Map.RoomEx third = zone.addRoomEx(roomWidth * 2, 0, roomWidth, roomWidth);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});
    return zone;
  }

  private static final class TownMap extends com.riiablo.map.Map {
    private final Zone town = new Zone() {
      @Override public boolean isTown() { return true; }
    };

    TownMap() { super(0, 0); }

    @Override public Zone getZone(Vector2 point) { return town; }
  }

  private static final class BarrierMap extends com.riiablo.map.Map {
    final Zone zone = new Zone();

    BarrierMap() { super(0, 0); }

    @Override public Zone getZone(Vector2 point) { return zone; }

    @Override public boolean castRay(com.badlogic.gdx.ai.utils.Ray<Vector2> ray,
        int flags, int size, com.badlogic.gdx.ai.utils.Collision<Vector2> dst) {
      return true;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int entityId = Engine.INVALID_ENTITY;
    int skillLevel;
    String petType;
    int petMaximum;
    int missiles;
    int missileEntityId = Engine.INVALID_ENTITY;
    String missileName;
    String attackSkill;
    final java.util.ArrayList<String> missileNames = new java.util.ArrayList<>();
    final java.util.ArrayList<Vector2> missileDirections = new java.util.ArrayList<>();
    final java.util.ArrayList<Integer> missileEntityIds = new java.util.ArrayList<>();

    @Override
    public int createSummonedPet(int ownerId, com.riiablo.codec.excel.MonStats.Entry summon,
        String petType, int skillId, int skillLevel, int petMax, boolean passive,
        int durationFrames, float x, float y) {
      created++;
      this.petType = petType;
      this.petMaximum = Math.max(1, petMax);
      this.skillLevel = skillLevel;
      entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = summon;
      world.getMapper(Position.class).create(entityId).position.set(x, y);
      world.getMapper(UnitStates.class).create(entityId).init(entityId);
      world.getMapper(SummonedPet.class).create(entityId)
          .set(ownerId, petType, skillId, skillLevel, passive, durationFrames);
      return entityId;
    }

    @Override
    public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int missileId = world.create();
      missileEntityId = missileId;
      world.getMapper(Missile.class).create(missileId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(missileId).position.set(position);
      world.getMapper(Velocity.class).create(missileId).velocity.set(angle).setLength(row.Vel);
      missiles++;
      missileName = row.Missile;
      missileNames.add(row.Missile);
      missileDirections.add(new Vector2(angle));
      missileEntityIds.add(missileId);
      Monster trapMonster = world.getMapper(Monster.class).get(ownerId);
      attackSkill = trapMonster != null && trapMonster.monstats != null
          ? trapMonster.monstats.Skill1 : null;
      return missileId;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.tohit, 1000);
    attrs.reset();
    return attrs;
  }

  private static int createSelectableFallenCorpse(World world,
      com.riiablo.codec.excel.MonStats.Entry fallen, float x, float y, float maxHp) {
    int corpseId = world.create();
    Monster corpseMonster = world.getMapper(Monster.class).create(corpseId);
    corpseMonster.monstats = fallen;
    corpseMonster.monstats2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
    assertNotNull(corpseMonster.monstats2);
    assertTrue(corpseMonster.monstats2.corpseSel);
    world.getMapper(Position.class).create(corpseId).position.set(x, y);
    Attributes corpseAttrs = attributes(maxHp);
    corpseAttrs.get(Stat.hitpoints).set(0);
    world.getMapper(AttributesWrapper.class).create(corpseId).attrs = corpseAttrs;
    world.getMapper(Corpse.class).create(corpseId).reset(Corpse.DEFAULT_DURATION, true);
    world.getMapper(UnitStates.class).create(corpseId).init(corpseId);
    return corpseId;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
