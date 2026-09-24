package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.map.d2moo.NativeShrineDistributionAudit;

/** Runs the shrine distribution audit without constructing the full game map. */
public final class OffscreenShrineAuditScreen extends ScreenAdapter {
  private final String outputDirectory;
  private boolean completed;

  public OffscreenShrineAuditScreen(String outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  @Override
  public void render(float delta) {
    if (completed) return;
    completed = true;
    int firstSeed = Integer.getInteger("riiablo.offscreen-shrine-first-seed", 0);
    int seedCount = Integer.getInteger("riiablo.offscreen-shrine-seed-count", 64);
    int difficulty = Integer.getInteger("riiablo.offscreen-shrine-difficulty", 0);
    String report = NativeShrineDistributionAudit.run(firstSeed, seedCount, difficulty);
    report += "\n" + com.riiablo.engine.server.object.ShrineEffectAudit.run();
    FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    output.child("shrine-distribution-audit.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenShrineAuditScreen", "[OFFSCREEN_SHRINE_AUDIT]\n" + report.trim());
    Gdx.app.exit();
  }
}
