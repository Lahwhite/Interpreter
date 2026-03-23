package cn.edu.nju.cs;

import java.util.HashMap;
import java.util.Map;

public class Evaluator extends MiniJavaParserBaseVisitor<Object> {
    private final Map<String, Object> symbolTable = new HashMap<>();

    
    // 检查过
    @Override
    public Object visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        var result = visit(ctx.expression());
        // System.out.println(result);
        return result;
    }

    // 检查过
    @Override
    public Object visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return visit(ctx.literal());
    }

    // 检查过
    @Override
    public Object visitLiteral(MiniJavaParser.LiteralContext ctx) {
        if (ctx.DECIMAL_LITERAL() != null) {
            return Integer.parseInt(ctx.DECIMAL_LITERAL().getText().replace("_", ""));
        } else if (ctx.BOOL_LITERAL() != null) {
            return "true".equals(ctx.getText());
        } else if (ctx.CHAR_LITERAL() != null) {
            return ctx.CHAR_LITERAL().getText().charAt(1);
        } else if (ctx.STRING_LITERAL() != null) {
            String lit = ctx.getText();
            return lit.substring(1, lit.length() - 1);
        }
        return null;
    }

    // 检查过
    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
        try {
            if (ctx.bop != null) {
                // 运算符
                String op = ctx.bop.getText();
                if (ctx.bop.getType() == MiniJavaParser.QUESTION) {
                    Object condition = visit(ctx.expression(0));
                    return evaluateTernaryOperator(condition, ctx.expression(1), ctx.expression(2));
                } else if (op.equals("and") || op.equals("or")) {
                    // 逻辑运算符
                    Object left = visit(ctx.expression(0));
                    return evaluateLogicalOperatorWithShortCircuit(left, ctx.expression(1), op);
                } else {
                    // 二元运算符
                    Object left = visit(ctx.expression(0));
                    Object right = visit(ctx.expression(1));
                    return evaluateBinaryOperator(left, right, op);
                }
            } else if (ctx.prefix != null) {
                // 前缀运算符
                Object operand = visit(ctx.expression(0));
                String op = ctx.prefix.getText();
                return evaluateUnaryPrefixOperator(operand, op);
            } else if (ctx.postfix != null) {
                // 后缀运算符
                Object operand = visit(ctx.expression(0));
                String op = ctx.postfix.getText();
                return evaluateUnaryPostfixOperator(operand, op);
            } else if (ctx.primitiveType() != null) {
                // 类型转换
                String type = ctx.primitiveType().getText();
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

    // 检查过
    @Override
    public Object visitPrimitiveType(MiniJavaParser.PrimitiveTypeContext ctx) {
        String type = ctx.getText();
        Object value = "";
        if (type.equals("int") || type.equals("char")) {
            value = 0;
        } else if (type.equals("boolean")) {
            value = false;
        } 
        return value;
    }

    private boolean isTruthy(Object v) {
        if (v instanceof Boolean b) return b;
        return true;
    }

    // int 和 char 转换为 int，检查过
    private int toInt(Object v, String op) {
        if (v instanceof Integer i) return i;
        if (v instanceof Character c) return (int) c;
        throw new RuntimeException("Invalid operand type for " + op + ": " + v.getClass());
    }

    // 逻辑运算符，检查过
    private Object evaluateLogicalOperatorWithShortCircuit(Object left, MiniJavaParser.ExpressionContext rightExpr, String op) {
        if (!(left instanceof Boolean)) {
            throw new RuntimeException("Invalid type for " + op + " operator: expected boolean, got " + left.getClass().getName());
        }
        
        if (op.equals("and")) {
            if (!isTruthy(left)) return false;
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for and operator: expected boolean, got " + right.getClass().getName());
            }
            return isTruthy(right);
        } else if (op.equals("or")) {
            if (isTruthy(left)) return true;
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for or operator: expected boolean, got " + right.getClass().getName());
            }
            return isTruthy(right);
        } else {
            throw new RuntimeException("Unknown logical operator: " + op);
        }
    }

    // 二元运算符，检查过
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
                    return toInt(right, "=");
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

    // 前缀运算符，检查过
    private Object evaluateUnaryPrefixOperator(Object operand, String op) {
        switch (op) {
            case "+":
                if (operand instanceof Integer || operand instanceof Character) 
                    return toInt(operand, "+");
                throw new RuntimeException("Invalid type for + operator");
            case "-":
                if (operand instanceof Integer || operand instanceof Character) 
                    return -toInt(operand, "-");
                throw new RuntimeException("Invalid type for - operator");
            case "not":
                if (operand instanceof Boolean) 
                    return !((Boolean) operand);
                throw new RuntimeException("Invalid type for not operator: expected boolean, got " + operand.getClass().getName());
            case "~":
                if (operand instanceof Integer || operand instanceof Character) 
                    return ~toInt(operand, "~");
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
    }

    // 后缀运算符，检查过
    private Object evaluateUnaryPostfixOperator(Object operand, String op) {
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
    }

    // 类型转换，检查过
    private Object evaluateTypeCast(Object operand, MiniJavaParser.PrimitiveTypeContext type) {
        if (type.INT() != null) {
            if (operand instanceof Character c) {
                int value = (int) c;
                if ((c & 0x8000) != 0) {
                    value |= 0xFFFF0000;
                }
                return value;
            }
            throw new RuntimeException("Cannot cast " + operand.getClass() + " to int");
        } else if (type.CHAR() != null) {
            if (operand instanceof Integer i) {
                int truncated = i & 0xFF;
                if ((truncated & 0x80) != 0) {
                    truncated |= 0xFFFFFF00;
                }
                return (char) truncated;
            }
            throw new RuntimeException("Cannot cast " + operand.getClass() + " to char");
        }

        throw new RuntimeException("Unknown type: " + type.getText());
    }

    // 三目运算符，检查过
    private Object evaluateTernaryOperator(Object condition, MiniJavaParser.ExpressionContext thenExpr, MiniJavaParser.ExpressionContext elseExpr) {
        try {
            if (!(condition instanceof Boolean)) {
                throw new RuntimeException("Ternary operator error: condition must be boolean");
            }
            return (Boolean) condition ? visit(thenExpr) : visit(elseExpr);
        } catch (Exception e) {
            throw new RuntimeException("Ternary operator error: " + e.getMessage());
        }
    }

    // == 运算符，检查过
    private boolean areEqual(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;

        // Check if types are the same
        if (left.getClass() != right.getClass()) {
            // Allow int <-> char comparison
            if (!((left instanceof Integer && right instanceof Character) || 
                  (left instanceof Character && right instanceof Integer))) {
                throw new RuntimeException("Type mismatch: cannot compare " + left.getClass().getName() + " and " + right.getClass().getName());
            }
        }

        // int <-> char numeric comparison
        if (left instanceof Integer li && right instanceof Character rc) {
            return li == toInt(rc, "areEqual");
        }
        if (left instanceof Character lc && right instanceof Integer ri) {
            return toInt(lc, "areEqual") == ri;
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
}