package com.riiablo.save;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListReader;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.codec.excel.CharStats;
import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.Skills;
import com.riiablo.io.ByteInput;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import com.riiablo.util.BufferUtils;

// TODO: support pooling CharData for multiplayer
public class CharData implements ItemData.UpdateListener, Pool.Poolable {
  private static final String TAG = "CharData";
  private static final Logger log = LogManager.getLogger(CharData.class);
  private static final boolean DEBUG       = true;
  private static final boolean DEBUG_ITEMS = DEBUG && !true;

  private static final IntIntMap defaultSkills = new IntIntMap();
  static {
    defaultSkills.put(SkillCodes.attack, 1);
    defaultSkills.put(SkillCodes.kick, 1);
    //defaultSkills.put(SkillCodes.throw_, 1);
    defaultSkills.put(SkillCodes.unsummon, 1);
    //defaultSkills.put(SkillCodes.left_hand_throw, 1);
    defaultSkills.put(SkillCodes.left_hand_swing, 1);
  }

  public       String name;
  public       byte   charClass;
  public       int    flags;
  public       byte   level;
  public final int    hotkeys[] = new int[D2S.NUM_HOTKEYS];
  public final int    actions[][] = new int[D2S.NUM_ACTIONS][D2S.NUM_BUTTONS];
  public final byte   towns[] = new byte[D2S.NUM_DIFFS];
  public       int    mapSeed;
  public final byte   realmData[] = new byte[144];

  final MercData   mercData = new MercData();
  final short      questData[][][] = new short[Riiablo.NUM_DIFFS][Riiablo.NUM_ACTS][8];
  final int        waypointData[][] = new int[Riiablo.NUM_DIFFS][Riiablo.NUM_ACTS];
  final long       npcIntroData[] = new long[Riiablo.NUM_DIFFS];
  final long       npcReturnData[] = new long[Riiablo.NUM_DIFFS];
  final Attributes statData = Attributes.obtainLarge();
  final IntIntMap  skillData = new IntIntMap();
  final ItemData   itemData = new ItemData(statData, null);
        Item       golemItemData;

  private final PotionRecovery healthPotion = new PotionRecovery();
  private final PotionRecovery manaPotion = new PotionRecovery();
  private int potionSeed;

  public int diff;
  public boolean managed;
  public CharacterClass classId;

  final IntIntMap            skills = new IntIntMap();
  final Array<StatRef>       chargedSkills = new Array<>(false, 16);
  final Array<SkillListener> skillListeners = new Array<>(false, 16);

  @Deprecated
  private static final ItemReader ITEM_READER = new ItemReader(); // TODO: inject
  @Deprecated
  private static final StatListReader STAT_READER = new StatListReader(); // TODO: inject

  /** Constructs a managed instance. Used for local players with complete save data */
  public static CharData loadFromD2S(int diff, D2S d2s) {
    return new CharData().set(diff, true).load(d2s);
  }

  /** Constructs an unmanaged instance. Used for remote players with complete save data. */
  @SuppressWarnings("deprecation") // d2s writer not implemented yet -- stub deprecated to avoid usage
  public static CharData loadFromBuffer(int diff, ByteBuffer buffer) {
    byte[] bytes = BufferUtils.readRemaining(buffer);
    D2S d2s = D2SReader.INSTANCE.readComplete(bytes, STAT_READER, ITEM_READER);
    return new CharData().set(diff, false).load(d2s);
  }

  /**
   * @param managed whether or not this data is backed by a file
   */
  public static CharData obtain(int diff, boolean managed, String name, byte charClass) {
    return obtain().set(diff, managed, name, charClass);
  }

  /** Constructs an uninitialized CharData -- must be initialized via #set */
  public static CharData obtain() {
    return new CharData();
  }

  /** Constructs an unmanaged instance. Used for remote players with only partial save data. */
  public static CharData createRemote(String name, byte charClass) {
    return new CharData().set(Riiablo.NORMAL, false, name, charClass);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append(name).append(classId).append("level", level).build();
  }

  public CharData set(int diff, boolean managed) {
    this.diff    = diff;
    this.managed = managed;
    return this;
  }

  public CharData set(int diff, boolean managed, String name, byte charClass) {
    set(diff, managed);
    this.name      = name;
    this.charClass = charClass;
    classId = CharacterClass.get(charClass);
    flags   = D2S.FLAG_EXPANSION;
    level   = 1;
    Arrays.fill(hotkeys, D2S.HOTKEY_UNASSIGNED);
    // D2 stores the normal attack as skill id 0. A newly-created character
    // must still have an explicit left/right action selection; leaving these
    // slots at the Java default makes the UI look unassigned even though the
    // input path happens to interpret 0 as attack.
    for (int[] actions : actions) Arrays.fill(actions, SkillCodes.attack);
    // 新角色：mapSeed 必须在创建时设置，与 D2 一致。用于地图生成的随机数序列，保证每个角色地图不同。
    mapSeed = (int) (System.currentTimeMillis() & 0xFFFFFFFF);
    potionSeed = mapSeed ^ 0x51ED270B;
    return this;
  }

  /** Whether this character uses the expansion rules encoded in the D2S flags. */
  public boolean isExpansion() {
    return (flags & D2S.FLAG_EXPANSION) != 0;
  }

  CharData() {
    itemData.addUpdateListener(this);
  }

  public CharData clear() {
    reset();
    return this;
  }

  public CharData load(D2S d2s) {
    /**
     * FIXME: designed to call {@link D2SReader#readRemaining} on local clients
     *        because they will only have had their headers loaded. This is a
     *        problem because network clients already have their remaining data
     *        loaded, and this method shouldn't have access to the remaining
     *        bytes.
     */
    managed = true;
    if (!d2s.bodyRead()) { // FIXME: workaround -- D2GS doesn't have D2S files, but will when authoritative
      byte[] data = D2SWriterStub.getBytes(d2s.name);
      assert data != null : "d2s.bodyRead(" + d2s.bodyRead() + ") but data == null";
      ByteInput in = ByteInput.wrap(data);
      in.skipBytes(D2SReader96.HEADER_SIZE);
      D2SReader.INSTANCE.readRemaining(d2s, in, STAT_READER, ITEM_READER);
    }
    D2SReader.INSTANCE.copyTo(d2s, this);
    preprocessItems();
    itemData.addUpdateListener(this);
    return this;
  }

  private void preprocessItems() {
    itemData.preprocessItems();
    mercData.itemData.preprocessItems();
  }

  @Override
  public void reset() {
    softReset();
    name      = null;
    charClass = -1;
    classId   = null;
    flags     = 0;
    level     = 0;
    Arrays.fill(hotkeys, D2S.HOTKEY_UNASSIGNED);
    for (int i = 0, s = D2S.NUM_ACTIONS; i < s; i++) Arrays.fill(actions[i], 0);
    Arrays.fill(towns, (byte) 0);
    mapSeed   = 0;
    potionSeed = 0;
    Arrays.fill(realmData, (byte) 0);

    mercData.flags = 0;
    mercData.seed  = 0;
    mercData.name  = 0;
    mercData.type  = 0;
    mercData.xp    = 0;

    for (int i = 0, i0 = Riiablo.NUM_DIFFS; i < i0; i++) {
      for (int a = 0; a < Riiablo.NUM_ACTS; a++) Arrays.fill(questData[i][a], (short) 0);
      Arrays.fill(waypointData[i], 0);
      npcIntroData[i] = 0;
      npcReturnData[i] = 0;
    }
  }

  void softReset() {
    healthPotion.clear();
    manaPotion.clear();
    statData.base().clear();
    statData.reset();
    skillData.clear();
    itemData.clear();
    itemData.addUpdateListener(this);
    mercData.statData.base().clear();
    mercData.statData.reset();
    mercData.itemData.clear();
    golemItemData = null;

    skills.clear();
    chargedSkills.clear();
    skillListeners.clear();

    DifficultyLevels.Entry diff = Riiablo.files.DifficultyLevels.get(this.diff);
    StatListRef base = statData.base();
    base.put(Stat.strength, 0);
    base.put(Stat.energy, 0);
    base.put(Stat.dexterity, 0);
    base.put(Stat.vitality, 0);
    base.put(Stat.statpts, 0);
    base.put(Stat.newskills, 0);
    base.put(Stat.hitpoints, 0);
    base.put(Stat.maxhp, 0);
    base.put(Stat.mana, 0);
    base.put(Stat.maxmana, 0);
    base.put(Stat.stamina, 0);
    base.put(Stat.maxstamina, 0);
    base.put(Stat.level, 0);
    base.put(Stat.experience, 0);
    base.put(Stat.gold, 0);
    base.put(Stat.goldbank, 0);
    base.put(Stat.armorclass, 0);
    base.put(Stat.damageresist, 0);
    base.put(Stat.magicresist, 0);
    base.put(Stat.fireresist, diff.ResistPenalty);
    base.put(Stat.lightresist, diff.ResistPenalty);
    base.put(Stat.coldresist, diff.ResistPenalty);
    base.put(Stat.poisonresist, diff.ResistPenalty);
    base.put(Stat.maxfireresist, 75);
    base.put(Stat.maxlightresist, 75);
    base.put(Stat.maxcoldresist, 75);
    base.put(Stat.maxpoisonresist, 75);

    // TODO: set base merc stats based on hireling tables and level
    base = mercData.statData.base();
    base.put(Stat.strength, 0);
    base.put(Stat.energy, 0);
    base.put(Stat.dexterity, 0);
    base.put(Stat.vitality, 0);
    base.put(Stat.statpts, 0);
    base.put(Stat.newskills, 0);
    base.put(Stat.hitpoints, 0);
    base.put(Stat.maxhp, 0);
    base.put(Stat.mana, 0);
    base.put(Stat.maxmana, 0);
    base.put(Stat.stamina, 0);
    base.put(Stat.maxstamina, 0);
    base.put(Stat.level, 0);
    base.put(Stat.experience, 0);
    base.put(Stat.gold, 0);
    base.put(Stat.goldbank, 0);
    base.put(Stat.armorclass, 0);
    base.put(Stat.damageresist, 0);
    base.put(Stat.magicresist, 0);
    base.put(Stat.fireresist, diff.ResistPenalty);
    base.put(Stat.lightresist, diff.ResistPenalty);
    base.put(Stat.coldresist, diff.ResistPenalty);
    base.put(Stat.poisonresist, diff.ResistPenalty);
    base.put(Stat.maxfireresist, 75);
    base.put(Stat.maxlightresist, 75);
    base.put(Stat.maxcoldresist, 75);
    base.put(Stat.maxpoisonresist, 75);
  }

  public void preloadItems() {
    itemData.load();
    mercData.itemData.load();
  }

  public boolean isManaged() {
    return managed;
  }

  public byte[] serialize() {
    Validate.isTrue(isManaged(), "Cannot serialize unmanaged data");
    // Serialize the current authoritative CharData instead of returning the
    // bytes cached by D2SWriterStub when the file was first read.  This keeps
    // stat/skill/quest/item changes made during play in the next network or
    // disk save and makes the result a native 1.10f round-trip.
    return D2SWriter.INSTANCE.writeD2S(D2SWriter96.createD2S(this));
  }

  public int getHotkey(int button, int skill) {
    return ArrayUtils.indexOf(hotkeys, button == Input.Buttons.LEFT ? skill | D2S.HOTKEY_LEFT_MASK : skill);
  }

  public void setHotkey(int button, int skill, int index) {
    hotkeys[index] = button == Input.Buttons.LEFT ? skill | D2S.HOTKEY_LEFT_MASK : skill;
  }

  public int getAction(int button) {
    return getAction(itemData.alternate, button);
  }

  public int getAction(int alternate, int button) {
    return actions[alternate][button];
  }

  public void setAction(int button, int skill) {
    setAction(itemData.alternate, button, skill);
  }

  public void setAction(int alternate, int button, int skill) {
    int previous = actions[alternate][button];
    actions[alternate][button] = skill;
    if (previous != skill && Gdx.app != null) {
      Gdx.app.log(TAG, "[SKILL_SELECT] alternate=" + alternate
          + " button=" + button + " skill=" + skill
          + " previous=" + previous);
    }
  }

  public boolean hasMerc() {
    return mercData.seed != 0;
  }

  public MercData getMerc() {
    return mercData;
  }

  public short[] getQuests(int act) {
    return questData[diff][act];
  }

  public int getWaypoints(int act) {
    validateWaypointAct(act);
    return waypointData[diff][act];
  }

  /** Current difficulty used by authoritative multiplayer snapshots. */
  public int getDifficulty() {
    return diff;
  }

  /** Applies a complete waypoint mask received from the authoritative server. */
  public void setWaypointMask(int difficulty, int act, int mask) {
    if (difficulty < 0 || difficulty >= Riiablo.NUM_DIFFS) {
      throw new IllegalArgumentException("Invalid waypoint difficulty: " + difficulty);
    }
    validateWaypointAct(act);
    waypointData[difficulty][act] = mask;
  }

  public boolean isWaypointActivated(int act, int waypointNo) {
    int waypoint = getWaypointIndex(act, waypointNo);
    return (waypointData[diff][act] & (1 << waypoint)) != 0;
  }

  /**
   * Activates a waypoint for the current difficulty.
   *
   * @return {@code true} when the waypoint changed from inactive to active
   */
  public boolean activateWaypoint(int act, int waypointNo) {
    return activateWaypoint(diff, act, waypointNo);
  }

  public boolean activateWaypoint(int difficulty, int act, int waypointNo) {
    if (difficulty < 0 || difficulty >= Riiablo.NUM_DIFFS) {
      throw new IllegalArgumentException("Invalid waypoint difficulty: " + difficulty);
    }
    int waypoint = getWaypointIndex(act, waypointNo);
    int mask = 1 << waypoint;
    int previous = waypointData[difficulty][act];
    waypointData[difficulty][act] = previous | mask;
    return (previous & mask) == 0;
  }

  public static int getNumWaypoints(int act) {
    validateWaypointAct(act);
    return act == Riiablo.ACT4 ? 3 : 9;
  }

  /** Converts the global Levels.txt waypoint number to its act-local save bit. */
  public static int getWaypointIndex(int act, int waypointNo) {
    int count = getNumWaypoints(act);
    int offset = getWaypointOffset(act);
    int waypoint = waypointNo - offset;
    if (waypoint < 0 || waypoint >= count) {
      throw new IllegalArgumentException(
          "Invalid global waypoint " + waypointNo + " for act " + act
              + " (expected " + offset + ".." + (offset + count - 1) + ")");
    }
    return waypoint;
  }

  public static int getWaypointOffset(int act) {
    validateWaypointAct(act);
    switch (act) {
      case Riiablo.ACT1: return 0;
      case Riiablo.ACT2: return 9;
      case Riiablo.ACT3: return 18;
      case Riiablo.ACT4: return 27;
      case Riiablo.ACT5: return 30;
      default: throw new AssertionError(act);
    }
  }

  private static void validateWaypointAct(int act) {
    if (act < 0 || act >= Riiablo.NUM_ACTS) {
      throw new IllegalArgumentException("Invalid waypoint act: " + act);
    }
  }

  public long getNpcIntro() {
    return npcIntroData[diff];
  }

  public long getNpcReturn() {
    return npcReturnData[diff];
  }

  public boolean hasGolemItem() {
    return golemItemData != null;
  }

  public Item getGolemItem() {
    return golemItemData;
  }

  public Attributes getStats() {
    return statData;
  }

  /**
   * Clamps live resources to their resolved maxima and mirrors them into the
   * persistent list used by D2S. Combat changes the aggregate list, while
   * save/load and progression snapshots also touch the base list; keeping the
   * two current values aligned prevents a later XP or item refresh from
   * restoring stale life.
   */
  public void synchronizeCurrentResources() {
    synchronizeCurrentResource(Stat.hitpoints, Stat.maxhp);
    synchronizeCurrentResource(Stat.mana, Stat.maxmana);
    synchronizeCurrentResource(Stat.stamina, Stat.maxstamina);
  }

  private void synchronizeCurrentResource(short currentStat, short maximumStat) {
    StatRef current = statData.aggregate().get(currentStat, StatRef.obtain());
    StatRef maximum = statData.aggregate().get(maximumStat, StatRef.obtain());
    if (current == null || maximum == null) return;
    float currentValue = current.asFixed();
    float maximumValue = maximum.asFixed();
    float value = Math.max(0f, Math.min(currentValue, maximumValue));
    if (currentValue != value) {
      StatRef base = statData.base().get(currentStat, StatRef.obtain());
      log.warn("[RESOURCE_CLAMP] character={} stat={} current={} maximum={} base={}",
          name, currentStat, currentValue, maximumValue,
          base == null ? "missing" : Float.toString(base.asFixed()));
    }
    statData.aggregate().put(currentStat, value);
    statData.base().put(currentStat, value);
  }

  /**
   * Creates and places the new-character items declared by CharStats.txt.
   * Placement follows D2Game's PLAYER_CreateStartItem rules: belt-compatible
   * items are placed in the belt first, declared body slots are equipped, and
   * all remaining items are packed into the character inventory.
   */
  public void initializeStartItems(CharStats.Entry charStats) {
    if (charStats == null) throw new IllegalArgumentException("charStats cannot be null");
    if (itemData.itemData.size != 0) {
      throw new IllegalStateException("Starting items can only be initialized for an empty inventory");
    }

    itemData.charStats = charStats;
    ItemGenerator generator = new ItemGenerator();
    boolean[][] occupied = new boolean[4][10];
    int beltSlot = 0;
    int itemOrdinal = 0;
    int startSkill = findSkillId(charStats.StartSkill);

    for (int slot = 0; slot < charStats.item.length; slot++) {
      String code = charStats.item[slot];
      int count = parseStartItemCount(charStats.itemcount[slot]);
      if (code == null || code.isEmpty() || "0".equals(code) || count <= 0) continue;

      BodyLoc bodyLoc = parseBodyLoc(charStats.itemloc[slot]);
      for (int copy = 0; copy < count; copy++) {
        int id = mapSeed + (++itemOrdinal);
        Item item = generator.generateStartItem(code, id, slot == 0 ? startSkill : -1);

        if (item.typeEntry.Beltable && beltSlot < 4) {
          itemData.add(item);
          itemData.setLocation(item, Location.BELT);
          item.storeLoc = StoreLoc.NONE;
          item.bodyLoc = BodyLoc.NONE;
          item.gridX = (byte) beltSlot++;
          item.gridY = 0;
        } else if (bodyLoc != BodyLoc.NONE) {
          itemData.equip(bodyLoc, item);
        } else {
          int position = findInventoryPosition(occupied, item.base.invwidth, item.base.invheight);
          if (position < 0) {
            throw new IllegalStateException("No inventory space for starting item " + code);
          }
          int x = position % 10;
          int y = position / 10;
          int index = itemData.add(item);
          itemData.store(StoreLoc.INVENTORY, index, x, y);
          occupy(occupied, x, y, item.base.invwidth, item.base.invheight);
        }
      }
    }

    itemData.updateStats();
  }

  private static int findSkillId(String skillName) {
    if (skillName == null || skillName.isEmpty()) return -1;
    for (Skills.Entry skill : Riiablo.files.skills) {
      if (skillName.equalsIgnoreCase(skill.skill)) return skill.Id;
    }
    return -1;
  }

  private static int parseStartItemCount(String value) {
    if (value == null || value.isEmpty()) return 0;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      throw new IllegalArgumentException("Invalid CharStats starting item count: " + value);
    }
  }

  private static BodyLoc parseBodyLoc(String value) {
    if (value == null || value.isEmpty()) return BodyLoc.NONE;
    switch (value.toLowerCase(Locale.ROOT)) {
      case "head": return BodyLoc.HEAD;
      case "neck": return BodyLoc.NECK;
      case "tors": return BodyLoc.TORS;
      case "rarm": return BodyLoc.RARM;
      case "larm": return BodyLoc.LARM;
      case "rrin": return BodyLoc.RRIN;
      case "lrin": return BodyLoc.LRIN;
      case "belt": return BodyLoc.BELT;
      case "feet": return BodyLoc.FEET;
      case "glov": return BodyLoc.GLOV;
      case "rarm2": return BodyLoc.RARM2;
      case "larm2": return BodyLoc.LARM2;
      default: throw new IllegalArgumentException("Unknown CharStats body location: " + value);
    }
  }

  private static int findInventoryPosition(boolean[][] occupied, int width, int height) {
    for (int y = 0; y <= occupied.length - height; y++) {
      for (int x = 0; x <= occupied[y].length - width; x++) {
        boolean available = true;
        for (int dy = 0; dy < height && available; dy++) {
          for (int dx = 0; dx < width; dx++) {
            if (occupied[y + dy][x + dx]) {
              available = false;
              break;
            }
          }
        }
        if (available) return y * 10 + x;
      }
    }
    return -1;
  }

  private static void occupy(boolean[][] occupied, int x, int y, int width, int height) {
    for (int dy = 0; dy < height; dy++) {
      for (int dx = 0; dx < width; dx++) {
        occupied[y + dy][x + dx] = true;
      }
    }
  }

  public void update() {
    // Rebuild equipment-derived stats before applying character-derived values.
    // Calling onUpdated() directly would add dexterity / 4 to the existing
    // aggregate on every invocation.
    itemData.updateStats();
  }

  @Override
  public void onUpdated(ItemData itemData) {
    assert itemData.stats == statData;

    // ItemData can be mutated while a character is still being assembled
    // (for example when a legacy/remote record has no stat section yet).
    // Native characters always contain these core vitals; skip the derived
    // refresh until the character record is complete.
    if (statData.get(Stat.stamina) == null
        || statData.get(Stat.maxstamina) == null
        || statData.get(Stat.hitpoints) == null
        || statData.get(Stat.maxhp) == null
        || statData.get(Stat.mana) == null
        || statData.get(Stat.maxmana) == null
        || statData.get(Stat.dexterity) == null
        || statData.get(Stat.armorclass) == null) {
      log.debug("Skipping derived stat refresh for incomplete character stat list");
      return;
    }

    // This appears to be hard-coded in the original client
    int dex = statData.get(Stat.dexterity).asInt();
    StatRef armorclass = statData.get(Stat.armorclass);
    armorclass.add(dex / 4);
    armorclass.forceUnmodified();

    skills.clear();
    skills.putAll(skillData);
    skills.putAll(defaultSkills);
    Item LARM = itemData.getEquipped(BodyLoc.LARM);
    Item RARM = itemData.getEquipped(BodyLoc.RARM);
    if ((LARM != null && LARM.typeEntry.Throwable)
     || (RARM != null && RARM.typeEntry.Throwable)) {
      skills.put(SkillCodes.throw_, 1);
      if (classId == CharacterClass.BARBARIAN) {
        skills.put(SkillCodes.left_hand_throw, 1);
      }
    }
    IntArray inventoryItems = itemData.getStore(StoreLoc.INVENTORY);
    int[] cache = inventoryItems.items;
    for (int i = 0, s = inventoryItems.size, j; i < s; i++) {
      j = cache[i];
      Item item = itemData.getItem(j);
      if (item.type.is(Type.BOOK) || item.type.is(Type.SCRO)) {
        if (item.base.code.equalsIgnoreCase("ibk")) {
          skills.getAndIncrement(SkillCodes.book_of_identify, 0, item.attrs.get(Stat.quantity).asInt());
        } else if (item.base.code.equalsIgnoreCase("isc")) {
          skills.getAndIncrement(SkillCodes.scroll_of_identify, 0, 1);
        } else if (item.base.code.equalsIgnoreCase("tbk")) {
          skills.getAndIncrement(SkillCodes.book_of_townportal, 0, item.attrs.get(Stat.quantity).asInt());
        } else if (item.base.code.equalsIgnoreCase("tsc")) {
          skills.getAndIncrement(SkillCodes.scroll_of_townportal, 0, 1);
        }
      }
    }

    chargedSkills.clear();
    for (StatRef stat : statData.remaining()) {
      switch (stat.id()) {
        case Stat.item_nonclassskill:
          skills.getAndIncrement(stat.encodedParams(), 0, stat.asInt());
          break;
        case Stat.item_charged_skill:
          chargedSkills.add(stat.copy());
          break;
        default:
          // do nothing
      }
    }
    notifySkillChanged(skills, chargedSkills);
  }

  public int getSkill(int skill) {
    return skills.get(skill, 0);
  }

  /** Returns the learned/base level, excluding item and default-skill bonuses. */
  public int getBaseSkillLevel(int skill) {
    return skillData.get(skill, 0);
  }

  /** Returns the unspent skill points currently available to this character. */
  public int getAvailableSkillPoints() {
    StatRef points = statData.aggregate().get(Stat.newskills);
    if (points == null) points = statData.base().get(Stat.newskills);
    return points == null ? 0 : Math.max(0, points.asInt());
  }

  /** Returns unspent attribute points, preferring the presentation aggregate. */
  public int getAvailableStatPoints() {
    StatRef points = statData.aggregate().get(Stat.statpts);
    if (points == null) points = statData.base().get(Stat.statpts);
    return points == null ? 0 : Math.max(0, points.asInt());
  }

  /** Updates an unspent point counter in both the save/base and presentation lists. */
  public void setAvailablePoints(short stat, int value) {
    int safeValue = Math.max(0, value);
    statData.base().put(stat, safeValue);
    statData.aggregate().put(stat, safeValue);
  }

  /**
   * Sets the base level of a learned skill and refreshes all skill listeners.
   * Item-granted and class-default skills remain layered by onUpdated(); this
   * method is intentionally limited to the saved character skill data.
   */
  public boolean setSkillLevel(int skill, int level) {
    if (skill < 0 || level < 0) return false;
    int oldBase = skillData.get(skill, 0);
    int oldEffective = skills.get(skill, 0);
    int nonBaseBonus = Math.max(0, oldEffective - oldBase);
    skillData.put(skill, level);
    // Keep item/default bonuses intact while making the newly learned level
    // visible immediately. A later equipment refresh recomputes this exactly.
    skills.put(skill, level + nonBaseBonus);
    notifySkillChanged(skills, chargedSkills);
    return true;
  }

  /** Notifies UI/network listeners after non-item character data changes. */
  public void notifySkillChanged() {
    notifySkillChanged(skills, chargedSkills);
  }

  public ItemData getItems() {
    return itemData;
  }

//  @Override
  public void groundToCursor(Item item) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "groundToCursor " + item);
    itemData.pickup(item);
  }

//  @Override
  public void cursorToGround() {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "cursorToGround");
    itemData.drop();
  }

  public void itemToCursor(int i) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "itemToCursor " + i);
    itemData.pickup(i);
  }

//  @Override
  public void storeToCursor(int i) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "storeToCursor " + i);
    itemToCursor(i);
  }

//  @Override
  public void cursorToStore(StoreLoc storeLoc, int x, int y) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "cursorToStore " + storeLoc + "," + x + "," + y);
    itemData.storeCursor(storeLoc, x, y);
  }

//  @Override
  public void swapStoreItem(int i, StoreLoc storeLoc, int x, int y) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "swapStoreItem " + i + "," + storeLoc + "," + x + "," + y);
    // Native inventory behavior tops up a compatible arrow/bolt quiver
    // instead of exchanging the two item objects. Any excess remains on the
    // cursor so it can be placed elsewhere.
    if (storeLoc == StoreLoc.INVENTORY && itemData.mergeCursorIntoStoredAmmo(i)) return;
    cursorToStore(storeLoc, x, y);
    storeToCursor(i);
  }

  public void bodyToCursor(BodyLoc bodyLoc) {
    bodyToCursor(bodyLoc, false);
  }

  public void cursorToBody(BodyLoc bodyLoc) {
    cursorToBody(bodyLoc, false);
  }

  public void swapBodyItem(BodyLoc bodyLoc) {
    swapBodyItem(bodyLoc, false);
  }

//  @Override
  public void bodyToCursor(BodyLoc bodyLoc, boolean merc) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "bodyToCursor " + bodyLoc + "," + (merc ? "merc" : "player"));
    if (itemData.cursor != ItemData.INVALID_ITEM) {
      log.warn("[ITEM_MOVE_REJECTED] operation=bodyToCursor bodyLoc={} merc={} reason=cursor_occupied",
          bodyLoc, merc);
      return;
    }
    Item item;
    if (merc) {
      int i = mercData.itemData.unequip(bodyLoc);
      if (i == ItemData.INVALID_ITEM) {
        log.warn("[ITEM_MOVE_REJECTED] operation=bodyToCursor bodyLoc={} merc=true reason=empty_slot", bodyLoc);
        return;
      }
      itemData.cursor = itemData.add(item = mercData.itemData.remove(i));
    } else {
      itemData.cursor = itemData.unequip(bodyLoc);
      if (itemData.cursor == ItemData.INVALID_ITEM) {
        log.warn("[ITEM_MOVE_REJECTED] operation=bodyToCursor bodyLoc={} merc=false reason=empty_slot", bodyLoc);
        return;
      }
      item = itemData.getItem(itemData.cursor);
    }
    itemData.setLocation(item, Location.CURSOR);
  }

//  @Override
  public void cursorToBody(BodyLoc bodyLoc, boolean merc) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "cursorToBody " + bodyLoc + "," + (merc ? "merc" : "player"));
    if (itemData.cursor == ItemData.INVALID_ITEM) {
      log.warn("[ITEM_MOVE_REJECTED] operation=cursorToBody bodyLoc={} merc={} reason=empty_cursor",
          bodyLoc, merc);
      return;
    }
    // Some UI paths use CURSOR_TO_BODY even when the destination is already
    // occupied.  Preserve native quiver-merge behavior for that path too.
    if (!merc && itemData.mergeCursorIntoEquippedAmmo(bodyLoc)) return;
    if (merc) {
      Item item = itemData.getItem(itemData.cursor);
      itemData.remove(itemData.cursor);
      mercData.itemData.equip(bodyLoc, item);
    } else {
      itemData.equip(bodyLoc, itemData.cursor);
    }
    itemData.cursor = ItemData.INVALID_ITEM;
  }

  /**
   * FIXME: originally worked as an aggregate call on {@link #cursorToBody(BodyLoc, boolean)} and
   *        {@link #bodyToCursor(BodyLoc, boolean)}, and while that worked fine programically to
   *        pass the assertions within {@link ItemData}, {@link ItemData.LocationListener#onChanged}
   *        was being called out of order for setting the cursor, causing the cursor to be unset
   *        within the UI immediately after being changed.
   */
//  @Override
  public void swapBodyItem(BodyLoc bodyLoc, boolean merc) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "swapBodyItem " + bodyLoc + "," + (merc ? "merc" : "player"));

    if (itemData.cursor == ItemData.INVALID_ITEM) {
      log.warn("[ITEM_MOVE_REJECTED] operation=swapBodyItem bodyLoc={} merc={} reason=empty_cursor",
          bodyLoc, merc);
      return;
    }

    // Dragging a compatible arrow/bolt quiver onto the equipped quiver is a
    // native quantity merge.  Keep this before the regular swap path so a
    // partially consumed equipped stack is topped up instead of replaced.
    if (!merc && itemData.mergeCursorIntoEquippedAmmo(bodyLoc)) return;

    ItemData equippedItems = merc ? mercData.itemData : itemData;
    if (equippedItems.getSlot(bodyLoc) == null) {
      log.warn("[ITEM_MOVE_RECOVERED] operation=swapBodyItem bodyLoc={} merc={} reason=empty_slot action=cursorToBody",
          bodyLoc, merc);
      cursorToBody(bodyLoc, merc);
      return;
    }

    // #bodyToCursor(BodyLoc,boolean)
    Item newCursorItem;
    int newCursor;
    if (merc) {
      int i = mercData.itemData.unequip(bodyLoc);
      newCursor = itemData.add(newCursorItem = mercData.itemData.remove(i));
    } else {
      newCursor = itemData.unequip(bodyLoc);
      newCursorItem = itemData.getItem(newCursor);
    }

    // #cursorToBody(BodyLoc,boolean)
    if (merc) {
      Item item = itemData.getItem(itemData.cursor);
      itemData.remove(itemData.cursor);
      mercData.itemData.equip(bodyLoc, item);
      if (newCursor >= itemData.cursor) newCursor--; // removing item invalidated the index
    } else {
      itemData.equip(bodyLoc, itemData.cursor);
    }

    itemData.cursor = newCursor;
    itemData.setLocation(newCursorItem, Location.CURSOR);
  }

//  @Override
  public void beltToCursor(int i) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "beltToCursor");
    itemToCursor(i);
  }

//  @Override
  public void cursorToBelt(int x, int y) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "cursorToBelt");
    assert itemData.cursor != ItemData.INVALID_ITEM;
    int i = itemData.cursor;
    Item item = itemData.getItem(i);
    // Keep the cursor untouched when a stale UI/network command targets a
    // belt row that the currently equipped belt does not provide.
    if (!itemData.canStoreInBelt(item, x, y)) return;
    itemData.cursor = ItemData.INVALID_ITEM;
    item.gridX = (byte) x;
    item.gridY = (byte) y;
    itemData.setLocation(item, Location.BELT);
  }

  /**
   * FIXME: originally worked as an aggregate call on {@link #cursorToBelt(int, int)} and
   *        {@link #beltToCursor(int)}, and while that worked fine programically to pass the
   *        assertions within {@link ItemData}, {@link ItemData.LocationListener#onChanged}
   *        was being called out of order for setting the cursor, causing the cursor to be unset
   *        within the UI immediately after being changed.
   */
//  @Override
  public void swapBeltItem(int i) {
    if (DEBUG_ITEMS) Gdx.app.log(TAG, "swapBeltItem");

    // #beltToCursor(int)
    Item newCursorItem = itemData.getItem(i);

    // #cursorToBelt(int,int)
    Item item = itemData.getItem(itemData.cursor);
    item.gridX = newCursorItem.gridX;
    item.gridY = newCursorItem.gridY;
    itemData.setLocation(item, Location.BELT);
    itemData.cursor = ItemData.INVALID_ITEM;

    itemData.pickup(i);
  }

  /** Uses the lowest potion in one of the four native belt columns. */
  public boolean useBeltPotion(int column) {
    Item potion = itemData.getBeltPotion(column);
    if (potion == null || potion.type == null || !potion.type.is(Type.POTI)) return false;
    if (!applyPotion(potion)) return false;
    return itemData.consumeBeltPotion(potion);
  }

  /** Uses one potion directly from the character inventory. */
  public boolean useInventoryPotion(Item potion) {
    if (potion == null || potion.type == null || !potion.type.is(Type.POTI)
        || potion.location != Location.STORED || potion.storeLoc != StoreLoc.INVENTORY) return false;
    return applyPotion(potion) && itemData.consumeStoredItem(potion);
  }

  /** Removes one unit after an authoritative cursor-potion use succeeds. */
  public boolean consumeCursorPotion(Item potion) {
    return itemData.consumeCursorItem(potion);
  }

  private boolean applyPotion(Item potion) {
    Misc.Entry misc = potion.base instanceof Misc.Entry ? (Misc.Entry) potion.base : null;
    int pSpell = misc == null ? 0 : misc.pSpell;
    boolean applied;
    if (pSpell == 3) {
      applied = applyTimedPotion(potion.code, misc);
    } else if (pSpell == 5) {
      applied = applyRejuvenationPotion(potion.code, misc);
    } else {
      // Classification by code is only a compatibility fallback for old
      // compact item snapshots that did not preserve all Misc.txt fields.
      String code = potion.code == null ? "" : potion.code.toLowerCase(Locale.ROOT);
      if (code.startsWith("hp") || code.startsWith("mp")) {
        applied = applyTimedPotion(code, misc);
      } else if ("rvs".equals(code) || "rvl".equals(code)) {
        applied = applyRejuvenationPotion(code, misc);
      } else {
        // Stamina, antidote and thawing potions require their own timed states.
        return false;
      }
    }
    if (!applied) return false;
    return true;
  }

  private boolean applyTimedPotion(String code, Misc.Entry misc) {
    String stat = first(misc == null ? null : misc.stat);
    boolean health = "hpregen".equalsIgnoreCase(stat);
    boolean mana = "manarecovery".equalsIgnoreCase(stat);
    if (!health && !mana) {
      String normalized = code == null ? "" : code.toLowerCase(Locale.ROOT);
      health = normalized.startsWith("hp");
      mana = normalized.startsWith("mp");
    }
    if (!health && !mana) return false;

    int amount = parsePositive(first(misc == null ? null : misc.calc));
    int frames = parsePositive(misc == null ? null : misc.len);
    if (amount <= 0 || frames <= 0) {
      int tier = potionTier(code);
      amount = health
          ? new int[] {0, 30, 60, 100, 180, 320}[tier]
          : new int[] {0, 20, 40, 80, 150, 250}[tier];
      frames = health
          ? new int[] {0, 192, 160, 171, 192, 256}[tier]
          : 128;
    }

    amount = health ? bonusLifeByClass(amount) : bonusManaByClass(amount);
    short chanceStat = health ? Stat.vitality : Stat.energy;
    int chanceAttribute = statData.aggregate().getValue(chanceStat, 0);
    if (rollPotionDouble(chanceAttribute)) amount *= 2;
    (health ? healthPotion : manaPotion).add(amount << 8, frames);
    return true;
  }

  private boolean applyRejuvenationPotion(String code, Misc.Entry misc) {
    int healthPercent = percentageFor(misc, "hitpoints");
    int manaPercent = percentageFor(misc, "mana");
    if (healthPercent <= 0 && manaPercent <= 0) {
      int percent = "rvl".equalsIgnoreCase(code) ? 100 : 35;
      healthPercent = percent;
      manaPercent = percent;
    }
    if (healthPercent > 0) restorePotionPercent(Stat.hitpoints, Stat.maxhp, healthPercent);
    if (manaPercent > 0) restorePotionPercent(Stat.mana, Stat.maxmana, manaPercent);
    return healthPercent > 0 || manaPercent > 0;
  }

  /** Advances native HEALTHPOT/MANAPOT state by one 25 Hz game frame. */
  public void tickPotionRecovery() {
    tickPotionRecovery(healthPotion, Stat.hitpoints, Stat.maxhp);
    tickPotionRecovery(manaPotion, Stat.mana, Stat.maxmana);
  }

  private void tickPotionRecovery(
      PotionRecovery recovery, short currentStat, short maximumStat) {
    if (recovery.framesRemaining <= 0) return;
    restorePotionStatEncoded(currentStat, maximumStat, recovery.rateEncoded);
    if (--recovery.framesRemaining == 0) recovery.rateEncoded = 0;
  }

  private void restorePotionPercent(short currentStat, short maximumStat, int percent) {
    StatRef maximum = statData.aggregate().get(maximumStat, StatRef.obtain());
    if (maximum == null) return;
    long amount = (long) maximum.encodedValues() * percent / 100L;
    restorePotionStatEncoded(currentStat, maximumStat,
        (int) Math.min(Integer.MAX_VALUE, amount));
  }

  private void restorePotionStatEncoded(
      short currentStat, short maximumStat, int amountEncoded) {
    StatRef current = statData.aggregate().get(currentStat, StatRef.obtain());
    StatRef maximum = statData.aggregate().get(maximumStat, StatRef.obtain());
    if (current == null || maximum == null) return;
    int value = (int) Math.min((long) maximum.encodedValues(),
        (long) current.encodedValues() + Math.max(0, amountEncoded));
    current.setEncoded(value);
  }

  private static String first(String[] values) {
    return values == null || values.length == 0 ? null : values[0];
  }

  private static int parsePositive(String value) {
    if (value == null) return 0;
    try {
      return Math.max(0, Integer.parseInt(value.trim()));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static int potionTier(String code) {
    int tier = code != null && code.length() > 2 && Character.isDigit(code.charAt(2))
        ? code.charAt(2) - '0' : 1;
    return Math.max(1, Math.min(5, tier));
  }

  private static int percentageFor(Misc.Entry misc, String requestedStat) {
    if (misc == null || misc.stat == null || misc.calc == null) return 0;
    int count = Math.min(misc.stat.length, misc.calc.length);
    for (int i = 0; i < count; i++) {
      if (requestedStat.equalsIgnoreCase(misc.stat[i])) return parsePositive(misc.calc[i]);
    }
    return 0;
  }

  private int bonusLifeByClass(int value) {
    if (classId == CharacterClass.BARBARIAN) return value * 2;
    if (classId == CharacterClass.AMAZON || classId == CharacterClass.PALADIN
        || classId == CharacterClass.ASSASSIN) return value + (value >> 1);
    return value;
  }

  private int bonusManaByClass(int value) {
    if (classId == CharacterClass.SORCERESS || classId == CharacterClass.NECROMANCER
        || classId == CharacterClass.DRUID) return value * 2;
    if (classId == CharacterClass.AMAZON || classId == CharacterClass.PALADIN
        || classId == CharacterClass.ASSASSIN) return value + (value >> 1);
    return value;
  }

  private boolean rollPotionDouble(int attribute) {
    if (attribute <= 0) return false;
    int roll = nextPotionRandom() % 100;
    int attributeRoll = nextPotionRandom() % attribute;
    return roll < attributeRoll / 2;
  }

  private int nextPotionRandom() {
    if (potionSeed == 0) {
      potionSeed = (mapSeed != 0 ? mapSeed : name == null ? 1 : name.hashCode()) ^ 0x51ED270B;
    }
    potionSeed = potionSeed * 1103515245 + 12345;
    return potionSeed & 0x7FFFFFFF;
  }

  private static final class PotionRecovery {
    int rateEncoded;
    int framesRemaining;

    void add(int amountEncoded, int frames) {
      int combinedFrames = framesRemaining + frames;
      long combinedAmount = (long) framesRemaining * rateEncoded + amountEncoded;
      rateEncoded = combinedFrames <= 0 ? 0
          : (int) Math.min(Integer.MAX_VALUE, combinedAmount / combinedFrames);
      framesRemaining = Math.max(0, combinedFrames);
    }

    void clear() {
      rateEncoded = 0;
      framesRemaining = 0;
    }
  }

  public static class MercData {
    public int   flags;
    public int   seed;
    public short name;
    public short type;
    public long  xp;

    final Attributes statData = Attributes.obtainLarge();
    final ItemData   itemData = new ItemData(statData, null);

    public Attributes getStats() {
      return statData;
    }

    public ItemData getItems() {
      return itemData;
    }

    public String getName() {
      return String.format("0x%04X", name);
    }
  }

  public void clearListeners() {
    itemData.equipListeners.clear();
    mercData.itemData.equipListeners.clear();
    itemData.alternateListeners.clear();
    mercData.itemData.alternateListeners.clear();
    skillListeners.clear();
  }

  public boolean addSkillListener(SkillListener l) {
    skillListeners.add(l);
    return true;
  }

  private void notifySkillChanged(IntIntMap skills, Array<StatRef> chargedSkills) {
    for (SkillListener l : skillListeners) l.onChanged(this, skills, chargedSkills);
  }

  public interface SkillListener {
    void onChanged(CharData client, IntIntMap skills, Array<StatRef> chargedSkills);
  }
}
