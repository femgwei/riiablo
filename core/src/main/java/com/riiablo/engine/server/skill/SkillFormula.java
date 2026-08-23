package com.riiablo.engine.server.skill;

import com.riiablo.codec.excel.Skills;
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
    return evaluate(expression, skill, skillLevel, name -> 0);
  }

  public static int evaluate(String expression, Skills.Entry skill, int skillLevel,
      ToIntFunction<String> skillLevelResolver) {
    if (expression == null || expression.trim().isEmpty()) return 0;
    Parser parser = new Parser(expression, skill, Math.max(1, skillLevel),
        skillLevelResolver == null ? name -> 0 : skillLevelResolver);
    return parser.parse();
  }

  private static final class Parser {
    private final String text;
    private final Skills.Entry skill;
    private final int level;
    private final ToIntFunction<String> skillLevelResolver;
    private int index;

    Parser(String expression, Skills.Entry skill, int level,
        ToIntFunction<String> skillLevelResolver) {
      text = expression.trim();
      this.skill = skill;
      this.level = level;
      this.skillLevelResolver = skillLevelResolver;
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
          // ln12 is Param1 + level * Param2 in the native evaluator.  dmXY
          // is used by a few legacy rows with the same level convention.
          return base + level * step;
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
      if (consume('.')) identifier();
      skipWhitespace();
      consume(')');
      if (name == null || name.isEmpty()) return 0;
      return skillLevelResolver.applyAsInt(name);
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
