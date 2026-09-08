package com.riiablo.save;

import com.badlogic.gdx.utils.Array;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.StatListReader;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;
import com.riiablo.item.Location;
import com.riiablo.item.Quality;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.Type;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewCharacterStartItemsTest extends RiiabloTest {
  @Test
  void createsNativeStartingLoadoutForEveryClass() {
    for (CharacterClass clazz : CharacterClass.values()) {
      CharData character = newCharacter(clazz);
      Array<Item> items = character.getItems().getItems();

      assertEquals(clazz == CharacterClass.SORCERESS || clazz == CharacterClass.NECROMANCER ? 7 : 8,
          items.size, clazz.toString());
      assertEquals(4, count(items, Location.BELT, StoreLoc.NONE, "hp1"), clazz.toString());
      assertEquals(1, count(items, Location.STORED, StoreLoc.INVENTORY, "tsc"), clazz.toString());
      assertEquals(1, count(items, Location.STORED, StoreLoc.INVENTORY, "isc"), clazz.toString());

      Item rightArm = character.getItems().getSlot(BodyLoc.RARM);
      assertNotNull(rightArm, clazz.toString());
      assertEquals(clazz.entry().item[0], rightArm.code, clazz.toString());
      if (clazz == CharacterClass.SORCERESS || clazz == CharacterClass.NECROMANCER) {
        assertNull(character.getItems().getSlot(BodyLoc.LARM), clazz.toString());
      } else {
        assertEquals(clazz.entry().item[1], character.getItems().getSlot(BodyLoc.LARM).code,
            clazz.toString());
      }

      int startSkill = skillId(clazz.entry().StartSkill);
      if (startSkill >= 0) {
        StatRef skillBonus = rightArm.attrs.list(0).get(Stat.item_singleskill, startSkill);
        assertNotNull(skillBonus, clazz + " start skill");
        assertEquals(startSkill, skillBonus.encodedParams(), clazz + " start skill id");
        assertEquals(1, skillBonus.encodedValues(), clazz + " start skill bonus");
      }

      Set<Integer> ids = new HashSet<>();
      for (Item item : items) {
        assertTrue((item.flags & Item.ITEMFLAG_BEGINNER) != 0, item.code);
        assertTrue((item.flags & Item.ITEMFLAG_IDENTIFIED) != 0, item.code);
        assertEquals(Item.VERSION_110, item.version, item.code);
        if ((item.flags & Item.ITEMFLAG_COMPACT) == 0) {
          assertEquals(Quality.NORMAL, item.quality, item.code);
          assertTrue(ids.add(item.id), "duplicate standard item id " + item.id);
          if (item.type.is(Type.WEAP) || item.type.is(Type.ARMO)) {
            assertNotNull(item.attrs.base().get(Stat.maxdurability), item.code);
          }
        }
      }

      assertInventoryDoesNotOverlap(items);
    }
  }

  @Test
  void startingItemsRoundTripThroughVersion96Save() {
    for (CharacterClass clazz : CharacterClass.values()) {
      CharData character = newCharacter(clazz);
      D2S encoded = D2SWriter96.createD2S(character);
      byte[] bytes = new D2SWriter96().writeD2S(encoded);
      D2S decoded = D2SReader.INSTANCE.readComplete(bytes, new StatListReader(), new ItemReader());

      assertEquals(encoded.items.items.size, decoded.items.items.size, clazz.toString());
      for (int i = 0; i < encoded.items.items.size; i++) {
        Item expected = encoded.items.items.get(i);
        Item actual = decoded.items.items.get(i);
        assertEquals(expected.code, actual.code, clazz + " item " + i);
        assertEquals(expected.location, actual.location, clazz + " item " + i);
        assertEquals(expected.bodyLoc, actual.bodyLoc, clazz + " item " + i);
        assertEquals(expected.storeLoc, actual.storeLoc, clazz + " item " + i);
        assertEquals(expected.gridX, actual.gridX, clazz + " item " + i);
        assertEquals(expected.gridY, actual.gridY, clazz + " item " + i);
        assertTrue((actual.flags & Item.ITEMFLAG_BEGINNER) != 0, clazz + " item " + i);
        if ((expected.flags & Item.ITEMFLAG_COMPACT) == 0) {
          assertEquals(Quality.NORMAL, actual.quality, clazz + " item " + i);
          assertEquals(expected.id, actual.id, clazz + " item " + i);
          assertNotNull(actual.attrs.list(0), clazz + " item " + i);
        }
      }

      Item weapon = decoded.items.items.first();
      int startSkill = skillId(clazz.entry().StartSkill);
      if (startSkill >= 0) {
        StatRef skillBonus = weapon.attrs.list(0).get(Stat.item_singleskill, startSkill);
        assertNotNull(skillBonus, clazz + " round-trip start skill");
        assertEquals(startSkill, skillBonus.encodedParams(), clazz.toString());
        assertEquals(1, skillBonus.encodedValues(), clazz.toString());
      }
    }
  }

  @Test
  void mercenaryHeaderAndCorpseSectionsRoundTrip() {
    CharData character = newCharacter(CharacterClass.AMAZON);
    D2S encoded = D2SWriter96.createD2S(character);
    encoded.merc.flags = 0x1234;
    encoded.merc.seed = 0x5678;
    encoded.merc.name = (short) 0x0102;
    encoded.merc.type = (short) 0x0304;
    encoded.merc.experience = 987654L;
    encoded.merc.items = new D2S.ItemData();
    encoded.merc.items.items = new Array<>();
    encoded.corpse.items.add(encoded.items.items.first());

    byte[] bytes = new D2SWriter96().writeD2S(encoded);
    D2S decoded = D2SReader.INSTANCE.readComplete(bytes, new StatListReader(), new ItemReader());

    assertEquals(encoded.merc.flags, decoded.merc.flags);
    assertEquals(encoded.merc.seed, decoded.merc.seed);
    assertEquals(encoded.merc.name, decoded.merc.name);
    assertEquals(encoded.merc.type, decoded.merc.type);
    assertEquals(encoded.merc.experience, decoded.merc.experience);
    assertEquals(0, decoded.merc.items.items.size);
    assertEquals(1, decoded.corpse.items.size);
    assertEquals(encoded.corpse.items.first().code, decoded.corpse.items.first().code);
  }

  @Test
  void socketedMercCorpseAndGolemItemsRoundTrip() {
    CharData character = newCharacter(CharacterClass.AMAZON);
    D2S encoded = D2SWriter96.createD2S(character);

    // Turn the starting weapon into a one-socket item and attach a compact
    // rune/potion-shaped child.  The child is deliberately stored as SOCKET;
    // this exercises the nested JM records written after the parent item.
    Item parent = encoded.items.items.first();
    parent.flags |= Item.ITEMFLAG_SOCKETED;
    parent.socketsFilled = 1;
    parent.attrs.base().put(Stat.item_numsockets, 1);
    Item socket = encoded.items.items.get(2);
    socket.location = Location.SOCKET;
    socket.bodyLoc = BodyLoc.NONE;
    socket.storeLoc = StoreLoc.NONE;
    parent.sockets = new Array<>();
    parent.sockets.add(socket);

    // Populate all optional item sections with real item records.  Reusing
    // objects is intentional: the D2S format stores independent records and
    // the reader must consume each section without relying on object identity.
    encoded.merc.flags = 0x1234;
    encoded.merc.seed = 0x5678;
    encoded.merc.name = (short) 0x0102;
    encoded.merc.type = (short) 0x0304;
    encoded.merc.experience = 987654L;
    encoded.merc.items = new D2S.ItemData();
    encoded.merc.items.items = new Array<>();
    encoded.merc.items.items.add(encoded.items.items.get(1));

    encoded.corpse.items.add(encoded.items.items.get(3));
    encoded.golem.exists = true;
    encoded.golem.item = encoded.items.items.get(4);

    byte[] bytes = new D2SWriter96().writeD2S(encoded);
    D2S decoded = D2SReader.INSTANCE.readComplete(bytes, new StatListReader(), new ItemReader());

    assertEquals(encoded.items.items.size, decoded.items.items.size);
    Item decodedParent = decoded.items.items.first();
    assertEquals(1, decodedParent.socketsFilled);
    assertEquals(1, decodedParent.sockets.size);
    assertEquals(socket.code, decodedParent.sockets.first().code);
    assertEquals(Location.SOCKET, decodedParent.sockets.first().location);
    assertEquals(1, decoded.merc.items.items.size);
    assertEquals(encoded.merc.items.items.first().code, decoded.merc.items.items.first().code);
    assertEquals(1, decoded.corpse.items.size);
    assertEquals(encoded.corpse.items.first().code, decoded.corpse.items.first().code);
    assertTrue(decoded.golem.exists);
    assertEquals(encoded.golem.item.code, decoded.golem.item.code);
  }

  private static CharData newCharacter(CharacterClass clazz) {
    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "StartHero", (byte) clazz.id);
    character.mapSeed = 0x12340000 + clazz.id * 0x100;
    character.initializeStartItems(clazz.entry());
    return character;
  }

  private static int count(Array<Item> items, Location location, StoreLoc store, String code) {
    int count = 0;
    for (Item item : items) {
      if (item.location == location && item.storeLoc == store && code.equals(item.code)) count++;
    }
    return count;
  }

  private static int skillId(String name) {
    for (Skills.Entry skill : Riiablo.files.skills) {
      if (name.equalsIgnoreCase(skill.skill)) return skill.Id;
    }
    return -1;
  }

  private static void assertInventoryDoesNotOverlap(Array<Item> items) {
    boolean[][] occupied = new boolean[4][10];
    for (Item item : items) {
      if (item.location != Location.STORED || item.storeLoc != StoreLoc.INVENTORY) continue;
      for (int y = item.gridY; y < item.gridY + item.base.invheight; y++) {
        for (int x = item.gridX; x < item.gridX + item.base.invwidth; x++) {
          assertFalse(occupied[y][x], item.code + " overlaps at " + x + "," + y);
          occupied[y][x] = true;
        }
      }
    }
  }
}
