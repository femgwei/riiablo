package com.riiablo.screen.panel;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.item.Item;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.ItemController;
import com.riiablo.widget.Label;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native-style portrait strip for the hireling and all living player summons. */
public final class MercenaryHud extends WidgetGroup implements Disposable {
  private static final float WIDTH = 56f, SLOT_GAP = 0f, HEIGHT = 70f;
  private static final float ICON_Y = 19f, BAR_Y = 63f;
  private static final String ICON_ROOT = "data\\global\\ui\\HIREABLES\\";
  private static final String UNNAMED = "UNNAMED";
  private static final float HEALTH_GREEN_THRESHOLD = 2f / 3f;
  private static final float HEALTH_YELLOW_THRESHOLD = 1f / 3f;

  private enum SummonKind {
    RAVEN(5, "Raven", "Raven.dc6"), WOLF(10, "Wolf", "Wolf.dc6"),
    SPIRIT(20, "Spirit", "OakSage.dc6"), VINE(30, "Vine", "Vines.dc6"),
    BEAR(40, "Bear", "Bear.dc6"), VALKYRIE(50, "Valkyrie", "valkarieicon.dc6"),
    DECOY(60, "Decoy", "amazonicon.dc6"), SKELETON(70, "Skeleton", "skeletonicon.dc6"),
    SKELETON_MAGE(80, "Skeleton Mage", "skeletonmageicon.dc6"), GOLEM(90, "Golem", "golemicon.dc6"),
    REVIVE(100, "Revived", "revivedicon.dc6"), SHADOW(110, "Shadow", "ShadowAssassin.dc6"),
    HYDRA(120, "Hydra", "sorceressicon.dc6");
    final int druidPriority; final String fallbackName; final String iconName;
    SummonKind(int p, String n, String i) { druidPriority = p; fallbackName = n; iconName = i; }
    static SummonKind from(String type) {
      switch (PetType.canonical(type)) {
        case "raven": return RAVEN;
        case "spiritwolf": case "fenris": return WOLF;
        case "totem": return SPIRIT;
        case "vine": return VINE;
        case "grizzly": return BEAR;
        case "valkyrie": return VALKYRIE;
        case "decoy": return DECOY;
        case "skeleton": return SKELETON;
        case "skeletonmage": return SKELETON_MAGE;
        case "golem": return GOLEM;
        case "revive": return REVIVE;
        case "shadowwarrior": case "shadowmaster": return SHADOW;
        case "hydra": return HYDRA;
        default: return null;
      }
    }
  }
  private static final class Slot {
    SummonKind kind; AssetDescriptor<DC6> icon; String name; int representativeId = -1;
    int count; int skillOrder = Integer.MAX_VALUE; float life; float maxLife; boolean mercenary;
  }

  private final Map<SummonKind, AssetDescriptor<DC6>> summonIcons =
      new LinkedHashMap<SummonKind, AssetDescriptor<DC6>>();
  private final AssetDescriptor<DC6>[] mercenaryIcons;
  private final Texture fill;
  private final Label tooltip, feedback;
  private final List<Label> slotNames = new ArrayList<Label>(), slotCounts = new ArrayList<Label>();
  private final List<Slot> slots = new ArrayList<Slot>();
  private final ItemController itemController;
  private EntitySubscription mercenaries, summonedPets;
  private int mercenaryId = -1, mercenarySlotIndex = -1;
  private float life, maxLife, feedbackRemaining;
  private boolean hovered;

  @SuppressWarnings("unchecked")
  public MercenaryHud(ItemController itemController) {
    this.itemController = itemController; setSize(WIDTH, HEIGHT); setTouchable(Touchable.enabled);
    mercenaryIcons = new AssetDescriptor[4];
    String[] mercNames = {"rogueicon.dc6", "act2hireableicon.dc6", "act3hireableicon.dc6", "barbhirable_icon.dc6"};
    for (int i = 0; i < mercNames.length; i++) mercenaryIcons[i] = descriptor(mercNames[i]);
    for (SummonKind kind : SummonKind.values()) summonIcons.put(kind, descriptor(kind.iconName));
    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888); pixmap.setColor(Color.WHITE); pixmap.fill();
    fill = new Texture(pixmap); pixmap.dispose();
    for (int i = 0; i < SummonKind.values().length + 1; i++) {
      Label name = new Label("", Riiablo.fonts.font16, Color.WHITE); name.setAlignment(Align.center);
      name.setTouchable(Touchable.disabled); name.setVisible(false); addActor(name); slotNames.add(name);
      Label count = new Label("", Riiablo.fonts.font16, Color.WHITE); count.setAlignment(Align.center);
      count.setTouchable(Touchable.disabled); count.setVisible(false); addActor(count); slotCounts.add(count);
    }
    tooltip = new Label("", Riiablo.fonts.font16, Color.WHITE); tooltip.setPosition(0, -35); tooltip.setSize(300, 32);
    tooltip.setAlignment(Align.left); tooltip.setTouchable(Touchable.disabled); tooltip.setVisible(false); addActor(tooltip);
    feedback = new Label("", Riiablo.fonts.font16, Color.WHITE); feedback.setPosition(-38, HEIGHT + 2); feedback.setSize(132, 16);
    feedback.setAlignment(Align.center); feedback.setTouchable(Touchable.disabled); feedback.setVisible(false); addActor(feedback);
    addListener(new ClickListener(Input.Buttons.LEFT) {
      @Override public void enter(InputEvent e, float x, float y, int p, com.badlogic.gdx.scenes.scene2d.Actor from) { updateHover(x); }
      @Override public void exit(InputEvent e, float x, float y, int p, com.badlogic.gdx.scenes.scene2d.Actor to) { hovered = false; tooltip.setVisible(false); }
      @Override public boolean mouseMoved(InputEvent e, float x, float y) { updateHover(x); return true; }
      @Override public void clicked(InputEvent e, float x, float y) { if (isMercenaryPoint(x, y)) useCursorPotion(); }
    });
    addListener(new ClickListener(Input.Buttons.RIGHT) {
      @Override public void clicked(InputEvent e, float x, float y) {
        if (isMercenaryPoint(x, y) && Riiablo.game != null && Riiablo.game.hirelingPanel != null) {
          Riiablo.game.setLeftPanel(Riiablo.game.hirelingPanel); e.handle();
        }
      }
    });
  }

  private AssetDescriptor<DC6> descriptor(String name) {
    AssetDescriptor<DC6> d = new AssetDescriptor<DC6>(ICON_ROOT + name, DC6.class, DC6Loader.DC6Parameters.COMBINE);
    Riiablo.assets.load(d); return d;
  }
  public boolean useCursorPotion() {
    Item item = Riiablo.cursor == null ? null : Riiablo.cursor.getItem();
    if (item == null || itemController == null || mercenaryId < 0) return false;
    if (maxLife > 0 && life >= maxLife) { showTemporaryMessage("暂时还不用，谢谢"); return false; }
    return itemController.useCursorPotionOnMercenary();
  }
  public void showTemporaryMessage(String message) { feedback.setText(message == null ? "" : message); feedbackRemaining = 1.5f; feedback.setVisible(message != null && !message.isEmpty()); }
  public boolean containsStagePoint(float stageX, float stageY) { Vector2 p = stageToLocalCoordinates(new Vector2(stageX, stageY)); return isMercenaryPoint(p.x, p.y); }
  @Override public void act(float delta) {
    super.act(delta); if (feedbackRemaining > 0 && (feedbackRemaining -= delta) <= 0) { feedbackRemaining = 0; feedback.setVisible(false); } refreshState();
  }

  private void refreshState() {
    slots.clear(); mercenaryId = -1; mercenarySlotIndex = -1; life = maxLife = 0;
    if (mercenaries == null && Riiablo.engine != null) mercenaries = Riiablo.engine.getAspectSubscriptionManager().get(Aspect.all(Mercenary.class, AttributesWrapper.class));
    if (summonedPets == null && Riiablo.engine != null) summonedPets = Riiablo.engine.getAspectSubscriptionManager().get(Aspect.all(SummonedPet.class, AttributesWrapper.class));
    if (Riiablo.game == null || Riiablo.engine == null) { setVisible(false); hideSlotLabels(); return; }
    collectMercenary(); collectSummons();
    if (findSlot(SummonKind.BEAR) != null) removeSlot(SummonKind.WOLF);
    Collections.sort(slots, new Comparator<Slot>() {
      @Override public int compare(Slot a, Slot b) {
        if (a.mercenary != b.mercenary) return a.mercenary ? -1 : 1; if (a.mercenary) return 0;
        if (isDruidFixedKind(a.kind) && isDruidFixedKind(b.kind) && a.kind.druidPriority != b.kind.druidPriority)
          return a.kind.druidPriority < b.kind.druidPriority ? -1 : 1;
        if (a.skillOrder != b.skillOrder) return a.skillOrder < b.skillOrder ? -1 : 1;
        return a.kind.druidPriority == b.kind.druidPriority ? 0 : (a.kind.druidPriority < b.kind.druidPriority ? -1 : 1);
      }
    });
    for (int i = 0; i < slots.size(); i++) if (slots.get(i).mercenary) mercenarySlotIndex = i;
    setSize(slots.size() * WIDTH + Math.max(0, slots.size() - 1) * SLOT_GAP, HEIGHT); updateSlotLabels(); setVisible(!slots.isEmpty());
  }
  private void collectMercenary() {
    if (mercenaries == null) return; IntBag entities = mercenaries.getEntities(); int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) { int id = ids[i]; Mercenary merc = Riiablo.engine.getMapper(Mercenary.class).get(id);
      if (merc == null || merc.ownerId != Riiablo.game.player) continue;
      UnitLifecycle l = Riiablo.engine.getMapper(UnitLifecycle.class).get(id); if (l != null && l.isDead()) continue;
      float[] v = readVitals(id); Slot s = new Slot(); s.mercenary = true; s.count = 1; s.representativeId = id;
      s.icon = mercenaryIcons[Math.max(0, Math.min(mercenaryIcons.length - 1, merc.mercType))]; s.name = resolveMercenaryName(merc); s.life = v[0]; s.maxLife = v[1]; slots.add(s);
      mercenaryId = id; life = s.life; maxLife = s.maxLife; return;
    }
  }
  private void collectSummons() {
    if (summonedPets == null) return; Map<SummonKind, Slot> grouped = new LinkedHashMap<SummonKind, Slot>(); IntBag entities = summonedPets.getEntities(); int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) { int id = ids[i]; SummonedPet pet = Riiablo.engine.getMapper(SummonedPet.class).get(id);
      if (pet == null || pet.ownerId != Riiablo.game.player) continue; float[] v = readVitals(id); UnitLifecycle l = Riiablo.engine.getMapper(UnitLifecycle.class).get(id);
      if (!isLivingSummon(pet, l, v[0], v[1])) continue; SummonKind kind = SummonKind.from(pet.petType); if (kind == null) continue;
      Slot s = grouped.get(kind); if (s == null) { s = new Slot(); s.kind = kind; s.icon = iconForSummon(pet, kind); s.name = summonName(pet, kind); s.representativeId = id; grouped.put(kind, s); }
      s.count++; s.life = v[0]; s.maxLife = v[1]; s.skillOrder = Math.min(s.skillOrder, skillTableOrder(pet.skillId));
    }
    slots.addAll(grouped.values());
  }
  private static boolean isDruidFixedKind(SummonKind k) { return k == SummonKind.RAVEN || k == SummonKind.WOLF || k == SummonKind.SPIRIT || k == SummonKind.VINE || k == SummonKind.BEAR; }
  /** Fixed native druid order: Raven, Wolf, Spirit, Vine, then Bear. */
  static int druidSummonPriority(String petType) {
    SummonKind kind = SummonKind.from(petType);
    return kind != null && isDruidFixedKind(kind) ? kind.druidPriority : Integer.MAX_VALUE;
  }
  /** Non-druid rows use their Skills.txt appearance order; druid rows use native order above. */
  static int summonSortPriority(String petType, int skillTableOrder) {
    int druid = druidSummonPriority(petType);
    return druid == Integer.MAX_VALUE ? skillTableOrder : druid;
  }
  private Slot findSlot(SummonKind k) { for (Slot s : slots) if (s.kind == k) return s; return null; }
  private void removeSlot(SummonKind k) { for (int i = slots.size() - 1; i >= 0; i--) if (slots.get(i).kind == k) slots.remove(i); }
  private com.riiablo.codec.excel.Skills.Entry skillEntry(int id) { return Riiablo.files != null && Riiablo.files.skills != null && id >= 0 ? Riiablo.files.skills.get(id) : null; }
  private int skillTableOrder(int id) { if (id < 0 || Riiablo.files == null || Riiablo.files.skills == null) return Integer.MAX_VALUE; int n = 0; for (com.riiablo.codec.excel.Skills.Entry e : Riiablo.files.skills) { if (e != null && e.Id == id) return n; n++; } return Integer.MAX_VALUE - 1; }
  private String summonName(SummonedPet pet, SummonKind kind) { if (kind == SummonKind.VALKYRIE) return resolveValkyrieName(); com.riiablo.codec.excel.Skills.Entry e = skillEntry(pet.skillId); return e != null && e.skill != null && !e.skill.isEmpty() ? e.skill : kind.fallbackName; }
  private AssetDescriptor<DC6> iconForSummon(SummonedPet pet, SummonKind kind) {
    String icon = kind.iconName; com.riiablo.codec.excel.Skills.Entry e = skillEntry(pet.skillId); String n = e == null || e.skill == null ? "" : e.skill.toLowerCase(Locale.ROOT).replace(" ", "");
    if (kind == SummonKind.GOLEM) { if (n.contains("bloodgolem")) icon = "bloodgolumicon.dc6"; else if (n.contains("irongolem")) icon = "metalgolumicon.dc6"; else if (n.contains("firegolem")) icon = "firegolumicon.dc6"; else if (n.contains("claygolem")) icon = "earthgolumicon.dc6"; }
    else if (kind == SummonKind.SPIRIT) { if (n.contains("heartofwolverine")) icon = "HeartOfWolverine.dc6"; else if (n.contains("spiritofbarbs")) icon = "SpiritOfBarbs.dc6"; }
    for (Map.Entry<SummonKind, AssetDescriptor<DC6>> x : summonIcons.entrySet()) if (x.getValue().fileName.endsWith(icon)) return x.getValue(); return summonIcons.get(kind);
  }
  private void updateSlotLabels() { hideSlotLabels(); for (int i = 0; i < slots.size(); i++) { Slot s = slots.get(i); float x = i * (WIDTH + SLOT_GAP); Label n = slotNames.get(i); n.setPosition(x, 1); n.setSize(WIDTH, 14); n.setText(s.name); n.setVisible(true); Label c = slotCounts.get(i); c.setPosition(x, 28); c.setSize(WIDTH, 18); c.setText(s.count > 1 ? Integer.toString(s.count) : ""); c.setVisible(s.count > 1); } tooltip.setPosition(Math.max(0, mercenarySlotIndex) * (WIDTH + SLOT_GAP), -35); tooltip.setText(hovered ? localized("mercenary_heal_hint", "将药水放在肖像上即可治疗\n按下滑鼠右键可打開物品栏（O)") : ""); tooltip.setVisible(hovered && mercenarySlotIndex >= 0); }
  private void hideSlotLabels() { for (Label l : slotNames) l.setVisible(false); for (Label l : slotCounts) l.setVisible(false); tooltip.setVisible(false); }
  private float[] readVitals(int id) { AttributesWrapper a = Riiablo.engine.getMapper(AttributesWrapper.class).get(id); if (a == null || a.attrs == null) return new float[] {0, 0}; StatRef hp = a.attrs.aggregate().get(Stat.hitpoints, StatRef.obtain()); StatRef max = a.attrs.aggregate().get(Stat.maxhp, StatRef.obtain()); return new float[] {hp == null ? 0 : hp.asFixed(), max == null ? 0 : max.asFixed()}; }
  private void updateHover(float x) { hovered = isMercenaryPoint(x, 0); tooltip.setVisible(hovered); }
  private boolean isMercenaryPoint(float x, float y) { if (mercenarySlotIndex < 0) return false; float left = mercenarySlotIndex * (WIDTH + SLOT_GAP); return x >= left && x <= left + WIDTH && y >= 0 && y <= HEIGHT; }
  static boolean isValkyriePetType(String t) { return SummonKind.from(t) == SummonKind.VALKYRIE; }
  static boolean isLivingSummon(SummonedPet p, UnitLifecycle l, float hp, float max) { return p != null && !p.deathPending && p.deadFrames <= 0 && (l == null || !l.isDead()) && max > 0 && hp > 0; }
  static int companionSlotCount(boolean merc, boolean valk) { return merc || valk ? (merc && valk ? 2 : 1) : 0; }
  static float companionSlotX(boolean merc, boolean valk) { return valk && merc ? WIDTH + SLOT_GAP : 0; }
  private static String resolveValkyrieName() { if (Riiablo.string != null && Riiablo.files != null && Riiablo.files.skills != null && Riiablo.files.skilldesc != null) { com.riiablo.codec.excel.Skills.Entry s = Riiablo.files.skills.get("Valkyrie"); if (s != null) { com.riiablo.codec.excel.SkillDesc.Entry d = Riiablo.files.skilldesc.get(s.skilldesc); if (d != null && d.str_name != null) { String v = Riiablo.string.lookup(d.str_name); if (v != null && !v.startsWith("ERROR:")) return v; } } } return "女武神"; }
  public static String resolveMercenaryName(Mercenary m) { return m == null ? localized("hireling_unnamed", UNNAMED) : resolveMercenaryName(m.mercType, m.nameId); }
  public static String resolveMercenaryName(int type, int id) { if (Riiablo.string == null) return localized("hireling_unnamed", UNNAMED); id = Math.max(0, id); String[] keys; switch (type) { case 0: keys = new String[] {String.format(Locale.ROOT, "merc%02d", id + 1)}; break; case 1: keys = new String[] {String.format(Locale.ROOT, "merca%03d", id + 181), String.format(Locale.ROOT, "merca%03d", id + 201)}; break; case 2: keys = new String[] {String.format(Locale.ROOT, "merca%03d", id + 182), String.format(Locale.ROOT, "merca%03d", Math.min(241, id + 222))}; break; case 3: keys = new String[] {String.format(Locale.ROOT, "MercX%03d", id + 31), String.format(Locale.ROOT, "MercX%03d", id + 101)}; break; default: return localized("hireling_unnamed", UNNAMED); } for (String k : keys) { String v = Riiablo.string.lookup(k); if (v != null && !v.startsWith("ERROR:")) return v; } return localized("hireling_unnamed", UNNAMED); }
  private static String localized(String key, String fallback) { if (Riiablo.bundle == null) return fallback; String v = Riiablo.bundle.get(key); return v == null || v.isEmpty() ? fallback : v; }
  static Color healthBarColor(float ratio) { if (ratio > HEALTH_GREEN_THRESHOLD) return Color.GREEN; if (ratio > HEALTH_YELLOW_THRESHOLD) return Color.YELLOW; return Color.RED; }

  public void drawCompanions(Batch batch, float alpha) { if (!isVisible()) return; for (int i = 0; i < slots.size(); i++) { Slot s = slots.get(i); boolean bar = s.mercenary || (s.kind != SummonKind.RAVEN && s.count == 1); drawCompanion(batch, alpha, s.icon, i * (WIDTH + SLOT_GAP), s.life, s.maxLife, bar, i == mercenarySlotIndex && hovered); } }
  @Override public void draw(Batch batch, float alpha) { if (!isVisible()) return; drawCompanions(batch, alpha); batch.setColor(Color.WHITE); super.draw(batch, alpha); }
  private void drawCompanion(Batch batch, float alpha, AssetDescriptor<DC6> d, float slotX, float hp, float max, boolean bar, boolean highlight) { if (d == null || !Riiablo.assets.isLoaded(d.fileName, DC6.class)) return; DC6 icon = Riiablo.assets.get(d.fileName, DC6.class); TextureRegion region = icon.getTexture(0); float x = getX() + slotX + (WIDTH - region.getRegionWidth()) * .5f, y = getY() + ICON_Y; batch.setColor(1, 1, 1, alpha); if (highlight && batch instanceof PaletteIndexedBatch) { PaletteIndexedBatch b = (PaletteIndexedBatch) batch; b.setBlendMode(BlendMode.BRIGHTEN, Riiablo.colors.highlight); batch.draw(region, x, y); b.resetBlendMode(); } else batch.draw(region, x, y); if (!bar) return; float ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max)); Color c = healthBarColor(ratio); if (batch instanceof PaletteIndexedBatch) ((PaletteIndexedBatch) batch).setBlendMode(BlendMode.SOLID, c); else batch.setColor(c.r, c.g, c.b, alpha); batch.draw(fill, getX() + slotX + 5f, getY() + BAR_Y, 46f * ratio, 5f); if (batch instanceof PaletteIndexedBatch) ((PaletteIndexedBatch) batch).resetBlendMode(); }
  @Override public void dispose() { fill.dispose(); for (AssetDescriptor<DC6> d : mercenaryIcons) Riiablo.assets.unload(d.fileName); for (AssetDescriptor<DC6> d : summonIcons.values()) Riiablo.assets.unload(d.fileName); }
}
