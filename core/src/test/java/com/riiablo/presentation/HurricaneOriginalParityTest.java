package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Position;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import java.util.*;
import org.junit.jupiter.api.Test;

class HurricaneOriginalParityTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/hurricane-path-1.10f.json"));
  }

  private static String name(int cls) {
    return cls==581?"hurricaneswoosh":cls==583?"hurricanerock":"hurricanetree";
  }

  @Test void everyAcceptedNativePositionVelocityFacingAndHeightMatchesTheDiscretePath() {
    int checked=0, inferred=0;
    for(JsonValue unit:fixture().get("units")) {
      int cls=unit.getInt("class_id");
      NativeHurricanePresentation.Flight flight=NativeHurricanePresentation.flight(
          unit.get("center").getInt(0),unit.get("center").getInt(1),unit.getInt("lane"),cls==581?20:18);
      if(!"direct_age0".equals(unit.getString("birth_position_evidence")))inferred++;
      for(JsonValue state:unit.get("states")) {
        int age=state.getInt(0);flight.advanceTo(age);
        String key=unit.getInt("unit_id")+" age="+age;
        assertEquals(state.getInt(1),flight.x,key+" x");assertEquals(state.getInt(2),flight.y,key+" y");
        assertEquals(state.getInt(3),flight.velocityX,key+" vx");assertEquals(state.getInt(4),flight.velocityY,key+" vy");
        assertEquals(state.getInt(5),flight.direction,key+" facing");
        assertEquals(unit.getInt("height"),state.getInt(9),key+" height");
        checked++;
      }
    }
    assertEquals(49614,checked);assertEquals(25,inferred);
  }

  @Test void originalResourceCountsAndNativeDirectionTableArePreservedWithoutInventingDrawEvidence() {
    JsonValue root=fixture();
    for(int d=0;d<64;d++)assertEquals(root.get("direction_table").getInt(d),NativeHurricanePresentation.direction(d,8));
    for(int cls:new int[]{581,583,585}) {
      com.riiablo.codec.excel.Missiles.Entry row=Riiablo.files.Missiles.get(name(cls));
      DCC dcc=DCC.loadFromFile(Riiablo.mpqs.resolve("data\\global\\missiles\\"+row.CelFile+".dcc"));
      assertEquals(cls==581?8:1,dcc.getNumDirections());assertEquals(cls==581?11:20,dcc.getNumFramesPerDir());
      assertEquals(cls==581?22:20,row.Range);
    }
    int checked=0;
    for(JsonValue unit:root.get("units"))for(JsonValue state:unit.get("states")) {
      int rate=unit.getInt("class_id")==581?128:256;
      assertEquals(state.getInt(0)*rate,state.getInt(6));assertEquals(rate,state.getInt(8));checked++;
    }
    assertEquals(49614,checked);
    assertEquals(26,root.get("rejected_samples").size);
    for(JsonValue rejected:root.get("rejected_samples")) {
      JsonValue state=rejected.get("state");
      assertEquals((state.getInt(0)-1)*state.getInt(8),state.getInt(6),"retain the adjacent-update observation");
    }
  }

  private static Map<Integer,JsonValue> representativeUnits(JsonValue root) {
    Map<Integer,JsonValue> units=new LinkedHashMap<>();
    for(JsonValue unit:root.get("units")) {
      int key=unit.getInt("lane")*1000+unit.getInt("class_id");
      JsonValue previous=units.get(key);
      if(previous==null || unit.get("states").size>previous.get("states").size)units.put(key,unit);
    }
    assertEquals(24,units.size());return units;
  }

  @Test void realProductionReplaysEveryObservedRepresentativeStateWithBoundedLifetimes() {
    productionReplay(false);
  }

  @Test void lateResourceArrivalDoesNotPauseRewindOrLengthenTheRealProductionFlight() {
    productionReplay(true);
  }

  private void productionReplay(boolean late) {
    com.riiablo.Colors previous=Riiablo.colors;Riiablo.colors=new com.riiablo.Colors();
    try(ReferenceElementalPresentationTest.Harness h=new ReferenceElementalPresentationTest.Harness()) {
      Map<Integer,JsonValue> units=new LinkedHashMap<>();Map<Integer,Animation> animations=new HashMap<>();
      for(JsonValue nativeUnit:representativeUnits(fixture()).values()) {
        ReferenceElementalEffect effect=h.system.hurricaneDebris(nativeUnit.get("center").getInt(0),
            nativeUnit.get("center").getInt(1),nativeUnit.getInt("lane"),name(nativeUnit.getInt("class_id")),nativeUnit.getInt("height"));
        assertNotNull(effect);int id=effect.entityId;units.put(id,nativeUnit);
        if(!late)animations.put(id,h.attachAnimation(id,nativeUnit.getInt("class_id")==581?8:1,
            nativeUnit.getInt("class_id")==581?11:20));
      }
      h.tick(0);int checked=0;
      for(int age=0;age<=22;age++) {
        if(age>0)h.tick(.04f);
        for(Map.Entry<Integer,JsonValue> entry:units.entrySet()) {
          int id=entry.getKey();JsonValue nativeUnit=entry.getValue();
          if(age>=nativeUnit.getInt("lifetime")) {
            assertFalse(h.world.getEntityManager().isActive(id));continue;
          }
          if(late && age==10) {
            Animation animation=h.attachAnimation(id,nativeUnit.getInt("class_id")==581?8:1,
                nativeUnit.getInt("class_id")==581?11:20);
            animations.put(id,animation);h.system.refreshLoadedEffect(id);
          }
          Position position=h.world.getMapper(Position.class).get(id);
          assertNotNull(position);assertTrue(position.hasNativePrecision());
          ReferenceElementalEffect effect=h.effect(id);assertEquals(age,effect.hurricaneFlight.age);
          for(JsonValue state:nativeUnit.get("states"))if(state.getInt(0)==age) {
            String key=name(nativeUnit.getInt("class_id"))+" lane="+nativeUnit.getInt("lane")+" age="+age;
            assertEquals(state.getInt(1),position.nativePrecisionX(),key+" x");
            assertEquals(state.getInt(2),position.nativePrecisionY(),key+" y");
            assertEquals(state.getInt(9),effect.offset.y,key+" height");
            if(animations.containsKey(id)) {
              Animation animation=animations.get(id);
              assertEquals(state.getInt(6)>>8,animation.getFrame(),key+" frame");
              assertEquals(NativeHurricanePresentation.direction(state.getInt(5),animation.getNumDirections()),animation.getDirection(),key+" direction");
            }
            checked++;
          }
        }
      }
      assertEquals(324,checked,"all retained states for the 24 lane/resource representatives");
    } finally {Riiablo.colors=previous;}
  }
}
