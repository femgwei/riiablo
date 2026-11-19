import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Runs the production Java seed-state API on the oracle's shared input cases. */
public final class SeedVectors {
  public static void main(String[] args) throws Exception {
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
    String line;
    while ((line = input.readLine()) != null) {
      String[] fields = line.trim().split("\\s+");
      String op = fields[0];
      int low = (int) Long.parseLong(fields[1]), high = (int) Long.parseLong(fields[2]);
      D2Seed state = new D2Seed(low, high);
      if (op.equals("init")) Seed.initSeed(state);
      else if (op.equals("low")) Seed.initLowSeed(state, low);
      else if (op.equals("set")) {
        state.setSeeds(0xDEADBEEF, 0xCAFEBABE);
        Seed.setSeeds(state, low, high);
      } else throw new IllegalArgumentException(op);
      int[] pairLow = new int[1], pairHigh = new int[1];
      Seed.getSeeds(state, pairLow, pairHigh);
      System.out.println(op + "," + Integer.toUnsignedLong(low) + "," + Integer.toUnsignedLong(high)
          + "," + Integer.toUnsignedLong(state.getNLowSeed()) + "," + Integer.toUnsignedLong(state.getNHighSeed())
          + "," + Integer.toUnsignedLong(Seed.getLowSeed(state)) + "," + Integer.toUnsignedLong(Seed.getHighSeed(state))
          + "," + Integer.toUnsignedLong(pairLow[0]) + "," + Integer.toUnsignedLong(pairHigh[0]));
    }
  }
}
