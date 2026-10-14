package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class NativeWhirlwindDataTest extends RiiabloTest {
  @Test
  void authoritativeWhirlwindStateSnapshotRestoresAndClearsReadOnlyReplica() {
    com.riiablo.engine.server.component.UnitStates source =
        new com.riiablo.engine.server.component.UnitStates().init(7);
    source.stateList.addState(com.riiablo.engine.server.state.StateId.WHIRLWIND, 0, 20, 7)
        .skillId = 151;
    com.riiablo.engine.server.component.UnitStates replica =
        new com.riiablo.engine.server.component.UnitStates().init(7);
    snapshot(source, replica);
    assertEquals(20, replica.stateList.getStateLevel(com.riiablo.engine.server.state.StateId.WHIRLWIND));
    org.junit.jupiter.api.Assertions.assertTrue(replica.snapshotOnly);
    source.stateList.removeState(com.riiablo.engine.server.state.StateId.WHIRLWIND);
    snapshot(source, replica);
    org.junit.jupiter.api.Assertions.assertFalse(replica.stateList.hasState(
        com.riiablo.engine.server.state.StateId.WHIRLWIND));
  }

  private static void snapshot(com.riiablo.engine.server.component.UnitStates source,
      com.riiablo.engine.server.component.UnitStates replica) {
    com.riiablo.engine.server.component.serializer.StateSerializer serializer =
        new com.riiablo.engine.server.component.serializer.StateSerializer();
    com.google.flatbuffers.FlatBufferBuilder builder = new com.google.flatbuffers.FlatBufferBuilder(256);
    int state = serializer.putData(builder, source);
    int types = com.riiablo.net.packet.d2gs.EntitySync.createComponentTypeVector(builder,
        new byte[] {com.riiablo.net.packet.d2gs.ComponentP.StateP});
    int components = com.riiablo.net.packet.d2gs.EntitySync.createComponentVector(builder, new int[] {state});
    builder.finish(com.riiablo.net.packet.d2gs.EntitySync.createEntitySync(
        builder, 7, 0, 0, types, components, 0L, 0L, 0L, 0L, 0L, -1));
    serializer.getData(com.riiablo.net.packet.d2gs.EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, replica);
  }

  @Test
  void nativeRowDrivesWhirlwindPathAndAttackWindows() {
    Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
    assertNotNull(skill);
    assertEquals(151, skill.Id);
    assertEquals(38, skill.srvstfunc);
    assertEquals(76, skill.srvdofunc);
    assertEquals(45, skill.cltdofunc);
    assertEquals("SQ", skill.anim);
    assertEquals(10, skill.seqnum);
    assertEquals("whirlwind", skill.aurastate);
    assertEquals("ln12", skill.calc1);
    assertEquals(-50, BarbarianSkills.calculateWhirlwindDamageBonus(skill, 1, name -> 0));
    assertEquals(102, BarbarianSkills.calculateWhirlwindDamageBonus(skill, 20, name -> 0));
    assertEquals(128, skill.SrcDam);
    assertEquals(5, skill.LevToHit);
  }
}
