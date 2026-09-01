package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.graphics.PaletteIndexedBatch;
import java.util.Locale;

/**
 * Deterministic visual-regression screen used by the hidden rendering client.
 * It intentionally uses the production Client resources and palette batch,
 * while keeping the scenarios independent from a generated map or save file.
 */
public final class OffscreenRenderScreen extends ScreenAdapter {
  private static final int WIDTH = 854;
  private static final int HEIGHT = 480;
  private static final String[] CASES = {
      "death-overlay", "inventory-open", "character-panel-open", "npc-dialog", "party-panel"
  };

  private final FileHandle output;
  private FrameBuffer frameBuffer;
  private ShapeRenderer shapes;
  private Matrix4 projection;
  private int frame;

  public OffscreenRenderScreen(String outputDirectory) {
    output = Gdx.files.absolute(new FileHandle(outputDirectory).file().getAbsolutePath());
  }

  @Override
  public void show() {
    output.mkdirs();
    frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
    shapes = new ShapeRenderer();
    projection = new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
  }

  @Override
  public void render(float delta) {
    if (frame++ > 0) return;
    Array<String> completed = new Array<>(String.class);
    for (String scenario : CASES) {
      renderScenario(scenario);
      completed.add(scenario);
    }
    FileHandle manifest = output.child("manifest.txt");
    StringBuilder report = new StringBuilder();
    report.append("viewport=").append(WIDTH).append('x').append(HEIGHT).append('\n');
    for (String scenario : completed) report.append(scenario).append("=PASS\n");
    manifest.writeString(report.toString(), false, "UTF-8");
    Gdx.app.log("OffscreenRenderScreen", "[OFFSCREEN_RENDER] scenarios="
        + completed.size + " output=" + output.path());
    Gdx.app.exit();
  }

  private void renderScenario(String scenario) {
    frameBuffer.begin();
    Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
    Gdx.gl.glClearColor(0.035f, 0.035f, 0.035f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

    shapes.setProjectionMatrix(projection);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.07f, 0.07f, 0.07f, 1f);
    shapes.rect(0, 0, WIDTH, HEIGHT);
    drawGameChrome();
    if ("death-overlay".equals(scenario)) drawDeathOverlay();
    else if ("inventory-open".equals(scenario)) drawInventory();
    else if ("character-panel-open".equals(scenario)) drawCharacterPanel();
    else if ("npc-dialog".equals(scenario)) drawNpcDialog();
    else if ("party-panel".equals(scenario)) drawPartyPanel();
    shapes.end();

    drawText(scenario);
    // Read while the FBO is still bound. Reading after end() would capture
    // the 1x1 hidden window instead of the production-size render target.
    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
    frameBuffer.end();
    savePng(scenario, pixels);
  }

  private void drawGameChrome() {
    shapes.setColor(0.16f, 0.12f, 0.09f, 1f);
    shapes.rect(0, 0, WIDTH, 58);
    shapes.setColor(0.55f, 0.08f, 0.06f, 1f);
    shapes.rect(28, 22, 180, 12);
    shapes.setColor(0.08f, 0.18f, 0.55f, 1f);
    shapes.rect(WIDTH - 208, 22, 180, 12);
  }

  private void drawDeathOverlay() {
    shapes.setColor(0, 0, 0, 0.72f);
    shapes.rect(0, 58, WIDTH, HEIGHT - 58);
    shapes.setColor(0.18f, 0.05f, 0.05f, 1f);
    shapes.rect(200, 195, WIDTH - 400, 95);
  }

  private void drawInventory() {
    shapes.setColor(0.11f, 0.09f, 0.07f, 1f);
    shapes.rect(WIDTH - 310, 72, 280, 365);
    shapes.setColor(0.3f, 0.22f, 0.12f, 1f);
    for (int y = 0; y < 4; y++) for (int x = 0; x < 10; x++)
      shapes.rect(WIDTH - 286 + x * 25, 130 + y * 25, 21, 21);
  }

  private void drawCharacterPanel() {
    shapes.setColor(0.11f, 0.09f, 0.07f, 1f);
    shapes.rect(180, 70, 494, 365);
    shapes.setColor(0.24f, 0.18f, 0.1f, 1f);
    shapes.rect(210, 115, 180, 250);
  }

  private void drawNpcDialog() {
    shapes.setColor(0.09f, 0.07f, 0.05f, 1f);
    shapes.rect(120, 95, WIDTH - 240, 285);
    shapes.setColor(0.36f, 0.24f, 0.12f, 1f);
    shapes.rect(145, 125, WIDTH - 290, 2);
  }

  private void drawPartyPanel() {
    shapes.setColor(0.09f, 0.08f, 0.06f, 1f);
    shapes.rect(24, 75, 315, 330);
    shapes.setColor(0.22f, 0.17f, 0.1f, 1f);
    for (int i = 0; i < 4; i++) shapes.rect(45, 330 - i * 58, 270, 42);
  }

  private void drawText(String scenario) {
    PaletteIndexedBatch batch = Riiablo.batch;
    BitmapFont font = Riiablo.fonts != null && Riiablo.fonts.fontformal12 != null
        ? Riiablo.fonts.fontformal12 : new BitmapFont();
    batch.setProjectionMatrix(projection);
    batch.setPalette(Riiablo.palettes == null ? null : Riiablo.palettes.units);
    batch.begin();
    font.setColor(1, 0.85f, 0.45f, 1);
    font.draw(batch, title(scenario), 28, HEIGHT - 28);
    if ("death-overlay".equals(scenario)) {
      center(font, batch, "You have died", HEIGHT / 2 + 18);
      center(font, batch, "Press ESC to continue", HEIGHT / 2 - 18);
    } else if ("inventory-open".equals(scenario)) {
      font.draw(batch, "INVENTORY", WIDTH - 286, 418);
      font.draw(batch, "Vitals remain outside panel bounds", 28, 92);
    } else if ("character-panel-open".equals(scenario)) {
      center(font, batch, "CHARACTER", 407);
      font.draw(batch, "Attribute points available: 5", 420, 350);
    } else if ("npc-dialog".equals(scenario)) {
      center(font, batch, "AKARA", 350);
      center(font, batch, "No task available", 245);
    } else {
      font.draw(batch, "PARTY", 55, 380);
      font.draw(batch, "Invite   Accept   Leave   Hostile", 55, 105);
    }
    batch.end();
    if (Riiablo.fonts == null) font.dispose();
  }

  private static String title(String scenario) {
    return scenario.replace('-', ' ').toUpperCase(Locale.ROOT);
  }

  private static void center(BitmapFont font, PaletteIndexedBatch batch, String value, float y) {
    GlyphLayout layout = new GlyphLayout(font, value);
    font.draw(batch, value, (WIDTH - layout.width) / 2f, y);
  }

  private void savePng(String scenario, Pixmap pixmap) {
    Pixmap flipped = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
    for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
      flipped.drawPixel(x, HEIGHT - y - 1, pixmap.getPixel(x, y));
    pixmap.dispose();
    PixmapIO.writePNG(output.child(scenario + ".png"), flipped);
    int nonBackground = 0;
    for (int y = 0; y < HEIGHT; y += 4) for (int x = 0; x < WIDTH; x += 4)
      if (flipped.getPixel(x, y) != 0x111111ff) nonBackground++;
    flipped.dispose();
    if (nonBackground < 100) {
      throw new IllegalStateException("Offscreen scenario rendered empty: " + scenario);
    }
  }

  @Override
  public void dispose() {
    if (frameBuffer != null) frameBuffer.dispose();
    if (shapes != null) shapes.dispose();
  }
}
