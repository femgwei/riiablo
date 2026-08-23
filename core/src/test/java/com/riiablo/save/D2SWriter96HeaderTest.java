package com.riiablo.save;

import com.badlogic.gdx.utils.Array;
import com.riiablo.attributes.StatListReader;
import com.riiablo.io.ByteInput;
import com.riiablo.item.ItemReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class D2SWriter96HeaderTest {
  private static final int HEADER_SIZE = 0x14F;

  @Test
  void writesNativeHeaderCountsAndKeepsSkillAndItemSectionsAligned() {
    D2S d2s = minimalExpansionSave("TestHero");
    byte[] data = new D2SWriter96().writeD2S(d2s);

    assertEquals(16, data[0x29] & 0xFF);
    assertEquals(30, data[0x2A] & 0xFF);
    assertEquals(1, data[0x2B] & 0xFF);
    assertEquals(d2s.timestamp, intLE(data, 0x2C));
    assertEquals(d2s.timestamp, intLE(data, 0x30));
    assertEquals(-1, intLE(data, 0x34));

    assertArrayEquals(new byte[] {0x57, 0x6F, 0x6F, 0x21},
        Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + 4));

    int statsOffset = HEADER_SIZE
        + D2SReader96.QUESTS_SIZE
        + D2SReader96.WAYPOINTS_SIZE
        + D2SReader96.NPCS_SIZE;
    assertArrayEquals(new byte[] {0x67, 0x66},
        Arrays.copyOfRange(data, statsOffset, statsOffset + 2));

    // Empty stats are gf + the 9-bit 0x1FF terminator padded to two bytes.
    int skillsOffset = statsOffset + 4;
    assertArrayEquals(new byte[] {0x69, 0x66},
        Arrays.copyOfRange(data, skillsOffset, skillsOffset + 2));

    int itemOffset = skillsOffset + 2 + (data[0x2A] & 0xFF);
    assertArrayEquals(new byte[] {0x4A, 0x4D},
        Arrays.copyOfRange(data, itemOffset, itemOffset + 2));
  }

  @Test
  void writesSizeAndChecksumAcceptedByTheNativeAlgorithm() {
    byte[] data = new D2SWriter96().writeD2S(minimalExpansionSave("Checksum"));

    assertEquals(data.length, intLE(data, 0x08));
    assertEquals(
        D2SWriter96.computeChecksum(data, 0x0C),
        intLE(data, 0x0C));
    assertEquals(
        intLE(data, 0x0C),
        D2SReader.INSTANCE.calculateChecksum(com.riiablo.io.ByteInput.wrap(data)));
  }

  @Test
  void generatedEmptyCharacterRoundTripsThroughEveryVersion96Section() {
    byte[] data = new D2SWriter96().writeD2S(minimalExpansionSave("RoundTrip"));
    ByteInput in = ByteInput.wrap(data);

    D2S decoded = D2SReader.INSTANCE.readD2S(in);
    D2SReader.INSTANCE.readRemaining(decoded, in, new StatListReader(), new ItemReader());

    assertTrue(decoded.bodyRead());
    assertEquals("RoundTrip", decoded.name());
    assertEquals(0, decoded.items.items.size);
    assertEquals(0, decoded.corpse.items.size);
    assertFalse(decoded.golem.exists);
    assertEquals(0, in.bytesRemaining());
  }

  @Test
  void rejectsNamesThatCannotRoundTripThroughTheClassicHeader() {
    assertTrue(D2S.isOriginalNameCompatible("Hero-01"));
    assertTrue(D2S.isOriginalNameCompatible("Test_Hero"));
    assertFalse(D2S.isOriginalNameCompatible("A"));
    assertFalse(D2S.isOriginalNameCompatible("-Hero"));
    assertFalse(D2S.isOriginalNameCompatible("Hero-"));
    assertFalse(D2S.isOriginalNameCompatible("中文角色"));
    assertFalse(D2S.isOriginalNameCompatible("Name.WithDot"));

    D2S d2s = minimalExpansionSave("中文角色");
    assertThrows(IllegalArgumentException.class, () -> new D2SWriter96().writeD2S(d2s));
  }

  @Test
  void marksOnlyTheCurrentDifficultyTownAsActive() {
    CharData charData = new CharData().set(0, false, "TownHero", (byte) 0);
    D2S d2s = D2SWriter96.createD2S(charData);

    assertEquals(0x80, d2s.towns[0] & 0xFF);
    assertEquals(0, d2s.towns[1] & 0xFF);
    assertEquals(0, d2s.towns[2] & 0xFF);
  }

  private static D2S minimalExpansionSave(String name) {
    D2S d2s = new D2S();
    d2s.version = D2S.VERSION_110;
    d2s.name = name;
    d2s.flags = D2S.FLAG_EXPANSION;
    d2s.charClass = 0;
    d2s.level = 1;
    d2s.timestamp = 0x12345678;
    d2s.hotkeys = new int[D2S.NUM_HOTKEYS];
    Arrays.fill(d2s.hotkeys, D2S.HOTKEY_UNASSIGNED);
    d2s.actions = new int[D2S.NUM_ACTIONS][D2S.NUM_BUTTONS];
    d2s.composites = new byte[16];
    d2s.colors = new byte[16];
    d2s.towns = new byte[D2S.NUM_DIFFS];

    d2s.merc = new D2S.MercData();

    d2s.quests = new D2S.QuestData();
    d2s.quests.flags = new byte[D2S.NUM_DIFFS][D2S.QuestData.NUM_QUESTFLAGS];

    d2s.waypoints = new D2S.WaypointData();
    d2s.waypoints.flags = new byte[D2S.NUM_DIFFS][D2S.WaypointData.NUM_WAYPOINTFLAGS];

    d2s.npcs = new D2S.NPCData();
    d2s.npcs.flags = new byte[D2S.NPCData.NUM_GREETINGS][D2S.NUM_DIFFS]
        [D2S.NPCData.NUM_INTROS];

    d2s.skills = new D2S.SkillData();
    d2s.skills.skills = new byte[30];

    d2s.items = new D2S.ItemData();
    d2s.items.items = new Array<>();
    d2s.corpse = new D2S.ItemData();
    d2s.corpse.items = new Array<>();
    d2s.golem = new D2S.GolemData();
    return d2s;
  }

  private static int intLE(byte[] data, int offset) {
    return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
  }
}
