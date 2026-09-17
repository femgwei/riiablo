package com.riiablo.engine.client.automap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.riiablo.Palettes;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.Palette;
import com.riiablo.graphics.PaletteIndexedBatch;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional renderer for the external BMP blob resources used by d2hackmap. */
public final class HackMapIconRenderer implements Disposable {
  private static final String TAG = "HackMapIconRenderer";
  private static final Pattern BLOB_FILE = Pattern.compile(
      "(?im)^\\s*(Player|Monster|Object|Missile|Item|Boss|Npc|My|Corpse|SuperUnique)"
          + "\\s+Blob\\s+File\\s*:\\s*\\\"?([^\\\"\\s/]+)");

  private static final String PLAYER = "player";
  private static final String MONSTER = "monster";
  private static final String OBJECT = "object";
  private static final String MISSILE = "missile";
  private static final String ITEM = "item";
  private static final String BOSS = "boss";
  private static final String NPC = "npc";
  private static final String MY = "my";
  private static final String CORPSE = "corpse";
  private static final String SUPER_UNIQUE = "superunique";

  private final ObjectMap<String, String> fileNames = new ObjectMap<>();
  private final ObjectMap<String, HackMapBlobCell> cells = new ObjectMap<>();
  private final ObjectMap<String, Sprite> sprites = new ObjectMap<>();
  private final Vector2 projected = new Vector2();
  private int[] palette;
  private FileHandle pluginDirectory;
  private String attemptedHome;
  private boolean requested;
  private boolean active;

  public HackMapIconRenderer() {
    resetDefaultFileNames();
  }

  public void setEnabled(boolean enabled, FileHandle d2Home) {
    if (!enabled) {
      requested = false;
      active = false;
      return;
    }
    String homePath = d2Home == null ? "" : d2Home.path();
    if (active && homePath.equals(attemptedHome)) return;
    if (requested && homePath.equals(attemptedHome)) return;
    requested = true;
    attemptedHome = homePath;

    if (!cells.isEmpty() && pluginDirectory != null) {
      active = true;
      return;
    }

    pluginDirectory = findPluginDirectory(d2Home);
    if (pluginDirectory == null) {
      log("disabled: no d2hackmap Plugin directory found for " + homePath);
      return;
    }

    resetDefaultFileNames();
    loadConfiguration(pluginDirectory.child("d2hackmap.cfg"));
    cells.clear();
    for (ObjectMap.Entry<String, String> entry : fileNames) {
      FileHandle bmp = resolveBmp(pluginDirectory, entry.value);
      if (bmp == null) continue;
      try {
        cells.put(entry.key, HackMapBlobCell.load(bmp));
      } catch (RuntimeException e) {
        if (Gdx.app != null) Gdx.app.error(TAG, e.getMessage());
      }
    }
    palette = loadAct1Palette();
    active = !cells.isEmpty();
    log("enabled=" + active + " plugin=" + pluginDirectory.path()
        + " icons=" + cells.size);
  }

  public boolean isActive() {
    return active;
  }

  public int getLoadedIconCount() {
    return cells.size;
  }

  public FileHandle getPluginDirectory() {
    return pluginDirectory;
  }

  public boolean hasIconFor(int markerType) {
    String role = roleFor(markerType);
    return active && role != null && cells.containsKey(role);
  }

  public boolean render(PaletteIndexedBatch batch, AutomapManager.EntityMarker marker,
      float alpha) {
    if (!active || batch == null || marker == null) return false;
    String role = roleFor(marker.type);
    HackMapBlobCell cell = role == null ? null : cells.get(role);
    if (cell == null && SUPER_UNIQUE.equals(role)) cell = cells.get(BOSS);
    if (cell == null) return false;

    int colorIndex = nearestPaletteIndex(marker.color);
    String spriteKey = role + ':' + colorIndex;
    Sprite sprite = sprites.get(spriteKey);
    if (sprite == null) {
      sprite = createSprite(cell, colorIndex);
      if (sprite == null) return false;
      sprites.put(spriteKey, sprite);
    }

    AutomapProjection.worldToAutomap(marker.worldX, marker.worldY, projected);
    batch.setColor(1f, 1f, 1f, alpha);
    batch.draw(sprite.region,
        projected.x - sprite.width * 0.5f,
        projected.y - sprite.height * 0.5f);
    return true;
  }

  private Sprite createSprite(HackMapBlobCell cell, int markerColorIndex) {
    try {
      DC6 dc6 = DC6.loadFromStream(
          new ByteArrayInputStream(cell.encodeDc6(markerColorIndex)));
      dc6.loadDirection(0, false);
      return new Sprite(dc6, dc6.getTexture(0, 0), cell.width, cell.height);
    } catch (RuntimeException e) {
      if (Gdx.app != null) Gdx.app.error(TAG, "Failed to create blob CellFile", e);
      return null;
    }
  }

  private int nearestPaletteIndex(Color color) {
    if (palette == null || color == null) return 0xFE;
    int red = Math.round(color.r * 255f);
    int green = Math.round(color.g * 255f);
    int blue = Math.round(color.b * 255f);
    int best = 1;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 1; i < palette.length; i++) {
      int candidate = palette[i];
      int dr = red - Palette.r(candidate);
      int dg = green - Palette.g(candidate);
      int db = blue - Palette.b(candidate);
      int distance = dr * dr + dg * dg + db * db;
      if (distance < bestDistance) {
        best = i;
        bestDistance = distance;
      }
    }
    // The palette shader reserves raw index 255; use its equivalent neighbor.
    return best == 0xFF ? 0xFE : best;
  }

  private static int[] loadAct1Palette() {
    try {
      FileHandle file = Riiablo.mpqs == null ? null : Riiablo.mpqs.resolve(Palettes.ACT1);
      return file == null ? null : Palette.loadFromFile(file).get();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void resetDefaultFileNames() {
    fileNames.clear();
    fileNames.put(PLAYER, "blobplayer");
    fileNames.put(MONSTER, "blobcross");
    fileNames.put(OBJECT, "blobchest");
    fileNames.put(MISSILE, "blobdot");
    fileNames.put(ITEM, "blobitem1");
    fileNames.put(BOSS, "blobBoss");
    fileNames.put(NPC, "blobNpc");
    fileNames.put(MY, "blobMe");
    fileNames.put(CORPSE, "blobCorpse");
  }

  private void loadConfiguration(FileHandle config) {
    if (config == null || !config.exists()) return;
    String text = new String(config.readBytes(), StandardCharsets.ISO_8859_1);
    Matcher matcher = BLOB_FILE.matcher(text);
    while (matcher.find()) {
      fileNames.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
    }
  }

  private static FileHandle resolveBmp(FileHandle directory, String configuredName) {
    if (configuredName == null || configuredName.isEmpty()) return null;
    String name = configuredName.toLowerCase(Locale.ROOT).endsWith(".bmp")
        ? configuredName : configuredName + ".bmp";
    FileHandle direct = directory.child(name);
    if (direct.exists()) return direct;
    for (FileHandle file : directory.list()) {
      if (file.name().equalsIgnoreCase(name)) return file;
    }
    return null;
  }

  static FileHandle findPluginDirectory(FileHandle d2Home) {
    if (d2Home == null) return null;
    Array<FileHandle> candidates = new Array<>();
    candidates.add(d2Home.child("Plugin"));
    FileHandle parent = d2Home.parent();
    if (parent != null) {
      candidates.add(parent.child("Plugin"));
      try {
        for (FileHandle sibling : parent.list()) {
          if (sibling.isDirectory()) candidates.add(sibling.child("Plugin"));
        }
      } catch (RuntimeException ignored) {
        // A restricted parent does not disable the direct Plugin location.
      }
    }
    for (FileHandle candidate : candidates) {
      if (isPluginDirectory(candidate)) return candidate;
    }
    return null;
  }

  private static boolean isPluginDirectory(FileHandle directory) {
    if (directory == null || !directory.exists() || !directory.isDirectory()) return false;
    if (directory.child("d2hackmap.cfg").exists()) return true;
    return resolveBmp(directory, "blobplayer") != null
        || resolveBmp(directory, "blobMe") != null;
  }

  private static String roleFor(int type) {
    switch (type) {
      case AutomapIconType.PLAYER: return MY;
      case AutomapIconType.PARTY_MEMBER: return PLAYER;
      case AutomapIconType.NPC: return NPC;
      case AutomapIconType.MONSTER:
      case AutomapIconType.CHAMPION:
      case AutomapIconType.MINION: return MONSTER;
      case AutomapIconType.UNIQUE: return SUPER_UNIQUE;
      case AutomapIconType.BOSS: return BOSS;
      case AutomapIconType.OBJECT: return OBJECT;
      case AutomapIconType.MISSILE: return MISSILE;
      case AutomapIconType.ITEM: return ITEM;
      case AutomapIconType.CORPSE: return CORPSE;
      default: return null;
    }
  }

  private static void log(String message) {
    if (Gdx.app != null) Gdx.app.log(TAG, "[AUTOMAP_HACKMAP] " + message);
  }

  @Override
  public void dispose() {
    for (Sprite sprite : sprites.values()) sprite.dc6.dispose();
    sprites.clear();
    cells.clear();
    active = false;
  }

  private static final class Sprite {
    final DC6 dc6;
    final TextureRegion region;
    final int width;
    final int height;

    Sprite(DC6 dc6, TextureRegion region, int width, int height) {
      this.dc6 = dc6;
      this.region = region;
      this.width = width;
      this.height = height;
    }
  }
}
