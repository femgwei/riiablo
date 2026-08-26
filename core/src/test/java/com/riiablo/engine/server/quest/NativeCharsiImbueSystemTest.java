package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NativeImbueRequestEvent;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.item.ItemWriter;
import com.riiablo.item.Quality;
import com.riiablo.item.RareQualityData;
import com.riiablo.save.CharData;
import io.netty.buffer.Unpooled;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class NativeCharsiImbueSystemTest extends RiiabloTest {
  @Test
  void replacesEligibleCursorItemBeforeCommittingReward() {
    ItemGenerator generator = new ItemGenerator();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new Act1QuestSystem(), generator,
            new NativeCharsiImbueSystem())
        .build());
    try {
      CharData data = character("ImbueHero", 12);
      data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] =
          Act1MalusQuest.completeObjective((short) 0);
      Item source = generator.generateStartItem("cap", 0x4401, -1);
      source.flags &= ~Item.ITEMFLAG_BEGINNER;
      int maxDurability = source.attrs.base().get(Stat.maxdurability).asInt();
      source.attrs.base().put(Stat.durability, 1);
      data.groundToCursor(source);
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId).data = data;
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = data.getStats();
      world.process();

      NativeImbueRequestEvent request = NativeImbueRequestEvent.obtain(playerId, source.id);
      world.getSystem(EventSystem.class).dispatch(request);

      Item output = data.getItems().getCursor();
      assertTrue(request.accepted);
      assertNotSame(source, output);
      assertEquals("cap", output.code);
      assertEquals(Quality.RARE, output.quality);
      assertEquals(16, output.ilvl);
      assertEquals(maxDurability, output.attrs.base().get(Stat.durability).asInt());
      assertTrue(output.attrs.list(0).size() > 0);

      ByteOutput encoded = ByteOutput.wrap(Unpooled.buffer());
      new ItemWriter().writeItem(output, encoded);
      Item decoded = new ItemReader().readItem(ByteInput.wrap(encoded.buffer()));
      assertEquals(Quality.RARE, decoded.quality);
      assertNotNull(decoded.qualityData);
      assertTrue(decoded.qualityData instanceof RareQualityData);
      assertTrue(decoded.attrs.list(0).size() > 0);

      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    } finally {
      world.dispose();
    }
  }

  @Test
  void rejectsMagicCursorItemAndKeepsRewardPending() {
    ItemGenerator generator = new ItemGenerator();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new Act1QuestSystem(), generator,
            new NativeCharsiImbueSystem())
        .build());
    try {
      CharData data = character("ImbueReject", 12);
      data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] =
          Act1MalusQuest.completeObjective((short) 0);
      Item source = generator.generateQuestReward("cap", 12, Quality.MAGIC, 0x4402);
      data.groundToCursor(source);
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId).data = data;
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = data.getStats();
      world.process();

      NativeImbueRequestEvent request = NativeImbueRequestEvent.obtain(playerId, source.id);
      world.getSystem(EventSystem.class).dispatch(request);

      assertFalse(request.accepted);
      assertEquals(source, data.getItems().getCursor());
      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    } finally {
      world.dispose();
    }
  }

  private static CharData character(String name, int level) {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
    data.getStats().base().put(Stat.level, level);
    data.getStats().reset();
    return data;
  }
}
