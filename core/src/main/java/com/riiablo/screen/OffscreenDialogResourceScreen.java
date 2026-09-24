package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.riiablo.Riiablo;
import com.riiablo.Palettes;
import com.riiablo.codec.DC6;
import com.riiablo.codec.Palette;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot exporter for the native DC6 resources used by dialogs and modal UI.
 * It exports every raw frame and the same 256-page composition used by the
 * DC6 loader, so the result is useful for deciding whether a resource is a
 * complete panel or only a border/button piece.
 */
public final class OffscreenDialogResourceScreen extends ScreenAdapter {
  private static final String PALETTE = Palettes.UNITS;

  private static final Resource[] RESOURCES = {
      r("conversation", "dialogbackground", "data\\global\\ui\\MENU\\dialogbackground.dc6"),
      r("conversation", "boxpieces", "data\\global\\ui\\MENU\\boxpieces.dc6"),
      r("in-game-gold-dialog", "buttontempok", "data\\global\\ui\\MENU\\buttontempok.dc6"),
      r("in-game-gold-dialog", "buttontempcancel", "data\\global\\ui\\MENU\\buttontempcancel.dc6"),
      r("in-game-gold-dialog", "okcancelbtn", "data\\global\\ui\\MENU\\okcancelbtn.dc6"),
      r("in-game-gold-dialog", "goldbtn", "data\\global\\ui\\MENU\\goldbtn.dc6"),
      r("popup-background", "TileableDialog", "data\\global\\ui\\FrontEnd\\TileableDialog.dc6"),
      r("popup-background", "PopUp_340x224", "data\\global\\ui\\FrontEnd\\PopUp_340x224.dc6"),
      r("popup-background", "PopUpLarge", "data\\global\\ui\\FrontEnd\\PopUpLarge.dc6"),
      r("popup-background", "PopUpLargest", "data\\global\\ui\\FrontEnd\\PopUpLargest.dc6"),
      r("popup-background", "PopUpLargest2", "data\\global\\ui\\FrontEnd\\PopUpLargest2.dc6"),
      r("popup-background", "PopUpLargestNoHoles", "data\\global\\ui\\FrontEnd\\PopUpLargestNoHoles.dc6"),
      r("popup-background", "PopupWide", "data\\global\\ui\\FrontEnd\\PopupWide.dc6"),
      r("popup-background", "PopUpWideNoHoles", "data\\global\\ui\\FrontEnd\\PopUpWideNoHoles.dc6"),
      r("popup-buttons", "PopUpOk", "data\\global\\ui\\FrontEnd\\PopUpOk.dc6"),
      r("popup-buttons", "PopUpOk2", "data\\global\\ui\\FrontEnd\\PopUpOk2.dc6"),
      r("popup-buttons", "PopUpOKCancel", "data\\global\\ui\\FrontEnd\\PopUpOKCancel.dc6"),
      r("popup-buttons", "PopUpOKCancel2", "data\\global\\ui\\FrontEnd\\PopUpOKCancel2.dc6"),
      r("text-input", "EditTextBoxWide", "data\\global\\ui\\FrontEnd\\EditTextBoxWide.dc6"),
      r("text-input", "textbox", "data\\global\\ui\\FrontEnd\\textbox.dc6"),
      r("text-input", "textbox2", "data\\global\\ui\\FrontEnd\\textbox2.dc6"),
      r("menu-popup", "BIGMENU_popupok", "data\\global\\ui\\BIGMENU\\popupok.dc6"),
      r("menu-popup", "CharSelect_PopUpOKCancel", "data\\global\\ui\\CharSelect\\PopUpOKCancel.dc6"),
      r("localized-error", "CHI_gamefailbadcharquest", "data\\local\\ui\\CHI\\gamefailbadcharquest.dc6"),
      r("localized-error", "CHI_gamefailurebadhireables", "data\\local\\ui\\CHI\\gamefailurebadhireables.dc6"),
  };

  private final FileHandle output;
  private boolean completed;

  public OffscreenDialogResourceScreen(String outputDirectory) {
    output = Gdx.files.absolute(new FileHandle(outputDirectory).file().getAbsolutePath());
  }

  @Override
  public void render(float delta) {
    if (completed) return;
    completed = true;
    output.mkdirs();
    FileHandle imageDir = output.child("images");
    if (imageDir.exists()) imageDir.deleteDirectory();
    imageDir.mkdirs();
    Palette palette = Palette.loadFromFile(Riiablo.mpqs.resolve(PALETTE));
    StringBuilder manifest = new StringBuilder(8192);
    manifest.append("palette=").append(PALETTE).append('\n');
    manifest.append("format=RGBA PNG; index 0 is transparent\n");

    int exported = 0;
    for (Resource resource : RESOURCES) {
      FileHandle source = Riiablo.mpqs.resolve(resource.path);
      if (source == null) {
        manifest.append(resource.category).append('\t').append(resource.name)
            .append("\tMISSING\t").append(resource.path).append('\n');
        continue;
      }
      DC6 dc6 = null;
      try {
        dc6 = DC6.loadFromFile(source);
        List<Pixmap> frames = new ArrayList<>();
        for (int d = 0; d < dc6.getNumDirections(); d++) {
          for (int f = 0; f < dc6.getNumFramesPerDir(); f++) {
            Pixmap image = rgba(dc6.getPixmap(d, f), palette);
            frames.add(image);
            FileHandle file = imageDir.child(resource.name + "_d" + d + "_f"
                + String.format("%03d", f) + ".png");
            PixmapIO.writePNG(file, image);
            exported++;
          }
        }
        int pageCount = writeComposed(resource.name, dc6, frames, imageDir);
        exported += pageCount;
        manifest.append(resource.category).append('\t').append(resource.name)
            .append("\tOK\tdirections=")
            .append(dc6.getNumDirections()).append("\tframes=")
            .append(dc6.getNumFramesPerDir()).append("\tcomposedPages=")
            .append(pageCount).append("\tpath=").append(resource.path).append('\n');
        for (Pixmap frame : frames) frame.dispose();
      } catch (Throwable t) {
        manifest.append(resource.category).append('\t').append(resource.name).append("\tERROR\t")
            .append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append('\n');
      } finally {
        if (dc6 != null) dc6.dispose();
      }
    }
    manifest.append("exportedImages=").append(exported).append('\n');
    output.child("manifest.tsv").writeString(manifest.toString(), false, "UTF-8");
    Gdx.app.log("OffscreenDialogResourceScreen", "[DIALOG_RESOURCE_EXPORT] output="
        + output.path() + " images=" + exported);
    Gdx.app.exit();
  }

  private static int writeComposed(String name, DC6 dc6, List<Pixmap> frames, FileHandle dir) {
    int pages = 0;
    int count = dc6.getNumFramesPerDir();
    for (int d = 0; d < dc6.getNumDirections(); d++) {
      int base = d * count;
      int columns = 0;
      for (int f = 0; f < count; f++) {
        columns++;
        if (dc6.getPixmap(d, f).getWidth() < 256) break;
      }
      int rows = 0;
      for (int f = 0; f < count; f += columns) {
        rows++;
        if (dc6.getPixmap(d, f).getHeight() < 256) break;
      }
      int perPage = Math.max(1, rows * columns);
      int pageCount = Math.max(1, count / perPage);
      for (int page = 0; page < pageCount; page++) {
        int first = page * perPage;
        int width = 0;
        for (int c = 0; c < columns; c++) width += dc6.getPixmap(d, first + c).getWidth();
        int height = 0;
        for (int r = 0; r < rows; r++) height += dc6.getPixmap(d, first + r * columns).getHeight();
        Pixmap composed = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        composed.setColor(0);
        composed.fill();
        int y = 0;
        for (int r = 0; r < rows; r++) {
          int x = 0;
          for (int c = 0; c < columns; c++) {
            Pixmap frame = frames.get(base + first + r * columns + c);
            composed.drawPixmap(frame, x, y);
            x += frame.getWidth();
          }
          y += frames.get(base + first + r * columns).getHeight();
        }
        PixmapIO.writePNG(dir.child(name + "_d" + d + "_page" + page + ".png"), composed);
        composed.dispose();
        pages++;
      }
    }
    return pages;
  }

  private static Pixmap rgba(Pixmap indexed, Palette palette) {
    int width = indexed.getWidth();
    int height = indexed.getHeight();
    Pixmap output = new Pixmap(width, height, Pixmap.Format.RGBA8888);
    java.nio.ByteBuffer pixels = indexed.getPixels().duplicate();
    int[] colors = palette.colors;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int index = pixels.get(y * width + x) & 0xFF;
        output.drawPixel(x, y, colors[index]);
      }
    }
    return output;
  }

  private static Resource r(String category, String name, String path) {
    return new Resource(category, name, path);
  }

  private static final class Resource {
    final String category;
    final String name;
    final String path;
    Resource(String category, String name, String path) {
      this.category = category;
      this.name = name;
      this.path = path;
    }
  }
}
