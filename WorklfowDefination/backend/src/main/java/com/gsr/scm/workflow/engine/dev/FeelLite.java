package com.gsr.scm.workflow.engine.dev;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A deliberately small subset of FEEL for sequence-flow conditions, used only by the development adapter.
 * Supported: {@code = a = "X"}, {@code !=, <, <=, >, >=}, {@code and}, {@code or}, {@code not(...)}, parentheses,
 * string / number / boolean / null literals and variable paths ({@code a.b}). Camunda evaluates full FEEL itself.
 */
public final class FeelLite {

    private FeelLite() {
    }

    public static class FeelException extends RuntimeException {
        public FeelException(String message) {
            super(message);
        }
    }

    public interface Expression extends Function<Map<String, Object>, Object> {
    }

    /** Compiles a condition; throws {@link FeelException} for unsupported syntax. */
    public static Expression compile(String source) {
        if (source == null) {
            throw new FeelException("Condition is empty");
        }
        String s = source.trim();
        if (s.startsWith("=")) {
            s = s.substring(1);
        }
        Parser p = new Parser(tokenize(s));
        Expression e = p.or();
        if (!p.atEnd()) {
            throw new FeelException("Unexpected token '" + p.peek().text + "' in condition: " + source);
        }
        return e;
    }

    public static boolean test(String source, Map<String, Object> variables) {
        return Boolean.TRUE.equals(compile(source).apply(variables));
    }

    // ------------------------------------------------------------ tokenizer

    private enum T { IDENT, STRING, NUMBER, OP, LPAREN, RPAREN }

    private record Token(T type, String text) {
    }

    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                tokens.add(new Token(T.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(T.RPAREN, ")"));
                i++;
            } else if (c == '"') {
                int end = s.indexOf('"', i + 1);
                if (end < 0) {
                    throw new FeelException("Unterminated string literal");
                }
                tokens.add(new Token(T.STRING, s.substring(i + 1, end)));
                i = end + 1;
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i + 1;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                tokens.add(new Token(T.NUMBER, s.substring(i, j)));
                i = j;
            } else if (c == '!' || c == '<' || c == '>' || c == '=') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                    tokens.add(new Token(T.OP, s.substring(i, i + 2)));
                    i += 2;
                } else if (c == '!') {
                    throw new FeelException("Unexpected '!'");
                } else {
                    tokens.add(new Token(T.OP, String.valueOf(c)));
                    i++;
                }
            } else if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_'
                        || s.charAt(j) == '.')) {
                    j++;
                }
                tokens.add(new Token(T.IDENT, s.substring(i, j)));
                i = j;
            } else {
                throw new FeelException("Unsupported character '" + c + "'");
            }
        }
        return tokens;
    }

    // ------------------------------------------------------------ parser

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean atEnd() {
            return pos >= tokens.size();
        }

        Token peek() {
            return atEnd() ? null : tokens.get(pos);
        }

        boolean acceptIdent(String word) {
            Token t = peek();
            if (t != null && t.type == T.IDENT && t.text.equals(word)) {
                pos++;
                return true;
            }
            return false;
        }

        Expression or() {
            Expression left = and();
            while (acceptIdent("or")) {
                Expression l = left;
                Expression r = and();
                left = v -> Boolean.TRUE.equals(l.apply(v)) || Boolean.TRUE.equals(r.apply(v));
            }
            return left;
        }

        Expression and() {
            Expression left = comparison();
            while (acceptIdent("and")) {
                Expression l = left;
                Expression r = comparison();
                left = v -> Boolean.TRUE.equals(l.apply(v)) && Boolean.TRUE.equals(r.apply(v));
            }
            return left;
        }

        Expression comparison() {
            Expression left = unary();
            Token t = peek();
            if (t != null && t.type == T.OP) {
                pos++;
                Expression right = unary();
                String op = t.text;
                return v -> compare(op, left.apply(v), right.apply(v));
            }
            return left;
        }

        Expression unary() {
            if (acceptIdent("not")) {
                expect(T.LPAREN);
                Expression inner = or();
                expect(T.RPAREN);
                return v -> !Boolean.TRUE.equals(inner.apply(v));
            }
            return primary();
        }

        Expression primary() {
            Token t = peek();
            if (t == null) {
                throw new FeelException("Unexpected end of condition");
            }
            pos++;
            return switch (t.type) {
                case LPAREN -> {
                    Expression e = or();
                    expect(T.RPAREN);
                    yield e;
                }
                case STRING -> v -> t.text;
                case NUMBER -> {
                    BigDecimal n = new BigDecimal(t.text);
                    yield v -> n;
                }
                case IDENT -> switch (t.text) {
                    case "true" -> v -> Boolean.TRUE;
                    case "false" -> v -> Boolean.FALSE;
                    case "null" -> v -> null;
                    case "and", "or", "not" -> throw new FeelException("Unexpected keyword '" + t.text + "'");
                    default -> v -> resolve(v, t.text);
                };
                default -> throw new FeelException("Unexpected token '" + t.text + "'");
            };
        }

        void expect(T type) {
            Token t = peek();
            if (t == null || t.type != type) {
                throw new FeelException("Expected " + type + " but found " + (t == null ? "end" : "'" + t.text + "'"));
            }
            pos++;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> vars, String path) {
        Object current = vars;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = ((Map<String, Object>) m).get(segment);
        }
        return current;
    }

    private static Object compare(String op, Object a, Object b) {
        if (a instanceof Number || b instanceof Number) {
            if (a == null || b == null) {
                return op.equals("!=") ? !Objects.equals(a, b) : op.equals("=") ? Objects.equals(a, b) : null;
            }
            if (!(a instanceof Number) || !(b instanceof Number)) {
                return op.equals("!=");
            }
            int c = new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
            return switch (op) {
                case "=" -> c == 0;
                case "!=" -> c != 0;
                case "<" -> c < 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                case ">=" -> c >= 0;
                default -> throw new FeelException("Unknown operator " + op);
            };
        }
        return switch (op) {
            case "=" -> Objects.equals(normalize(a), normalize(b));
            case "!=" -> !Objects.equals(normalize(a), normalize(b));
            default -> {
                if (a instanceof String sa && b instanceof String sb) {
                    int c = sa.compareTo(sb);
                    yield switch (op) {
                        case "<" -> c < 0;
                        case "<=" -> c <= 0;
                        case ">" -> c > 0;
                        default -> c >= 0;
                    };
                }
                yield null;
            }
        };
    }

    private static Object normalize(Object o) {
        return o instanceof Boolean || o instanceof String || o == null ? o : o.toString();
    }
}
