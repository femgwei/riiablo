package com.riiablo.item;

import com.riiablo.codec.excel.ItemTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Instance-owned transitive ItemTypes equivalences; safe to load without a game host. */
public final class ItemTypeCatalog {
  final ItemTypes table;
  private final Map<String, Type> types = new HashMap<>();
  private final Set<String> building = new HashSet<>();

  public ItemTypeCatalog(ItemTypes table) {
    this.table = Objects.requireNonNull(table, "table");
    for (ItemTypes.Entry row : table) get(row.Code);
  }

  public synchronized Type get(String code) {
    if (building.contains(code)) throw new IllegalArgumentException("cyclic item type: " + code);
    Type cached = types.get(code);
    if (cached != null) return cached;
    int id = table.index(code);
    if (id < 0) throw new IllegalArgumentException("unknown item type: " + code);
    ItemTypes.Entry row = table.get(id);
    Type type = new Type(this);
    type.set(id);
    building.add(code);
    try {
      if (row.Equiv != null) for (String equiv : row.Equiv) {
        if (equiv == null || equiv.isEmpty()) break;
        type.or(get(equiv));
      }
      types.put(code, type);
      return type;
    } finally { building.remove(code); }
  }

  public Type get(ItemTypes.Entry row) { return get(row.Code); }
  public Type get(ItemTypes.Entry first, ItemTypes.Entry second) {
    return Type.get(get(first), get(second));
  }
}
