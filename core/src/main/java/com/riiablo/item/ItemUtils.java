package com.riiablo.item;

import com.badlogic.gdx.utils.GdxRuntimeException;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.ItemEntry;

public class ItemUtils {
  private ItemUtils() {}

  @SuppressWarnings("unchecked")
  public static <T extends ItemEntry> T getBase(String code) {
    return getBase(Riiablo.files, code);
  }

  @SuppressWarnings("unchecked")
  public static <T extends ItemEntry> T getBase(com.riiablo.Files files, String code) {
    ItemEntry entry;
    if ((entry = files.armor  .get(code)) != null) return (T) entry;
    if ((entry = files.weapons.get(code)) != null) return (T) entry;
    if ((entry = files.misc   .get(code)) != null) return (T) entry;
    throw new GdxRuntimeException("Unable to locate entry for code: " + code);
  }

  public static int getBaseIndex(String code) {
    return getBaseIndex(Riiablo.files, code);
  }

  public static int getBaseIndex(com.riiablo.Files files, String code) {
    ItemEntry entry;
    if ((entry = files.armor  .get(code)) != null) return files.armor  .index(entry.code);
    if ((entry = files.weapons.get(code)) != null) return files.weapons.index(entry.code);
    if ((entry = files.misc   .get(code)) != null) return files.misc   .index(entry.code);
    throw new GdxRuntimeException("Unable to locate entry for code: " + code);
  }
}
