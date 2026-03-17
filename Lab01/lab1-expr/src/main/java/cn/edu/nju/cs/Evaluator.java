package cn.edu.nju.cs;

import java.util.HashMap;
import java.util.Map;

public class Evaluator extends MiniJavaParserBaseVisitor<Object> {
    private final Map<String, Object> variables = new HashMap<>();

    @Override
    public Object visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return visit(ctx.literal());
    }

    @Override
    public Object visitLiteral(MiniJavaParser.LiteralContext ctx) {
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
    }

    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
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
    }

    private boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Integer i) return i != 0;
        if (v instanceof Character c) return c != '\0';
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }

    private Object evaluateLogicalOperatorWithShortCircuit(Object left, MiniJavaParser.ExpressionContext rightExpr, String op) {
        boolean leftTruthy = isTruthy(left);
        if (op.equals("and")) {
            if (!leftTruthy) return false;
            Object right = visit(rightExpr);
            return isTruthy(right);
        }
        if (op.equals("or")) {
            if (leftTruthy) return true;
            Object right = visit(rightExpr);
            return isTruthy(right);
        }
        throw new RuntimeException("Unknown logical operator: " + op);
    }

    private Object evaluateBinaryOperator(Object left, Object right, String op) {
        try {
            switch (op) {
                case "+":
                    if (left instanceof String || right instanceof String) {
                        return left.toString() + right.toString();
                    } else if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left + (Integer) right;
                    } else if (left instanceof Boolean && right instanceof Boolean) {
                        return left.toString() + right.toString();
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) + ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) + (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left + ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for + operator");
                case "-":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left - (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) - (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left - ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) - ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for - operator");
                case "*":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left * (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) * (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left * ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) * ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for * operator");
                case "/":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return (Integer) left / (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return ((int) (char) left) / (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return (Integer) left / rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return ((int) (char) left) / rightVal;
                    }
                    throw new RuntimeException("Invalid types for / operator");
                case "%":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return (Integer) left % (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return ((int) (char) left) % (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return (Integer) left % rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return ((int) (char) left) % rightVal;
                    }
                    throw new RuntimeException("Invalid types for % operator");
                case "<":
                    return compare(left, right) < 0;
                case ">":
                    return compare(left, right) > 0;
                case "<=":
                    return compare(left, right) <= 0;
                case ">=":
                    return compare(left, right) >= 0;
                case "==":
                    return areEqual(left, right);
                case "!=":
                    return !areEqual(left, right);
                case "and":
                    if (left instanceof Boolean && right instanceof Boolean) {
                        return (Boolean) left && (Boolean) right;
                    } else if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left != 0 && (Integer) right != 0;
                    } else if (left instanceof String && right instanceof String) {
                        return !((String) left).isEmpty() && !((String) right).isEmpty();
                    } else if (left instanceof Character && right instanceof Character) {
                        return (char) left != 0 && (char) right != 0;
                    }
                    throw new RuntimeException("Invalid types for and operator");
                case "or":
                    if (left instanceof Boolean && right instanceof Boolean) {
                        return (Boolean) left || (Boolean) right;
                    } else if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left != 0 || (Integer) right != 0;
                    } else if (left instanceof String && right instanceof String) {
                        return !((String) left).isEmpty() || !((String) right).isEmpty();
                    } else if (left instanceof Character && right instanceof Character) {
                        return (char) left != 0 || (char) right != 0;
                    }
                    throw new RuntimeException("Invalid types for or operator");
                case "&":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left & (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) & (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left & ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) & ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for & operator");
                case "|":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left | (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) | (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left | ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) | ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for | operator");
                case "^":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left ^ (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) ^ (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left ^ ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) ^ ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for ^ operator");
                case "<<":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left << (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) << (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left << ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) << ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for << operator");
                case ">>":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >> (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left >> ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) >> ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for >> operator");
                case ">>>":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >>> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >>> (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left >>> ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) >>> ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for >>> operator");
                case "=":
                    return right;
                case "+=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left + (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) + (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left + ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) + ((int) (char) right);
                    } else if (left instanceof String || right instanceof String) {
                        return left.toString() + right.toString();
                    }
                    throw new RuntimeException("Invalid types for += operator");
                case "-=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left - (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) - (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left - ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) - ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for -= operator");
                case "*=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left * (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) * (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left * ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) * ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for *= operator");
                case "/=":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return (Integer) left / (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return ((int) (char) left) / (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return (Integer) left / rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return ((int) (char) left) / rightVal;
                    }
                    throw new RuntimeException("Invalid types for /= operator");
                case "%=":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return (Integer) left % (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) return 0;
                        return ((int) (char) left) % (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return (Integer) left % rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) return 0;
                        return ((int) (char) left) % rightVal;
                    }
                    throw new RuntimeException("Invalid types for %= operator");
                case "&=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left & (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) & (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left & ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) & ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for &= operator");
                case "|=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left | (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) | (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left | ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) | ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for |= operator");
                case "^=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left ^ (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) ^ (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left ^ ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) ^ ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for ^= operator");
                case "<<=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left << (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) << (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left << ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) << ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for <<= operator");
                case ">>=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >> (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left >> ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) >> ((int) (char) right);
                    }
                    throw new RuntimeException("Invalid types for >>= operator");
                case ">>>=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >>> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >>> (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        return (Integer) left >>> ((int) (char) right);
                    } else if (left instanceof Character && right instanceof Character) {
                        return ((int) (char) left) >>> ((int) (char) right);
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
                    return operand;
                case "-":
                    if (operand instanceof Integer) {
                        return -(Integer) operand;
                    } else if (operand instanceof Character) {
                        return -((int) (char) operand);
                    }
                    throw new RuntimeException("Invalid type for - operator");
                case "not":
                    if (operand instanceof Boolean) {
                        return !(Boolean) operand;
                    } else if (operand instanceof Integer) {
                        return (Integer) operand == 0;
                    } else if (operand instanceof String) {
                        return ((String) operand).isEmpty();
                    } else if (operand instanceof Character) {
                        return (char) operand == 0;
                    }
                    throw new RuntimeException("Invalid type for not operator");
                case "~":
                    if (operand instanceof Integer) {
                        return ~(Integer) operand;
                    } else if (operand instanceof Character) {
                        return ~((int) (char) operand);
                    }
                    throw new RuntimeException("Invalid type for ~ operator");
                case "++":
                    if (operand instanceof Integer) {
                        return (Integer) operand + 1;
                    } else if (operand instanceof Character) {
                        return (char) (((int) (char) operand) + 1);
                    }
                    throw new RuntimeException("Invalid type for ++ operator");
                case "--":
                    if (operand instanceof Integer) {
                        return (Integer) operand - 1;
                    } else if (operand instanceof Character) {
                        return (char) (((int) (char) operand) - 1);
                    }
                    throw new RuntimeException("Invalid type for -- operator");
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
                    if (operand instanceof Integer) {
                        return (Integer) operand + 1;
                    } else if (operand instanceof Character) {
                        return (char) (((int) (char) operand) + 1);
                    }
                    throw new RuntimeException("Invalid type for ++ operator");
                case "--":
                    if (operand instanceof Integer) {
                        return (Integer) operand - 1;
                    } else if (operand instanceof Character) {
                        return (char) (((int) (char) operand) - 1);
                    }
                    throw new RuntimeException("Invalid type for -- operator");
                default:
                    throw new RuntimeException("Unknown unary postfix operator: " + op);
            }
        } catch (ClassCastException e) {
            throw new RuntimeException("Type mismatch: " + e.getMessage());
        }
    }

    private Object evaluateTypeCast(Object operand, MiniJavaParser.PrimitiveTypeContext type) {
        try {
            if (type.BOOLEAN() != null) {
                if (operand instanceof Integer) {
                    return ((Integer) operand) != 0;
                } else if (operand instanceof String) {
                    String s = (String) operand;
                    if (s.equalsIgnoreCase("true")) {
                        return true;
                    } else if (s.equalsIgnoreCase("false")) {
                        return false;
                    } else {
                        try {
                            return Integer.parseInt(s) != 0;
                        } catch (NumberFormatException e) {
                            return !s.isEmpty();
                        }
                    }
                } else if (operand instanceof Character) {
                    return ((int) (char) operand) != 0;
                } else if (operand instanceof Boolean) {
                    return operand;
                }
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to boolean");
            } else if (type.CHAR() != null) {
                if (operand instanceof Integer) {
                    int value = (Integer) operand;
                    if (value >= 0 && value <= 65535) {
                        return (char) value;
                    } else {
                        throw new RuntimeException("Integer value out of char range: " + value);
                    }
                } else if (operand instanceof String) {
                    String s = (String) operand;
                    return s.length() > 0 ? s.charAt(0) : '\0';
                } else if (operand instanceof Boolean) {
                    return ((Boolean) operand) ? 't' : 'f';
                } else if (operand instanceof Character) {
                    return operand;
                }
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to char");
            } else if (type.INT() != null) {
                if (operand instanceof Boolean) {
                    return ((Boolean) operand) ? 1 : 0;
                } else if (operand instanceof String) {
                    try {
                        return Integer.parseInt((String) operand);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Cannot parse string to int: " + operand);
                    }
                } else if (operand instanceof Character) {
                    return (int) (char) operand;
                } else if (operand instanceof Integer) {
                    return operand;
                }
                throw new RuntimeException("Cannot cast " + operand.getClass() + " to int");
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
            boolean takeThen = isTruthy(condition);
            return takeThen ? visit(thenExpr) : visit(elseExpr);
        } catch (Exception e) {
            throw new RuntimeException("Ternary operator error: " + e.getMessage());
        }
    }

    private boolean areEqual(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;

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
        return left.equals(right);
    }

    private int compare(Object left, Object right) {
        try {
            if (left instanceof Integer && right instanceof Integer) {
                return ((Integer) left).compareTo((Integer) right);
            } else if (left instanceof Boolean && right instanceof Boolean) {
                return ((Boolean) left).compareTo((Boolean) right);
            } else if (left instanceof String && right instanceof String) {
                return ((String) left).compareTo((String) right);
            } else if (left instanceof Character && right instanceof Character) {
                return ((Character) left).compareTo((Character) right);
            } else if (left instanceof Integer && right instanceof Character) {
                return ((Integer) left).compareTo((int) (char) right);
            } else if (left instanceof Character && right instanceof Integer) {
                return ((Integer) ((int) (char) left)).compareTo((Integer) right);
            } else if (left instanceof Number && right instanceof Number) {
                return ((Integer) (((Number) left).intValue())).compareTo(((Number) right).intValue());
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