package cn.edu.nju.cs;

import java.util.HashMap;
import java.util.Map;

public class Evaluator extends MiniJavaParserBaseVisitor<Object> {
    private final Map<String, Object> variables = new HashMap<>();

    
    @Override
    public Object visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        try {
            return visit(ctx.expression());
        } catch (Exception e) {
            System.err.println("Process exits with 34.");
            System.exit(34);
            return null; // This line is unreachable, but required for compilation
        }
    }

    @Override
    public Object visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        try {
            if (ctx.expression() != null) {
                return visit(ctx.expression());
            }
            return visit(ctx.literal());
        } catch (Exception e) {
            System.err.println("Process exits with 34.");
            System.exit(34);
            return null; // This line is unreachable, but required for compilation
        }
    }

    @Override
    public Object visitLiteral(MiniJavaParser.LiteralContext ctx) {
        try {
            if (ctx.DECIMAL_LITERAL() != null) {
                String text = ctx.DECIMAL_LITERAL().getText().replace("_", "");
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Invalid number format: " + text);
                }
            } else if (ctx.BOOL_LITERAL() != null) {
                return Boolean.parseBoolean(ctx.BOOL_LITERAL().getText());
            } else if (ctx.CHAR_LITERAL() != null) {
                return parseCharLiteral(ctx.CHAR_LITERAL().getText());
            } else if (ctx.STRING_LITERAL() != null) {
                return parseStringLiteral(ctx.STRING_LITERAL().getText());
            }
            throw new RuntimeException("Unknown literal type");
        } catch (Exception e) {
            System.err.println("Process exits with 34.");
            System.exit(34);
            return null; // This line is unreachable, but required for compilation
        }
    }

    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
        try {
            if (ctx.bop != null) {
                String op = ctx.bop.getText();
                if (op.equals("?")) {
                    Object condition = visit(ctx.expression(0));
                    return evaluateTernaryOperator(condition, ctx.expression(1), ctx.expression(2));
                } else if (op.equals("and") || op.equals("or")) {
                    Object left = visit(ctx.expression(0));
                    return evaluateLogicalOperatorWithShortCircuit(left, ctx.expression(1), op);
                } else {
                    Object left = visit(ctx.expression(0));
                    Object right = visit(ctx.expression(1));
                    return evaluateBinaryOperator(left, right, op);
                }
            } else if (ctx.prefix != null) {
                Object operand = visit(ctx.expression(0));
                String op = ctx.prefix.getText();
                return evaluateUnaryPrefixOperator(operand, op);
            } else if (ctx.postfix != null) {
                Object operand = visit(ctx.expression(0));
                String op = ctx.postfix.getText();
                return evaluateUnaryPostfixOperator(operand, op);
            } else if (ctx.primitiveType() != null) {
                Object operand = visit(ctx.expression(0));
                return evaluateTypeCast(operand, ctx.primitiveType());
            }
            return visitChildren(ctx);
        } catch (Exception e) {
            System.err.println("Process exits with 34.");
            System.exit(34);
            return null; // This line is unreachable, but required for compilation
        }
    }

    private boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Integer i) return i != 0;
        if (v instanceof Character c) return c != '\0';
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }

    private int toInt(Object v, String op) {
        if (v instanceof Integer i) return i;
        if (v instanceof Character c) return (int) c;
        throw new RuntimeException("Invalid operand type for " + op + ": " + v.getClass());
    }

    private Object evaluateLogicalOperatorWithShortCircuit(Object left, MiniJavaParser.ExpressionContext rightExpr, String op) {
        // Check if left operand is boolean type
        if (!(left instanceof Boolean)) {
            throw new RuntimeException("Invalid type for " + op + " operator: expected boolean, got " + left.getClass().getName());
        }
        
        if (op.equals("and")) {
            if (!isTruthy(left)) return false;
            Object right = visit(rightExpr);
            // Check if right operand is boolean type
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for and operator: expected boolean, got " + right.getClass().getName());
            }
            return isTruthy(right);
        } else if (op.equals("or")) {
            if (isTruthy(left)) return true;
            Object right = visit(rightExpr);
            // Check if right operand is boolean type
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for or operator: expected boolean, got " + right.getClass().getName());
            }
            return isTruthy(right);
        } else {
            throw new RuntimeException("Unknown logical operator: " + op);
        }
    }

    private Object evaluateBinaryOperator(Object left, Object right, String op) {
        try {
            switch (op) {
                case "+":
                    if (left instanceof String || right instanceof String) {
                        return left.toString() + right.toString();
                    } else if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "+") + toInt(right, "+");
                    }
                    throw new RuntimeException("Invalid types for + operator");
                case "-":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "-") - toInt(right, "-");
                    }
                    throw new RuntimeException("Invalid types for - operator");
                case "*":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "*") * toInt(right, "*");
                    }
                    throw new RuntimeException("Invalid types for * operator: both operands must be int or char");
                case "/":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        int r = toInt(right, "/");
                        if (r == 0) throw new ArithmeticException("Division by zero");
                        return toInt(left, "/") / r;
                    }
                    throw new RuntimeException("Invalid types for / operator: both operands must be int or char");
                case "%":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        int r = toInt(right, "%");
                        if (r == 0) throw new ArithmeticException("Division by zero");
                        return toInt(left, "%") % r;
                    }
                    throw new RuntimeException("Invalid types for % operator: both operands must be int or char");
                case "<":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return compare(left, right) < 0;
                    }
                    throw new RuntimeException("Invalid types for < operator: both operands must be int or char");
                case ">":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return compare(left, right) > 0;
                    }
                    throw new RuntimeException("Invalid types for > operator: both operands must be int or char");
                case "<=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return compare(left, right) <= 0;
                    }
                    throw new RuntimeException("Invalid types for <= operator: both operands must be int or char");
                case ">=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return compare(left, right) >= 0;
                    }
                    throw new RuntimeException("Invalid types for >= operator: both operands must be int or char");
                case "==":
                    return areEqual(left, right);
                case "!=":
                    return !areEqual(left, right);
                case "&":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "&") & toInt(right, "&");
                    }
                    throw new RuntimeException("Invalid types for & operator: both operands must be int or char");
                case "|":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "|") | toInt(right, "|");
                    }
                    throw new RuntimeException("Invalid types for | operator: both operands must be int or char");
                case "^":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "^") ^ toInt(right, "^");
                    }
                    throw new RuntimeException("Invalid types for ^ operator: both operands must be int or char");
                case "<<":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "<<") << toInt(right, "<<");
                    }
                    throw new RuntimeException("Invalid types for << operator");
                case ">>":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, ">>") >> toInt(right, ">>");
                    }
                    throw new RuntimeException("Invalid types for >> operator");
                case ">>>":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, ">>>") >>> toInt(right, ">>>");
                    }
                    throw new RuntimeException("Invalid types for >>> operator");
                case "=":
                    return right;
                case "+=":
                    if (left instanceof String || right instanceof String) {
                        return left.toString() + right.toString();
                    }
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "+=") + toInt(right, "+=");
                    }
                    throw new RuntimeException("Invalid types for += operator");
                case "-=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "-=") - toInt(right, "-=");
                    }
                    throw new RuntimeException("Invalid types for -= operator");
                case "*=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "*=") * toInt(right, "*=");
                    }
                    throw new RuntimeException("Invalid types for *= operator");
                case "/=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        int r = toInt(right, "/=");
                        if (r == 0) throw new ArithmeticException("Division by zero");
                        return toInt(left, "/=") / r;
                    }
                    throw new RuntimeException("Invalid types for /= operator");
                case "%=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        int r = toInt(right, "%=");
                        if (r == 0) throw new ArithmeticException("Division by zero");
                        return toInt(left, "%=") % r;
                    }
                    throw new RuntimeException("Invalid types for %= operator");
                case "&=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "&=") & toInt(right, "&=");
                    }
                    throw new RuntimeException("Invalid types for &= operator");
                case "|=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "|=") | toInt(right, "|=");
                    }
                    throw new RuntimeException("Invalid types for |= operator");
                case "^=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "^=") ^ toInt(right, "^=");
                    }
                    throw new RuntimeException("Invalid types for ^= operator");
                case "<<=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, "<<=") << toInt(right, "<<=");
                    }
                    throw new RuntimeException("Invalid types for <<= operator");
                case ">>=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, ">>=") >> toInt(right, ">>=");
                    }
                    throw new RuntimeException("Invalid types for >>= operator");
                case ">>>=":
                    if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                        return toInt(left, ">>>=") >>> toInt(right, ">>>=");
                    }
                    throw new RuntimeException("Invalid types for >>>= operator");
                default:
                    throw new RuntimeException("Unknown binary operator: " + op);
            }
        } catch (ClassCastException e) {
            throw new RuntimeException("Type mismatch: " + e.getMessage());
        }
    }

    private Object evaluateUnaryPrefixOperator(Object operand, String op) {
        try {
            switch (op) {
                case "+":
                    if (operand instanceof Integer || operand instanceof Character) return toInt(operand, "+");
                    throw new RuntimeException("Invalid type for + operator");
                case "-":
                    if (operand instanceof Integer || operand instanceof Character) return -toInt(operand, "-");
                    throw new RuntimeException("Invalid type for - operator");
                case "not":
                    if (operand instanceof Boolean) return !((Boolean) operand);
                    throw new RuntimeException("Invalid type for not operator: expected boolean, got " + operand.getClass().getName());
                case "~":
                    if (operand instanceof Integer || operand instanceof Character) return ~toInt(operand, "~");
                    throw new RuntimeException("Invalid type for ~ operator");
                case "++":
                    // ++ operator can only be applied to variables, not literals
                    throw new RuntimeException("Invalid use of ++ operator: can only be applied to variables");
                case "--":
                    // -- operator can only be applied to variables, not literals
                    throw new RuntimeException("Invalid use of -- operator: can only be applied to variables");
                default:
                    throw new RuntimeException("Unknown unary prefix operator: " + op);
            }
        } catch (ClassCastException e) {
            throw new RuntimeException("Type mismatch: " + e.getMessage());
        }
    }

    private Object evaluateUnaryPostfixOperator(Object operand, String op) {
        try {
            switch (op) {
                case "++":
                    // ++ operator can only be applied to variables, not literals
                    throw new RuntimeException("Invalid use of ++ operator: can only be applied to variables");
                case "--":
                    // -- operator can only be applied to variables, not literals
                    throw new RuntimeException("Invalid use of -- operator: can only be applied to variables");
                default:
                    throw new RuntimeException("Unknown unary postfix operator: " + op);
            }
        } catch (ClassCastException e) {
            throw new RuntimeException("Type mismatch: " + e.getMessage());
        }
    }

    private Object evaluateTypeCast(Object operand, MiniJavaParser.PrimitiveTypeContext type) {
        try {
            if (type.INT() != null) {
                if (operand instanceof Integer) return operand;
                if (operand instanceof Character c) return (int) c; // Sign extension
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to int");
            } else if (type.CHAR() != null) {
                if (operand instanceof Character) return operand;
                if (operand instanceof Integer i) return (char) (i & 0xFF); // Signed 8-bit truncation
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to char");
            } else if (type.BOOLEAN() != null) {
                if (operand instanceof Boolean) return operand;
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to boolean");
            } else if (type.STRING() != null) {
                return operand.toString();
            }
            throw new RuntimeException("Unknown type: " + type.getText());
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            }
            throw new RuntimeException("Type cast error: " + e.getMessage());
        }
    }

    private Object evaluateTernaryOperator(Object condition, MiniJavaParser.ExpressionContext thenExpr, MiniJavaParser.ExpressionContext elseExpr) {
        try {
            if (!(condition instanceof Boolean)) {
                throw new RuntimeException("Ternary operator error: condition must be boolean");
            }
            return ((Boolean) condition) ? visit(thenExpr) : visit(elseExpr);
        } catch (Exception e) {
            throw new RuntimeException("Ternary operator error: " + e.getMessage());
        }
    }

    private boolean areEqual(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;

        // Check if types are the same
        if (left.getClass() != right.getClass()) {
            // Allow int <-> char comparison
            if (!((left instanceof Integer && right instanceof Character) || (left instanceof Character && right instanceof Integer))) {
                throw new RuntimeException("Type mismatch: cannot compare " + left.getClass().getName() + " and " + right.getClass().getName());
            }
        }

        // int <-> char numeric comparison
        if (left instanceof Integer li && right instanceof Character rc) {
            return li == (int) rc;
        }
        if (left instanceof Character lc && right instanceof Integer ri) {
            return (int) lc == ri;
        }
        // char <-> char
        if (left instanceof Character lc2 && right instanceof Character rc2) {
            return lc2.charValue() == rc2.charValue();
        }
        // int <-> int
        if (left instanceof Integer && right instanceof Integer) {
            return left.equals(right);
        }
        // String <-> String
        if (left instanceof String && right instanceof String) {
            return left.equals(right);
        }
        // Boolean <-> Boolean
        if (left instanceof Boolean && right instanceof Boolean) {
            return left.equals(right);
        }
        
        // Any other type combination is invalid
        throw new RuntimeException("Type mismatch: cannot compare " + left.getClass().getName() + " and " + right.getClass().getName());
    }

    private int compare(Object left, Object right) {
        try {
            if ((left instanceof Integer || left instanceof Character) && (right instanceof Integer || right instanceof Character)) {
                return Integer.compare(toInt(left, "compare"), toInt(right, "compare"));
            } else if (left instanceof String && right instanceof String) {
                return ((String) left).compareTo((String) right);
            } else if (left instanceof Boolean && right instanceof Boolean) {
                return Boolean.compare((Boolean) left, (Boolean) right);
            }
            throw new RuntimeException("Cannot compare " + left.getClass() + " and " + right.getClass());
        } catch (Exception e) {
            throw new RuntimeException("Comparison error: " + e.getMessage());
        }
    }

    private static Character parseCharLiteral(String tokenText) {
        // tokenText includes quotes, e.g.  '\n'  or  'a'
        if (tokenText == null || tokenText.length() < 3 || tokenText.charAt(0) != '\'' || tokenText.charAt(tokenText.length() - 1) != '\'') {
            throw new RuntimeException("Invalid character literal: " + tokenText);
        }
        String inner = tokenText.substring(1, tokenText.length() - 1);
        if (inner.isEmpty()) {
            throw new RuntimeException("Invalid character literal: " + tokenText);
        }
        if (inner.length() == 1 && inner.charAt(0) != '\\') {
            return inner.charAt(0);
        }
        if (inner.charAt(0) != '\\') {
            throw new RuntimeException("Invalid escape in character literal: " + tokenText);
        }
        return unescapeSingleEscape(inner);
    }

    private static String parseStringLiteral(String tokenText) {
        if (tokenText == null || tokenText.length() < 2 || tokenText.charAt(0) != '"' || tokenText.charAt(tokenText.length() - 1) != '"') {
            throw new RuntimeException("Invalid string literal: " + tokenText);
        }
        String inner = tokenText.substring(1, tokenText.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i + 1 >= inner.length()) {
                throw new RuntimeException("Invalid escape in string literal: " + tokenText);
            }
            // Reuse the char-literal unescape logic by slicing from this backslash
            int start = i;
            if (inner.charAt(i + 1) == 'u') {
                int j = i + 1;
                while (j < inner.length() && inner.charAt(j) == 'u') j++;
                if (j + 4 > inner.length()) throw new RuntimeException("Invalid unicode escape in string literal: " + tokenText);
                String esc = inner.substring(start, j + 4);
                sb.append(unescapeSingleEscape(esc));
                i = j + 3;
            } else {
                char next = inner.charAt(i + 1);
                if (next >= '0' && next <= '7') {
                    int j = i + 1;
                    int count = 0;
                    while (j < inner.length() && count < 3) {
                        char d = inner.charAt(j);
                        if (d < '0' || d > '7') break;
                        j++;
                        count++;
                    }
                    String esc = inner.substring(start, j);
                    sb.append(unescapeSingleEscape(esc));
                    i = j - 1;
                } else {
                    String esc = inner.substring(start, i + 2);
                    sb.append(unescapeSingleEscape(esc));
                    i = i + 1;
                }
            }
        }
        return sb.toString();
    }

    private static char unescapeSingleEscape(String escapeText) {
        // escapeText starts with backslash, e.g. "\\n", "\\0", "\\123", "\\u0041", "\\uuuu0041"
        if (escapeText == null || escapeText.isEmpty() || escapeText.charAt(0) != '\\') {
            throw new RuntimeException("Invalid escape: " + escapeText);
        }
        if (escapeText.length() == 2) {
            return switch (escapeText.charAt(1)) {
                case 'b' -> '\b';
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'f' -> '\f';
                case 'r' -> '\r';
                case '"' -> '"';
                case '\'' -> '\'';
                case '\\' -> '\\';
                case '0' -> '\0';
                default -> throw new RuntimeException("Unknown escape: \\" + escapeText.charAt(1));
            };
        }
        // Unicode: backslash + 'u' + 4 hex digits (allow repeated 'u')
        if (escapeText.length() >= 6 && escapeText.charAt(1) == 'u') {
            int idx = 1;
            while (idx < escapeText.length() && escapeText.charAt(idx) == 'u') idx++;
            if (idx + 4 != escapeText.length()) {
                throw new RuntimeException("Invalid unicode escape: " + escapeText);
            }
            int codePoint = Integer.parseInt(escapeText.substring(idx, idx + 4), 16);
            return (char) codePoint;
        }
        // Octal: \0 .. \377 (we allow 1-3 digits after backslash)
        char first = escapeText.charAt(1);
        if (first >= '0' && first <= '7') {
            int value = 0;
            for (int i = 1; i < escapeText.length(); i++) {
                char d = escapeText.charAt(i);
                if (d < '0' || d > '7') {
                    throw new RuntimeException("Invalid octal escape: " + escapeText);
                }
                value = (value << 3) + (d - '0');
            }
            return (char) value;
        }
        throw new RuntimeException("Invalid escape: " + escapeText);
    }
}