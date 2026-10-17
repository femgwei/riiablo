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
