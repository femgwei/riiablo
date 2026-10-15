package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.skill.SkillCodes;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillRatMissileTest extends RiiabloTest {
  @Test void runtimeExtraCountsMatchOriginalMonstatsTxtForEveryQuillRatVariant() {
    String[] lines = Riiablo.mpqs.resolve("data\\global\\excel\\monstats.txt")
        .readString("windows-1252").split("\\r?\\n");
    String[] header = lines[0].split("\\t", -1);
    String[] names = {"aip3", "aip3(N)", "aip3(H)"};
    int[] columns = {-1, -1, -1};
    for (int i = 0; i < header.length; i++) for (int d = 0; d < 3; d++)
      if (names[d].equalsIgnoreCase(header[i])) columns[d] = i;
    for (int column : columns) assertTrue(column >= 0);
    int variants = 0;
    for (String line : lines) {
      String[] cells = line.split("\\t", -1);
      MonStats.Entry row = Riiablo.files.monstats.get(cells[0]);
      if (row == null || !"quillrat1".equals(row.BaseId)) continue;
      variants++;
      for (int d = 0; d < 3; d++) {
        int count = cells[columns[d]].isEmpty() ? 0 : Integer.parseInt(cells[columns[d]]);
        assertEquals(count, row.aip3[d], row.Id + " difficulty=" + d);
        System.out.println("[QUILL_RAW] " + row.Id + " difficulty=" + d + " count=" + count);
      }
    }
    assertTrue(variants >= 5);
  }

  @Test void realRowsUseDifficultySpecificExtraCountsAndPrimaryLevel() {
    for (String name : new String[] {"quillrat1", "quillrat2", "quillrat5"}) {
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      assertEquals("quillrat1", row.BaseId);
      System.out.println("[QUILL_DATA] " + name + " aip3=" + java.util.Arrays.toString(row.aip3));
      for (int diff = 0; diff < 3; diff++) try (Fixture f = new Fixture(row, diff, 0)) {
        f.fire();
        assertEquals(1 + row.aip3[diff], f.factory.directions.size());
        assertEquals(1 + Riiablo.files.DifficultyLevels.get(diff).MonsterSkillBonus,
            f.factory.levels.get(0).intValue());
        for (int i = 1; i < f.factory.levels.size(); i++) assertEquals(1, f.factory.levels.get(i).intValue());
      }
    }
  }

  @Test void seedUsesUnitIdAndCumulativeSignsAndRestartsEachAttack() {
    int[][][] signs = {
        {{-5,-5},{-5,-5},{-5,-5},{5,-5},{-5,5},{-5,5}},
        {{5,5},{5,5},{-5,-5},{5,-5},{5,-5},{-5,5}}
    };
    for (int id = 0; id < 2; id++) try (Fixture f = new Fixture(custom("quillrat1", 6), 0, id)) {
      assertEquals(id, f.caster);
      f.world.getMapper(Monster.class).get(f.caster).rngState = 123456;
      f.fire();
      assertEquals(7, f.factory.directions.size());
      for (int i = 0; i < 6; i++) {
        Vector2 expected = new Vector2(30 + signs[id][i][0], 40 + signs[id][i][1]).sub(10, 10).nor();
        assertTrue(expected.epsilonEquals(f.factory.directions.get(i + 1), 0.000001f), "shot=" + i);
      }
      List<Vector2> first = new ArrayList<>(f.factory.directions);
      f.fire(); // Duplicate keyframe in same cast.
      assertEquals(7, f.factory.directions.size());
      f.world.getMapper(Casting.class).get(f.caster).monsterMissileCreated = false;
      f.fire();
      assertEquals(first, f.factory.directions.subList(7, 14));
      assertEquals(123456, f.world.getMapper(Monster.class).get(f.caster).rngState);
      assertEquals(f.target, f.world.getMapper(Casting.class).get(f.caster).targetId);
    }
  }

  @Test void nonzeroParametersSelectCurrentDifficultyAndZeroTargetCoordinateSkipsOnlyThatShot() {
    MonStats.Entry row = custom("quillrat1", 0);
    row.aip3 = new int[] {1, 2, 3};
    for (int diff = 0; diff < 3; diff++) try (Fixture f = new Fixture(row, diff, 0)) {
      f.fire();
      assertEquals(diff + 2, f.factory.directions.size());
    }
    try (Fixture f = new Fixture(custom("quillrat1", 6), 0, 0)) {
      f.world.getMapper(Position.class).get(f.target).position.set(5, 40);
      f.fire();
      // ID 0's first six cumulative X offsets: -5,-5,-5,+5,-5,-5.
      assertEquals(2, f.factory.directions.size());
      assertTrue(new Vector2(0, 1).epsilonEquals(f.factory.directions.get(1), 0.000001f));
    }
  }

  @Test void failedPrimaryHasNoExtrasAndFailedSecondaryDoesNotStopVolley() {
    try (Fixture f = new Fixture(custom("quillrat1", 6), 0, 0)) {
      f.factory.failAttempt = 0;
      f.fire();
      assertEquals(1, f.factory.directions.size());
      assertFalse(f.world.getMapper(Casting.class).get(f.caster).monsterMissileCreated);
    }
    try (Fixture f = new Fixture(custom("quillrat1", 6), 0, 0)) {
      f.factory.failAttempt = 2;
      f.fire();
      assertEquals(7, f.factory.directions.size());
      assertEquals(6, f.factory.created.size());
      assertTrue(new Vector2(35, 35).sub(10, 10).nor().epsilonEquals(f.factory.directions.get(4), 0.000001f));
    }
  }

  @Test void sameAiButDifferentBaseDoesNotEmitExtrasAndSkillCastDoesNotUseBasicMissile() throws Exception {
    try (Fixture f = new Fixture(custom("other", 6), 0, 0)) {
      f.fire();
      assertEquals(1, f.factory.directions.size());
      f.world.getMapper(Casting.class).get(f.caster).skillId = 47;
      java.lang.reflect.Method spawn = Actioneer.class.getDeclaredMethod("spawnMonsterAttackMissile", int.class, int.class);
      spawn.setAccessible(true);
      assertEquals(false, spawn.invoke(f.actioneer, f.caster, f.target));
      assertEquals(1, f.factory.directions.size());
    }
  }

  @Test void everyA2ProjectileHasOwnDamageSnapshotAndMercenaryPrimaryUsesOwnerLevel() {
    try (Fixture f = new Fixture(custom("quillrat1", 3), 2, 0)) {
      f.world.getMapper(CofReference.class).create(f.caster).mode = Engine.Monster.MODE_A2;
      Monster monster = f.world.getMapper(Monster.class).get(f.caster);
      monster.attack2MinDamage = 41; monster.attack2MaxDamage = 49; monster.attack2ToHit = 321;
      f.world.getMapper(Mercenary.class).create(f.caster).ownerId = f.caster;
      f.world.getMapper(Player.class).remove(f.target);
      f.fire();
      assertEquals(17, f.factory.levels.get(0).intValue());
      assertEquals(4, f.factory.created.size());
      for (int id : f.factory.created) {
        Missile missile = f.world.getMapper(Missile.class).get(id);
        assertTrue(missile.damageSnapshot);
        assertEquals(321, missile.attackRating);
        assertTrue(missile.attackMinDamage >= 41);
        assertTrue(missile.attackMaxDamage >= 49);
      }
      assertEquals(7, f.world.getMapper(AttributesWrapper.class).get(f.caster).attrs.get(Stat.mindamage).asInt());
    }
  }

  private MonStats.Entry custom(String base, int count) {
    MonStats.Entry template = Riiablo.files.monstats.get("quillrat1");
    MonStats.Entry row = new MonStats.Entry();
    row.Id = template.Id; row.MonStatsEx = template.MonStatsEx; row.hcIdx = template.hcIdx;
    row.BaseId = base; row.AI = "QuillRat";
    row.MissA1 = row.MissA2 = template.MissA2;
    row.aip3 = new int[] {count, count, count};
    return row;
  }

  private static Attributes attrs() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, 100f); attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.level, 17); attrs.base().put(Stat.mindamage, 7);
    attrs.base().put(Stat.maxdamage, 9); attrs.base().put(Stat.tohit, 100);
    attrs.reset();
    return attrs;
  }

  private static final class Fixture implements AutoCloseable {
    final RecordingFactory factory = new RecordingFactory();
    final World world;
    final Actioneer actioneer;
    final int caster, target;
    Fixture(MonStats.Entry row, int difficulty, int padding) {
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new CofManager(), new Actioneer(), new Pathfinder(), factory).build()
          .register("factory", factory).register("map", new Map(0, difficulty)));
      for (int i = 0; i < padding; i++) world.create();
      caster = world.create(); target = world.create();
      world.getMapper(Monster.class).create(caster).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Class.class).create(caster).type = Class.Type.MON;
      world.getMapper(Position.class).create(caster).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(caster).attrs = attrs();
      world.getMapper(Position.class).create(target).position.set(30, 40);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attrs();
      world.getMapper(Player.class).create(target);
      Casting casting = world.getMapper(Casting.class).create(caster);
      casting.skillId = SkillCodes.attack; casting.targetId = target; casting.targetVec.set(30, 40);
      actioneer = world.getSystem(Actioneer.class);
    }
    void fire() { actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(caster, Engine.KEYFRAME_ATK)); }
    @Override public void close() { world.dispose(); }
  }

  private static final class RecordingFactory extends EntityFactory {
    protected ComponentMapper<Missile> mMissile;
    protected ComponentMapper<Monster> mMonster;
    protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
    protected ComponentMapper<CofReference> mCofReference;
    final List<Vector2> directions = new ArrayList<>();
    final List<Integer> levels = new ArrayList<>(), created = new ArrayList<>();
    int failAttempt = -1;
    @Override public int createMissile(Missiles.Entry row, Vector2 direction, Vector2 pos, int owner, int level) {
      int attempt = directions.size();
      directions.add(new Vector2(direction)); levels.add(level);
      if (attempt == failAttempt) return -1;
      int id = world.create();
      created.add(id);
      Missile missile = mMissile.create(id).set(row, pos, row.Range).setOwner(owner);
      CofReference cof = mCofReference.get(owner);
      MissileDamageResolver.initialize(missile, mAttributesWrapper.get(owner).attrs,
          mMonster.get(owner), cof == null ? Engine.Monster.MODE_A1 : cof.mode, level, 0);
      return id;
    }
    @Override public int createMissile(int id, Vector2 a, Vector2 p) { return -1; }
    @Override public int createPlayer(CharData data, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int id, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
