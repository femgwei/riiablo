package com.riiablo.engine.client.automap;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

/** Reader/writer for the native Diablo II character .map and .ma0-.ma3 files. */
public final class AutomapExplorationStore {
  public static final int MAP_SIZE = 24;
  public static final int LAYER_COUNT = 100;
  public static final int MA_DIRECTORY_SIZE = LAYER_COUNT * Integer.BYTES;
  public static final int MA_LAYER_HEADER_SIZE = 32;
  public static final int MA_CELL_SIZE = 6;

  private AutomapExplorationStore() {}

  public static final class MapSeeds {
    public int headerSize;
    public int version;
    public final int[] seeds = new int[4];

    public int seed(int difficulty) {
      if (difficulty < 0 || difficulty >= seeds.length) throw new IllegalArgumentException();
      return seeds[difficulty];
    }
  }

  /** A native cell record: uint16 cell number followed by signed int16 X/Y. */
  public static final class Cell {
    public final int cellNo;
    public final short x;
    public final short y;

    public Cell(int cellNo, short x, short y) {
      this.cellNo = cellNo;
      this.x = x;
      this.y = y;
    }
  }

  public static final class Layer {
    public int unknown;
    public final Array<Cell> floors = new Array<>();
    public final Array<Cell> walls = new Array<>();
    public final Array<Cell> objects = new Array<>();
    public final Array<Cell> extras = new Array<>();
  }

  public static final class MaFile {
    public final Layer[] layers = new Layer[LAYER_COUNT];
  }

  public static MapSeeds readMap(FileHandle file) throws IOException {
    byte[] data = file.readBytes();
    if (data.length != MAP_SIZE) throw new IOException("Invalid Diablo II .map size: " + data.length);
    MapSeeds result = new MapSeeds();
    result.headerSize = read32(data, 0);
    result.version = read32(data, 4);
    if (result.headerSize != 12 || result.version != 1) {
      throw new IOException("Unsupported Diablo II .map header");
    }
    for (int i = 0; i < result.seeds.length; i++) result.seeds[i] = read32(data, 8 + i * 4);
    return result;
  }

  public static void writeMap(FileHandle file, MapSeeds map) throws IOException {
    byte[] data = new byte[MAP_SIZE];
    write32(data, 0, map.headerSize == 0 ? 12 : map.headerSize);
    write32(data, 4, map.version == 0 ? 1 : map.version);
    for (int i = 0; i < map.seeds.length; i++) write32(data, 8 + i * 4, map.seeds[i]);
    file.writeBytes(data, false);
  }

  public static MaFile readMa(FileHandle file) throws IOException {
    byte[] data = file.readBytes();
    if (data.length < MA_DIRECTORY_SIZE) throw new EOFException("Truncated Diablo II .ma file");
    MaFile result = new MaFile();
    for (int layerNo = 0; layerNo < LAYER_COUNT; layerNo++) {
      int offset = read32(data, layerNo * 4);
      if (offset == 0) continue;
      if (offset < MA_DIRECTORY_SIZE || offset > data.length - MA_LAYER_HEADER_SIZE) {
        throw new IOException("Invalid .ma layer offset: " + offset);
      }
      Layer layer = new Layer();
      layer.unknown = read32(data, offset + 12);
      int[] sizes = {read32(data, offset + 16), read32(data, offset + 20),
          read32(data, offset + 24), read32(data, offset + 28)};
      int cursor = offset + MA_LAYER_HEADER_SIZE;
      cursor = readCells(data, cursor, sizes[0], layer.floors);
      cursor = readCells(data, cursor, sizes[1], layer.walls);
      cursor = readCells(data, cursor, sizes[2], layer.objects);
      readCells(data, cursor, sizes[3], layer.extras);
      result.layers[layerNo] = layer;
    }
    return result;
  }

  public static void writeMa(FileHandle file, MaFile ma) throws IOException {
    byte[] directory = new byte[MA_DIRECTORY_SIZE];
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (int layerNo = 0; layerNo < LAYER_COUNT; layerNo++) {
      Layer layer = ma.layers[layerNo];
      if (layer == null) continue;
      write32(directory, layerNo * 4, MA_DIRECTORY_SIZE + body.size());
      byte[] header = new byte[MA_LAYER_HEADER_SIZE];
      write32(header, 12, layer.unknown);
      write32(header, 16, byteSize(layer.floors));
      write32(header, 20, byteSize(layer.walls));
      write32(header, 24, byteSize(layer.objects));
      write32(header, 28, byteSize(layer.extras));
      body.write(header);
      writeCells(body, layer.floors);
      writeCells(body, layer.walls);
      writeCells(body, layer.objects);
      writeCells(body, layer.extras);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(directory.length + body.size());
    out.write(directory);
    body.writeTo(out);
    file.writeBytes(out.toByteArray(), false);
  }

  private static int readCells(byte[] data, int offset, int byteSize, Array<Cell> cells)
      throws IOException {
    if (byteSize < 0 || byteSize % MA_CELL_SIZE != 0 || offset + byteSize > data.length) {
      throw new IOException("Invalid .ma cell list size: " + byteSize);
    }
    for (int end = offset + byteSize; offset < end; offset += MA_CELL_SIZE) {
      cells.add(new Cell(read16(data, offset), (short) read16(data, offset + 2),
          (short) read16(data, offset + 4)));
    }
    return offset;
  }

  private static int byteSize(Array<Cell> cells) {
    return cells.size * MA_CELL_SIZE;
  }

  private static void writeCells(ByteArrayOutputStream out, Array<Cell> cells) {
    byte[] encoded = new byte[MA_CELL_SIZE];
    for (Cell cell : cells) {
      write16(encoded, 0, cell.cellNo);
      write16(encoded, 2, cell.x);
      write16(encoded, 4, cell.y);
      out.write(encoded, 0, encoded.length);
    }
  }

  private static int read16(byte[] data, int offset) {
    return (data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8;
  }

  private static int read32(byte[] data, int offset) {
    return read16(data, offset) | read16(data, offset + 2) << 16;
  }

  private static void write16(byte[] data, int offset, int value) {
    data[offset] = (byte) value;
    data[offset + 1] = (byte) (value >>> 8);
  }

  private static void write32(byte[] data, int offset, int value) {
    write16(data, offset, value);
    write16(data, offset + 2, value >>> 16);
  }
}
