package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DiabPrisonIntegrationTest extends RiiabloTest {
  @Test void explicitMobileIntegerAllocationEstablishesRawButLegacyRebuildDropsIt() {
    try (Fixture f = new Fixture("diablo")) {
      int row = Riiablo.files.monstats.get("fallen1").hcIdx;
      int id = f.factory.createMonsterAtIntegerSelection(row, 150, 150, 0, 0L, -1, -1);
      assertTrue(id >= 0);
      Position position = f.world.getMapper(Position.class).get(id);
      assertEquals(new Vector2(150, 150), position.position);
      assertEquals((150 << 16) | 0x8000, position.nativePrecisionX());
      assertEquals((150 << 16) | 0x8000, position.nativePrecisionY());
      assertTrue(f.factory.reinitializeMonster(id, row, Engine.Monster.MODE_NU));
      assertFalse(position.hasNativePrecision());
    }
  }

  @Test void outOfRawDomainSelectionKeepsLegacyAllocationWithoutPrecision() {
    try (Fixture f = new Fixture("diablo")) {
      int row = Riiablo.files.monstats.get("fallen1").hcIdx;
      int id = f.factory.createMonsterAtIntegerSelection(row, 65536, 150, 0, 0L, -1, -1);
      assertTrue(id >= 0, "raw eligibility must not change legacy allocation success");
      Position position = f.world.getMapper(Position.class).get(id);
      assertEquals(65536f, position.position.x);
      assertFalse(position.hasNativePrecision());
    }
  }

  @Test void realS3CreatesFourNativeRowsBlocksExitAndHasNoTimedExpiry() {
    for (String boss : new String[] {"diablo", "diabloclone"}) try (Fixture f = new Fixture(boss)) {
      f.cast(); IntArray group = f.group(); assertEquals(4, group.size);
      int[][] offsets = {{1,1},{1,-1},{-1,-1},{-1,1}};
      for (int i=0;i<4;i++) {
        int id=group.get(i); Monster m=f.world.getMapper(Monster.class).get(id);
        assertEquals(340+i,m.monstats.hcIdx); assertEquals(group.first(),m.minionOwnerId);
        assertEquals(new Vector2(128+offsets[i][0],128+offsets[i][1]),f.pos(id));
        Position position = f.world.getMapper(Position.class).get(id);
        assertEquals(((128 + offsets[i][0]) << 16) | 0x8000, position.nativePrecisionX());
        assertEquals(((128 + offsets[i][1]) << 16) | 0x8000, position.nativePrecisionY());
        assertFalse(f.world.getMapper(SummonedPet.class).has(id));
        assertTrue(f.world.getMapper(NativeUnitFlags.class).get(id).has(NativeUnitFlags.NO_TREASURE_CLASS));
        assertTrue(f.hp(id)>0); assertTrue(f.collisions.hasPresence(id));
      }
      assertFalse(f.path.findPath(f.target,new Vector2(140,128),false,-1));
      for(int i=0;i<650;i++)f.tick();
      for(int i=0;i<group.size;i++)assertTrue(f.hp(group.get(i))>0);
      assertEquals(1000000f,f.hp(f.target));
    }
  }

  @Test void anySegmentDeathKillsOnlyItsOwnGroupAndReleasesCollision() {
    for(int victim=0;victim<4;victim++)try(Fixture f=new Fixture("diablo")) {
      f.cast(); IntArray group=new IntArray(f.group());
      int other=f.factory.createDiabPrisonSegment(340,150,150);f.tick();
      f.attrs(group.get(victim)).get(Stat.hitpoints).set(0f);
      f.events.dispatch(DeathEvent.obtain(f.target,group.get(victim)));f.tick();
      for(int i=0;i<group.size;i++) {
        int id=group.get(i);assertEquals(0,f.hp(id));assertFalse(f.collisions.hasPresence(id));
        assertNull(f.world.getMapper(DiabPrison.class).get(id).members);
        assertEquals(-1,f.world.getMapper(Monster.class).get(id).minionOwnerId);
      }
      assertTrue(f.hp(other)>0);assertTrue(f.hp(f.source)>0);
      assertTrue(f.path.findPath(f.target,new Vector2(140,128),false,-1));
    }
  }

  @Test void blockedRootStopsChildrenButBlockedChildDoesNotStopLaterRows() {
    try(Fixture f=new Fixture("diablo")) {
      f.zone.or(129,129,DT1.Tile.FLAG_BLOCK_WALK);f.cast();assertNull(f.group());
    }
    try(Fixture f=new Fixture("diablo")) {
      f.zone.or(129,127,DT1.Tile.FLAG_BLOCK_WALK);f.cast();
      IntArray group=f.group();assertEquals(3,group.size);
      assertEquals(342,f.world.getMapper(Monster.class).get(group.get(1)).monstats.hcIdx);
    }
  }

  @Test void removedTargetDoesNotTurnCoordinatesIntoANewPrison() {
    try(Fixture f=new Fixture("diablo")) {
      f.begin();f.world.delete(f.target);f.finish();assertNull(f.group());
    }
  }

  @Test void townTargetRejectsAndEncodedObjectTargetUsesItsLivePosition() {
    try(Fixture f=new Fixture("diablo")) {
      f.world.getMapper(MapWrapper.class).get(f.target).zone = new Map.Zone() {
        @Override public boolean isTown(){return true;}
      };
      f.cast();assertNull(f.group());
    }
    try(Fixture f=new Fixture("diablo")) {
      int object=f.world.create();
      f.world.getMapper(com.riiablo.engine.server.component.Object.class).create(object);
      f.world.getMapper(Position.class).create(object).position.set(140,140);
      f.world.getMapper(MapWrapper.class).create(object).set(f.map,f.zone);f.tick();
      f.world.getSystem(Actioneer.class).cast(f.source,199,-1,new Vector2(object,2));f.finish();
      assertEquals(4,f.group().size);assertEquals(new Vector2(141,141),f.pos(f.group().first()));
    }
  }

  @Test void retainedDeadTargetStillSuppliesTheNativePrisonCenter() {
    try(Fixture f=new Fixture("diablo")) {
      f.begin();f.attrs(f.target).get(Stat.hitpoints).set(0f);f.finish();
      assertEquals(4,f.group().size);
    }
  }

  @Test void exactSpawnChecksDynamicCenterAndReservesItBeforeNextCast() {
    try(Fixture f=new Fixture("diablo")) {
      assertEquals(-1,f.factory.createDiabPrisonSegment(340,128,128));
      int id=f.factory.createDiabPrisonSegment(340,140,140);assertTrue(id>=0);
      assertEquals(-1,f.factory.createDiabPrisonSegment(340,140,140));
      assertEquals(new Vector2(140,140),f.pos(id));
    }
  }

  @Test void recycledMemberIdCannotKillAnUnrelatedMonster() {
    try(Fixture f=new Fixture("diablo")) {
      f.cast();IntArray group=new IntArray(f.group());
      int removed=group.get(1);f.world.delete(removed);f.tick();
      int replacement=f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx,150,150);f.tick();
      assertEquals(removed,replacement,"exercise actual ECS id recycling");
      f.attrs(group.first()).get(Stat.hitpoints).set(0f);
      f.events.dispatch(DeathEvent.obtain(f.target,group.first()));f.tick();
      assertTrue(f.hp(replacement)>0);assertTrue(f.collisions.hasPresence(replacement));
    }
  }

  @Test void nativeCellInputRejectsOutOfRangeBeforeAllocating() {
    try (Fixture f = new Fixture("diablo")) {
      int count = f.world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all(Monster.class)).getEntities().size();
      assertEquals(-1, f.factory.createDiabPrisonSegment(340, -1, 140));
      assertEquals(-1, f.factory.createDiabPrisonSegment(340, 140, 65536));
      assertEquals(-1, f.factory.createDiabPrisonSegment(340, 65536, 140));
      f.tick();
      assertEquals(count, f.world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all(Monster.class)).getEntities().size());
    }
  }

  @Test void legacyMonsterRebuildAtSameProjectionDoesNotRetainNativeLowWords() {
    try (Fixture f = new Fixture("diablo")) {
      int row = Riiablo.files.monstats.get("fallen1").hcIdx;
      int id = f.factory.createMonster(row, 150, 150);
      Position position = f.world.getMapper(Position.class).get(id);
      assertFalse(position.hasNativePrecision(), "ordinary float creation is not a raw source");
      position.initializeNativeCell(150, 150);
      assertTrue(position.hasNativePrecision());
      assertTrue(f.factory.reinitializeMonster(id, row, Engine.Monster.MODE_NU));
      assertEquals(new Vector2(150, 150), position.position);
      assertFalse(position.hasNativePrecision(), "same-valued legacy rebuild must invalidate explicitly");
    }
  }

  static class Fixture implements AutoCloseable {
    final World previous=Riiablo.engine;
    final com.riiablo.codec.D2 previousAnim=Riiablo.anim;
    final Map map=new Map(1,0);final Map.Zone zone=map.createSyntheticArena(104,4,4);
    final ServerEntityFactory factory=new ServerEntityFactory();final EventSystem events=new EventSystem();
    final DynamicUnitCollisionSystem collisions=new DynamicUnitCollisionSystem();final Pathfinder path=new Pathfinder();
    final World world;final int source,target;int ticks;
    Fixture(String boss) {
      Riiablo.anim=com.riiablo.codec.D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
      world=new World(new WorldConfigurationBuilder().with(events,new ItemManager(),
          new ObjectInteractor(),new WarpInteractor(),new ItemInteractor(),new CofManager(),
          new StateUpdater(),new Actioneer(),new ServerSkillSystem(),new ServerMonsterCorpseSystem(),
          new SequenceHandler(),new AnimStepper(),new AnimDataResolver(),collisions,path,
          new VelocityAdder(),factory).build().register("map",map).register("factory",factory)
          .register("townPortalRegistry",new com.riiablo.engine.server.portal.TownPortalRegistry()));
      Riiablo.engine=world;world.setDelta(SimulationClock.STEP_SECONDS);
      target=factory.createPlayer("PrisonTarget",Riiablo.AMAZON,128,128);
      attrs(target).base().put(Stat.hitpoints,1000000);attrs(target).base().put(Stat.maxhp,1000000);attrs(target).reset();
      source=factory.createMonster(Riiablo.files.monstats.get(boss).hcIdx,146,128);tick();
    }
    void begin(){world.getSystem(Actioneer.class).cast(source,199,target,pos(target).cpy());}
    void finish(){for(int i=0;i<120;i++){tick();if(!world.getMapper(Casting.class).has(source))return;}fail("cast did not finish");}
    void cast(){begin();assertTrue(world.getMapper(Casting.class).has(source));finish();}
    IntArray group(){com.artemis.utils.IntBag ids=world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(DiabPrison.class)).getEntities();return ids.size()==0?null:world.getMapper(DiabPrison.class).get(ids.get(0)).members;}
    Vector2 pos(int id){return world.getMapper(Position.class).get(id).position;}
    Attributes attrs(int id){return world.getMapper(AttributesWrapper.class).get(id).attrs;}
    float hp(int id){return attrs(id).get(Stat.hitpoints).asFixed();}
    void tick(){assertTrue(ticks++<1500);world.process();}
    public void close(){world.dispose();map.dispose();Riiablo.engine=previous;Riiablo.anim=previousAnim;}
  }
}
