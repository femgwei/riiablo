package com.riiablo.screen.panel;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Quests;
import com.riiablo.engine.client.QuestPresentation;
import com.riiablo.graphics.BlendMode;
import com.riiablo.loader.DC6Loader;
import com.riiablo.widget.AnimationWrapper;
import com.riiablo.widget.Button;
import com.riiablo.widget.DCWrapper;
import com.riiablo.widget.DialogScroller;
import com.riiablo.widget.Label;
import com.riiablo.save.CharData;
import com.riiablo.util.AssetReferenceScope;
import com.riiablo.util.Disposal;

import java.util.Comparator;

public class QuestsPanel extends WidgetGroup implements Disposable {
  private static final String TAG = "QuestsPanel";
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final CharData character = java.util.Objects.requireNonNull(Riiablo.charData, "character");
  private final Array<Button> ownedButtons = new Array<>();
  private final Array<QuestButton> questButtons = new Array<>();
  private final Tab[] tabs = new Tab[5];
  private boolean disposed;
  private final PanelSession session;
  public void bindWorld(com.artemis.World world) { session.bindWorld(world); }

  final AssetDescriptor<DC6> questbackgroundDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\questbackground.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion questbackground;

  final AssetDescriptor<DC6> expquesttabsDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\expquesttabs.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC expquesttabs;

  final AssetDescriptor<DC6> questlastDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\questlast.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  Button btnPlayQuest;

  final AssetDescriptor<DC6> questsocketsDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\questsockets.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC questsockets;

  final AssetDescriptor<DC6> questdoneDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\questdone.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC questdone;

  AssetDescriptor<DC6>[] questiconsDescriptor;
  DC[] questicons;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  Button btnExit;

  Tab activeTab;

  public QuestsPanel() {
    this(Riiablo.game);
  }

  public QuestsPanel(com.riiablo.screen.GameScreen owner) {
    session = new PanelSession(owner);
    try {
      initialize();
      session.finish(this);
    } catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  @SuppressWarnings("unchecked")
  private void initialize() {
    references.load(questbackgroundDescriptor);
    assets.finishLoadingAsset(questbackgroundDescriptor);
    questbackground = assets.get(questbackgroundDescriptor).getTexture();
    setSize(questbackground.getRegionWidth(), questbackground.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    btnPlayQuest = new Button(new Button.ButtonStyle() {{
      references.load(questlastDescriptor);
      assets.finishLoadingAsset(questlastDescriptor);
      up   = new TextureRegionDrawable(assets.get(questlastDescriptor).getTexture(0));
      down = new TextureRegionDrawable(assets.get(questlastDescriptor).getTexture(1));
    }});
    ownedButtons.add(btnPlayQuest);
    btnPlayQuest.setPosition(227, 10);
    addActor(btnPlayQuest);

    btnExit = new Button(new Button.ButtonStyle() {{
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    ownedButtons.add(btnExit);
    btnExit.setPosition(278, 10);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        setVisible(false);
      }
    });
    addActor(btnExit);

    references.load(expquesttabsDescriptor);
    assets.finishLoadingAsset(expquesttabsDescriptor);
    expquesttabs = assets.get(expquesttabsDescriptor);

    references.load(questsocketsDescriptor);
    assets.finishLoadingAsset(questsocketsDescriptor);
    questsockets = assets.get(questsocketsDescriptor);

    references.load(questdoneDescriptor);
    assets.finishLoadingAsset(questdoneDescriptor);
    questdone = assets.get(questdoneDescriptor);

    @SuppressWarnings("unchecked")
    Array<Quests.Entry>[] quests = (Array<Quests.Entry>[]) new Array[5];
    for (int i = 0; i < quests.length; i++) quests[i] = new Array<>(6);
    for (Quests.Entry quest : Riiablo.files.quests) {
      if (quest.visible) {
        quests[quest.act].add(quest);
      }
    }
    Comparator<Quests.Entry> comparator = new Comparator<Quests.Entry>() {
      @Override
      public int compare(Quests.Entry o1, Quests.Entry o2) {
        return o1.order - o2.order;
      }
    };
    int numQuests = 0;
    for (Array<Quests.Entry> quest : quests) {
      quest.sort(comparator);
      numQuests += quest.size;
    }

    questiconsDescriptor = (AssetDescriptor<DC6>[]) new AssetDescriptor[numQuests];
    questicons = new DC[numQuests];
    for (int act = 0, q = 0; act < 5; act++) {
      for (Quests.Entry quest : quests[act]) {
        questiconsDescriptor[q] = new AssetDescriptor<>("data\\global\\ui\\MENU\\" + quest.icon + ".dc6", DC6.class);
        references.load(questiconsDescriptor[q]);
        assets.finishLoadingAsset(questiconsDescriptor[q]);
        questicons[q] = assets.get(questiconsDescriptor[q]);
        q++;
      }
    }

    for (int i = 0, q = 0; i < tabs.length; i++) {
      Tab tab = tabs[i] = new Tab();
      for (Quests.Entry quest : quests[i]) {
        tab.addQuest(quest, q++, QuestPresentation.recordIndex(quest));
      }

      tab.pack();
      //tab.questIcons.setSize(315, 200);
      //tab.questIcons.layout();
      tab.setSize(315, 352);
      tab.layout();
      tab.setPosition(3, getHeight() - 32, Align.topLeft);
      //tab.questIcons.setY(tab.getHeight(), Align.top);
      tab.setVisible(false);
      //tab.setDebug(true, true);
      addActor(tab);
    }

    float x = 2, y = getHeight() - 3;
    Button[] actors = new Button[5];
    for (int i = 0; i < actors.length; i++) {
      final int j = i << 1;
      final Button actor = actors[i] = new Button(new Button.ButtonStyle() {{
        down = new TextureRegionDrawable(expquesttabs.getTexture(j));
        up   = new TextureRegionDrawable(expquesttabs.getTexture(j + 1));
        checked = down;
      }});
      ownedButtons.add(actor);
      actor.setHighlightedBlendMode(BlendMode.ID, Color.WHITE);
      actor.setPosition(x, y, Align.topLeft);
      actor.setUserObject(tabs[i]);
      actor.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          for (Tab tab : tabs) if (tab != null) tab.setVisible(false);
          activeTab = (Tab) actor.getUserObject();
          activeTab.setVisible(true);
        }
      });
      addActor(actor);
      x += actor.getWidth();
    }

    ButtonGroup<Button> tabGroup = new ButtonGroup<>();
    tabGroup.add(actors);
    tabGroup.setMinCheckCount(1);
    tabGroup.setMaxCheckCount(1);
    activeTab = tabs[0];
    activeTab.setVisible(true);

    btnPlayQuest.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        if (activeTab != null && activeTab.selected != null) {
          String speech = activeTab.selected.replaySpeech();
          if (speech != null && Riiablo.files.speech.get(speech) != null) {
            activeTab.questDialog.play(speech);
          }
        }
      }
    });

    //setDebug(true, true);
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (!isCurrentSession()) return;
    batch.draw(questbackground, getX(), getY());
    super.draw(batch, parentAlpha);
  }

  @Override
  public void act(float delta) {
    if (!isCurrentSession()) return;
    super.act(delta);
    if (activeTab != null) activeTab.refresh();
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    activeTab = null;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      for (Tab tab : tabs) {
        if (tab == null) continue;
        tab.selected = null;
        cleanup.dispose(tab.questDialog);
        // A partially built tab need not have reached addActor yet.
        cleanup.run(() -> clearActorTree(tab));
        cleanup.run(tab::remove);
      }
      for (QuestButton button : questButtons) {
        cleanup.run(button.anim::reset);
      }
      cleanup.run(() -> clearActorTree(this));
      for (Button button : ownedButtons) {
        cleanup.run(button::clearListeners);
        cleanup.run(button::remove);
        cleanup.dispose(button);
      }
      cleanup.run(this::remove);
      cleanup.run(() -> session.dispose(this));
      cleanup.dispose(references);
    } finally {
      ownedButtons.clear(); questButtons.clear();
      java.util.Arrays.fill(tabs, null);
      questbackground = null; expquesttabs = null; questsockets = null;
      questdone = null; questicons = null; btnPlayQuest = null; btnExit = null;
    }
  }

  private boolean isCurrentSession() {
    return !disposed && session.isCurrent() && character == Riiablo.charData;
  }

  @Override public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }

  @Override public void setVisible(boolean visible) {
    if (disposed || visible && !isCurrentSession()) return;
    super.setVisible(visible);
    if (!visible && tabs != null) try (Disposal cleanup = new Disposal()) {
      for (Tab tab : tabs) if (tab != null) cleanup.run(tab::hide);
    }
  }

  private static void clearActorTree(Actor actor) {
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(actor::clearListeners);
      cleanup.run(actor::clearActions);
      if (actor instanceof Group) {
        Group group = (Group) actor;
        for (Actor child : group.getChildren().toArray(Actor.class)) {
          cleanup.run(() -> clearActorTree(child));
        }
        cleanup.run(group::clearChildren);
      }
    }
  }

  private class Tab extends Table {
    static final int QUEST_COLS = 3;
    private QuestButton selected = null;
    Table questIcons;
    Label questName;
    DialogScroller questDialog;

    Tab() {
      questIcons = new Table();
      for (int i = 0; i < QUEST_COLS; i++) {
        questIcons.columnDefaults(i).size(80, 95).space(4, 16, 4, 16);
      }
      questIcons.align(Align.top | Align.center);
      add(questIcons).height(197).growX().row();

      questName = new Label(Riiablo.fonts.font16);
      questName.setAlignment(Align.center);
      add(questName).height(24).growX().row();

      questDialog = new DialogScroller(new DialogScroller.DialogCompletionListener() {
        @Override
        public void onCompleted(DialogScroller d) {
          if (!isCurrentSession()) return;
          if (selected != null) setQuestText(selected);
        }
      }) {
        @Override public void play(String speech) { if (isCurrentSession()) super.play(speech); }
        @Override public void setText(String text) { if (isCurrentSession()) super.setText(text); }
        @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }
        @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
      };
      add(questDialog).grow().row();
    }

    @Override
    public void setVisible(boolean visible) {
      super.setVisible(visible);
      if (!visible) hide();
    }

    void hide() {
      if (!isCurrentSession()) return;
      questDialog.setText(selected != null ? Riiablo.string.lookup(selected.quest.qsts[0]) : "");
    }

    void setSelected(QuestButton quest) {
      if (!isCurrentSession()) return;
      if (selected != quest) {
        if (selected != null) selected.setSelected(false);
        selected = quest;
        quest.setSelected(true);
        questName.setText(Riiablo.string.lookup(quest.getName()));
        setQuestText(quest);
      }
    }

    void addQuest(Quests.Entry quest, int q, int nativeRecordIndex) {
      QuestButton button = new QuestButton(this, quest, q, nativeRecordIndex);
      questButtons.add(button);
      questIcons.add(button);
      if (questIcons.getCells().size % QUEST_COLS == 0) {
        questIcons.row();
      }
    }

    void refresh() {
      if (!isCurrentSession()) return;
      for (com.badlogic.gdx.scenes.scene2d.Actor actor : questIcons.getChildren()) {
        if (actor instanceof QuestButton) ((QuestButton) actor).refresh();
      }
      if (selected != null && selected.consumeRecordChanged()) setQuestText(selected);
    }

    private void setQuestText(QuestButton quest) {
      String textId = quest.textId();
      if (textId == null) questDialog.setText("");
      else questDialog.setTextId(textId);
    }
  }

  private class QuestButton extends WidgetGroup {
    private static final int FRAME_UP       = 0;
    private static final int FRAME_DOWN     = 25;
    private static final int FRAME_DISABLED = 26;

    final Quests.Entry quest;
    final Tab parent;
    final Animation anim;
    final DCWrapper completed;
    final DCWrapper overlay;
    final ClickListener clickListener;
    final int nativeRecordIndex;
    short lastRecord = Short.MIN_VALUE;
    boolean recordChanged;

    QuestButton(Tab tab, Quests.Entry quest, int q, int nativeRecordIndex) {
      this.parent = tab;
      this.quest = quest;
      this.nativeRecordIndex = nativeRecordIndex;
      setName(quest.qstr);

      completed = new DCWrapper() {
        @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
      };
      completed.setDrawable(questdone.getTexture(quest.questdone));
      completed.setPosition(5, 4);
      completed.setSize(72, 86);
      completed.setVisible(false);
      addActor(completed);

      anim = Animation.newAnimation(questicons[q]);
      anim.setClamp(FRAME_UP, FRAME_DOWN);
      anim.setFrameDuration(Float.MAX_VALUE);
      AnimationWrapper animWrapper = new AnimationWrapper(anim) {
        @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }
        @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
      };
      animWrapper.setPosition(5, 4);
      addActor(animWrapper);

      overlay = new DCWrapper() {
        @Override public void draw(Batch batch, float alpha) { if (isCurrentSession()) super.draw(batch, alpha); }
      };
      overlay.setDrawable(questsockets.getTexture(0));
      overlay.setSize(80, 95);
      addActor(overlay);

      addListener(clickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          parent.setSelected(QuestsPanel.QuestButton.this);
        }
      });
      refresh();
    }

    void setSelected(boolean b) {
      overlay.setDrawable(questsockets.getTexture(b ? 1 : 0));
    }

    @Override
    public void act(float delta) {
      if (!isCurrentSession()) return;
      super.act(delta);
      if (nativeRecordIndex > 0 && !isAvailable()) {
        anim.setFrame(FRAME_DISABLED);
      } else if (clickListener.isVisualPressed()) {
        anim.setFrame(FRAME_DOWN);
      } else {
        anim.setFrame(FRAME_UP);
      }
    }

    void refresh() {
      if (!isCurrentSession() || nativeRecordIndex <= 0) return;
      short record = record();
      if (record != lastRecord) {
        lastRecord = record;
        recordChanged = true;
      }
      boolean available = isAvailable();
      setTouchable(available ? Touchable.enabled : Touchable.disabled);
      completed.setVisible(QuestPresentation.isComplete(quest, record));
    }

    boolean consumeRecordChanged() {
      boolean changed = recordChanged;
      recordChanged = false;
      return changed;
    }

    boolean isAvailable() {
      int level = character.getStats().aggregate().getValue(Stat.level, 0);
      return nativeRecordIndex <= 0
          || QuestPresentation.isAvailable(quest, nativeRecordIndex, record(), level);
    }

    short record() {
      short[] records = character.getQuests(quest.act);
      return records != null && nativeRecordIndex > 0 && nativeRecordIndex < records.length
          ? records[nativeRecordIndex] : 0;
    }

    String textId() {
      return nativeRecordIndex > 0
          ? QuestPresentation.textId(quest, record())
          : quest.qsts[0];
    }

    String replaySpeech() {
      return nativeRecordIndex > 0
          ? QuestPresentation.replaySpeech(quest, nativeRecordIndex, record()) : null;
    }
  }
}
