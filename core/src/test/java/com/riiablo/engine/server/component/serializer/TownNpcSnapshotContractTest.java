package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.net.packet.d2gs.CofComponentsP;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.PositionP;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Wire contracts for the native NPC identity used to reconstruct client appearance. */
class TownNpcSnapshotContractTest extends RiiabloTest {
  @Test void akaraIdentityResolvesNativeAppearanceAndPositionPacketsAreDetached() {
    MonStats.Entry row = Riiablo.files.monstats.get(MonsterType.AKARA);
    assertNotNull(row);
    assertTrue(row.npc);
    assertTrue(row.interact);
    assertEquals("PS", row.Code);
    Monster source = new Monster().set(row, null);
    FlatBufferBuilder identityBuilder = new FlatBufferBuilder(64);
    identityBuilder.finish(new MonsterSerializer().putData(identityBuilder, source));
    MonsterP identity = MonsterP.getRootAsMonsterP(identityBuilder.dataBuffer());
    assertEquals(MonsterType.AKARA, identity.monsterId());
    assertSame(row, Riiablo.files.monstats.get(identity.monsterId()));

    Position position = new Position();
    position.position.set(121, 246);
    FlatBufferBuilder positionBuilder = new FlatBufferBuilder(64);
    positionBuilder.finish(new PositionSerializer().putData(positionBuilder, position));
    PositionP oldPosition = PositionP.getRootAsPositionP(positionBuilder.dataBuffer());
    position.position.set(150, 270);
    assertEquals(121f, oldPosition.x());
    assertEquals(246f, oldPosition.y());

    CofComponents components = new CofComponents();
    components.component[COF.Component.TR] = CofComponents.COMPONENT_LIT;
    FlatBufferBuilder appearanceBuilder = new FlatBufferBuilder(64);
    appearanceBuilder.finish(new CofComponentsSerializer().putData(appearanceBuilder, components));
    CofComponentsP oldAppearance = CofComponentsP.getRootAsCofComponentsP(appearanceBuilder.dataBuffer());
    components.component[COF.Component.TR] = CofComponents.COMPONENT_NIL;
    assertEquals(CofComponents.COMPONENT_LIT, oldAppearance.component(COF.Component.TR));
  }
}
