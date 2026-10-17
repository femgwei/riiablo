package com.riiablo.item;

import com.badlogic.gdx.utils.Bits;
import com.riiablo.codec.excel.ItemTypes;

/** A type closure bound to the table that defined its ordinals. */
public class Type extends Bits {
  final ItemTypeCatalog catalog;

  Type(ItemTypeCatalog catalog) { super(0x7F); this.catalog = catalog; }

  /** Legacy host entry; explicit callers use ItemTypeCatalog directly. */
  public static Type get(String code) { return com.riiablo.Riiablo.files.itemTypes().get(code); }
  public static Type get(ItemTypes.Entry row) { return get(row.Code); }
  public static Type get(ItemTypes.Entry first, ItemTypes.Entry second) {
    return get(get(first), get(second));
  }
  public static Type get(Type first, Type second) {
    if (first.catalog.table != second.catalog.table) {
      throw new IllegalArgumentException("cannot combine different item type tables");
    }
    if (second.is(NONE)) return first;
    Type combined = new Type(first.catalog);
    combined.or(first);
    combined.or(second);
    return combined;
  }

  public boolean is(int ordinal) { return ordinal >= 0 && get(ordinal); }
  public boolean is(String code) { return is(catalog.table.index(code)); }
  @Override public String toString() { return getOrdinalString(); }
  public String getOrdinalString() {
    StringBuilder result = new StringBuilder();
    for (int i = nextSetBit(0); i >= 0; i = nextSetBit(i + 1)) {
      if (result.length() > 0) result.append(',');
      result.append(catalog.table.get(i).Code);
    }
    return result.toString();
  }
  public String getHexString() {
    short[] words = new short[(numBits() + Short.SIZE - 1) / Short.SIZE];
    for (int i = nextSetBit(0); i >= 0; i = nextSetBit(i + 1)) {
      words[i / Short.SIZE] |= 1 << (i % Short.SIZE);
    }
    StringBuilder result = new StringBuilder();
    for (int i = words.length - 1; i >= 0; i--) {
      if (result.length() > 0) result.append(' ');
      result.append(String.format("%04x", words[i]));
    }
    return result.toString();
  }

  public static final String NONE = "";
  public static final String SHIE = "shie";
  public static final String TORS = "tors";
  public static final String GOLD = "gold";
  public static final String BOWQ = "bowq";
  public static final String XBOQ = "xboq";
  public static final String PLAY = "play";
  public static final String HERB = "herb";
  public static final String POTI = "poti";
  public static final String RING = "ring";
  public static final String ELIX = "elix";
  public static final String AMUL = "amul";
  public static final String CHAR = "char";
  public static final String BOOT = "boot";
  public static final String GLOV = "glov";
  public static final String BOOK = "book";
  public static final String BELT = "belt";
  public static final String GEM = "gem";
  public static final String TORC = "torc";
  public static final String SCRO = "scro";
  public static final String SCEP = "scep";
  public static final String WAND = "wand";
  public static final String STAF = "staf";
  public static final String BOW = "bow";
  public static final String AXE = "axe";
  public static final String CLUB = "club";
  public static final String SWOR = "swor";
  public static final String HAMM = "hamm";
  public static final String KNIF = "knif";
  public static final String SPEA = "spea";
  public static final String POLE = "pole";
  public static final String XBOW = "xbow";
  public static final String MACE = "mace";
  public static final String HELM = "helm";
  public static final String TPOT = "tpot";
  public static final String QUES = "ques";
  public static final String BODY = "body";
  public static final String KEY = "key";
  public static final String TKNI = "tkni";
  public static final String TAXE = "taxe";
  public static final String JAVE = "jave";
  public static final String WEAP = "weap";
  public static final String MELE = "mele";
  public static final String MISS = "miss";
  public static final String THRO = "thro";
  public static final String COMB = "comb";
  public static final String ARMO = "armo";
  public static final String SHLD = "shld";
  public static final String MISC = "misc";
  public static final String SOCK = "sock";
  public static final String SECO = "seco";
  public static final String ROD = "rod";
  public static final String MISL = "misl";
  public static final String BLUN = "blun";
  public static final String JEWL = "jewl";
  public static final String CLAS = "clas";
  public static final String AMAZ = "amaz";
  public static final String BARB = "barb";
  public static final String NECR = "necr";
  public static final String PALA = "pala";
  public static final String SORC = "sorc";
  public static final String ASSN = "assn";
  public static final String DRUI = "drui";
  public static final String H2H = "h2h";
  public static final String ORB = "orb";
  public static final String HEAD = "head";
  public static final String ASHD = "ashd";
  public static final String PHLM = "phlm";
  public static final String PELT = "pelt";
  public static final String CLOA = "cloa";
  public static final String RUNE = "rune";
  public static final String CIRC = "circ";
  public static final String HPOT = "hpot";
  public static final String MPOT = "mpot";
  public static final String RPOT = "rpot";
  public static final String SPOT = "spot";
  public static final String APOT = "apot";
  public static final String WPOT = "wpot";
  public static final String SCHA = "scha";
  public static final String MCHA = "mcha";
  public static final String LCHA = "lcha";
  public static final String ABOW = "abow";
  public static final String ASPE = "aspe";
  public static final String AJAV = "ajav";
  public static final String H2H2 = "h2h2";
  public static final String MBOQ = "mboq";
  public static final String MXBQ = "mxbq";
  public static final String GEM0 = "gem0";
  public static final String GEM1 = "gem1";
  public static final String GEM2 = "gem2";
  public static final String GEM3 = "gem3";
  public static final String GEM4 = "gem4";
  public static final String GEMA = "gema";
  public static final String GEMD = "gemd";
  public static final String GEME = "geme";
  public static final String GEMR = "gemr";
  public static final String GEMS = "gems";
  public static final String GEMT = "gemt";
  public static final String GEMZ = "gemz";
}
