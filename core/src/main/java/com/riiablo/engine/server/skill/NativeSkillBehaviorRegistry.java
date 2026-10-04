package com.riiablo.engine.server.skill;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.codec.excel.Skills;

/**
 * Closed registry of native skill behavior families.
 *
 * <p>This is the riiablo equivalent of dark-magic's exact-ID behavior
 * manifest.  Similar-looking callback numbers are not enough: the skill id,
 * start callback and do callback must all agree with a declaration before a
 * family is returned.  New skills should be added here with a focused test
 * and native evidence instead of being inferred from a neighboring row.
 */
public final class NativeSkillBehaviorRegistry {
  private static final String EVIDENCE = "d2moo-1.10f-row-and-srv-dispatch";
  private static final IntMap<NativeSkillBehavior> AMAZON = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> SORCERESS = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> PALADIN = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> NECROMANCER = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> BARBARIAN = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> DRUID = new IntMap<>();
  private static final IntMap<NativeSkillBehavior> ASSASSIN = new IntMap<>();

  static {
    // Amazon 6..35.  Callback numbers are the D2MOO SkillAma dispatch table;
    // missile names are retained as evidence for the later missile-family
    // resolver and are not used to infer support on their own.
    add(6,  "missile.arrow",                 0,  0, "magicarrow");
    add(7,  "missile.elemental-arrow",       4,  0, "firearrow");
    add(8,  "state.point-area",              0,  6);
    add(9,  "passive.stat-list",             0,  0);
    add(10, "melee.multi-hit",               5,  7);
    add(11, "missile.elemental-arrow-freeze",4,  0, "coldarrow");
    add(12, "missile.multishot",             4,  8, "multipleshotarrow", "multipleshotbolt");
    add(13, "passive.stat-list",             0,  0);
    add(14, "melee.elemental",               6,  2);
    add(15, "missile.poison-javelin",        4,  0, "poisonjav");
    add(16, "missile.impact-area",           4,  0, "explodingarrow");
    add(17, "state.point-area",              0,  6);
    add(18, "passive.stat-list",             0,  0);
    add(19, "melee.durability",              7,  2);
    add(20, "missile.elemental-javelin",     4,  0, "lightningjavelin");
    add(21, "missile.freeze",                4,  0, "icearrow");
    add(22, "missile.guided",                4, 10, "guidedarrow");
    add(23, "passive.stat-list",             0,  0);
    add(24, "melee.multibolt",               6, 11, "chargedstrikebolt");
    add(25, "missile.poison-cloud",          4,  0, "plaguejavelin");
    add(26, "missile.multishot",             8, 12, "strafearrow", "strafebolt");
    add(27, "missile.impact-area-periodic",  4,  0, "immolationarrow");
    add(28, "summon.decoy",                  0, 15);
    add(29, "passive.stat-list",             0,  0);
    add(30, "melee.multi-target",            9, 13);
    add(31, "missile.impact-area-freeze",   4,  0, "freezingarrow");
    add(32, "summon.valkyrie",               0, 16);
    add(33, "passive.stat-list",             0,  0);
    add(34, "melee.chain",                  10, 14, "lightningstrike");
    add(35, "missile.split",                 4,  0, "lightningfury");

    // Sorceress 36..65.  These are the exact 1.10f Skills.txt rows and the
    // SkillSor/D2MOO server dispatch callbacks.  The registry is deliberately
    // closed over IDs and callbacks: a custom table with a changed callback
    // must fail closed instead of being classified by its display name.
    addSorceress(36, "missile.straight",                 0,   0,  "firebolt");
    addSorceress(37, "passive.stat-list",                0,   0);
    addSorceress(38, "missile.multibolt",                0,  17,  "chargedbolt");
    addSorceress(39, "missile.straight",                 0,   0,  "icebolt");
    addSorceress(40, "state.self-timed",                0,  18);
    addSorceress(41, "missile.stream",                 11,  19,  "inferno");
    addSorceress(42, "aura.percent-life",               0,  20);
    addSorceress(43, "utility.push",                   12,  21);
    addSorceress(44, "missile.radial-freeze",           0,  22,  "frostnova");
    addSorceress(45, "missile.straight-freeze",         0,   0,  "iceblast");
    addSorceress(46, "state.ground-trail",              0,  23,  "blaze");
    addSorceress(47, "missile.straight-impact-area",    0,   0,  "fireball");
    addSorceress(48, "missile.radial",                  0,  22,  "nova");
    addSorceress(49, "missile.straight",                0,   0,  "lightning");
    addSorceress(50, "state.self-timed",                0,  18);
    addSorceress(51, "state.ground-area",               0,  24,  "firewallmaker", "firewall");
    addSorceress(52, "state.targeted-timed",            0,  25);
    addSorceress(53, "missile.chain",                   0,  26,  "chainlightning");
    addSorceress(54, "movement.point-relocate",         0,  27);
    addSorceress(55, "missile.straight-impact-area-freeze", 0, 0, "glacialspike");
    addSorceress(56, "missile.impact-area-periodic",    0,  28,  "meteorcenter", "meteor");
    addSorceress(57, "state.periodic-strike",          13,  29,  "thunderstorm");
    addSorceress(58, "state.self-timed",                0,  23);
    addSorceress(59, "missile.controller-area",         0,  28,  "blizzardcenter");
    addSorceress(60, "state.self-timed-retaliation",    0,  18,  "chillingarmorbolt");
    addSorceress(61, "passive.stat-list",               0,   0);
    addSorceress(62, "summon.hydra",                   14, 144);
    addSorceress(63, "passive.stat-list",               0,   0);
    addSorceress(64, "missile.controller-split",         0,   0,  "frozenorb");
    addSorceress(65, "passive.stat-list",               0,   0);

    // Paladin rows admitted by the existing native data and ECS contracts.
    // Keep this registration intentionally small until each remaining row has
    // an exact D2MOO dispatch and focused behavior evidence.
    addPaladin(112, "missile.blessed-hammer", 0, 73, "blessedhammer");
    addPaladin(114, "aura.holy-freeze",       0, 81);
    addPaladin(116, "state.conversion",      32, 79);
    addPaladin(117, "state.holy-shield",      36, 18);
    addPaladin(121, "missile.fist-of-heavens", 0, 80, "fistoftheheavensdelay");
    addPaladin(98,  "aura.selected-party-stat",      0, 65);
    addPaladin(99,  "aura.selected-party-periodic",  0, 65);
    addPaladin(100, "aura.selected-party-stat",      0, 65);
    addPaladin(103, "aura.selected-party-stat",      0, 65);
    addPaladin(104, "aura.selected-party-stat",      0, 65);
    addPaladin(105, "aura.selected-party-stat",      0, 65);
    addPaladin(108, "aura.selected-party-stat",      0, 65);
    addPaladin(109, "aura.selected-party-periodic",  0, 65);
    addPaladin(110, "aura.selected-party-stat",      0, 65);
    addPaladin(115, "aura.selected-party-stat",      0, 65);
    addPaladin(120, "aura.selected-party-periodic",  0, 65);
    addPaladin(124, "aura.selected-corpse-periodic", 0, 82);
    addPaladin(125, "aura.selected-party-stat",      0, 65);

    // Necromancer rows admitted by the corpse, summon, curse and poison
    // contracts.  The remaining rows stay unregistered until their native
    // callback and lifecycle evidence receives the same focused audit.
    addNecromancer(66, "curse.area",                  0, 30);
    addNecromancer(70, "summon.skeleton",            15, 31);
    addNecromancer(73, "melee.poison-dagger",        16, 32);
    addNecromancer(74, "corpse.explosion",           17, 55);
    addNecromancer(80, "summon.skeletal-mage",       15, 31);
    addNecromancer(83, "corpse.poison-explosion",    17, 63,
        "poisonexplosioncloud");
    addNecromancer(92, "missile.poison-nova",         0, 22, "poisonnova");
    addNecromancer(72, "state.point-area-curse",      0, 30);
    addNecromancer(75, "summon.golem",                0, 56);
    addNecromancer(85, "summon.golem",                0, 56);
    addNecromancer(90, "summon.golem",               20, 57);
    addNecromancer(94, "summon.golem",                0, 56);
    addNecromancer(95, "summon.targeted-corpse",     21, 58);

    // Barbarian rows covered by the war-cry, corpse and core combat audits.
    addBarbarian(130, "warcry.howl",            0, 22);
    addBarbarian(131, "corpse.find-potion",     33, 69);
    addBarbarian(137, "warcry.taunt",           0, 71);
    addBarbarian(138, "warcry.shout",           0, 68);
    addBarbarian(142, "corpse.find-item",       34, 72);
    addBarbarian(146, "warcry.battle-cry",      0, 68);
    addBarbarian(149, "warcry.battle-orders",   0, 68);
    addBarbarian(150, "corpse.grim-ward",       33, 75);
    addBarbarian(151, "melee.whirlwind",        38, 76);
    addBarbarian(152, "melee.berserk",          39, 2);
    addBarbarian(154, "warcry.war-cry",         0, 68);
    addBarbarian(155, "warcry.battle-command",  0, 68);

    // Druid rows covered by the shape, melee and elemental-area contracts.
    addDruid(223, "state.werewolf",       0, 116);
    addDruid(225, "missile.firestorm",    0, 117);
    addDruid(228, "state.werebear",       0, 116);
    addDruid(229, "missile.molten-boulder", 0, 0);
    addDruid(232, "melee.feral-rage",    56, 120);
    addDruid(233, "melee.maul",          56, 120);
    addDruid(234, "missile.fissure",      0, 28);
    addDruid(238, "melee.rabies",        57, 121, "rabiesplague");
    addDruid(239, "melee.fire-claws",    58, 2);
    addDruid(242, "melee.hunger",         0, 122);
    addDruid(243, "missile.shock-wave",   0, 8, "shockwave");
    addDruid(244, "missile.volcano",      0, 123);
    addDruid(248, "melee.fury",          37, 13);
    addDruid(249, "state.armageddon",     0, 124);
    addDruid(250, "state.hurricane",      0, 124);

    // Assassin 251..277 rows admitted by the dark-magic trap-family audit.
    // The 1.10f display names intentionally remain data-backed (Fire Trauma
    // and Shock Field differ from the expansion-era dark-magic names).  Trap
    // rows share SrvDo45, so exact IDs are required to keep their pet/lifecycle
    // contracts separate; a callback match alone must never infer a family.
    addAssassin(251, "missile.fire-trauma",       0,  0, "bomb in air");
    addAssassin(256, "missile.shock-field",       0, 43, "shock field in air");
    addAssassin(257, "trap.blade-sentinel",       0, 44, "blade creeper");
    addAssassin(261, "trap.charged-bolt-sentry",  0, 45);
    addAssassin(262, "trap.wake-of-fire-sentry",  0, 45);
    addAssassin(266, "missile.blade-fury",       26, 48, "bladefragment1");
    addAssassin(271, "trap.lightning-sentry",     0, 45);
    addAssassin(272, "trap.inferno-sentry",       0, 45);
    addAssassin(276, "trap.death-sentry",         0, 45);
    addAssassin(277, "state.blade-shield",       28, 54, "blade shield attachment");
  }

  private NativeSkillBehaviorRegistry() {}

  private static void add(int id, String family, int start, int done, String... missiles) {
    AMAZON.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addSorceress(int id, String family, int start, int done,
      String... missiles) {
    SORCERESS.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addPaladin(int id, String family, int start, int done,
      String... missiles) {
    PALADIN.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addNecromancer(int id, String family, int start, int done,
      String... missiles) {
    NECROMANCER.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addBarbarian(int id, String family, int start, int done,
      String... missiles) {
    BARBARIAN.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addDruid(int id, String family, int start, int done,
      String... missiles) {
    DRUID.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  private static void addAssassin(int id, String family, int start, int done,
      String... missiles) {
    ASSASSIN.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  /** Returns the exact Amazon declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveAmazon(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = AMAZON.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Returns the declaration for any currently registered native row. */
  public static NativeSkillBehavior resolve(Skills.Entry skill) {
    NativeSkillBehavior behavior = resolveAmazon(skill);
    if (behavior != null) return behavior;
    behavior = resolveSorceress(skill);
    if (behavior != null) return behavior;
    behavior = resolvePaladin(skill);
    if (behavior != null) return behavior;
    behavior = resolveNecromancer(skill);
    if (behavior != null) return behavior;
    behavior = resolveBarbarian(skill);
    if (behavior != null) return behavior;
    behavior = resolveDruid(skill);
    if (behavior != null) return behavior;
    return resolveAssassin(skill);
  }

  /** Returns the exact Sorceress declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveSorceress(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = SORCERESS.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact rows currently covered by the registry. */
  public static int amazonSize() { return AMAZON.size; }

  /** Number of exact Sorceress rows currently covered by the registry. */
  public static int sorceressSize() { return SORCERESS.size; }

  /** Returns the exact Paladin declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolvePaladin(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = PALADIN.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact Paladin rows currently covered by the registry. */
  public static int paladinSize() { return PALADIN.size; }

  /** Returns the exact Necromancer declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveNecromancer(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = NECROMANCER.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact Necromancer rows currently covered by the registry. */
  public static int necromancerSize() { return NECROMANCER.size; }

  /** Returns the exact Barbarian declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveBarbarian(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = BARBARIAN.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact Barbarian rows currently covered by the registry. */
  public static int barbarianSize() { return BARBARIAN.size; }

  /** Returns the exact Druid declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveDruid(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = DRUID.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact Druid rows currently covered by the registry. */
  public static int druidSize() { return DRUID.size; }

  /** Returns the exact Assassin declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveAssassin(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = ASSASSIN.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Number of exact Assassin rows currently covered by the registry. */
  public static int assassinSize() { return ASSASSIN.size; }
}
