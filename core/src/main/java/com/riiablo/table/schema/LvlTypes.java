package com.riiablo.table.schema;

import com.riiablo.table.annotation.Format;
import com.riiablo.table.annotation.Schema;

@Schema(indexed = true)
@SuppressWarnings("unused")
public class LvlTypes {
  @Override
  public String toString() {
    return Name;
  }

  public String Name;

  public int Id;

  @Format(
      format = "File %d",
      startIndex = 1,
      endIndex = 33)
  public String File[];

  public boolean Beta;
  public int Act;
  public boolean Expansion;
}
