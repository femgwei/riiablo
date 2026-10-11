package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1AndarielQuest;
import com.riiablo.engine.server.quest.Act1WarrivIntroQuest;
import com.riiablo.engine.server.quest.Act1BloodRavenQuest;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.engine.server.quest.Act1CainQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.save.CharData;
import com.riiablo.graphics.BlendMode;
import com.riiablo.widget.Label;

/** Draws the native Act I quest-available/reward marker above town NPCs. */
@GpuSystem
@All({Monster.class, Position.class})
public class Act1QuestIndicatorSystem extends IteratingSystem {
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected RenderSystem renderer;

  @Wire(name = "iso") protected IsometricCamera iso;

  private final Array<MarkerPosition> markers = new Array<>();
  private final Label marker = new Label("!", Riiablo.fonts.font16, Riiablo.colors.gold);
  private static final AssetDescriptor<DCC> QUEST_MARKER_DESCRIPTOR =
      new AssetDescriptor<>("data\\global\\overlays\\NPCSpeechBalloon.dcc", DCC.class);
  private static final float QUEST_MARKER_X_OFFSET = -6f;
  private Animation questMarkerAnimation;
  private boolean questMarkerLoadQueued;

  private static final class MarkerPosition {
    final float x;
    final float anchorY;
    final float fallbackY;

    MarkerPosition(float x, float anchorY, float fallbackY) {
      this.x = x;
      this.anchorY = anchorY;
      this.fallbackY = fallbackY;
    }
  }

  @Override
  protected void begin() {
    markers.clear();
    loadQuestMarkerAnimation();
  }

  @Override
  protected void process(int entityId) {
    int playerId = renderer.getSrc();
    if (!mPlayer.has(playerId)) return;
    CharData data = mPlayer.get(playerId).data;
    Monster npc = mMonster.get(entityId);
    if (data == null || npc.monstats == null || !hasQuestMarker(npc.monstats.hcIdx, data)) return;

    Vector2 screen = new Vector2(mPosition.get(entityId).position);
    iso.toScreen(screen);
    MonStats2.Entry visual = Riiablo.files.monstats2.get(npc.monstats.MonStatsEx);
    float markerHeight = visual == null ? 80 : Math.max(64, visual.pixHeight);
    // NPCSpeechBalloon.dcc is authored around the unit's native ground
    // anchor. Keep both anchors: the DCC uses the ground point, while the
    // text fallback needs the old head-height position.
    markers.add(new MarkerPosition(screen.x, screen.y, screen.y + markerHeight));
  }

  @Override
  protected void end() {
    if (markers.size == 0) return;
    if (questMarkerAnimation != null) questMarkerAnimation.act(Gdx.graphics.getDeltaTime());
    Riiablo.batch.begin();
    for (MarkerPosition position : markers) {
      if (questMarkerAnimation != null) {
        questMarkerAnimation.draw(
            Riiablo.batch, position.x + QUEST_MARKER_X_OFFSET, position.anchorY);
      } else {
        marker.setPosition(position.x, position.fallbackY, Align.center | Align.bottom);
        marker.draw(Riiablo.batch, 1f);
      }
    }
    Riiablo.batch.end();
  }

  private void loadQuestMarkerAnimation() {
    if (questMarkerAnimation != null || Riiablo.assets == null) return;
    if (!questMarkerLoadQueued) {
      Riiablo.assets.load(QUEST_MARKER_DESCRIPTOR);
      questMarkerLoadQueued = true;
    }
    if (!Riiablo.assets.isLoaded(QUEST_MARKER_DESCRIPTOR)) return;

    DCC dcc = Riiablo.assets.get(QUEST_MARKER_DESCRIPTOR);
    // Keep the native overlay self-lit. BRIGHTEN preserves the authored glow
    // in dark outdoor scenes where a luminosity-only alpha makes the marker
    // appear gray and noticeably dimmer than the original client.
    questMarkerAnimation = Animation.builder().layer(dcc, BlendMode.BRIGHTEN_LUMINOSITY).build();
    questMarkerAnimation.setMode(Animation.Mode.LOOP);
  }

  static boolean hasQuestMarker(int monsterType, CharData data) {
    short[] quests = data.getQuests(Riiablo.ACT1);
    if (quests == null) return false;
    short record;
    switch (monsterType) {
      case MonsterType.AKARA:
        record = quests[Act1DenOfEvilQuest.RECORD];
        if (!Act1QuestPresentation.isComplete(record)) {
          return !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)
              || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
        }
        record = quests[Act1CainQuest.RECORD];
        return !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
      case MonsterType.KASHYA:
        if (!Act1DenOfEvilQuest.unlocksNextQuest(quests[Act1DenOfEvilQuest.RECORD])) {
          return false;
        }
        record = quests[Act1BloodRavenQuest.RECORD];
        // D2MOO's ACT1Q2_ActiveFilterCallback disables Kashya's quest state
        // after the Blood Raven reward has been granted.  The intermediate
        // STARTED flag is reset at that point, so checking only "not started"
        // would incorrectly recreate the exclamation mark on every visit.
        if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE)) {
          return false;
        }
        return !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
      case MonsterType.CHARSI:
        record = quests[Act1MalusQuest.RECORD];
        int level = data.getStats().aggregate().getValue(Stat.level, 0);
        return Act1MalusQuest.canStart(record, level)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
            || (level >= Act1MalusQuest.MINIMUM_LEVEL
                && data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE));
      case MonsterType.WARRIV:
        short denRecord = quests[Act1DenOfEvilQuest.RECORD];
        if (NativeQuestRecord.has(denRecord, NativeQuestRecord.STARTED)
            && !Act1DenOfEvilQuest.isFinished(denRecord)) {
          return false;
        }
        record = quests[Act1WarrivIntroQuest.RECORD];
        if (Act1WarrivIntroQuest.isActive(record)) return true;
        record = quests[Act1AndarielQuest.RECORD];
        return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
      case MonsterType.NAVI:
        // D2MOO's A1Q7 active filter is tied to the Den of Evil state.  Flavie
        // warns the player while A1Q1 is still open, but stops advertising
        // that branch once the objective/reward has been resolved.
        record = quests[Act1DenOfEvilQuest.RECORD];
        return !Act1DenOfEvilQuest.isFinished(record);
      case MonsterType.DECKARDCAIN:
      case MonsterType.DECKARDCAIN_TOWN:
        return false;
      default:
        return false;
    }
  }

}
