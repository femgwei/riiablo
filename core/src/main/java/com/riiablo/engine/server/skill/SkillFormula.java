package com.riiablo.engine.server.skill;

import com.riiablo.codec.excel.Skills;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Small, deliberately bounded evaluator for the arithmetic used by D2's
 * Skills.txt calc columns.  It is not intended to execute arbitrary input;
 * unsupported identifiers evaluate to zero so a malformed data row cannot
 * create an unbounded server loop.
 *
 * <p>The native evaluator stores compiled expressions, while this project
 * loads the text tables directly.  Keeping the parser here gives server
 * skills the same data-driven behaviour without duplicating every formula in
 * Java.  Supported forms include constants, {@code lvl}, {@code parN},
 * {@code lnAB}, {@code min/max(a,b)} and {@code skill('name'.blvl)}.</p>
 */
public final class SkillFormula {
  private SkillFormula() {}

  public static int evaluate(String expression, Skills.Entry skill, int skillLevel) {
    return evaluate(expression, skill, skillLevel, name -> 0, name -> null);
  }

  public static int evaluate(String expression, Skills.Entry skill, int skillLevel,
      ToIntFunction<String> skillLevelResolver) {
    return evaluate(expression, skill, skillLevel, skillLevelResolver, name -> null);
  }

  /**
   * Evaluates formulas whose {@code skill('name'.lnXY)} operands need both
   * the referenced skill's hard level and its own Param columns.
   */
  public static int evaluate(String expression, Skills.Entry skill, int skillLevel,
      ToIntFunction<String> skillLevelResolver,
      Function<String, Skills.Entry> skillResolver) {
    if (expression == null || expression.trim().isEmpty()) return 0;
    String normalized = expression.trim();
    // Excel exports occasionally quote an entire formula (for example
    // Berserk's calc2 duration). Native Skills.txt evaluates the contents,
    // not the quote characters themselves.
    if (normalized.length() >= 2
        && ((normalized.charAt(0) == '"' && normalized.charAt(normalized.length() - 1) == '"')
            || (normalized.charAt(0) == '\'' && normalized.charAt(normalized.length() - 1) == '\''))) {
      normalized = normalized.substring(1, normalized.length() - 1).trim();
    }
    Parser parser = new Parser(normalized, skill, Math.max(1, skillLevel),
        skillLevelResolver == null ? name -> 0 : skillLevelResolver,
        skillResolver == null ? name -> null : skillResolver);
    return parser.parse();
  }

  private static final class Parser {
    private final String text;
    private final Skills.Entry skill;
    private final int level;
    private final ToIntFunction<String> skillLevelResolver;
    private final Function<String, Skills.Entry> skillResolver;
    private int index;

    Parser(String expression, Skills.Entry skill, int level,
        ToIntFunction<String> skillLevelResolver,
        Function<String, Skills.Entry> skillResolver) {
      text = expression.trim();
      this.skill = skill;
      this.level = level;
      this.skillLevelResolver = skillLevelResolver;
      this.skillResolver = skillResolver;
    }

    int parse() {
      int value = expression();
      skipWhitespace();
      return index == text.length() ? value : 0;
    }

    private int expression() {
      int value = term();
      while (true) {
        skipWhitespace();
        if (consume('+')) value += term();
        else if (consume('-')) value -= term();
        else return value;
      }
    }

    private int term() {
      int value = factor();
      while (true) {
        skipWhitespace();
        if (consume('*')) value *= factor();
        else if (consume('/')) {
          int divisor = factor();
          value = divisor == 0 ? 0 : value / divisor;
        } else return value;
      }
    }

    private int factor() {
      skipWhitespace();
      if (consume('+')) return factor();
      if (consume('-')) return -factor();
      if (consume('(')) {
        int value = expression();
        consume(')');
        return value;
      }
      if (index >= text.length()) return 0;
      char c = text.charAt(index);
      if (c >= '0' && c <= '9') return number();
      if (c == '"' || c == '\'') {
        // A bare quoted literal is only meaningful as a skill name argument.
        readQuoted();
        return 0;
      }
      String identifier = identifier();
      if (identifier.isEmpty()) return 0;
      if ("min".equalsIgnoreCase(identifier) || "max".equalsIgnoreCase(identifier)) {
        consume('(');
        int left = expression();
        consume(',');
        int right = expression();
        consume(')');
        return "min".equalsIgnoreCase(identifier) ? Math.min(left, right) : Math.max(left, right);
      }
      if ("skill".equalsIgnoreCase(identifier)) return skillCall();
      if ("lvl".equalsIgnoreCase(identifier)) return level;
      if ("toht".equalsIgnoreCase(identifier)) {
        return skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
      }
      if (identifier.regionMatches(true, 0, "par", 0, 3)) {
        return param(parseDigits(identifier, 3));
      }
      if (identifier.length() == 4
          && (identifier.regionMatches(true, 0, "ln", 0, 2)
              || identifier.regionMatches(true, 0, "dm", 0, 2))) {
        int first = identifier.charAt(2) - '0';
        int second = identifier.charAt(3) - '0';
        if (first >= 1 && first <= 9 && second >= 1 && second <= 9) {
          int base = param(first);
          int step = param(second);
          if (identifier.regionMatches(true, 0, "dm", 0, 2)) {
            // Native diminishing-return macro:
            // a + 110 * level * (b - a) / (100 * (level + 6)).
            long numerator = 110L * level * (step - base);
            return base + (int) (numerator / (100L * (level + 6)));
          }
          // D2Common SKILLS_GetSpecialParamValue (lnXY) uses the first
          // parameter at skill level one, then adds the second parameter for
          // each subsequent level.
          return base + (level - 1) * step;
        }
      }
      return 0;
    }

    private int skillCall() {
      skipWhitespace();
      if (!consume('(')) return 0;
      skipWhitespace();
      String name = readQuoted();
      // Native expressions commonly append .blvl/.ln12 to the quoted name.
      skipWhitespace();
      String special = null;
      if (consume('.')) special = identifier();
      skipWhitespace();
      consume(')');
      if (name == null || name.isEmpty()) return 0;
      int referencedLevel = Math.max(0, skillLevelResolver.applyAsInt(name));
      if (referencedLevel <= 0 || special == null || special.isEmpty()
          || "blvl".equalsIgnoreCase(special)) {
        return referencedLevel;
      }
      Skills.Entry referencedSkill = skillResolver.apply(name);
      if (referencedSkill == null || special.length() != 4) return 0;
      if (special.regionMatches(true, 0, "ln", 0, 2)
          || special.regionMatches(true, 0, "dm", 0, 2)) {
        int first = special.charAt(2) - '0';
        int second = special.charAt(3) - '0';
        if (first < 1 || first > 9 || second < 1 || second > 9) return 0;
        int base = param(referencedSkill, first);
        int step = param(referencedSkill, second);
        if (special.regionMatches(true, 0, "dm", 0, 2)) {
          long numerator = 110L * referencedLevel * (step - base);
          return base + (int) (numerator / (100L * (referencedLevel + 6)));
        }
        return base + (referencedLevel - 1) * step;
      }
      return 0;
    }

    private String readQuoted() {
      skipWhitespace();
      if (index >= text.length()) return null;
      char quote = text.charAt(index);
      if (quote != '"' && quote != '\'') return null;
      index++;
      int start = index;
      while (index < text.length() && text.charAt(index) != quote) index++;
      String value = text.substring(start, Math.min(index, text.length()));
      if (index < text.length()) index++;
      return value;
    }

    private int number() {
      int start = index;
      while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
      try {
        return Integer.parseInt(text.substring(start, index));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }

    private String identifier() {
      skipWhitespace();
      int start = index;
      while (index < text.length()) {
        char c = text.charAt(index);
        if (!(Character.isLetterOrDigit(c) || c == '_')) break;
        index++;
      }
      return text.substring(start, index);
    }

    private int parseDigits(String value, int start) {
      if (start >= value.length() || !Character.isDigit(value.charAt(start))) return 0;
      return value.charAt(start) - '0';
    }

    private int param(int number) {
      return param(skill, number);
    }

    private static int param(Skills.Entry skill, int number) {
      if (skill == null || skill.Param == null || number < 1 || number > skill.Param.length) return 0;
      return skill.Param[number - 1];
    }

    private boolean consume(char expected) {
      skipWhitespace();
      if (index < text.length() && text.charAt(index) == expected) {
        index++;
        return true;
      }
      return false;
    }

    private void skipWhitespace() {
      while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
    }
  }
}
