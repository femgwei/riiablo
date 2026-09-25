package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.riiablo.Riiablo;
import com.riiablo.D2Language;
import com.riiablo.Fonts;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.FontBaselineCalibration;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.widget.Label;

/**
 * Deterministic font-baseline probe for the native mercenary panel fonts.
 *
 * <p>The output contains both direct BitmapFont baselines and actual Riiablo
 * Labels with the same fixed 16px actor box used by HirelingPanel. Red guide
 * lines are the requested baseline; the metrics report makes a font-atlas
 * offset distinguishable from a Scene2D layout offset.</p>
 */
public final class OffscreenFontBaselineScreen extends ScreenAdapter {
  private static final int WIDTH = 854;
  private static final int HEIGHT = 480;
  private static final int BASELINE_Y = 365;
  private static final int BOX_Y = 270;
  private static final int CORRECTED_BOX_Y = 150;
  private static final int BOX_HEIGHT = 16;

  private final FileHandle output;
  private FrameBuffer frameBuffer;
  private ShapeRenderer shapes;
  private Matrix4 projection;
  private int frame;

  public OffscreenFontBaselineScreen(String outputDirectory) {
    output = Gdx.files.absolute(new FileHandle(outputDirectory).file().getAbsolutePath());
  }

  @Override
  public void show() {
    output.mkdirs();
    // The production game stages these fonts until the first gameplay screen.
    Riiablo.fonts.initializeGameplayFonts();
    frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
    shapes = new ShapeRenderer();
    projection = new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
  }

  @Override
  public void render(float delta) {
    if (frame++ > 0) return;
    frameBuffer.begin();
    Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
    Gdx.gl.glClearColor(0.035f, 0.035f, 0.035f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

    shapes.setProjectionMatrix(projection);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.08f, 0.08f, 0.08f, 1f);
    shapes.rect(0, 0, WIDTH, HEIGHT);
    shapes.setColor(0.20f, 0.20f, 0.20f, 1f);
    shapes.rect(20, 70, WIDTH - 40, 2);
    shapes.rect(20, BOX_Y, WIDTH - 40, 1);
    shapes.rect(20, CORRECTED_BOX_Y, WIDTH - 40, 1);
    shapes.setColor(0.90f, 0.18f, 0.12f, 1f);
    shapes.rect(28, BASELINE_Y, WIDTH - 56, 1);
    shapes.end();

    PaletteIndexedBatch batch = Riiablo.batch;
    batch.setProjectionMatrix(projection);
    batch.setPalette(Riiablo.palettes == null ? null : Riiablo.palettes.units);
    batch.begin();
    BitmapFont reportFont = Riiablo.fonts.fontformal12;
    reportFont.setColor(1f, 0.85f, 0.45f, 1f);
    reportFont.draw(batch, "FONT BASELINE PROBE  red = requested baseline", 28, 445);
    reportFont.draw(batch, "Direct draw at identical y", 58, 405);
    reportFont.draw(batch, "Label Align.bottom in 16px box", 440, 405);

    FontTBL.BitmapFont cjk = Riiablo.fonts.ReallyTheLastSucker;
    FontTBL.BitmapFont number = Riiablo.fonts.font8;
    Fonts englishFonts = new Fonts(Riiablo.assets, D2Language.ENGLISH);
    drawDirect(batch, cjk, "生命", 58, BASELINE_Y);
    drawDirect(batch, number, "45/45", 250, BASELINE_Y);

    Label cjkLabel = new Label("生命", cjk);
    cjkLabel.setAutoSize(false);
    cjkLabel.setBounds(440, BOX_Y, 70, BOX_HEIGHT);
    cjkLabel.setAlignment(Align.left | Align.bottom);
    Label numberLabel = new Label("45/45", number);
    numberLabel.setAutoSize(false);
    numberLabel.setBounds(560, BOX_Y, 81, BOX_HEIGHT);
    numberLabel.setAlignment(Align.center | Align.bottom);
    cjkLabel.draw(batch, 1f);
    numberLabel.draw(batch, 1f);

    reportFont.draw(batch, "Label after measured compact correction", 440, 185);
    Label correctedCjk = new Label("生命", cjk);
    correctedCjk.setAutoSize(false);
    correctedCjk.setBounds(440, CORRECTED_BOX_Y, 70, BOX_HEIGHT);
    correctedCjk.setAlignment(Align.left | Align.bottom);
    Label correctedNumber = new Label("45/45", number);
    correctedNumber.setAutoSize(false);
    float compactOffset = Riiablo.fonts.labelBaselineAlignmentOffset(cjk, number);
    correctedNumber.setBounds(560, CORRECTED_BOX_Y + compactOffset, 81, BOX_HEIGHT);
    correctedNumber.setAlignment(Align.center | Align.bottom);
    correctedCjk.draw(batch, 1f);
    correctedNumber.draw(batch, 1f);

    reportFont.draw(batch, "ReallyTheLastSucker", 58, 225);
    reportFont.draw(batch, "font8", 250, 225);
    reportFont.draw(batch, "CJK + number", 440, 225);
    batch.end();

    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
    frameBuffer.end();
    savePng(pixels);
    writeReport(cjk, number, englishFonts);
    Gdx.app.log("OffscreenFontBaselineScreen", "[FONT_BASELINE_PROBE] output=" + output.path());
    Gdx.app.exit();
  }

  private static void drawDirect(PaletteIndexedBatch batch, BitmapFont font,
      String text, float x, float baseline) {
    font.setColor(1f, 1f, 1f, 1f);
    font.draw(batch, text, x, baseline);
  }

  private void writeReport(FontTBL.BitmapFont cjk, FontTBL.BitmapFont number, Fonts englishFonts) {
    StringBuilder out = new StringBuilder();
    out.append("viewport=").append(WIDTH).append('x').append(HEIGHT).append('\n');
    out.append("baselineY=").append(BASELINE_Y).append('\n');
    appendMetrics(out, "ReallyTheLastSucker", cjk);
    appendMetrics(out, "font8", number);
    out.append("calibration.font8=")
        .append(FontBaselineCalibration.correction(englishFonts.font8, number)).append('\n');
    out.append("calibration.font16=")
        .append(FontBaselineCalibration.correction(englishFonts.font16, Riiablo.fonts.font16)).append('\n');
    out.append("calibration.fontformal11=")
        .append(FontBaselineCalibration.correction(englishFonts.fontformal11,
            Riiablo.fonts.fontformal11)).append('\n');
    out.append("calibration.font6=")
        .append(FontBaselineCalibration.correction(englishFonts.font6, Riiablo.fonts.font6)).append('\n');
    out.append("calibration.font24=")
        .append(FontBaselineCalibration.correction(englishFonts.font24, Riiablo.fonts.font24)).append('\n');
    out.append("calibration.font30=")
        .append(FontBaselineCalibration.correction(englishFonts.font30, Riiablo.fonts.font30)).append('\n');
    out.append("calibration.font42=")
        .append(FontBaselineCalibration.correction(englishFonts.font42, Riiablo.fonts.font42)).append('\n');
    out.append("calibration.fontformal10=")
        .append(FontBaselineCalibration.correction(englishFonts.fontformal10,
            Riiablo.fonts.fontformal10)).append('\n');
    out.append("calibration.fontformal12=")
        .append(FontBaselineCalibration.correction(englishFonts.fontformal12,
            Riiablo.fonts.fontformal12)).append('\n');
    out.append("calibration.fontexocet10=")
        .append(FontBaselineCalibration.correction(englishFonts.fontexocet10,
            Riiablo.fonts.fontexocet10)).append('\n');
    out.append("calibration.fontridiculous=")
        .append(FontBaselineCalibration.correction(englishFonts.fontridiculous,
            Riiablo.fonts.fontridiculous)).append('\n');
    out.append("calibration.ReallyTheLastSucker=")
        .append(FontBaselineCalibration.correction(englishFonts.ReallyTheLastSucker,
            cjk)).append('\n');
    out.append("hirelingCompactValueOffset=")
        .append(Riiablo.fonts.labelBaselineAlignmentOffset(cjk, number))
        .append('\n');
    out.append("hirelingHealthY=202\n");
    output.child("font-baseline.txt").writeString(out.toString(), false, "UTF-8");
  }

  private static void appendMetrics(StringBuilder out, String name, BitmapFont font) {
    BitmapFont.BitmapFontData data = font.getData();
    BitmapFont.Glyph glyph = data.getGlyph(name.equals("font8") ? '4' : '生');
    out.append(name)
        .append(" lineHeight=").append(data.lineHeight)
        .append(" ascent=").append(data.ascent)
        .append(" descent=").append(data.descent)
        .append(" down=").append(data.down)
        .append(" glyphYoffset=").append(glyph == null ? "missing" : glyph.yoffset)
        .append('\n');
  }

  private void savePng(Pixmap pixels) {
    Pixmap flipped = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
    for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
      flipped.drawPixel(x, HEIGHT - y - 1, pixels.getPixel(x, y));
    pixels.dispose();
    PixmapIO.writePNG(output.child("font-baseline.png"), flipped);
    flipped.dispose();
  }

  @Override
  public void dispose() {
    if (frameBuffer != null) frameBuffer.dispose();
    if (shapes != null) shapes.dispose();
  }
}
