package com.riiablo.codec.excel;

import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;

/** Immutable provenance and content identity for one TXT table load. */
public final class TxtTableSource {
  public enum Version { UNSPECIFIED, DIABLO_II_110F }

  public final String path;
  public final Version version;
  public final TxtTableManifest manifest;

  private TxtTableSource(String path, Version version, TxtTableManifest manifest) {
    this.path = path == null || path.isEmpty() ? "memory" : path;
    this.version = version == null ? Version.UNSPECIFIED : version;
    this.manifest = manifest;
  }

  public static TxtTableSource fromBytes(byte[] bytes) throws IOException {
    return fromBytes("memory", Version.UNSPECIFIED, bytes);
  }

  public static TxtTableSource fromHandle(FileHandle handle, byte[] bytes) throws IOException {
    return fromBytes(handle == null ? "memory" : handle.toString(), Version.UNSPECIFIED, bytes);
  }

  public static TxtTableSource fromBytes(String path, Version version, byte[] bytes)
      throws IOException {
    if (bytes == null) throw new NullPointerException("bytes");
    return new TxtTableSource(path, version, TxtTableManifest.create(bytes));
  }

  public TxtTableSource declareVersion(Version declared) {
    return new TxtTableSource(path, declared, manifest);
  }

  public boolean matches(TxtTableSource other) {
    return other != null && manifest.rawSha256.equals(other.manifest.rawSha256)
        && manifest.semanticSha256.equals(other.manifest.semanticSha256)
        && version == other.version;
  }

  @Override public String toString() {
    return "path=" + path + ",version=" + version + "," + manifest;
  }
}
