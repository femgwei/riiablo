import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Production Java sequence with identical starting words, bounds and draw count. */
public final class RngVectors {
  public static void main(String[] args) throws Exception {
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
    String line;
    int index = 0;
    StringBuilder output = new StringBuilder();
    while ((line = input.readLine()) != null) {
      String[] f = line.trim().split("\\s+");
      D2Seed seed = new D2Seed((int) Long.parseLong(f[1]), (int) Long.parseLong(f[2]));
      int bound = Integer.parseInt(f[3]), steps = Integer.parseInt(f[4]);
      if (!f[0].equals("raw") && !f[0].equals("limited")) throw new IllegalArgumentException(f[0]);
      for (int step = 0; step < steps; step++) {
        long value = f[0].equals("raw") ? Seed.rollRandomNumber(seed)
            : Integer.toUnsignedLong(Seed.rollLimitedRandomNumber(seed, bound));
        output.append(index).append(',').append(step).append(',').append(Long.toUnsignedString(value))
            .append(',').append(Integer.toUnsignedLong(seed.getNLowSeed()))
            .append(',').append(Integer.toUnsignedLong(seed.getNHighSeed())).append('\n');
      }
      index++;
    }
    System.out.print(output);
  }
}
