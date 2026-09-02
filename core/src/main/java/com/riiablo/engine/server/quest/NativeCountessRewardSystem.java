package com.riiablo.engine.server.quest;

import java.util.List;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.NativeCountessQuestEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.item.TreasureClassResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Expands the Countess' SuperUniques.txt treasure class into ground items. */
@Wire(failOnNull = false)
public class NativeCountessRewardSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(NativeCountessRewardSystem.class);
  private static final int COUNTESS_SUPER_UNIQUE_ID = 6;

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Monster> mMonster;
  protected ItemGenerator itemGenerator;
  @Wire(name = "factory")
  protected EntityFactory factory;

  @Subscribe
  public void onCountessKilled(NativeCountessQuestEvent event) {
    if (event == null || event.countessId < 0 || !mPosition.has(event.countessId)
        || factory == null || itemGenerator == null || Riiablo.files == null
        || Riiablo.files.SuperUniques == null || Riiablo.files.TreasureClassEx == null) {
      log.warn("[A1Q5] Countess treasure service unavailable: victim={} factory={} generator={} files={}",
          event == null ? -1 : event.countessId, factory != null,
          itemGenerator != null, Riiablo.files != null);
      return;
    }

    SuperUniques.Entry countess = findCountess();
    String treasureClass = treasureClass(countess, event.difficulty);
    if (treasureClass == null || treasureClass.isEmpty()) {
      log.warn("[A1Q5] Countess treasure class missing: difficulty={}", event.difficulty);
      return;
    }

    Position position = mPosition.get(event.countessId);
    int itemLevel = monsterLevel(event.countessId, event.difficulty);
    RandomXS128 random = new RandomXS128(seed(event, position));
    List<TreasureClassResolver.Drop> drops = new TreasureClassResolver(
        Riiablo.files.TreasureClassEx).resolve(treasureClass, itemLevel,
            random::nextInt, TreasureClassResolver.NATIVE_MAX_DROPS);

    int created = 0;
    for (int i = 0; i < drops.size(); i++) {
      TreasureClassResolver.Drop drop = drops.get(i);
      String code = TreasureClassResolver.baseToken(drop.token);
      if (createItem(code, itemLevel, position.position.x, position.position.y,
          i, random) >= 0) {
        created++;
      }
    }
    log.info("[A1Q5] Countess native TC resolved: victim={} tc={} difficulty={} rolled={} created={}",
        event.countessId, treasureClass, event.difficulty, drops.size(), created);
  }

  private SuperUniques.Entry findCountess() {
    SuperUniques.Entry indexed = Riiablo.files.SuperUniques.get(COUNTESS_SUPER_UNIQUE_ID);
    if (indexed != null && indexed.hcIdx == COUNTESS_SUPER_UNIQUE_ID) return indexed;
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && entry.hcIdx == COUNTESS_SUPER_UNIQUE_ID) return entry;
    }
    return null;
  }

  static String treasureClass(SuperUniques.Entry countess, int difficulty) {
    if (countess == null) return null;
    switch (Math.max(0, Math.min(difficulty, 2))) {
      case 1: return countess.TCNightmare;
      case 2: return countess.TCHell;
      default: return countess.TC;
    }
  }

  private int monsterLevel(int entityId, int difficulty) {
    if (!mMonster.has(entityId)) return 1;
    MonStats.Entry stats = mMonster.get(entityId).monstats;
    if (stats == null || stats.Level == null || stats.Level.length == 0) return 1;
    int index = Math.max(0, Math.min(difficulty, stats.Level.length - 1));
    return Math.max(1, com.riiablo.engine.server.NativeDataTables.value(stats.Level, index, 1));
  }

  private int createItem(String code, int itemLevel, float x, float y,
      int index, RandomXS128 random) {
    if (code == null || code.isEmpty()) return -1;
    try {
      Item item = itemGenerator.generate(code);
      item.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      item.version = Item.VERSION_110;
      item.quality = Quality.NORMAL;
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      float angle = index * MathUtils.PI2 / Math.max(1, TreasureClassResolver.NATIVE_MAX_DROPS);
      float radius = 1.5f + random.nextFloat() * 1.5f;
      int entityId = factory.createItem(item,
          x + MathUtils.cos(angle) * radius, y + MathUtils.sin(angle) * radius);
      item.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[A1Q5] Countess item creation failed: code={} ilvl={}", code, itemLevel, t);
      return -1;
    }
  }

  private static long seed(NativeCountessQuestEvent event, Position position) {
    long seed = 0xC0A17E55L;
    seed = 31 * seed + event.countessId;
    seed = 31 * seed + event.difficulty;
    seed = 31 * seed + Float.floatToRawIntBits(position.position.x);
    return 31 * seed + Float.floatToRawIntBits(position.position.y);
  }
}
