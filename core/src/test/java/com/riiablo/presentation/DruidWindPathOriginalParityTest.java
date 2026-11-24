package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.NativeDruidWindMotion;
import java.util.*;
import org.junit.jupiter.api.Test;

class DruidWindPathOriginalParityTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/druid-wind-path-1.10f.json"));
  }

  @Test void realVisualAndAuthorityPathsMatchEveryObservedNativePositionAndVelocity() {
    int checked=0;
    for(JsonValue cast:fixture().get("cases")) {
      Map<String,Object> port=DruidSurfaceOriginalAnalysis.simulate(cast);
      for(boolean visual:new boolean[]{true,false}) {
        List<Map<?,?>> births=births(port,cast.name,visual);
        assertEquals(cast.get("units").size,births.size());
        int index=0;
        for(JsonValue unit:cast.get("units")) {
          Map<?,?> birth=births.get(index++);
          Map<Integer,Map<?,?>> states=states(birth);
          for(JsonValue source:unit.get("states")) {
            int age=source.getInt("total_frames")-source.getInt("current_frame");
            Map<?,?> actual=states.get(age);assertNotNull(actual,cast.name+" age="+age);
            assertEquals(source.getInt("x_fixed"),actual.get("x_fixed"),cast.name+" x age="+age);
            assertEquals(source.getInt("y_fixed"),actual.get("y_fixed"),cast.name+" y age="+age);
            assertEquals(source.getInt("velocity_x"),actual.get("velocity_x"));
            assertEquals(source.getInt("velocity_y"),actual.get("velocity_y"));
            if(!visual && age>0) {
              assertEquals(true,actual.get("hidden_damage_carrier"));
              assertEquals(false,actual.get("loaded"),"matched combat carriers do not acquire a duplicate DCC");
            }
            checked++;
          }
        }
      }
    }
    assertEquals(442,checked);
  }

  @Test void targetBasedPathInitializationReproducesBothHalvesOfTheObservedFinalSeed() {
    int checked=0;
    for(JsonValue cast:fixture().get("cases")) {
      int i=0;
      for(JsonValue unit:cast.get("units")) {
        JsonValue first=unit.get("states").child;
        D2Seed seed=new D2Seed(cast.get("identity").getInt("target_x")+i++,666);
        for(int n=0;n<first.getInt("total_frames")/2;n++)Seed.rollRandomNumber(seed);
        assertEquals(first.getLong("seed_low"),Integer.toUnsignedLong(seed.getNLowSeed()));
        assertEquals(first.getLong("seed_high"),Integer.toUnsignedLong(seed.getNHighSeed()));checked++;
      }
    }
    assertEquals(4,checked);
  }

  @Test void fixedSpriteVariantUsesTheNativeLookupAndDoesNotFollowVelocityTurns() {
    JsonValue table=fixture().get("direction_table");
    for(int i=0;i<64;i++)assertEquals(table.getInt(i),NativeDruidWindPresentation.direction(i,4));
    for(JsonValue cast:fixture().get("cases")) {
      Map<String,Object> port=DruidSurfaceOriginalAnalysis.simulate(cast);
      for(Map<?,?> birth:births(port,cast.name,true)) {
        Set<Object> directions=new HashSet<>();Set<String> velocities=new HashSet<>();
        for(Object value:(List<?>)birth.get("states")) {
          Map<?,?> state=(Map<?,?>)value;
          velocities.add(state.get("velocity_x")+","+state.get("velocity_y"));
          if(Boolean.TRUE.equals(state.get("loaded")))directions.add(state.get("direction"));
        }
        assertTrue(velocities.size()>1);assertEquals(1,directions.size());
      }
    }
  }

  @Test void coldResourcesDoNotSuspendTheNativeVisualPathAndBothLifetimesRemainBounded() {
    int checked=0;
    for(JsonValue cast:fixture().get("cases")) {
      Map<String,Object> port=DruidSurfaceOriginalAnalysis.simulate(cast,1000);
      int index=0;
      for(Map<?,?> birth:births(port,cast.name,true)) {
        JsonValue unit=cast.get("units").get(index++);Map<Integer,Map<?,?>> states=states(birth);
        for(JsonValue source:unit.get("states")) {
          Map<?,?> state=states.get(source.getInt("total_frames")-source.getInt("current_frame"));
          assertEquals(false,state.get("loaded"));
          assertEquals(source.getInt("x_fixed"),state.get("x_fixed"));
          assertEquals(source.getInt("y_fixed"),state.get("y_fixed"));checked++;
        }
        assertEquals(birth.get("lifetime"),birth.get("removed_tick"));
      }
    }
    assertEquals(221,checked);
  }

  @Test void legacyRolledCheckpointSeedsKeepThePreviousPathPolicy() {
    Missile m=new Missile();m.missile=Riiablo.files.Missiles.get("twister");m.skillId=240;m.chargedBoltPath=true;
    m.chargedBoltTargetX=4530;m.chargedBoltSeedLow=4530;m.chargedBoltSeedHigh=666;
    assertTrue(NativeDruidWindMotion.handles(m));
    D2Seed old=new D2Seed(4522,666);Seed.rollRandomNumber(old);
    m.chargedBoltSeedLow=old.getNLowSeed();m.chargedBoltSeedHigh=old.getNHighSeed();
    assertFalse(NativeDruidWindMotion.handles(m));
    m.chargedBoltSeedLow=4530;m.chargedBoltSeedHigh=666;m.chargedBoltPath=false;
    assertFalse(NativeDruidWindMotion.handles(m),"stationary synthetic damage carriers are not wind paths");
  }

  private static Map<Integer,Map<?,?>> states(Map<?,?> birth) {
    Map<Integer,Map<?,?>> states=new HashMap<>();
    for(Object value:(List<?>)birth.get("states")) {
      Map<?,?> state=(Map<?,?>)value;states.put(((Number)state.get("age")).intValue(),state);
    }
    return states;
  }
  private static List<Map<?,?>> births(Map<String,Object> port,String name,boolean visual) {
    List<Map<?,?>> result=new ArrayList<>();
    for(Object value:(List<?>)port.get("births")) {
      Map<?,?> row=(Map<?,?>)value;
      if(name.equals(row.get("missile")) && Boolean.valueOf(visual).equals(row.get("presentation_only")))result.add(row);
    }
    return result;
  }
}
