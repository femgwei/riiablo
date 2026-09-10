package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.serializer.MissileSerializer;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.net.packet.d2gs.MissileP;
import org.junit.jupiter.api.Test;

/** Multiplayer presentation contract for server-owned area skills. */
class AuthoritativeAreaSkillSnapshotTest extends RiiabloTest {
  @Test
  void complexAreaSkillsNeverCreateASecondClientMissileSet() {
    // Hydra is intentionally absent: native SrvDo144 creates three monster
    // units, whose visuals follow MonsterP rather than MissileP.
    Skills.Entry hydra = Riiablo.files.skills.get(SkillId.HYDRA);
    assertNotNull(hydra);
    assertFalse(SkillCastHandler.hasServerMissile(hydra));
    int[] skills = {
        SkillId.FIRESTORM, SkillId.FISSURE, SkillId.VOLCANO,
        SkillId.ARMAGEDDON, SkillId.HURRICANE
    };
    for (int id : skills) {
      Skills.Entry row = Riiablo.files.skills.get(id);
      assertNotNull(row, Integer.toString(id));
      assertTrue(SkillCastHandler.shouldReuseServerMissile(row,
          true, false, false, false, false, false, false), row.skill);
    }
  }

  @Test
  void oneAuthoritativePacketProducesEquivalentReadOnlyClientReplicas() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.VOLCANO);
    assertNotNull(skill);
    String missileName = firstNonEmpty(skill.srvmissilea,
        firstNonEmpty(skill.srvmissile, skill.cltmissilea));
    Missiles.Entry row = Riiablo.files.Missiles.get(missileName);
    assertNotNull(row, missileName);

    Missile authority = new Missile();
    authority.missile = row;
    authority.ownerId = 71;
    authority.range = 43f;
    authority.skillId = skill.Id;
    authority.damageLevel = 12;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int offset = new MissileSerializer().putData(builder, authority);
    builder.finish(offset);
    MissileP packet = MissileP.getRootAsMissileP(builder.dataBuffer());

    Missile firstClient = new Missile();
    Missile secondClient = new Missile();
    ClientNetworkReceiver.applyMissileSnapshot(firstClient, packet);
    ClientNetworkReceiver.applyMissileSnapshot(secondClient, packet);

    assertFalse(firstClient.authoritative);
    assertFalse(secondClient.authoritative);
    assertEquals(skill.Id, firstClient.skillId);
    assertEquals(firstClient.skillId, secondClient.skillId);
    assertEquals(12, firstClient.damageLevel);
    assertEquals(firstClient.damageLevel, secondClient.damageLevel);
    assertEquals(43f, firstClient.range);
    assertEquals(firstClient.range, secondClient.range);
  }

  private static String firstNonEmpty(String first, String second) {
    return first != null && !first.isEmpty() ? first : second;
  }
}
