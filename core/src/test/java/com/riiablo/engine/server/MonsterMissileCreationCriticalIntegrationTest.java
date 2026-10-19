package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import org.junit.jupiter.api.Test;

class MonsterMissileCreationCriticalIntegrationTest extends RiiabloTest {
  static Missiles.Entry row() {
    Missiles.Entry r = new Missiles.Entry(); r.Missile = "creation-crit-audit";
    r.SrcDamage = 128; r.HitShift = 8; r.Range = 100;
    r.Collision = true; r.CollideType = 3; r.CollideKill = true; return r;
  }

  @Test void captureIsIdempotentFrozenAndImpactDoublesOnlyPhysicalWithoutCriticalReroll() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
      f.source.base().put(Stat.passive_critical_strike,100);
      f.source.base().put(Stat.firemindam,2); f.source.base().put(Stat.firemaxdam,2); f.source.reset();
      Missile p = f.spawn(row()); p.rngState = 135;
      int[] draws = {0};
      MissileDamageResolver.captureMonsterCreationCritical(p,f.source,null,n -> { draws[0]++; return 0; });
      assertTrue(p.nativePhysical.creationCritical); assertEquals(1,draws[0]);
      f.source.base().put(Stat.passive_critical_strike,0); f.source.reset();
      MissileDamageResolver.captureMonsterCreationCritical(p,f.source,null,n -> fail("already captured"));
      NativeSeedReference expected = new NativeSeedReference(135);
      int physical = 2 * (2560 + expected.nextInt(256));
      f.land();
      assertEquals(10000 * 256 - physical - 512, f.target.attrs.get(Stat.hitpoints).encodedValues());
      assertEquals(expected.state(),f.probe.impactSeeds.get(f.probe.request.missileId).intValue());
      assertEquals(physical / 256f,f.probe.applied.physicalDamage);
      assertEquals(2f,f.probe.request.fireDamage);
    }
  }

  @Test void pureTablePacketDoesNotDrawOrInheritOwnerCritical() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
      f.source.base().put(Stat.passive_critical_strike,100); f.source.reset();
      Missiles.Entry r = row(); r.SrcDamage = 0; r.MinDamage = r.MaxDamage = 10;
      Missile p = f.spawn(r);
      MissileDamageResolver.captureMonsterCreationCritical(p,f.source,null,n -> fail("no source"));
      assertTrue(p.nativePhysical.criticalCaptured); assertFalse(p.nativePhysical.creationCritical);
      f.land(); assertEquals(10000 * 256 - 2560,f.target.attrs.get(Stat.hitpoints).encodedValues());
    }
  }

  @Test void realFactoryCapturesOncePerProjectileFromOwnerStream() {
    ServerEntityFactory factory = new ServerEntityFactory();
    CofManager cofs = new CofManager();
    com.riiablo.map.Map map = org.mockito.Mockito.mock(com.riiablo.map.Map.class);
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), cofs, factory)
        .build().register("map",map).register("factory",factory)
        .register("serverContext",new ServerGameplayContext(com.riiablo.Riiablo.files,1234))
        .register("townPortalRegistry",new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      int owner = world.create();
      com.riiablo.engine.server.component.Monster monster = world.getMapper(
          com.riiablo.engine.server.component.Monster.class).create(owner)
          .set(new com.riiablo.codec.excel.MonStats.Entry(),null);
      world.getMapper(com.riiablo.engine.server.component.CofReference.class).create(owner).mode =
          com.riiablo.engine.Engine.Monster.MODE_NU;
      com.riiablo.attributes.Attributes attrs = com.riiablo.attributes.Attributes.obtainStandard();
      attrs.base().put(Stat.level,40); attrs.base().put(Stat.mindamage,10);
      attrs.base().put(Stat.maxdamage,11); attrs.base().put(Stat.passive_critical_strike,100); attrs.reset();
      world.getMapper(com.riiablo.engine.server.component.AttributesWrapper.class).create(owner).attrs = attrs;
      cofs.setMode(owner,com.riiablo.engine.Engine.Monster.MODE_A1,true);
      monster.rngState = 777; NativeSeedReference expected = new NativeSeedReference(777);
      for (int i = 0; i < 2; i++) {
        int id = factory.createMissile(com.riiablo.Riiablo.files.Missiles.get("arrow"),
            new com.badlogic.gdx.math.Vector2(1,0),new com.badlogic.gdx.math.Vector2(10,10),owner,1);
        Missile p = world.getMapper(Missile.class).get(id);
        assertTrue(p.nativePhysical.criticalCaptured); assertTrue(p.nativePhysical.creationCritical);
        expected.nextInt(100); assertEquals(expected.state(),monster.rngState);
        assertEquals(NativeRng.forUnit(1234,id).state(),p.rngState);
      }
    } finally { world.dispose(); }
  }

  @Test void twoOrdinaryProjectilesConsumeTheirOwnImpactStreams() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
      Missile a = f.spawn(row()), b = f.spawn(row()); a.rngState = 135; b.rngState = 246;
      MissileDamageResolver.captureMonsterCreationCritical(a,f.source,null,n -> 99);
      MissileDamageResolver.captureMonsterCreationCritical(b,f.source,null,n -> 99);
      NativeSeedReference ar = new NativeSeedReference(135), br = new NativeSeedReference(246);
      int damage = 5120 + ar.nextInt(256) + br.nextInt(256);
      f.land(); assertEquals(10000 * 256 - damage,f.target.attrs.get(Stat.hitpoints).encodedValues());
      assertEquals(2,f.probe.impactSeeds.size());
      assertTrue(f.probe.impactSeeds.containsValue(ar.state()));
      assertTrue(f.probe.impactSeeds.containsValue(br.state()));
    }
  }

  @Test void ownerMonsterCriticalStacksWithCreationCriticalButOnlyOwnerCriticalDoublesFire() {
    try (MonsterMissilePhysicalIntegrationTest.Fixture f = new MonsterMissilePhysicalIntegrationTest.Fixture()) {
      f.monster.monstats = new com.riiablo.codec.excel.MonStats.Entry();
      f.monster.monstats.Crit = 100; f.monster.rngState = 777;
      f.source.base().put(Stat.passive_critical_strike,100);
      f.source.base().put(Stat.firemindam,2); f.source.base().put(Stat.firemaxdam,2); f.source.reset();
      Missile p = f.spawn(row()); p.rngState = 135;
      MissileDamageResolver.captureMonsterCreationCritical(p,f.source,null,n -> 0);
      NativeSeedReference expectedOwner = new NativeSeedReference(777); expectedOwner.nextInt(100);
      NativeSeedReference expectedImpact = new NativeSeedReference(135);
      int physical = 4 * (2560 + expectedImpact.nextInt(256));
      f.land();
      assertEquals(10000 * 256 - physical - 1024,f.target.attrs.get(Stat.hitpoints).encodedValues());
      assertEquals(expectedOwner.state(),f.monster.rngState);
      assertEquals(expectedImpact.state(),f.probe.impactSeeds.get(f.probe.request.missileId).intValue());
      assertEquals(4f,f.probe.request.fireDamage);
    }
  }
}
