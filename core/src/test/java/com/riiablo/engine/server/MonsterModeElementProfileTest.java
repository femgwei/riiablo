package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.combat.NativeMonsterModeElements;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Table adapter + legacy creation wiring, NOT action-lifetime or original-process captures. */
class MonsterModeElementProfileTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void allThreeModeRowsUseTheOriginalAdditiveFlagQuirk(int slot) {
    MonStats.Entry row = row();
    String[] modes = {"", "", ""}; modes[slot] = "A1";
    row.El1Mode = modes[0]; row.El2Mode = modes[1]; row.El3Mode = modes[2];
    row.El1Type = row.El2Type = row.El3Type = "fire";
    NativeMonsterModeElements p = elements(files(row), row, n -> fail("guaranteed row"), 1);
    // 0x40/0x41/0x42 all select El1 in CalculateMonsterStatsByLevel.
    int dm = Riiablo.files.MonLvl.get(40).LDM[1];
    assertEquals(dm, p.minimum[1]); assertEquals(dm * 2, p.maximum[1]);
    assertEquals(1, p.matchedRows);
  }

  @Test void duplicateRowsOverwriteAndDistinctChannelsRemain() {
    MonStats.Entry row = row(); row.El1Mode = row.El2Mode = row.El3Mode = "A1";
    row.El1Type = row.El2Type = "cold"; row.El3Type = "mag";
    NativeMonsterModeElements p = elements(files(row), row, n -> 0, 1);
    int dm = Riiablo.files.MonLvl.get(40).LDM[1];
    assertEquals(dm, p.minimum[3]); assertEquals(dm, p.minimum[5]);
    assertEquals(17, p.coldLength); assertEquals(3, p.matchedRows);
  }

  @ParameterizedTest
  @CsvSource({"2,5,0,0", "3,3,25,0", "4,4,0,50"})
  void randomMappingAndFallbackUseOriginalTypeNotHostEnum(int roll, int channel,
      int cold, int poison) {
    MonStats.Entry row = row(); row.El1Type = "rand"; row.El1Dur = new int[] {0,0,0};
    NativeMonsterModeElements p = elements(files(row), row, n -> {
      assertEquals(5, n); return roll;
    }, 1);
    int dm = Riiablo.files.MonLvl.get(40).LDM[1];
    assertEquals(channel == 4 ? dm * 10 : dm, p.minimum[channel]);
    assertEquals(cold, p.coldLength); assertEquals(poison, p.poisonLength);
  }

  @Test void noRatioWritesTheWrongNativeOutputFieldsSoModeDamageIsZero() {
    MonStats.Entry row = row(); row.noRatio = true; row.El1Type = "pois";
    NativeMonsterModeElements p = elements(files(row), row, n -> 0, 1);
    assertEquals(0, p.maximum[4]); assertEquals(34, p.poisonLength);
  }

  @Test void chanceIsPerDifficultyAndDoesNotRollForAnotherMode() {
    MonStats.Entry row = row(); row.El1Pct = new int[] {0,50,100};
    Files files = files(row);
    assertEquals(0, elements(files, row, n -> fail("zero chance"), 0).matchedRows);
    assertEquals(0, elements(files, row, n -> 50, 1).matchedRows);
    assertEquals(1, elements(files, row, n -> 49, 1).matchedRows);
    assertEquals(1, elements(files, row, n -> fail("guaranteed"), 2).matchedRows);
    assertEquals(0, MonsterModeDamageResolver.resolveElements(files, n -> fail("wrong mode"),
        row, 40, 1, Engine.Monster.MODE_A2, 1, true).matchedRows);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void modeAdapterUsesClassicOrExpansionDamageTable(boolean expansion) {
    MonStats.Entry row = row(); row.El1Type = "pois";
    NativeMonsterModeElements p = MonsterModeDamageResolver.resolveElements(files(row), n -> 0,
        row, 40, 1, Engine.Monster.MODE_A1, 8, expansion);
    int dm = (expansion ? Riiablo.files.MonLvl.get(40).LDM : Riiablo.files.MonLvl.get(40).DM)[1];
    assertEquals((dm + (expansion ? dm * 56 / 128 : 0)) * 10, p.minimum[4]);
    assertEquals((17 + (expansion ? 17 * 56 / 128 : 0)) * 2, p.poisonLength);
  }

  @Test void completeProfileAndSourceAdapterUseTheSameElementSelection() {
    MonStats.Entry row = row(); row.El2Mode = "A1"; row.El2Type = "fire";
    Files files = files(row);
    MonsterModeDamageResolver.Profile p = MonsterModeDamageResolver.resolve(files, n -> 0,
        row, 40, 1, Engine.Monster.MODE_A1, 1, true);
    assertEquals(Riiablo.files.MonLvl.get(40).LDM[1], p.elementalMin[1]);
    assertEquals(2, p.matchedElementProfiles);
  }

  @Test void legacyMissileUsesOwnerLevelNotMissileSkillLevelAndDoesNotEnableNewPolicy() {
    MonStats.Entry row = row(); row.El1MinD = row.El1MaxD = new int[] {100,100,100};
    Files files = files(row);
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = row;
      f.source.base().put(Stat.level, 40); f.source.reset();
      Missile p = new Missile().set(missile(), new Vector2(), 100);
      assertTrue(MissileDamageResolver.initialize(files, p, f.source, f.monster,
          Engine.Monster.MODE_A1, 1, 1));
      assertNull(p.nativeElemental, "mode stat-list lifetime remains unaudited");
      assertEquals(Riiablo.files.MonLvl.get(40).LDM[1], p.damage.get(Stat.firemindam).asInt());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void realFactoryPassesMapDifficultyToModeElementCreation(int difficulty) {
    MonStats.Entry row = row(); row.El1Pct = new int[] {0,100,100};
    Files files = files(row);
    ServerEntityFactory factory = new ServerEntityFactory();
    Map map = mock(Map.class); when(map.getDifficulty()).thenReturn(difficulty);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), factory)
        .build().register("map", map).register("factory", factory)
        .register("serverContext", new ServerGameplayContext(files, 1234))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      int owner = world.create();
      world.getMapper(Monster.class).create(owner).set(row, null);
      world.getMapper(CofReference.class).create(owner).mode = Engine.Monster.MODE_A1;
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.level, 40); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      Missiles.Entry missile = Riiablo.files.Missiles.get("arrow");
      assertTrue(missile.SrcDamage > 0);
      int id = factory.createMissile(missile, new Vector2(1,0), new Vector2(10,10), owner, 1);
      Missile p = world.getMapper(Missile.class).get(id);
      int expected = difficulty == 0 ? 0 : Riiablo.files.MonLvl.get(40).LDM[difficulty];
      assertEquals(expected * missile.SrcDamage / 128,
          p.damage.getValue(Stat.firemindam, 0), "difficulty must come from the map");
    } finally { world.dispose(); }
  }

  @Test void correctedModeElementProjectionReachesActualCollisionLifeAndEvent() {
    MonStats.Entry row = row(); row.El1MinD = row.El1MaxD = new int[] {100,100,100};
    Files files = files(row);
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = row;
      f.source.base().put(Stat.level, 40);
      f.source.base().put(Stat.mindamage, 0); f.source.base().put(Stat.maxdamage, 0); f.source.reset();
      Missiles.Entry missile = missile();
      missile.Collision = true; missile.CollideType = 3; missile.CollideKill = true;
      Missile p = f.spawn(missile); p.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(files, p, f.source, f.monster,
          Engine.Monster.MODE_A1, 1, 1));
      assertNull(p.nativeElemental);
      int expected = Riiablo.files.MonLvl.get(40).LDM[1];
      f.land();
      assertEquals(10000 * 256 - expected * 256, f.target.attrs.get(Stat.hitpoints).encodedValues());
      assertNotNull(f.probe.applied); assertEquals(expected, f.probe.applied.lifeLost);
    }
  }

  private static NativeMonsterModeElements elements(Files files, MonStats.Entry row,
      java.util.function.IntUnaryOperator random, int difficulty) {
    return MonsterModeDamageResolver.resolveElements(files, random, row, 40, difficulty,
        Engine.Monster.MODE_A1, 1, true);
  }

  private static MonStats.Entry row() {
    MonStats.Entry row = new MonStats.Entry(); row.hcIdx = 7777; row.Id = "mode-element-audit";
    row.El1Mode = "A1"; row.El1Type = "fire";
    row.El1Pct = row.El2Pct = row.El3Pct = new int[] {100,100,100};
    row.El1MinD = new int[] {100,100,100}; row.El1MaxD = new int[] {200,200,200};
    row.El1Dur = new int[] {17,17,17};
    row.El2MinD = new int[] {700,700,700}; row.El2MaxD = new int[] {800,800,800};
    row.El2Dur = new int[] {90,90,90};
    row.El3MinD = new int[] {900,900,900}; row.El3MaxD = new int[] {1000,1000,1000};
    return row;
  }

  private static Files files(MonStats.Entry row) {
    Files files = mock(Files.class);
    try {
      for (java.lang.reflect.Field field : Files.class.getFields()) {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          field.setAccessible(true); field.set(files, field.get(Riiablo.files));
        }
      }
      MonStats table = mock(MonStats.class); when(table.get(row.hcIdx)).thenReturn(row);
      java.lang.reflect.Field field = Files.class.getField("monstats");
      field.setAccessible(true); field.set(files, table);
    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    return files;
  }

  private static Missiles.Entry missile() {
    Missiles.Entry row = new Missiles.Entry(); row.Missile = "mode-element-audit";
    row.SrcDamage = 128; row.HitShift = 8; row.Range = 100;
    return row;
  }
}
