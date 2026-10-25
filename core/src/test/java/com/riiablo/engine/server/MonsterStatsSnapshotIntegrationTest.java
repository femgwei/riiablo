package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.combat.MonsterStatsSnapshot;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.monster.MonsterAffix;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Actual table lookup -> spawn snapshot -> factory ECS attributes; not a manual stat write. */
class MonsterStatsSnapshotIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"0,1,0", "0,8,2", "1,2,0", "1,8,1", "2,8,2", "2,10,5"})
  void factoryKeepsFrozenSpawnValuesAndLaterModeRefreshUsesCurrentLevel(int difficulty, int players, int rank) {
    MonStats.Entry row = Riiablo.files.monstats.get("skeleton1");
    Map map = mock(Map.class); Map.Zone zone = new Map.Zone();
    zone.level = Riiablo.files.Levels.get(2);
    when(map.getDifficulty()).thenReturn(difficulty);
    when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
    ServerEntityFactory factory = new ServerEntityFactory();
    World previousWorld = Riiablo.engine;
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new ItemManager(),
        new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), new CofManager(),
        new Pathfinder(), factory)
        .build().register("map", map).register("factory", factory)
        .register("serverContext", new ServerGameplayContext(Riiablo.files, 1234))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      Riiablo.engine = world;
      for (int i = 0; i < players; i++) world.getMapper(Player.class).create(world.create());
      world.process();
      int baseLevel = PreviousMonsterStatsCalculator.resolveMonsterLevel(row, zone.level, difficulty, true);
      PreviousMonsterStatsCalculator.MonsterStatsInit old = new PreviousMonsterStatsCalculator.MonsterStatsInit();
      assertTrue(PreviousMonsterStatsCalculator.calculateMonsterStatsByLevel(
          Riiablo.files, row.hcIdx, 1, difficulty, baseLevel, (short) 7, old));
      // Monster.cpp consumes direct component variants before the inclusive HP roll.
      // Keep the legacy stat-formula oracle; only replace its former global RNG input.
      D2Seed expectedSeed = new D2Seed(NativeRng.forUnit(1234, players).state(), 666);
      for (String value : Riiablo.files.monstats2.get(row.MonStatsEx).ComponentV) {
        if (!value.isEmpty()) Seed.rollLimitedRandomNumber(expectedSeed, value.replace("\"", "").split(",").length);
      }
      int min = Math.min(old.minHP, old.maxHP), max = Math.max(old.minHP, old.maxHP);
      int baseHp = min + Seed.rollLimitedRandomNumber(expectedSeed, max - min + 1);
      int hp = Math.min(baseHp + (int) ((long) baseHp
          * PreviousMonsterStatsCalculator.nativeHpBonus(players) / 100), (1 << 23) - 1);
      hp = PreviousMonsterStatsCalculator.nativeRankHitpoints(hp, rank, difficulty);
      MonsterStatsSnapshot snapshot = MonsterStatsCalculator.spawnSnapshot(
          Riiablo.files, row, 1, difficulty, baseLevel, rank, players);
      int id = factory.createMonster(row.hcIdx, 10, 10, rank, MonsterAffix.STONE_SKIN, -1, -1);
      assertEquals(players, id);
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(id).attrs;
      Monster monster = world.getMapper(Monster.class).get(id);
      assertEquals(expectedSeed.getNLowSeed(), monster.nativeSeed.getNLowSeed());
      assertEquals(expectedSeed.getNHighSeed(), monster.nativeSeed.getNHighSeed());
      assertEquals(hp * 256, attrs.get(Stat.hitpoints).encodedValues());
      assertEquals(hp * 256, attrs.get(Stat.maxhp).encodedValues());
      assertEquals(snapshot.level, attrs.getValue(Stat.level, 0));
      assertEquals(players, attrs.getValue(Stat.monster_playercount, 0));
      assertEquals(old.AC * 2, attrs.getValue(Stat.armorclass, 0), "affix stays after base snapshot");
      assertEquals(snapshot.experience, attrs.getValue(Stat.experience, 0));
      assertEquals(snapshot.attack1.attackRating, attrs.getValue(Stat.tohit, 0));
      assertEquals(snapshot.attack1.minDamage, attrs.getValue(Stat.mindamage, 0));
      assertEquals(snapshot.attack2.attackRating, monster.attack2ToHit);
      assertEquals(snapshot.attack2.minDamage, monster.attack2MinDamage);
      assertEquals(snapshot.attack2.maxDamage, monster.attack2MaxDamage);

      // Spawn HP/AC use base level; a later action reads the rank-adjusted current level.
      for (int flag : new int[] {8, 16, 32}) {
        PreviousMonsterStatsCalculator.MonsterStatsInit mode = new PreviousMonsterStatsCalculator.MonsterStatsInit();
        PreviousMonsterStatsCalculator.calculateMonsterStatsByLevel(Riiablo.files, row.hcIdx, 1,
            difficulty, snapshot.level, (short) flag, mode);
        PreviousMonsterStatsCalculator.applyNativeCombatMultiplier(mode,
            PreviousMonsterStatsCalculator.nativeCombatMultiplier(row, difficulty, players, true));
        int animation = flag == 8 ? Engine.Monster.MODE_A1 : flag == 16 ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_S1;
        MonsterModeDamageResolver.Profile actual = MonsterModeDamageResolver.resolveWithElements(
            Riiablo.files, row, snapshot.level, difficulty, animation, players, true, null);
        assertEquals(mode.TH, actual.attackRating);
        assertEquals(flag == 8 ? mode.A1MinD : flag == 16 ? mode.A2MinD : mode.S1MinD, actual.minDamage);
      }
    } finally {
      world.dispose(); Riiablo.engine = previousWorld;
    }
  }
}
