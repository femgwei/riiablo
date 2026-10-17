package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Exercises production systems with two simultaneous Worlds and no global game host. */
class ServerSystemIsolationTest {
  @Test void actFiveMessageValidationUsesExplicitMonsterRows() throws Exception {
    Files previous = Riiablo.files;
    Files files = mock(Files.class);
    com.riiablo.codec.excel.MonStats monsters = mock(com.riiablo.codec.excel.MonStats.class);
    java.lang.reflect.Field field = Files.class.getField("monstats");
    field.setAccessible(true);
    field.set(files, monsters);
    com.riiablo.codec.excel.MonStats.Entry drehya = new com.riiablo.codec.excel.MonStats.Entry();
    drehya.Id = "Drehya";
    when(monsters.get(999)).thenReturn(drehya);
    CharData character = mock(CharData.class);
    when(character.isExpansion()).thenReturn(true);
    short[] records = new short[8];
    records[com.riiablo.engine.server.quest.Act5PrisonQuest.RECORD] =
        com.riiablo.engine.server.quest.NativeQuestRecord.set((short) 0,
            com.riiablo.engine.server.quest.NativeQuestRecord.REWARD_PENDING);
    records[com.riiablo.engine.server.quest.Act5NihlathakQuest.RECORD] =
        com.riiablo.engine.server.quest.NativeQuestRecord.set((short) 0,
            com.riiablo.engine.server.quest.NativeQuestRecord.STARTED);
    when(character.getQuests(Riiablo.ACT5)).thenReturn(records);
    try {
      Riiablo.files = null;
      assertTrue(com.riiablo.engine.server.quest.Act5QuestMessageValidator.isAllowed(files, 999, character,
          com.d2moo.common.drlg.D2LevelIds.LEVEL_HARROGATH,
          com.riiablo.engine.server.quest.Act5NihlathakQuest.MESSAGE_DREHYA_START));
      assertFalse(com.riiablo.engine.server.quest.Act5QuestMessageValidator.isAllowed(files, 998, character,
          com.d2moo.common.drlg.D2LevelIds.LEVEL_HARROGATH,
          com.riiablo.engine.server.quest.Act5NihlathakQuest.MESSAGE_DREHYA_START));
    } finally { Riiablo.files = previous; }
  }

  @Test void trapSkillAndMissileImpactRowsStayBoundWhenGlobalsAreUnavailable() throws Exception {
    Files previous = Riiablo.files;
    Files first = files("first-impact", 11);
    Files second = files("second-impact", 29);
    Skills.Entry firstSkill = new Skills.Entry();
    Skills.Entry secondSkill = new Skills.Entry();
    when(first.skills.get("trap-attack")).thenReturn(firstSkill);
    when(second.skills.get("trap-attack")).thenReturn(secondSkill);
    Monster monster = new Monster();
    monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
    monster.monstats.Skill1 = "trap-attack";
    Missiles.Entry row = new Missiles.Entry();
    row.CltHitSubMissile = new String[] {"impact"};
    Missiles.Entry firstImpact = first.Missiles.get(7);
    Missiles.Entry secondImpact = second.Missiles.get(7);
    when(first.Missiles.get("impact")).thenReturn(firstImpact);
    when(second.Missiles.get("impact")).thenReturn(secondImpact);
    Missile a = new Missile();
    Missile b = new Missile();
    a.missile = b.missile = row;
    try {
      Riiablo.files = null;
      assertSame(firstSkill, AssassinTrapSystem.resolveAttackSkill(first, monster, null));
      assertSame(secondSkill, AssassinTrapSystem.resolveAttackSkill(second, monster, null));
      com.riiablo.engine.server.missile.MissileDamageResolver.configureMetadata(first, a, null, 0, 0);
      com.riiablo.engine.server.missile.MissileDamageResolver.configureMetadata(second, b, null, 0, 0);
      assertEquals("first-impact", a.impactDcc);
      assertEquals("second-impact", b.impactDcc);
      com.riiablo.save.CharData character = mock(com.riiablo.save.CharData.class);
      firstSkill.Id = 10;
      secondSkill.Id = 11;
      when(character.getSkill(10)).thenReturn(1);
      Skills.Entry dependent = new Skills.Entry();
      dependent.reqskill1 = "trap-attack";
      assertTrue(com.riiablo.engine.server.skill.NativeSkillResolver.prerequisitesMet(first, character, dependent));
      assertFalse(com.riiablo.engine.server.skill.NativeSkillResolver.prerequisitesMet(second, character, dependent));
    } finally { Riiablo.files = previous; }
  }

  @Test void skillDamageAndCombatRollsDoNotConsumeTheClientRandomSource() {
    java.util.Random original = com.badlogic.gdx.math.MathUtils.random;
    try {
      com.badlogic.gdx.math.RandomXS128 forbidden = mock(com.badlogic.gdx.math.RandomXS128.class);
      com.badlogic.gdx.math.MathUtils.random = forbidden;
      NativeRng expected = new NativeRng(123);
      NativeRng actual = new NativeRng(123);
      for (int i = 0; i < 8; i++) {
        assertEquals(10 + expected.nextInt(90), ServerSkillSystem.rollNativeDamage(actual, 10, 100));
      }
      com.riiablo.engine.server.combat.CombatSystem combat =
          new com.riiablo.engine.server.combat.CombatSystem(new NativeRng(123));
      com.riiablo.engine.server.combat.CombatSystem.AttackerData attacker =
          new com.riiablo.engine.server.combat.CombatSystem.AttackerData();
      com.riiablo.engine.server.combat.CombatSystem.DefenderData defender =
          new com.riiablo.engine.server.combat.CombatSystem.DefenderData();
      attacker.minDamage = 10;
      attacker.maxDamage = 20;
      attacker.alwaysHit = true;
      defender.passiveDodge = 50;
      combat.calculateAttack(attacker, defender);
      verifyNoInteractions(forbidden);
    } finally { com.badlogic.gdx.math.MathUtils.random = original; }
  }

  @Test void trapRollsKeepUnitStateAndUseTheWorldSeed() {
    AssassinTrapSystem first = new AssassinTrapSystem();
    AssassinTrapSystem second = new AssassinTrapSystem();
    first.serverContext = new ServerGameplayContext(mock(Files.class), 123);
    second.serverContext = new ServerGameplayContext(mock(Files.class), 456);
    Monster a = new Monster();
    Monster b = new Monster();
    NativeRng expectedA = NativeRng.forUnit(123, 7);
    NativeRng expectedB = NativeRng.forUnit(456, 7);
    int previousSeed = Riiablo.gameSeed;
    try {
      for (int i = 0; i < 8; i++) {
        Riiablo.gameSeed = -i;
        assertEquals(expectedA.nextInt(100), first.roll(a, 7, 100));
        assertEquals(expectedB.nextInt(100), second.roll(b, 7, 100));
        assertEquals(expectedA.state(), a.rngState);
        assertEquals(expectedB.state(), b.rngState);
      }
    } finally { Riiablo.gameSeed = previousSeed; }
  }

  @Test void factoriesAndSkillsUseTheirOwnRowsAndSeedWithIdenticalEntityIds() throws Exception {
    Files previous = Riiablo.files;
    int previousSeed = Riiablo.gameSeed;
    Files firstFiles = files("first-controller", 11);
    Files secondFiles = files("second-controller", 29);
    ServerEntityFactory firstFactory = new ServerEntityFactory();
    ServerEntityFactory secondFactory = new ServerEntityFactory();
    ServerSkillSystem firstSkills = new ServerSkillSystem();
    ServerSkillSystem secondSkills = new ServerSkillSystem();
    try (ServerSystemHarness first = new ServerSystemHarness(
            new ServerGameplayContext(firstFiles, 123), mock(Map.class),
            config -> config.register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()),
            firstFactory, firstSkills,
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), new ItemManager());
         ServerSystemHarness second = new ServerSystemHarness(
            new ServerGameplayContext(secondFiles, 456), mock(Map.class),
            config -> config.register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()),
            secondFactory, secondSkills,
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), new ItemManager())) {
      Riiablo.files = null;
      Riiablo.gameSeed = -999;
      int a = caster(first.world());
      int b = caster(second.world());
      assertEquals(a, b);
      firstSkills.onSkillDo(SkillDoEvent.obtain(a, 286, -1, new Vector2(10, 10), 0, 0));
      secondSkills.onSkillDo(SkillDoEvent.obtain(b, 286, -1, new Vector2(20, 20), 0, 0));
      Missile firstMissile = first.world().getMapper(Missile.class).get(a + 1);
      Missile secondMissile = second.world().getMapper(Missile.class).get(b + 1);
      assertNotNull(firstMissile);
      assertNotNull(secondMissile);
      assertSame(firstFiles.Missiles.get(7), firstMissile.missile);
      assertSame(secondFiles.Missiles.get(7), secondMissile.missile);
      assertEquals(11, firstMissile.nativeLifetimeFrames);
      assertEquals(29, secondMissile.nativeLifetimeFrames);
      assertEquals(NativeRng.forUnit(123, a + 1).state(), firstMissile.rngState);
      assertEquals(NativeRng.forUnit(456, b + 1).state(), secondMissile.rngState);
      first.step();
      second.step();
      assertEquals(1, first.simulation().tickNumber());
      assertEquals(1f / 25f, second.world().getDelta());
    } finally { Riiablo.files = previous; Riiablo.gameSeed = previousSeed; }
  }

  @Test void waypointInitializationDoesNotBorrowAnotherCharacterOrTheClientCharacter() {
    CharData previous = Riiablo.charData;
    CharData active = mock(CharData.class);
    CharData inactive = mock(CharData.class);
    when(active.isWaypointActivated(0, 1)).thenReturn(true);
    ObjectInitializer firstObjects = new ObjectInitializer();
    ObjectInitializer secondObjects = new ObjectInitializer();
    ObjectInitializer dedicatedObjects = new ObjectInitializer();
    Files files = mock(Files.class);
    try (ServerSystemHarness first = new ServerSystemHarness(new ServerGameplayContext(files, 1, active),
            mock(Map.class), new CofManager(), firstObjects);
         ServerSystemHarness second = new ServerSystemHarness(new ServerGameplayContext(files, 2, inactive),
            mock(Map.class), new CofManager(), secondObjects);
         ServerSystemHarness dedicated = new ServerSystemHarness(new ServerGameplayContext(files, 3),
            mock(Map.class), new CofManager(), dedicatedObjects)) {
      Riiablo.charData = active;
      int a = waypoint(first.world());
      int b = waypoint(second.world());
      int c = waypoint(dedicated.world());
      firstObjects.initialize(a);
      secondObjects.initialize(b);
      dedicatedObjects.initialize(c);
      assertEquals(Engine.Object.MODE_ON, first.world().getMapper(CofReference.class).get(a).mode);
      assertEquals(Engine.Object.MODE_NU, second.world().getMapper(CofReference.class).get(b).mode);
      assertEquals(Engine.Object.MODE_NU, dedicated.world().getMapper(CofReference.class).get(c).mode);
      verify(active).isWaypointActivated(0, 1);
      verify(inactive).isWaypointActivated(0, 1);
    } finally { Riiablo.charData = previous; }
  }

  private static int caster(World world) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(1, 1);
    return id;
  }

  private static int waypoint(World world) {
    int id = world.create();
    Objects.Entry row = new Objects.Entry();
    row.InitFn = 17;
    world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = row;
    world.getMapper(CofReference.class).create(id);
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.level.Waypoint = 1;
    world.getMapper(MapWrapper.class).create(id).zone = zone;
    return id;
  }

  private static Files files(String name, int range) throws Exception {
    Files files = mock(Files.class);
    Skills skills = mock(Skills.class);
    Missiles missiles = mock(Missiles.class);
    java.lang.reflect.Field skillsField = Files.class.getField("skills");
    skillsField.setAccessible(true);
    skillsField.set(files, skills);
    java.lang.reflect.Field missilesField = Files.class.getField("Missiles");
    missilesField.setAccessible(true);
    missilesField.set(files, missiles);
    Skills.Entry skill = new Skills.Entry();
    skill.Id = 286;
    skill.srvmissile = name;
    when(skills.get(286)).thenReturn(skill);
    Missiles.Entry missile = new Missiles.Entry();
    missile.Id = 7;
    missile.Missile = name;
    missile.CelFile = name;
    missile.Range = range;
    missile.pSrvHitFunc = 54;
    when(missiles.get(name)).thenReturn(missile);
    when(missiles.get(7)).thenReturn(missile);
    return files;
  }
}
