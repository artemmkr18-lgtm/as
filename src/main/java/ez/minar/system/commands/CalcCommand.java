package ez.minar.system.commands;

import net.minecraft.util.Formatting;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

public final class CalcCommand {
    private static final DecimalFormat FMT = new DecimalFormat("#.######");

    private CalcCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String line = input.trim();
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals(".calc") && !lower.startsWith(".calc ")) {
            return false;
        }

        String expr = line.length() > 5 ? line.substring(5).trim() : "";
        if (expr.isEmpty()) {
            usage();
            return true;
        }

        try {
            double result = evaluate(expr);
            CommandFeedback.message("Результат: " + expr + " = " + FMT.format(result), Formatting.GREEN);
        } catch (Exception e) {
            CommandFeedback.message("Ошибка вычисления: " + e.getMessage(), Formatting.RED);
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String lower = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(lower) || (lower.startsWith(".") && ".calc".startsWith(lower))) {
            return List.of(".calc");
        }
        if (lower.startsWith(".calc ")) {
            return List.of();
        }
        return List.of();
    }

    private static void usage() {
        CommandFeedback.message("Использование: .calc <выражение> (пример: .calc 2.5 * (10 + 4))", Formatting.YELLOW);
    }

    public static double evaluate(String expression) {
        return new Parser(expression).parse();
    }

    private static class Parser {
        private final String str;
        private int pos = -1;
        private int ch;

        Parser(String str) {
            this.str = str;
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        public double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) {
                throw new IllegalArgumentException("Неожиданный символ: " + (char) ch);
            }
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            while (true) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0) throw new ArithmeticException("Деление на ноль");
                    x /= divisor;
                } else if (eat('%')) x %= parseFactor();
                else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return +parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new IllegalArgumentException("Пропущена закрывающая скобка ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z') {
                while (ch >= 'a' && ch <= 'z') nextChar();
                String func = str.substring(startPos, this.pos);
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new IllegalArgumentException("Пропущена закрывающая скобка после " + func);
                } else {
                    x = parseFactor();
                }
                x = switch (func) {
                    case "sqrt" -> Math.sqrt(x);
                    case "sin" -> Math.sin(Math.toRadians(x));
                    case "cos" -> Math.cos(Math.toRadians(x));
                    case "tan" -> Math.tan(Math.toRadians(x));
                    case "abs" -> Math.abs(x);
                    case "log" -> Math.log10(x);
                    case "ln" -> Math.log(x);
                    default -> throw new IllegalArgumentException("Неизвестная функция: " + func);
                };
            } else {
                throw new IllegalArgumentException("Неожиданный символ: " + (char) ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());

            return x;
        }
    }
}
