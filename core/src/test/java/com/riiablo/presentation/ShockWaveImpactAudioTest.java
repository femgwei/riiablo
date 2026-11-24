package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.audio.Audio;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.map.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ShockWaveImpactAudioTest extends RiiabloTest {
  @Test void nativeStunHitClassOverridesTheRawLightningRowWithoutInventingDamageOrSprites() {
    Audio audio=mock(Audio.class); EntityFactory factory=mock(EntityFactory.class);
    AtomicInteger listener=new AtomicInteger(-1);
    ClientGameplayContext context=new ClientGameplayContext(null,listener::get,()->null,null,
        null,audio,null,Riiablo.files,null,null,null,null,null,null);
    MissileImpactPresentationSystem system=new MissileImpactPresentationSystem();
    World world=new World(new WorldConfigurationBuilder().with(new EventSystem(),factory,new OverlayManager(),system)
        .build().register("factory",factory).register("map",new Map(0,0)).register("clientContext",context));
    try {
      int source=world.create(),target=world.create(); listener.set(source);
      world.getMapper(Position.class).create(source).position.set(10,10);
      world.getMapper(Position.class).create(target).position.set(11,10);
      com.riiablo.codec.excel.Missiles.Entry row=Riiablo.files.Missiles.get("shockwave");
      assertEquals(64,row.HitClass); assertEquals(7,row.pSrvDmgFunc);
      assertNotNull(Riiablo.files.Sounds.get("impact_stun_1"));
      clearInvocations(factory);
      system.onMissileImpact(MissileImpactEvent.obtain(-1,row.Id,source,target,new Vector2(11,10)));
      verify(audio).play("impact_stun_1",true);
      verify(audio,never()).play("impact_lightning_1",true);
      verifyNoMoreInteractions(audio); verifyNoInteractions(factory);
      assertFalse(world.getMapper(UnitStates.class).has(target),
          "the presentation event must not fabricate the authoritative stun state");
    } finally { world.dispose(); }
  }
}
