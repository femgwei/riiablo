package com.riiablo.screen.panel;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.TxtParser;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.npc.HirelingOffer;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.item.VendorPricing;
import com.riiablo.net.packet.d2gs.NpcHirelingOffer;
import com.riiablo.net.packet.d2gs.NpcServiceOperation;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.net.packet.d2gs.NpcServiceType;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;
import com.riiablo.util.Disposal;
import com.riiablo.util.ActorTree;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;

/** Native textslid-based hire roster. Opening this modal never hires or charges gold. */
public final class HireMercenaryPanel extends WidgetGroup implements Disposable {
  private static final float WIDTH = 490, HEIGHT = 340, HEADER = 34, FOOTER = 34;
  private static final int VISIBLE_ROWS = 7;
  private static final float ROW_HEIGHT = 33;
  private final AssetDescriptor<DC6> descriptor = new AssetDescriptor<>(
      "data\\global\\ui\\MENU\\textslid.dc6", DC6.class);
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final CharData.MercData mercData = character.getMerc();
  private final com.riiablo.mpq.MPQFileHandleResolver mpqs = Riiablo.mpqs;
  private GameScreen owner;
  private com.artemis.World world;
  private AssetReferenceScope references;
  private final Array<HirelingOffer> offers = new Array<>();
  private final ObjectMap<String, String> descriptions = new ObjectMap<>();
  private Label title, message;
  private LabelButton cancel, yes, no;
  private final WidgetGroup rows = new WidgetGroup();
  private PaletteIndexedColorDrawable background;
  private DC6 pieces;
  private int npcEntityId, npcType, first, previousSeed;
  private long pending;
  private boolean purchasing, confirming;
  private float elapsed;
  private HirelingOffer selected;
  private String failure;
  private float panelX, panelY;
  private Stage focusStage;
  private Actor previousFocus, previousScrollFocus;
  private long rosterGeneration;
  private boolean disposed;

  public HireMercenaryPanel() {
    this(null);
  }

  public HireMercenaryPanel(GameScreen owner) {
    this.owner = owner;
    world = owner == null ? Riiablo.engine : null;
    try { initialize(); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    setVisible(false);
    setTouchable(Touchable.enabled);
    background = new PaletteIndexedColorDrawable(Riiablo.colors.modal50);
    title = label("", Align.center);
    title.setColor(Riiablo.colors.gold);
    message = label("", Align.center);
    message.setWrap(true);
    cancel = button(Riiablo.bundle.get("cancel"), this::cancel);
    yes = button(Riiablo.bundle.get("yes"), this::confirm);
    no = button(Riiablo.bundle.get("no"), () -> { confirming = false; rebuild(); });
    addActor(rows);
    addActor(title);
    addActor(message);
    addActor(cancel);
    addActor(yes);
    addActor(no);
    addListener(new InputListener() {
      @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (!isInteractive()) return false;
        if (pointer != 0 || button != Input.Buttons.LEFT) return true;
        if (!confirming && pending == 0 && x >= panelX + WIDTH - 20 && x <= panelX + WIDTH - 6
            && y >= panelY + FOOTER + 8 && y <= panelY + HEIGHT - HEADER - 8) {
          scrollTo(y);
        }
        return true; // Consume the whole screen: no click-through into the world.
      }
      @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
        if (!isInteractive()) return;
        if (!confirming && pending == 0 && x >= panelX + WIDTH - 30) scrollTo(y);
      }
      @Override public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
        if (!isInteractive()) return false;
        if (!confirming && pending == 0) scroll((int) Math.signum(amountY));
        return true;
      }
      @Override public boolean keyDown(InputEvent event, int keycode) {
        if (!isInteractive()) return false;
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) cancel();
        else if (keycode == Input.Keys.UP) scroll(-1);
        else if (keycode == Input.Keys.DOWN) scroll(1);
        else if (keycode == Input.Keys.PAGE_UP) scroll(-VISIBLE_ROWS);
        else if (keycode == Input.Keys.PAGE_DOWN) scroll(VISIBLE_ROWS);
        else if (keycode == Input.Keys.ENTER && confirming) confirm();
        return true;
      }
    });
  }

  private Label label(String text, int alignment) {
    return new Label(text, Riiablo.fonts.font16, alignment).setAutoSize(false);
  }

  private LabelButton button(String text, Runnable action) {
    LabelButton button = new LabelButton(text, Riiablo.fonts.font16);
    button.setAutoSize(false);
    button.setAlignment(Align.center);
    button.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) {
        if (isInteractive() && button.isVisible()) action.run();
      }
    });
    return button;
  }

  private void loadAssets() {
    if (!isCurrentSession() || pieces != null) return;
    AssetReferenceScope attempt = new AssetReferenceScope(assets);
    try {
      attempt.load(descriptor);
      assets.finishLoadingAsset(descriptor);
      DC6 loaded = assets.get(descriptor);
      com.badlogic.gdx.files.FileHandle file = mpqs.resolve("data\\global\\excel\\HireDesc.txt");
      if (file != null) {
        TxtParser parser = TxtParser.loadFromFile(file);
        try {
          int code = parser.getColumnId("Code"), name = parser.getColumnId("Hireling Description");
          if (code >= 0 && name >= 0) {
            while (parser.nextLine() != null) descriptions.put(parser.getString(code), parser.getString(name));
          }
        } finally { com.badlogic.gdx.utils.StreamUtils.closeQuietly(parser); }
      }
      pieces = loaded;
      references = attempt;
    } catch (RuntimeException | Error failure) {
      descriptions.clear();
      try { attempt.dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  /** Presents a read-only service snapshot; also used by the offscreen native-asset check. */
  public void presentOffers(Array<HirelingOffer> available) {
    if (!isCurrentSession()) return;
    close();
    loadAssets();
    offers.clear();
    offers.addAll(available);
    pending = 0;
    purchasing = false;
    confirming = false;
    failure = null;
    setVisible(true);
    rebuild();
  }

  public void open(int npcEntityId, int npcType) {
    if (!isCurrentSession()) return;
    close();
    Stage current = getStage();
    if (current == null) return;
    try {
      loadAssets();
      this.npcEntityId = npcEntityId;
      this.npcType = npcType;
      offers.clear();
      first = 0;
      pending = 0;
      confirming = false;
      selected = null;
      purchasing = false;
      failure = null;
      previousSeed = mercData.seed;
      previousFocus = current.getKeyboardFocus();
      previousScrollFocus = current.getScrollFocus();
      focusStage = current;
      setVisible(true);
      toFront();
      if (!current.setKeyboardFocus(this) || !current.setScrollFocus(this)) { close(); return; }
      showStatus("mercenary_hire_loading");
      if (networked()) {
        send(NpcServiceOperation.OPEN, null);
      } else {
        NativeMercenaryRewardSystem rewards = rewards();
        if (rewards == null) showFailure("MERCENARY_SERVICE_UNAVAILABLE");
        else {
          offers.addAll(rewards.hirelingOffers(game().player, npcType));
          rebuild();
        }
      }
    } catch (RuntimeException | Error failure) {
      try { close(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private boolean networked() {
    GameScreen game = game();
    return game != null && game.vendorPanel != null && game.vendorPanel.isNetworked();
  }

  private NativeMercenaryRewardSystem rewards() {
    return world == null || game() == null ? null : world.getSystem(NativeMercenaryRewardSystem.class);
  }

  private void send(byte operation, HirelingOffer offer) {
    ClientNetworkSynchronizer network = world == null ? null : world.getSystem(ClientNetworkSynchronizer.class);
    elapsed = 0;
    pending = network == null ? 0 : network.requestNpcService(npcEntityId,
        NpcServiceType.HIRE, operation, offer == null ? 0 : offer.seed,
        offer == null ? -1 : offer.index, previousSeed & 0xFFFFFFFFL);
    if (pending == 0) showFailure("CONNECTION_UNAVAILABLE");
  }

  public void applyNetworkResult(NpcServiceResult result) {
    if (!isInteractive() || result == null || pending == 0 || result.requestId() != pending) return;
    pending = 0;
    if (result.mercenary() != null) previousSeed = result.mercenary().seed();
    if (result.success() && purchasing) { close(); return; }
    purchasing = false;
    failure = null;
    offers.clear();
    for (int i = 0; i < result.hirelingsLength(); i++) {
      NpcHirelingOffer source = result.hirelings(i);
      HirelingOffer offer = new HirelingOffer();
      offer.index = source.index(); offer.seed = source.seed();
      offer.mercType = source.mercType(); offer.hirelingId = source.hirelingId();
      offer.nameId = source.nameId(); offer.level = source.level(); offer.life = source.life();
      offer.defense = source.defense(); offer.cost = (int) source.cost();
      offer.description = source.description();
      offers.add(offer);
    }
    rebuild();
    if (!result.success()) showFailure(result.reason());
  }

  private void choose(HirelingOffer offer) {
    if (!isInteractive() || confirming || pending != 0 || purchasing) return;
    selected = offer;
    previousSeed = mercData.seed;
    if (previousSeed != 0) {
      confirming = true;
      rebuild();
    } else confirm();
  }

  private void confirm() {
    if (!isInteractive() || selected == null || pending != 0 || purchasing) return;
    confirming = false;
    purchasing = true;
    showStatus("mercenary_hire_loading");
    if (networked()) send(NpcServiceOperation.HIRE, selected);
    else {
      String reason = rewards() == null ? "MERCENARY_SERVICE_UNAVAILABLE"
          : rewards().hireSelectedMercenary(game().player, npcType, selected.index,
              selected.seed, previousSeed);
      purchasing = false;
      if (reason == null) close();
      else {
        offers.clear();
        if (rewards() != null) offers.addAll(rewards().hirelingOffers(game().player, npcType));
        rebuild();
        showFailure(reason);
      }
    }
  }

  private void showStatus(String key) {
    failure = null;
    retireRows();
    message.setText(Riiablo.bundle.get(key));
    message.setVisible(true);
    yes.setVisible(false);
    no.setVisible(false);
  }

  private void showFailure(String reason) {
    failure = reason;
    purchasing = false;
    String key = "mercenary_hire_error_" + reason;
    String text;
    try { text = Riiablo.bundle.get(key); }
    catch (java.util.MissingResourceException ignored) { text = Riiablo.bundle.get("mercenary_hire_error"); }
    message.setText(text);
    message.setVisible(true);
    rows.setVisible(false);
    // Cancel returns to the refreshed roster, without replaying a purchase.
  }

  public void cancel() {
    if (!isInteractive()) return;
    if (confirming) { confirming = false; rebuild(); }
    else if (message.isVisible() && offers.size > 0 && pending == 0 && !purchasing) {
      failure = null;
      rebuild();
    }
    else close();
  }

  public void close() {
    super.setVisible(false);
    pending = 0;
    purchasing = confirming = false;
    selected = null;
    Stage previous = focusStage;
    Actor keyboard = previousFocus, scroll = previousScrollFocus;
    focusStage = null; previousFocus = previousScrollFocus = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::retireRows);
      if (previous != null) {
        cleanup.run(() -> { if (previous.getKeyboardFocus() == this)
          previous.setKeyboardFocus(keyboard != null && keyboard.getStage() == previous ? keyboard : null); });
        cleanup.run(() -> { if (previous.getScrollFocus() == this)
          previous.setScrollFocus(scroll != null && scroll.getStage() == previous ? scroll : null); });
      }
    }
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    if (!visible) close();
    else super.setVisible(true);
  }

  private boolean isCurrentSession() {
    return !disposed && assets == Riiablo.assets && character == Riiablo.charData
        && mercData == character.getMerc() && world == Riiablo.engine
        && (owner != null ? Riiablo.game == owner
            : Riiablo.game == null || Riiablo.game.hireMercenaryPanel == this);
  }

  private boolean isInteractive() { return isCurrentSession() && isVisible(); }
  private GameScreen game() { return owner != null ? owner : Riiablo.game; }

  /** Assembly binds once, after world creation and before publishing the GameScreen. */
  public void bindWorld(com.artemis.World world) {
    if (disposed) return;
    java.util.Objects.requireNonNull(world, "world");
    if (this.world != null && this.world != world) throw new IllegalStateException("world already bound");
    this.world = world;
  }

  private void retireRows() {
    rosterGeneration++;
    try (Disposal cleanup = new Disposal()) {
      for (Actor child : rows.getChildren().toArray(Actor.class)) cleanup.run(() -> ActorTree.clear(child));
      cleanup.run(rows::clearChildren);
    }
  }

  private void choose(HirelingOffer offer, long generation) {
    if (generation == rosterGeneration && rows.isVisible() && offers.contains(offer, true)) choose(offer);
  }

  private void scroll(int amount) {
    if (!isInteractive() || confirming || pending != 0 || purchasing) return;
    first = MathUtils.clamp(first + amount, 0, Math.max(0, offers.size - VISIBLE_ROWS));
    rebuild();
  }

  private void scrollTo(float y) {
    if (y > panelY + HEIGHT - HEADER - 20) scroll(-1);
    else if (y < panelY + FOOTER + 20) scroll(1);
    else {
      float fraction = (panelY + HEIGHT - HEADER - 25 - y) / (HEIGHT - HEADER - FOOTER - 50);
      first = MathUtils.clamp(Math.round(fraction * Math.max(0, offers.size - VISIBLE_ROWS)),
          0, Math.max(0, offers.size - VISIBLE_ROWS));
      rebuild();
    }
  }

  private void rebuild() {
    if (failure != null) {
      showFailure(failure);
      layoutControls();
      return;
    }
    if (pending != 0 || purchasing) {
      showStatus("mercenary_hire_loading");
      layoutControls();
      return;
    }
    retireRows();
    rows.setVisible(!confirming);
    first = Math.min(first, Math.max(0, offers.size - VISIBLE_ROWS));
    yes.setVisible(confirming);
    no.setVisible(confirming);
    message.setVisible(confirming || offers.size == 0);
    message.setText(Riiablo.bundle.get(confirming ? "mercenary_hire_replace" : "mercenary_hire_empty"));
    if (!confirming) {
      final long generation = rosterGeneration;
      for (int row = 0; row < VISIBLE_ROWS && first + row < offers.size; row++) {
        HirelingOffer offer = offers.get(first + row);
        String text = Riiablo.bundle.format("mercenary_hire_row",
            MercenaryHud.resolveMercenaryName(offer.mercType, offer.nameId), offer.level,
            offer.life, offer.defense, offer.cost);
        LabelButton entry = button(text, () -> choose(offer, generation));
        // Native English font16 is much wider than the CJK font. Use the
        // compact native list font so the price is never hidden by ellipsis.
        if (Riiablo.language != com.riiablo.D2Language.CHINESE) entry.setFont(Riiablo.fonts.font8);
        entry.setAlignment(Align.left);
        entry.setEllipsis(true);
        entry.setBounds(panelX + 30, panelY + HEIGHT - HEADER - 25 - row * ROW_HEIGHT,
            WIDTH - 66, 16);
        if (entry.getPrefWidth() > entry.getWidth())
          entry.setFontScale(entry.getWidth() / entry.getPrefWidth());
        rows.addActor(entry);
        String key = descriptionKey(offer.description);
        String skill = offer.description == null || offer.description.isEmpty() ? ""
            : key == null ? descriptions.get(offer.description, "") : Riiablo.string.lookup(key);
        Label description = label(skill, Align.left);
        if (Riiablo.language != com.riiablo.D2Language.CHINESE) description.setFont(Riiablo.fonts.font8);
        description.setBounds(entry.getX() + 6, entry.getY() - 14, entry.getWidth() - 6, 16);
        description.setTouchable(Touchable.disabled);
        rows.addActor(description);
        // The second line belongs to the same selectable candidate.
        Actor hit = new Actor();
        hit.setBounds(entry.getX(), entry.getY() - 15, entry.getWidth(), 15);
        hit.addListener(new ClickListener() {
          @Override public void clicked(InputEvent event, float x, float y) { choose(offer, generation); }
        });
        rows.addActor(hit);
      }
    }
    layoutControls();
  }

  /** HireDesc.txt's English descriptions are not TBL keys. Use the native hireling labels. */
  public static String descriptionKey(String code) {
    if (code == null) return null;
    switch (code) {
      case "farw": return "strhirespecial1";
      case "carw": return "strhirespecial2";
      case "comb": return "StrMercEx15";
      case "def": return "StrMercEx12";
      case "off": return "StrMercEx14";
      case "fire": return "strhirespecial7";
      case "cold": return "strhirespecial6";
      case "ltng": return "strhirespecial5";
      default: return null;
    }
  }

  private void layoutControls() {
    title.setBounds(panelX + 8, panelY + HEIGHT - HEADER + 2, WIDTH - 16, HEADER - 4);
    cancel.setBounds(panelX + 8, panelY + 2, WIDTH - 16, FOOTER - 4);
    message.setBounds(panelX + 28, panelY + FOOTER + 60, WIDTH - 56, 120);
    yes.setBounds(panelX + 85, panelY + FOOTER + 28, 120, 24);
    no.setBounds(panelX + WIDTH - 205, panelY + FOOTER + 28, 120, 24);
  }

  @Override public void act(float delta) {
    if (!isCurrentSession()) return;
    super.act(delta);
    if (!isVisible() || getStage() == null) return;
    float x = (getStage().getWidth() - WIDTH) / 2;
    float y = Math.max(8, (getStage().getHeight() - HEIGHT) / 2 + 25);
    setSize(getStage().getWidth(), getStage().getHeight());
    if (panelX != x || panelY != y) { panelX = x; panelY = y; rebuild(); }
    title.setText(Riiablo.bundle.format("mercenary_hire_title", VendorPricing.availableGold(character)));
    if (pending != 0 && (elapsed += delta) >= 15) {
      pending = 0;
      offers.clear(); // Do not resend an unacknowledged payment after a timeout.
      showFailure("CONNECTION_UNAVAILABLE");
    }
    if (world != null && game() != null) {
      com.artemis.ComponentMapper<Position> positions = world.getMapper(Position.class);
      if (!positions.has(npcEntityId) || !positions.has(game().player)
          || positions.get(npcEntityId).position.dst2(positions.get(game().player).position) > 64) close();
    }
  }

  @Override public void draw(Batch batch, float alpha) {
    if (!isCurrentSession() || pieces == null) return;
    background.draw(batch, panelX + 2, panelY + 2, WIDTH - 4, HEIGHT - 4);
    frame(batch, panelX, panelY, WIDTH, FOOTER);
    frame(batch, panelX, panelY + FOOTER, WIDTH, HEIGHT - HEADER - FOOTER);
    frame(batch, panelX, panelY + HEIGHT - HEADER, WIDTH, HEADER);
    if (!confirming && !message.isVisible()) {
      float x = panelX + WIDTH - 18, bottom = panelY + FOOTER + 10;
      float top = panelY + HEIGHT - HEADER - 18;
      for (float y = bottom + 12; y < top; y += 12) batch.draw(pieces.getTexture(13), x, y);
      batch.draw(pieces.getTexture(9), x, top);
      batch.draw(pieces.getTexture(8), x, bottom);
      float track = top - bottom - 12;
      float thumb = Math.max(12, track * Math.min(1f, (float) VISIBLE_ROWS / Math.max(1, offers.size)));
      float thumbTop = top - 1 - (track - thumb) * first / Math.max(1, offers.size - VISIBLE_ROWS);
      for (float y = thumbTop - thumb + 12; y < thumbTop - 12; y += 12)
        batch.draw(pieces.getTexture(15), x, y);
      batch.draw(pieces.getTexture(14), x, thumbTop - 12);
      batch.draw(pieces.getTexture(16), x, thumbTop - thumb);
    }
    super.draw(batch, alpha);
  }

  private void frame(Batch batch, float x, float y, float w, float h) {
    // textslid: four corners (0..3), top/left/bottom/right edges (4..7).
    for (float dx = 12; dx < w - 12; dx += 12) {
      batch.draw(pieces.getTexture(4), x + dx, y + h - 13, Math.min(12, w - 12 - dx), 13);
      batch.draw(pieces.getTexture(6), x + dx, y, Math.min(12, w - 12 - dx), 13);
    }
    for (float dy = 13; dy < h - 13; dy += 13) {
      batch.draw(pieces.getTexture(5), x, y + dy, 12, Math.min(13, h - 13 - dy));
      batch.draw(pieces.getTexture(7), x + w - 12, y + dy, 12, Math.min(13, h - 13 - dy));
    }
    batch.draw(pieces.getTexture(0), x, y + h - 13);
    batch.draw(pieces.getTexture(1), x, y);
    batch.draw(pieces.getTexture(2), x + w - 12, y);
    batch.draw(pieces.getTexture(3), x + w - 12, y + h - 13);
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(this::close);
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(() -> ActorTree.clear(this));
      cleanup.run(() -> ActorTree.clear(cancel));
      cleanup.run(() -> ActorTree.clear(yes));
      cleanup.run(() -> ActorTree.clear(no));
      cleanup.dispose(references);
    } finally {
      pieces = null;
      offers.clear();
      descriptions.clear();
      pending = 0;
      selected = null;
      previousFocus = previousScrollFocus = null; focusStage = null;
      references = null; world = null; owner = null;
      title = message = null; cancel = yes = no = null; background = null;
      failure = null; purchasing = confirming = false;
    }
  }
}
