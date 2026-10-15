package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.NativeHirelingExperienceTable;
import com.riiablo.engine.server.npc.HirelingOffer;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.screen.panel.HireMercenaryPanel;

/** Renders the actual production roster with real MPQ data, without starting a game. */
public final class OffscreenHireMercenaryScreen extends ScreenAdapter {
  private final FileHandle output;
  private Stage stage;
  private FrameBuffer framebuffer;
  private boolean complete;

  public OffscreenHireMercenaryScreen(String directory) { output = new FileHandle(directory); }

  @Override public void show() {
    output.mkdirs();
    Riiablo.fonts.initializeGameplayFonts();
    Riiablo.charData.clear().set(0, false, "HirePreview", Riiablo.AMAZON);
    Riiablo.charData.getStats().base().put(Stat.gold, 1637);
    Riiablo.charData.getStats().aggregate().put(Stat.gold, 1637);
    stage = new Stage(new FitViewport(800, 600), Riiablo.batch);
    stage.getViewport().update(800, 600, true);
    framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, 800, 600, false);
    MercenaryManager manager = new MercenaryManager();
    manager.setNativeExperienceTable(NativeHirelingExperienceTable.load());
    manager.setRosterDifficulty(Integer.getInteger("riiablo.hire-preview-difficulty", 0));
    Array<HirelingOffer> offers = manager.hirelingOffers(0,
        Integer.getInteger("riiablo.hire-preview-npc", 198), 70);
    if (offers.size < 3) throw new IllegalStateException("Native hire roster is empty");
    HireMercenaryPanel panel = new HireMercenaryPanel();
    stage.addActor(panel);
    panel.presentOffers(offers);
    StringBuilder manifest = new StringBuilder("viewport=800x600\n");
    for (HirelingOffer offer : offers) manifest.append(offer.hirelingId).append('\t')
        .append(offer.description).append('\t').append(offer.level).append('\t')
        .append(offer.life).append('\t').append(offer.defense).append('\t').append(offer.cost).append('\n');
    output.child("roster.tsv").writeString(manifest.toString(), false, "UTF-8");
  }

  @Override public void render(float delta) {
    if (complete) return;
    complete = true;
    framebuffer.begin();
    Gdx.gl.glViewport(0, 0, 800, 600);
    Gdx.gl.glClearColor(0.16f, 0.14f, 0.11f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Riiablo.batch.setPalette(Riiablo.palettes.units);
    stage.act(0);
    stage.draw();
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 800, 600);
    PixmapIO.writePNG(output.child("hire-mercenary.png"), pixels, -1, true);
    pixels.dispose();
    framebuffer.end();
    Gdx.app.log("HirePreview", "[HIRE_PREVIEW] " + output.path());
    Gdx.app.exit();
  }

  @Override public void dispose() {
    if (stage != null) {
      for (com.badlogic.gdx.scenes.scene2d.Actor actor : stage.getActors())
        if (actor instanceof com.badlogic.gdx.utils.Disposable)
          ((com.badlogic.gdx.utils.Disposable) actor).dispose();
      stage.dispose();
    }
    if (framebuffer != null) framebuffer.dispose();
  }
}
