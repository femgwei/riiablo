package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import org.junit.jupiter.api.Test;

class DruidStormOriginalParityTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/druid-storm-1.10f.json"));
  }

  @Test void everyAcceptedNativeRockAndTailGraphicsOffsetMatchesTheProductionSampler() {
    int checked=0;
    for (JsonValue sample : fixture().get("cases").get("249").get("samples")) {
      int cls=sample.getInt("class_id"); if (cls!=578 && cls!=579) continue;
      ReferenceElementalEffect effect=new ReferenceElementalEffect(); effect.nativeArmageddon=true;
      int age=sample.getInt("age");
      ReferenceElementalPresentationSystem.sample(effect,age/25f);
      assertEquals(25,sample.getInt("lifetime"));
      assertEquals(sample.get("graphics").getInt(1)/2048f,effect.offset.x);
      assertEquals(sample.get("graphics").getInt(3)/2048f,effect.offset.y);
      assertEquals(-15*2048,sample.get("graphics").getInt(4));
      assertEquals(-25*2048,sample.get("graphics").getInt(6));
      checked++;
    }
    assertTrue(checked>2500,"Compare all accepted native falling layers");
  }

  @Test void everyAcceptedNativeFrameMatchesRockTailExplosionAndGroundFireProfiles() {
    int checked=0;
    for (JsonValue sample : fixture().get("cases").get("249").get("samples")) {
      int cls=sample.getInt("class_id"), age=sample.getInt("age");
      String name=cls==578?"armageddonrock":cls==579?"armageddontail":cls==580?"armageddonexplosion":"armageddonfire";
      ReferenceElementalEffect effect=new ReferenceElementalEffect();
      ReferenceElementalPresentationSystem.configure(name,effect); effect.age=age/25f;
      int frames=Riiablo.files.Missiles.get(name).AnimLen;
      assertEquals(sample.get("animation").getInt(0)/256,
          ReferenceElementalPresentationSystem.frameAt(effect,frames),name+" age="+age);
      checked++;
    }
    assertTrue(checked>5000);
  }

  @Test void bothBackgroundStatesRefreshBeyondTenSecondsAndLeaveNoResidualEffects() {
    JsonValue root=fixture(); assertTrue(root.getBoolean("background_verified"));
    assertTrue(root.getBoolean("owned_game_closed"));
    for (JsonValue cast : root.get("cases")) {
      JsonValue transitions=cast.get("state_transitions");
      assertEquals(3,transitions.size);
      assertFalse(transitions.get(0).getBoolean("active"));
      assertTrue(transitions.get(1).getBoolean("active"));
      assertFalse(transitions.get(2).getBoolean("active"));
      int duration=transitions.get(2).getInt("elapsed_ms")-transitions.get(1).getInt("elapsed_ms");
      assertTrue(duration>17000 && duration<17500);
      assertEquals(0,cast.get("summary").getInt("final_units"));
    }
    assertEquals(new Vector2(375,625),NativeArmageddonPresentation.offset(0,new Vector2()));
    assertEquals(Vector2.Zero,NativeArmageddonPresentation.offset(25,new Vector2()));
  }
}
