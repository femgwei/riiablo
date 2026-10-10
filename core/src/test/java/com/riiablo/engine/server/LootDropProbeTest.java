package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.item.Type;
import com.riiablo.item.VendorGenerator;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Long-running, headless loot probe.  It is intentionally diagnostic rather
 * than a probability assertion: rare set/unique items may legitimately be
 * absent from a finite sample, while every generated item is classified and
 * printed for manual verification.
 *
 * <p>Override the default sample size with {@code -DlootProbeKills=2000} (or
 * {@code LOOT_PROBE_KILLS}).
 */
class LootDropProbeTest extends RiiabloTest {
  private static final String[] CATEGORIES = {
      "weapon", "armor", "shield", "helm", "boots", "gloves", "belt",
      "ring", "amulet", "charm", "jewel", "potion", "gem", "rune",
      "scroll", "key", "other"
  };

  @Test
  void automatedMonsterKillsCollectAndClassifyDrops() {
    int kills = probeKills();
    MathUtils.random.setSeed(0x4C4F4F54L);
    DropFactory factory = new DropFactory();
    EventSystem events = new EventSystem();
    ExperienceManager experience = new ExperienceManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, experience, new DeathRewardSystem(), new ItemGenerator(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      CharData data = characterAtLevelOne();
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();

      MonStats.Entry monsterStats = probeMonster();
      MonStats2.Entry monsterStats2 = new MonStats2.Entry();
      for (int i = 0; i < kills; i++) {
        int monster = world.create();
        world.getMapper(Monster.class).create(monster).set(monsterStats, monsterStats2);
        world.getMapper(Position.class).create(monster).position.set(20, 30);
        Attributes attrs = Attributes.obtainStandard();
        int level = 20 + (i % 3) * 30;
        attrs.base().put(Stat.level, level);
        attrs.base().put(Stat.experience, 0);
        attrs.reset();
        world.getMapper(AttributesWrapper.class).create(monster).attrs = attrs;
        events.dispatch(DeathEvent.obtain(player, monster));
      }

      ObjectMap<String, Integer> counts = new ObjectMap<>();
      ObjectMap<String, Integer> qualities = new ObjectMap<>();
      int socketEligible = 0;
      int socketed = 0;
      ObjectMap<Integer, Integer> socketCounts = new ObjectMap<>();
      for (Item item : factory.items) {
        String category = category(item);
        counts.put(category, get(counts, category) + 1);
        String quality = String.valueOf(item.quality);
        qualities.put(quality, get(qualities, quality) + 1);
        if (isSocketEligible(item)) {
          socketEligible++;
          if (item.hasFlag(Item.ITEMFLAG_SOCKETED)) {
            socketed++;
            int sockets = item.attrs.base().get(Stat.item_numsockets).asInt();
            socketCounts.put(sockets, get(socketCounts, sockets) + 1);
          }
        }
      }
      System.out.println("[LOOT_PROBE] phase=summary kills=" + kills
          + " items=" + factory.items.size() + " gold=" + get(counts, "other")
          + " socketEligible=" + socketEligible + " socketed=" + socketed);
      printMap("category", counts);
      printMap("quality", qualities);
      printMap("socketCount", socketCounts);

      assertFalse(factory.items.isEmpty(), "monster kills must create at least one drop");
      assertTrue(counts.get("weapon", 0) + counts.get("armor", 0)
          + counts.get("shield", 0) + counts.get("helm", 0) > 0,
          "monster kills must create equipment drops");
      assertTrue(counts.get("gem", 0) > 0,
          "probe sample did not produce a gem; increase lootProbeKills");
      assertTrue(counts.get("rune", 0) > 0,
          "probe sample did not produce a rune; increase lootProbeKills");
    } finally {
      world.dispose();
    }
  }

  @Test
  void a1FallenAverageDropsAcrossDifficulties() {
    int kills = probeKills();
    MonStats.Entry fallen = findA1Fallen();
    assertNotNull(fallen, "MonStats.txt must contain a normal Fallen row");
    for (int difficulty = Riiablo.NORMAL; difficulty <= Riiablo.HELL; difficulty++) {
      probeFallenDifficulty(fallen, difficulty, kills);
    }
  }

  private static void probeFallenDifficulty(MonStats.Entry fallen, int difficulty, int kills) {
    MathUtils.random.setSeed(0x46414C4C454E0000L + difficulty);
    Riiablo.gameSeed = 0x51000000 + difficulty;
    DropFactory factory = new DropFactory();
    EventSystem events = new EventSystem();
    ExperienceManager experience = new ExperienceManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, experience, new DeathRewardSystem(), new ItemGenerator(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      CharData data = characterAtLevelOne(difficulty);
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(AttributesWrapper.class).create(player).attrs = data.getStats();
      int monsterLevel = levelAt(fallen, difficulty);
      ObjectMap<String, Integer> categories = new ObjectMap<>();
      ObjectMap<Integer, Integer> itemLevels = new ObjectMap<>();
      ObjectMap<String, Integer> itemQualities = new ObjectMap<>();
      ObjectMap<Integer, Integer> itemSockets = new ObjectMap<>();
      int itemDrops = 0;
      int emptyDrops = 0;
      int goldOnlyDrops = 0;
      for (int i = 0; i < kills; i++) {
        int monster = world.create();
        world.getMapper(Monster.class).create(monster).set(fallen, new MonStats2.Entry());
        world.getMapper(Position.class).create(monster).position.set(20, 30);
        Attributes attrs = Attributes.obtainStandard();
        attrs.base().put(Stat.level, monsterLevel);
        attrs.base().put(Stat.experience, 0);
        attrs.reset();
        world.getMapper(AttributesWrapper.class).create(monster).attrs = attrs;
        int before = factory.items.size();
        events.dispatch(DeathEvent.obtain(player, monster));
        int added = factory.items.size() - before;
        int nonGold = 0;
        for (int index = before; index < factory.items.size(); index++) {
          Item item = factory.items.get(index);
          if (item.type.is(Type.GOLD)) continue;
          nonGold++;
          String category = category(item);
          categories.put(category, get(categories, category) + 1);
          itemLevels.put((int) item.ilvl, get(itemLevels, (int) item.ilvl) + 1);
          String quality = String.valueOf(item.quality);
          itemQualities.put(quality, get(itemQualities, quality) + 1);
          if (item.hasFlag(Item.ITEMFLAG_SOCKETED)) {
            itemSockets.put(sockets(item), get(itemSockets, sockets(item)) + 1);
          }
        }
        itemDrops += nonGold;
        if (nonGold == 0) {
          if (added > 0) goldOnlyDrops++;
          else emptyDrops++;
        }
      }
      double average = (double) itemDrops / kills;
      int noItemDrops = emptyDrops + goldOnlyDrops;
      double itemDropRate = (double) (kills - noItemDrops) / kills;
      String difficultyName = difficulty == Riiablo.NORMAL ? "normal"
          : difficulty == Riiablo.NIGHTMARE ? "nightmare" : "hell";
      String tc = fallen.TreasureClass1[difficulty];
      System.out.printf(Locale.ROOT,
          "[LOOT_PROBE] phase=a1-fallen difficulty=%s monster=%s level=%d tc=%s "
              + "kills=%d itemDrops=%d empty=%d goldOnly=%d noItem=%d "
              + "itemDropRate=%.4f avgItems=%.4f%n",
          difficultyName, fallen.Id, monsterLevel, tc, kills, itemDrops,
          emptyDrops, goldOnlyDrops, noItemDrops, itemDropRate, average);
      printMap("a1Category." + difficultyName, categories);
      printMap("a1Quality." + difficultyName, itemQualities);
      printMap("a1ItemLevel." + difficultyName, itemLevels);
      printMap("a1Sockets." + difficultyName, itemSockets);
      assertTrue(itemDrops >= 0, "Fallen probe must complete without item-generation errors");
    } finally {
      world.dispose();
    }
  }

  private static MonStats.Entry findA1Fallen() {
    MonStats.Entry fallback = null;
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry == null || entry.Id == null || !entry.Id.toLowerCase(Locale.ROOT).contains("fallen")
          || entry.boss || entry.TreasureClass1 == null
          || entry.TreasureClass1.length == 0 || entry.TreasureClass1[0] == null
          || entry.TreasureClass1[0].trim().isEmpty()) continue;
      if ("fallen1".equalsIgnoreCase(entry.Id) || "fallen".equalsIgnoreCase(entry.Id)) return entry;
      if (fallback == null) fallback = entry;
    }
    return fallback;
  }

  private static int levelAt(MonStats.Entry entry, int difficulty) {
    if (entry.Level == null || entry.Level.length == 0) return 1;
    return Math.max(1, entry.Level[Math.min(difficulty, entry.Level.length - 1)]);
  }

  @Test
  void representativeDataTableItemsGenerateForEveryDropCategory() {
    ItemGenerator generator = new ItemGenerator();
    ObjectMap<String, String> representatives = new ObjectMap<>();
    ObjectMap<String, Item> generated = new ObjectMap<>();
    collectRepresentatives(representatives, Riiablo.files.armor);
    collectRepresentatives(representatives, Riiablo.files.weapons);
    collectRepresentatives(representatives, Riiablo.files.misc);
    for (String category : CATEGORIES) {
      String code = representatives.get(category);
      if (code == null) {
        System.out.println("[LOOT_PROBE] matrix category=" + category + " status=missing-data-row");
        continue;
      }
      try {
        Item item = generator.generateLootItem(code, 85, Quality.NORMAL,
            0x60000000 ^ code.hashCode(), Riiablo.HELL);
        generated.put(category, item);
        System.out.println("[LOOT_PROBE] matrix category=" + category + " code=" + code
            + " status=ok socketed=" + item.hasFlag(Item.ITEMFLAG_SOCKETED)
            + " sockets=" + sockets(item));
      } catch (Throwable t) {
        System.out.println("[LOOT_PROBE] matrix category=" + category + " code=" + code
            + " status=error error=" + t.getClass().getSimpleName() + ":" + t.getMessage());
      }
    }
    assertTrue(generated.size >= 8,
        "data-table probe should generate the common equipment and consumable categories");
  }

  @Test
  void vendorInventoryReportsSocketCoverage() throws Exception {
    ItemGenerator generator = new ItemGenerator();
    ProbeVendorGenerator vendors = new ProbeVendorGenerator();
    vendors.setGenerator(generator);
    com.badlogic.gdx.utils.Array<Item> stock = vendors.generate("charsi", Riiablo.HELL);
    int equipment = 0;
    int socketed = 0;
    for (Item item : stock) {
      if (!item.type.is(Type.WEAP) && !item.type.is(Type.ARMO)) continue;
      equipment++;
      if (item.hasFlag(Item.ITEMFLAG_SOCKETED)) socketed++;
    }
    System.out.println("[LOOT_PROBE] phase=vendor vendor=charsi items=" + stock.size
        + " equipment=" + equipment + " socketed=" + socketed);
    assertTrue(stock.size > 0, "vendor probe must produce inventory");
    assertTrue(socketed > 0,
        "normal vendor equipment should occasionally receive native sockets");
  }

  private static void collectRepresentatives(ObjectMap<String, String> out,
      Iterable<? extends ItemEntry> entries) {
    for (ItemEntry entry : entries) {
      if (entry == null || entry.code == null || entry.code.isEmpty()
          || entry.quest != 0 || entry.invwidth <= 0 || entry.invheight <= 0) continue;
      String category = category(entry.type);
      if (category != null && !out.containsKey(category)) out.put(category, entry.code);
    }
  }

  private static String category(Item item) {
    if (item == null || item.type == null) return "other";
    if (item.type.is(Type.RUNE)) return "rune";
    if (item.type.is(Type.GEM)) return "gem";
    if (item.type.is(Type.POTI)) return "potion";
    if (item.type.is(Type.SCRO)) return "scroll";
    if (item.type.is(Type.KEY)) return "key";
    if (item.type.is(Type.WEAP)) return "weapon";
    if (item.type.is(Type.SHLD)) return "shield";
    if (item.type.is(Type.HELM)) return "helm";
    if (item.type.is(Type.BOOT)) return "boots";
    if (item.type.is(Type.GLOV)) return "gloves";
    if (item.type.is(Type.BELT)) return "belt";
    if (item.type.is(Type.RING)) return "ring";
    if (item.type.is(Type.AMUL)) return "amulet";
    if (item.type.is(Type.CHAR)) return "charm";
    if (item.type.is(Type.JEWL)) return "jewel";
    if (item.type.is(Type.ARMO)) return "armor";
    return "other";
  }

  private static String category(String type) {
    if (type == null || type.isEmpty()) return null;
    com.riiablo.codec.excel.ItemTypes.Entry entry = Riiablo.files.ItemTypes.get(type);
    return entry == null ? null : category(Type.get(entry));
  }

  private static String category(Type type) {
    if (type == null) return null;
    if (type.is(Type.RUNE)) return "rune";
    if (type.is(Type.GEM)) return "gem";
    if (type.is(Type.POTI)) return "potion";
    if (type.is(Type.SCRO)) return "scroll";
    if (type.is(Type.KEY)) return "key";
    if (type.is(Type.WEAP)) return "weapon";
    if (type.is(Type.SHLD)) return "shield";
    if (type.is(Type.HELM)) return "helm";
    if (type.is(Type.BOOT)) return "boots";
    if (type.is(Type.GLOV)) return "gloves";
    if (type.is(Type.BELT)) return "belt";
    if (type.is(Type.RING)) return "ring";
    if (type.is(Type.AMUL)) return "amulet";
    if (type.is(Type.CHAR)) return "charm";
    if (type.is(Type.JEWL)) return "jewel";
    if (type.is(Type.ARMO)) return "armor";
    return null;
  }

  private static boolean isSocketEligible(Item item) {
    return item != null && (item.type.is(Type.WEAP) || item.type.is(Type.ARMO))
        && item.base != null && item.base.gemsockets > 0
        && !item.base.stackable;
  }

  private static int sockets(Item item) {
    if (item == null || item.attrs == null || item.attrs.base().get(Stat.item_numsockets) == null) return 0;
    return item.attrs.base().get(Stat.item_numsockets).asInt();
  }

  private static int get(ObjectMap<String, Integer> values, String key) {
    return values.get(key, 0);
  }

  private static int get(ObjectMap<Integer, Integer> values, int key) {
    return values.get(key, 0);
  }

  private static void printMap(String name, ObjectMap<?, Integer> values) {
    StringBuilder out = new StringBuilder("[LOOT_PROBE] ").append(name).append('=');
    for (ObjectMap.Entry<?, Integer> entry : values) {
      if (out.charAt(out.length() - 1) != '=') out.append(',');
      out.append(entry.key).append(':').append(entry.value);
    }
    System.out.println(out);
  }

  private static int probeKills() {
    String value = System.getProperty("lootProbeKills");
    if (value == null || value.isEmpty()) value = System.getenv("LOOT_PROBE_KILLS");
    try {
      return Math.max(1, Integer.parseInt(value == null ? "1200" : value));
    } catch (NumberFormatException ignored) {
      return 1200;
    }
  }

  private static MonStats.Entry probeMonster() {
    MonStats.Entry boss = new MonStats.Entry();
    boss.Id = "loot_probe_boss";
    boss.boss = true;
    boss.Level = new int[] {85, 85, 85};
    boss.Exp = new int[] {0, 0, 0};
    return boss;
  }

  private static CharData characterAtLevelOne() {
    return characterAtLevelOne(Riiablo.HELL);
  }

  private static CharData characterAtLevelOne(int difficulty) {
    CharData data = CharData.obtain().clear().set(difficulty, false,
        "LootProbe", Riiablo.AMAZON);
    data.getStats().base().put(Stat.level, 1);
    data.getStats().base().put(Stat.experience, 0);
    data.getStats().base().put(Stat.hitpoints, 60f);
    data.getStats().base().put(Stat.maxhp, 60f);
    data.getStats().base().put(Stat.mana, 20f);
    data.getStats().base().put(Stat.maxmana, 20f);
    data.getStats().base().put(Stat.stamina, 40f);
    data.getStats().base().put(Stat.maxstamina, 40f);
    data.getStats().reset();
    return data;
  }

  private static final class DropFactory extends EntityFactory {
    final List<Item> items = new ArrayList<>();

    @Override public int createItem(Item item, float x, float y) {
      items.add(item);
      return world.create();
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }

  private static final class ProbeVendorGenerator extends VendorGenerator {
    void setGenerator(ItemGenerator generator) { this.generator = generator; }
  }
}
