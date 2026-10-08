package com.riiablo.engine.client;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.widget.Label;

@GpuSystem
@All({Monster.class, Hovered.class})
@Exclude(com.riiablo.engine.client.component.Label.class)
public class MonsterLabelManager extends BaseEntitySystem {
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<SummonedPet> mSummonedPet;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  @Wire(name = "batch")
  protected PaletteIndexedBatch batch;

  private final Vector2 tmpVec2 = new Vector2();
  private final MonsterLabel monsterLabel = new MonsterLabel();

  @Override
  protected void begin() {
    monsterLabel.setVisible(false);
  }

  @Override
  protected void end() {
    if (!monsterLabel.isVisible()) return;
    batch.begin();
    // This is the native top-center summon/monster header, not a world-space
    // nameplate. Keep it aligned with the top edge of the summon icon strip.
    tmpVec2.set(Gdx.graphics.getWidth() / 2, iso.viewportHeight * 0.05f);
    iso.unproject(tmpVec2);
    monsterLabel.setPosition(tmpVec2.x, tmpVec2.y, Align.top | Align.center);
    monsterLabel.draw(batch, 1);
    batch.end();
  }

  @Override
  protected void processSystem() {
    IntBag entities = getEntityIds();
    if (entities.size() > 0) {
      for (int i = 0, s = entities.size(); i < s; i++) {
        final int entityId = entities.get(i);
        final float percent = monsterLabel.set(entityId);
        final boolean visible = percent > 0f;
        monsterLabel.setVisible(visible);
        if (visible) break;
      }
    }
  }

  private class MonsterLabel extends Table {
    static final float HORIZONTAL_PADDING = 16;
    static final float VERTICAL_PADDING = 2;

    Table label;
    Label name;
    PaletteIndexedColorDrawable background;

    MonsterLabel() {
      label = new Table();
      label.setBackground(background = new PaletteIndexedColorDrawable(
          Riiablo.colors.darkRed) {{
        setTopHeight(VERTICAL_PADDING);
        setBottomHeight(VERTICAL_PADDING);
        setLeftWidth(HORIZONTAL_PADDING);
        setRightWidth(HORIZONTAL_PADDING);
      }});
      label.add(name = new com.riiablo.widget.Label(Riiablo.fonts.font16));
      label.pack();

      // The original header is a compact dark-red health bar with the name
      // centered inside it; there is no second monster-description row.
      add(label).center().row();
      pack();
    }

    float set(int entityId) {
      Monster monster = mMonster.get(entityId);
      MonStats.Entry monstats = monster.monstats;
      String displayName = MonsterNameResolver.displayName(monster);
      if (mSummonedPet.has(entityId)) {
        SummonedPet pet = mSummonedPet.get(entityId);
        // Monster rows such as skeleton1 and druidbear use generic native
        // names.  PetType is the authoritative summon-list name and keeps
        // the top-center label stable across revived/custom monster rows.
        String summonName = PetType.getNameForLabel(pet != null ? pet.petType : null);
        if ((displayName == null || displayName.isEmpty())
            && summonName != null && !summonName.isEmpty()) displayName = summonName;
      }
      name.setText(displayName);
      name.setColor(monster.rank == MonsterRank.CHAMPION
          ? Riiablo.colors.blue
          : MonsterRank.isUnique(monster.rank) ? Riiablo.colors.gold : Riiablo.colors.white);

      AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
      if (wrapper == null || wrapper.attrs == null) return 0f;
      Attributes attrs = wrapper.attrs;
      // Attributes#get(short) returns a reused temporary reference. Keep
      // separate refs or the second lookup aliases both stats and the ratio
      // stays at 1.0 for every monster.
      StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
      StatRef max = attrs.get(Stat.maxhp, StatRef.obtain());
      if (hp == null || max == null) return 0f;
      final float hitpoints = hp.asFixed();
      final float maxhp = max.asFixed();
      if (!Float.isFinite(hitpoints) || !Float.isFinite(maxhp) || maxhp <= 0f) return 0f;
      return background.percent = Math.max(0f, Math.min(1f, hitpoints / maxhp));
    }
  }
}
