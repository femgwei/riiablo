package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HackMapBlobCellTest {
  @TempDir Path temp;

  @Test void convertsBottomUpIndexedBmpToNativeCellFileRle() throws Exception {
    Path file = temp.resolve("blobMe.bmp");
    Files.write(file, indexedBmp(8));

    HackMapBlobCell cell = HackMapBlobCell.load(new FileHandle(file.toFile()));
    assertEquals(3, cell.width);
    assertEquals(2, cell.height);
    assertArrayEquals(new byte[] {(byte) 255, 0, 104, 0, (byte) 255, 16},
        cell.copyPixels());

    byte[] dc6 = cell.encodeDc6(0x62);
    ByteBuffer header = ByteBuffer.wrap(dc6).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(6, header.getInt(0));
    assertEquals(1, header.getInt(16));
    assertEquals(1, header.getInt(20));
    assertEquals(28, header.getInt(24));
    assertEquals(3, header.getInt(32));
    assertEquals(2, header.getInt(36));
    assertEquals(11, header.getInt(56));
    assertArrayEquals(new byte[] {
        (byte) 0x81, 2, 0x62, 16, (byte) 0x80,
        1, 0x62, (byte) 0x81, 1, 104, (byte) 0x80
    }, java.util.Arrays.copyOfRange(dc6, 60, 71));
  }

  @Test void rejectsNonIndexedBmp() throws Exception {
    Path file = temp.resolve("rgb.bmp");
    Files.write(file, indexedBmp(24));
    assertThrows(GdxRuntimeException.class,
        () -> HackMapBlobCell.load(new FileHandle(file.toFile())));
  }

  @Test void findsPluginInSiblingDiabloDirectory() throws Exception {
    Path home = Files.createDirectories(temp.resolve("Diablo II 1.10F"));
    Path plugin = Files.createDirectories(temp.resolve("Diablo II").resolve("Plugin"));
    Files.write(plugin.resolve("d2hackmap.cfg"), new byte[0]);
    FileHandle found = HackMapIconRenderer.findPluginDirectory(
        new FileHandle(home.toFile()));
    assertNotNull(found);
    assertEquals(plugin.toFile().getCanonicalPath(), found.file().getCanonicalPath());
  }

  private static byte[] indexedBmp(int bitsPerPixel) {
    int width = 3;
    int height = 2;
    int pixelOffset = 1078;
    int stride = 4;
    ByteBuffer bmp = ByteBuffer.allocate(pixelOffset + stride * height)
        .order(ByteOrder.LITTLE_ENDIAN);
    bmp.put((byte) 'B').put((byte) 'M');
    bmp.putInt(bmp.capacity());
    bmp.putInt(0);
    bmp.putInt(pixelOffset);
    bmp.putInt(40);
    bmp.putInt(width);
    bmp.putInt(height);
    bmp.putShort((short) 1);
    bmp.putShort((short) bitsPerPixel);
    bmp.putInt(0);
    bmp.putInt(stride * height);
    bmp.putInt(0).putInt(0).putInt(0).putInt(0);
    bmp.position(pixelOffset);
    // BMP stores positive-height rows bottom-up.
    bmp.put((byte) 0).put((byte) 255).put((byte) 16).put((byte) 0);
    bmp.put((byte) 255).put((byte) 0).put((byte) 104).put((byte) 0);
    return bmp.array();
  }
}
