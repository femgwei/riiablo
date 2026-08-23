package com.riiablo.save;

import com.badlogic.gdx.utils.Array;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.StatListReader;
import com.riiablo.codec.excel.Skills;
import com.riiablo.io.ByteInput;
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
      ByteInput in = ByteInput.wrap(bytes);

      D2S decoded = D2SReader.INSTANCE.readD2S(in);
      D2SReader.INSTANCE.readRemaining(decoded, in, new StatListReader(), new ItemReader());

      assertEquals(encoded.items.items.size, decoded.items.items.size, clazz.toString());
      assertEquals(0, in.bytesRemaining(), clazz.toString());
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
