package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.item.ItemQuality;
import com.riiablo.engine.server.item.LootManager;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Applies server-authoritative rewards after a monster dies.
 *
 * <p>Death can be reported by both the melee and missile paths in the same
 * frame.  The victim set makes reward processing idempotent, so experience
 * and loot cannot be granted twice when both paths observe the final hit.</p>
 */
public class DeathRewardSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(DeathRewardSystem.class);

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  @Wire(name = "factory")
  protected EntityFactory factory;

  /** ItemGenerator is registered as a world system by D2GS. */
  protected ItemGenerator itemGenerator;

  private final LootManager lootManager = new LootManager();
  private final IntSet rewardedVictims = new IntSet();

  @Subscribe
  public void onDeath(DeathEvent event) {
    if (event == null || event.victim < 0) return;
    if (!mMonster.has(event.victim)) return;
    if (!mPlayer.has(event.killer) || mPlayer.get(event.killer).data == null) {
      log.debug("[DEATH_REWARD] skip non-player killer: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }
    if (!rewardedVictims.add(event.victim)) {
      log.warn("[DEATH_REWARD] duplicate death ignored: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }

    Monster monster = mMonster.get(event.victim);
    Player player = mPlayer.get(event.killer);
    Position position = mPosition.has(event.victim) ? mPosition.get(event.victim) : null;
    if (position == null) {
      log.warn("[DEATH_REWARD] victim has no position: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }

    int difficulty = Math.max(0, Math.min(2, player.data.diff));
    int monsterLevel = monsterLevel(monster.monstats, difficulty);
    LootManager.LootConfig config = new LootManager.LootConfig();
    config.monsterLevel = monsterLevel;
    config.areaLevel = monsterLevel;
    config.difficulty = difficulty;
    config.isBoss = monster.monstats != null
        && (monster.monstats.boss || monster.monstats.SetBoss || monster.monstats.primeevil);
    config.isSuperUnique = monster.monstats != null && monster.monstats.SetBoss;
    config.isElite = config.isBoss || config.isSuperUnique;
    config.playerCount = 1;
    if (mAttributesWrapper.has(event.killer)) {
      Attributes attrs = mAttributesWrapper.get(event.killer).attrs;
      lootManager.applyPlayerBonuses(attrs, config);
    }

    LootManager.LootResult result = lootManager.calculateLoot(config);
    int createdItems = 0;
    for (int i = 0; i < result.getItemCount(); i++) {
      String code = result.itemCodes.get(i);
      int quality = result.itemQualities.get(i);
      int itemLevel = result.itemLevels.get(i);
      int itemId = createItem(code, quality, itemLevel, position.position.x, position.position.y);
      if (itemId >= 0) createdItems++;
      log.debug("[DEATH_REWARD] item: killer={}, victim={}, code={}, rolledQuality={}, "
              + "ilvl={}, entity={}", event.killer, event.victim, code, quality, itemLevel, itemId);
    }

    int goldEntity = createGold(result.goldAmount, monsterLevel,
        position.position.x, position.position.y);
    log.info("[DEATH_REWARD] killer={}, victim={}, monster={}, level={}, difficulty={}, "
            + "boss={}, gold={}, goldEntity={}, itemsRolled={}, itemsCreated={}",
        event.killer, event.victim,
        monster.monstats != null ? monster.monstats.Id : "unknown", monsterLevel, difficulty,
        config.isBoss, result.goldAmount, goldEntity, result.getItemCount(), createdItems);
  }

  private int monsterLevel(MonStats.Entry stats, int difficulty) {
    if (stats == null || stats.Level == null || stats.Level.length == 0) return 1;
    return Math.max(1, stats.Level[Math.min(difficulty, stats.Level.length - 1)]);
  }

  private int createItem(String code, int rolledQuality, int itemLevel, float x, float y) {
    if (factory == null || itemGenerator == null || code == null || code.isEmpty()) {
      log.warn("[DEATH_REWARD] item creation unavailable: factory={}, generator={}, code={}",
          factory != null, itemGenerator != null, code);
      return -1;
    }
    try {
      Item item = itemGenerator.generate(code);
      item.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      item.quality = safeQuality(rolledQuality);
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      int entityId = factory.createItem(item, x + MathUtils.random(-2f, 2f),
          y + MathUtils.random(-2f, 2f));
      // Item ids are serialized from the same object held by the component.
      // The entity id is a stable server-side fallback when no item id service
      // is available yet.
      item.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[DEATH_REWARD] item creation failed: code={}, quality={}, ilvl={}",
          code, rolledQuality, itemLevel, t);
      return -1;
    }
  }

  private int createGold(int amount, int itemLevel, float x, float y) {
    if (amount <= 0 || factory == null || itemGenerator == null) return -1;
    try {
      Item gold = itemGenerator.generate("gld");
      gold.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      gold.quality = Quality.NORMAL;
      gold.flags |= Item.ITEMFLAG_IDENTIFIED;
      gold.attrs.base().put(Stat.quantity, amount);
      int entityId = factory.createItem(gold, x + MathUtils.random(-1f, 1f),
          y + MathUtils.random(-1f, 1f));
      gold.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[DEATH_REWARD] gold creation failed: amount={}, level={}", amount, itemLevel, t);
      return -1;
    }
  }

  /**
   * Rare/crafted/set/unique quality data is not generated by ItemGenerator
   * yet.  Downgrade those rolls to MAGIC until affix/set/unique data is wired,
   * rather than emitting an item that ItemWriter cannot serialize.
   */
  private Quality safeQuality(int rolledQuality) {
    switch (rolledQuality) {
      case ItemQuality.INFERIOR: return Quality.LOW;
      case ItemQuality.SUPERIOR: return Quality.HIGH;
      case ItemQuality.MAGIC: return Quality.MAGIC;
      case ItemQuality.NORMAL: return Quality.NORMAL;
      case ItemQuality.SET:
      case ItemQuality.RARE:
      case ItemQuality.UNIQUE:
      case ItemQuality.CRAFT:
      case ItemQuality.TEMPERED:
        log.warn("[DEATH_REWARD] quality data unavailable; downgrade rolled quality {} to MAGIC",
            ItemQuality.getName(rolledQuality));
        return Quality.MAGIC;
      default: return Quality.NORMAL;
    }
  }
}
