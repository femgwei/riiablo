package com.riiablo.engine.server.quest;

import com.artemis.ComponentMapper;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NativeImbueRequestEvent;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Owns Charsi's cursor-item validation and atomic rare-item replacement. */
public class NativeCharsiImbueSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(NativeCharsiImbueSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ItemGenerator itemGenerator;
  protected EventSystem event;

  @Subscribe
  public void onImbueRequest(NativeImbueRequestEvent request) {
    if (request == null || !mPlayer.has(request.playerId)) return;
    Player player = mPlayer.get(request.playerId);
    if (player == null || player.data == null || itemGenerator == null) return;
    short record = player.data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return;

    Item source = player.data.getItems().getCursor();
    if (source == null || source.id != request.itemId || !isImbueable(source)) {
      log.warn("[A1Q3] Imbue rejected: player={} requestedItem={} cursorItem={} eligible={}",
          request.playerId, request.itemId, source == null ? -1 : source.id,
          isImbueable(source));
      return;
    }

    final Item output;
    try {
      output = itemGenerator.generateImbuedItem(source, playerLevel(request.playerId, player));
    } catch (Throwable t) {
      log.error("[A1Q3] Imbue generation failed: player={} item={} code={}",
          request.playerId, source.id, source.code, t);
      return;
    }
    if (!player.data.getItems().replaceItem(source, output)) return;

    request.accept();
    event.dispatch(NativeQuestRewardEvent.granted(request.playerId,
        QuestId.A1Q3_MALUS, NativeQuestRewardEvent.CHARSI_IMBUE));
    log.info("[A1Q3] Item imbued: player={} item={} code={} ilvl={} quality={}",
        request.playerId, output.id, output.code, output.ilvl, output.quality);
  }

  static boolean isImbueable(Item item) {
    if (item == null || item.base == null || item.type == null || item.base.quest != 0) {
      return false;
    }
    if (!item.type.is(Type.WEAP) && !item.type.is(Type.ARMO)) return false;
    if ((item.flags & (Item.ITEMFLAG_SOCKETED | Item.ITEMFLAG_RUNEWORD)) != 0
        || item.socketsFilled != 0) return false;
    return item.quality == Quality.LOW
        || item.quality == Quality.NORMAL
        || item.quality == Quality.HIGH;
  }

  private int playerLevel(int playerId, Player player) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : player.data.getStats();
    StatRef level = attrs == null ? null : attrs.get(Stat.level, StatRef.obtain());
    return Math.max(1, level == null ? 1 : level.asInt());
  }
}
