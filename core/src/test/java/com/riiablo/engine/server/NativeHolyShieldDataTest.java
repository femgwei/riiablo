package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;

/** Native 1.10f contracts for Holy Shield SrvSt36/SrvDo018. */
class NativeHolyShieldDataTest extends RiiabloTest {
  @Test
  void rowRetainsNativeGateAndPayload() {
    Skills.Entry skill = skill();
    assertEquals("Holy Shield", skill.skill);
    assertEquals(36, skill.srvstfunc);
    assertEquals(18, skill.srvdofunc);
    assertFalse(skill.aura);
    assertEquals("holyshield", skill.aurastate);
    assertEquals("ln12", skill.auralencalc);
    assertEquals("toblock", skill.aurastat[0]);
    assertEquals("dm56", skill.aurastatcalc[0]);
    assertEquals("ln34+skill('Defiance'.blvl)*par8", skill.calc1);
  }

  @Test
  void formulasAndStateRefreshMatchNativeData() {
    Skills.Entry skill = skill();
    assertEquals(14, PaladinSkills.getHolyShieldBlockBonus(skill, 1));
    assertEquals(25, PaladinSkills.getHolyShieldDefenseBonus(skill, 1, name -> 0));
    assertEquals(40, PaladinSkills.getHolyShieldDefenseBonus(skill, 2, name -> 0));

    StateList states = new StateList(42);
    UnitState first = PaladinSkills.applyHolyShieldState(states, skill, 1, 42, name -> 0);
    assertNotNull(first);
    assertEquals(StateId.HOLYSHIELD, first.stateId);
    assertEquals(14, first.getStatContributionValue(Stat.toblock));
    assertEquals(25, first.getStatContributionValue(Stat.skill_armor_percent));
    int duration = first.duration;

    UnitState second = PaladinSkills.applyHolyShieldState(states, skill, 2, 42, name -> 0);
    assertNotNull(second);
    assertSame(second, states.getState(StateId.HOLYSHIELD));
    assertEquals(18, second.getStatContributionValue(Stat.toblock));
    assertEquals(40, second.getStatContributionValue(Stat.skill_armor_percent));
    assertTrue(second.duration >= duration);
    assertEquals(1, states.size(), "recast must replace, not stack, Holy Shield");

    states.update();
    assertTrue(states.hasState(StateId.HOLYSHIELD));
    second.duration = 1;
    states.update();
    assertFalse(states.hasState(StateId.HOLYSHIELD), "expired shield must remove stat-list");
  }

  @Test
  void replicatedStateRebuildsNativePayloadOnRemoteClient() {
    UnitStates source = new UnitStates().init(7);
    PaladinSkills.applyHolyShieldState(source.stateList, skill(), 2, 7, name -> 0);
    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, source);
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {ComponentP.StateP});
    int components = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 7, 0, 0, types, components,
        0L, 0L, 0L, 0L, 0L, -1);
    builder.finish(root);
    UnitStates remote = new UnitStates().init(7);
    serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, remote);
    UnitState state = remote.stateList.getState(StateId.HOLYSHIELD);
    assertNotNull(state);
    assertEquals(18, state.getStatContributionValue(Stat.toblock));
    assertEquals(40, state.getStatContributionValue(Stat.skill_armor_percent));
    assertEquals(40, state.runtimeValue);
  }

  private static Skills.Entry skill() {
    Skills.Entry skill = Riiablo.files.skills.get(117);
    assertNotNull(skill, "missing 1.10f Skills.txt Holy Shield row");
    return skill;
  }
}
