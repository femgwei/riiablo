package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1AndarielQuest;
import com.riiablo.engine.server.quest.Act1BloodRavenQuest;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.engine.server.quest.Act1CainQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.map.RenderSystem;
import com.riiablo.save.CharData;
import com.riiablo.widget.Label;

/** Draws the native Act I quest-available/reward marker above town NPCs. */
@All({Monster.class, Position.class})
public class Act1QuestIndicatorSystem extends IteratingSystem {
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected RenderSystem renderer;

  @Wire(name = "iso") protected IsometricCamera iso;

  private final Array<Vector2> markers = new Array<>();
  private final Label marker = new Label("!", Riiablo.fonts.font16, Riiablo.colors.gold);

  @Override
  protected void begin() {
    markers.clear();
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
    screen.y += visual == null ? 80 : Math.max(64, visual.pixHeight);
    markers.add(screen);
  }

  @Override
  protected void end() {
    if (markers.size == 0) return;
    Riiablo.batch.begin();
    for (Vector2 position : markers) {
      marker.setPosition(position.x, position.y, Align.center | Align.bottom);
      marker.draw(Riiablo.batch, 1f);
    }
    Riiablo.batch.end();
  }

  static boolean hasQuestMarker(int monsterType, CharData data) {
    short[] quests = data.getQuests(Riiablo.ACT1);
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
        record = quests[Act1BloodRavenQuest.RECORD];
        return !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
      case MonsterType.CHARSI:
        record = quests[Act1MalusQuest.RECORD];
        return !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
            || data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE);
      case MonsterType.WARRIV:
        record = quests[Act1AndarielQuest.RECORD];
        return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
      case MonsterType.DECKARDCAIN:
      case MonsterType.DECKARDCAIN_TOWN:
        return false;
      default:
        return false;
    }
  }
}
