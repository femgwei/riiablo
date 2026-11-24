package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidWindPresentationLifecycleTest extends RiiabloTest {
  @Test void clientFactoryUsesTheSilentPresentationAllocationRoute() {
    ClientEntityFactory factory=mock(ClientEntityFactory.class);
    Missiles.Entry row=Riiablo.files.Missiles.get("twister");Vector2 at=new Vector2(100,100),direction=new Vector2(8,0);
    when(factory.createMissilePresentation(row,direction,at)).thenReturn(123);
    assertEquals(123,DruidWindPresentationSystem.createLocalMissile(factory,row,direction,at,1));
    verify(factory).createMissilePresentation(row,direction,at);
    verify(factory,never()).createMissile(any(Missiles.Entry.class),any(Vector2.class),any(Vector2.class),anyInt(),anyInt());
  }
  @Test void independentVisualsStayLocalAndDelayedCombatDeletionCannotMakeThemDrawTwice() {
    try(Fixture f=new Fixture(new Map(0,0))) {
      assertFalse(f.system.hidesCarrier(f.carrier));
      f.cast();assertEquals(3,f.created.size());assertTrue(f.system.hidesCarrier(f.carrier));
      for(int id:f.created) {
        Missile m=f.world.getMapper(Missile.class).get(id);
        assertTrue(m.presentationOnly);assertFalse(m.authoritative);
        assertFalse(f.world.getMapper(Networked.class).has(id));
      }
      f.steps(51);
      for(int id:f.created)assertFalse(f.world.getEntityManager().isActive(id));
      assertTrue(f.system.hidesCarrier(f.carrier),"late replica deletion cannot reveal a second wind sprite");
      assertTrue(f.world.getMapper(Missile.class).get(f.carrier).authoritative);
    }
  }

  @Test void repeatedCastsKeepSeparateBirthClocksAndExpireTheirOwnFlights() {
    try(Fixture f=new Fixture(new Map(0,0))) {
      f.cast();f.steps(25);f.cast();assertEquals(6,f.created.size());f.steps(25);
      for(int i=0;i<3;i++)assertFalse(f.world.getEntityManager().isActive(f.created.get(i)));
      for(int i=3;i<6;i++)assertTrue(f.world.getEntityManager().isActive(f.created.get(i)));
      f.steps(25);for(int id:f.created)assertFalse(f.world.getEntityManager().isActive(id));
    }
  }

  @Test void incompleteVisualAllocationRetainsTheEntireAuthoritativeFallback() {
    try(Fixture f=new Fixture(new Map(0,0))) {
      f.failCall=2;f.cast();f.steps(1);
      assertEquals(2,f.created.size());for(int id:f.created)assertFalse(f.world.getEntityManager().isActive(id));
      assertFalse(f.system.hidesCarrier(f.carrier));
    }
  }

  @Test void leavingAKnownHostSceneRemovesTheVisualWithoutAnImpact() {
    Map map=mock(Map.class);Map.Zone zone=mock(Map.Zone.class);
    when(map.getZone(any(Vector2.class))).thenAnswer(call->((Vector2)call.getArgument(0)).x<100.5f?zone:null);
    try(Fixture f=new Fixture(map)) {
      f.cast();f.steps(5);for(int id:f.created)assertFalse(f.world.getEntityManager().isActive(id));
      assertTrue(f.world.getEntityManager().isActive(f.carrier));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final DruidWindPresentationSystem system=new DruidWindPresentationSystem();
    final EntityFactory factory=mock(EntityFactory.class);
    final World world;
    final int caster,carrier;
    final List<Integer> created=new ArrayList<>();
    int calls,failCall=-1;
    Fixture(Map map) {
      world=new World(new WorldConfigurationBuilder().with(new EventSystem(),system,factory).build()
          .register("factory",factory).register("map",map)
          .register("clientContext",ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class),any(Vector2.class),any(Vector2.class),anyInt(),anyInt()))
          .thenAnswer(call->{
            if(++calls==failCall)return -1;
            int id=world.create();Missiles.Entry row=call.getArgument(0);Vector2 at=call.getArgument(2);
            world.getMapper(Missile.class).create(id).set(row,at,row.Range);
            world.getMapper(Position.class).create(id).position.set(at);
            world.getMapper(Velocity.class).create(id);world.getMapper(Networked.class).create(id);
            created.add(id);return id;
          });
      caster=world.create();world.getMapper(Position.class).create(caster).position.set(100,100);
      CharData data=CharData.createRemote("wind-life",Riiablo.DRUID);data.setSkillLevel(240,1);
      world.getMapper(Player.class).create(caster).data=data;
      carrier=world.create();Missile m=world.getMapper(Missile.class).create(carrier);
      m.missile=Riiablo.files.Missiles.get("twister");m.skillId=240;m.ownerId=caster;
      world.getMapper(Position.class).create(carrier).position.set(100,100);
    }
    void cast(){system.onSkillDo(SkillDoEvent.obtain(caster,240,-1,new Vector2(108,100),118,68));}
    void steps(int count){world.setDelta(1f/25f);for(int i=0;i<count;i++)world.process();}
    @Override public void close(){world.dispose();}
  }
}
