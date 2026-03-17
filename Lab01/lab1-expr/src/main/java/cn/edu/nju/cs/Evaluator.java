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
            String text = ctx.CHAR_LITERAL().getText();
            if (text.length() >= 2) {
                return text.charAt(1);
            }
            throw new RuntimeException("Invalid character literal");
        } else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            if (text.length() >= 2) {
                return text.substring(1, text.length() - 1);
            }
            return "";
        }
        throw new RuntimeException("Unknown literal type");
    }

    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
        if (ctx.bop != null) {
            String op = ctx.bop.getText();
            if (op.equals("?")) {
                Object condition = visit(ctx.expression(0));
                Object thenExpr = visit(ctx.expression(1));
                Object elseExpr = visit(ctx.expression(2));
                return evaluateTernaryOperator(condition, thenExpr, elseExpr);
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
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left / (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return ((int) (char) left) / (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left / rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return ((int) (char) left) / rightVal;
                    }
                    throw new RuntimeException("Invalid types for / operator");
                case "%":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left % (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return ((int) (char) left) % (Integer) right;
                    } else if (left instanceof Integer && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left % rightVal;
                    } else if (left instanceof Character && right instanceof Character) {
                        int rightVal = (int) (char) right;
                        if (rightVal == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
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
                    return left.equals(right);
                case "!=":
                    return !left.equals(right);
                case "and":
                    if (left instanceof Boolean && right instanceof Boolean) {
                        return (Boolean) left && (Boolean) right;
                    }
                    throw new RuntimeException("Invalid types for and operator");
                case "or":
                    if (left instanceof Boolean && right instanceof Boolean) {
                        return (Boolean) left || (Boolean) right;
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
                    }
                    throw new RuntimeException("Invalid types for << operator");
                case ">>":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >> (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for >> operator");
                case ">>>":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >>> (Integer) right;
                    } else if (left instanceof Character && right instanceof Integer) {
                        return ((int) (char) left) >>> (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for >>> operator");
                case "=":
                    return right;
                case "+=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left + (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for += operator");
                case "-=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left - (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for -= operator");
                case "*=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left * (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for *= operator");
                case "/=":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left / (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for /= operator");
                case "%=":
                    if (left instanceof Integer && right instanceof Integer) {
                        if ((Integer) right == 0) {
                            throw new ArithmeticException("Division by zero");
                        }
                        return (Integer) left % (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for %= operator");
                case "&=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left & (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for &= operator");
                case "|=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left | (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for |= operator");
                case "^=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left ^ (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for ^= operator");
                case "<<=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left << (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for <<= operator");
                case ">>=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >> (Integer) right;
                    }
                    throw new RuntimeException("Invalid types for >>= operator");
                case ">>>=":
                    if (left instanceof Integer && right instanceof Integer) {
                        return (Integer) left >>> (Integer) right;
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
                    return Boolean.parseBoolean((String) operand);
                } else if (operand instanceof Character) {
                    return ((int) (char) operand) != 0;
                }
                return operand;
            } else if (type.CHAR() != null) {
                if (operand instanceof Integer) {
                    return (char) (int) operand;
                } else if (operand instanceof String) {
                    String s = (String) operand;
                    return s.length() > 0 ? s.charAt(0) : '\0';
                } else if (operand instanceof Boolean) {
                    return ((Boolean) operand) ? 't' : 'f';
                }
                return operand;
            } else if (type.INT() != null) {
                if (operand instanceof Boolean) {
                    return ((Boolean) operand) ? 1 : 0;
                } else if (operand instanceof String) {
                    try {
                        return Integer.parseInt((String) operand);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                } else if (operand instanceof Character) {
                    return (int) (char) operand;
                }
                return operand;
            } else if (type.STRING() != null) {
                return operand.toString();
            }
            return operand;
        } catch (Exception e) {
            throw new RuntimeException("Type cast error: " + e.getMessage());
        }
    }

    private Object evaluateTernaryOperator(Object condition, Object thenExpr, Object elseExpr) {
        try {
            if (condition instanceof Boolean) {
                return (Boolean) condition ? thenExpr : elseExpr;
            } else if (condition instanceof Integer) {
                return ((Integer) condition) != 0 ? thenExpr : elseExpr;
            } else if (condition instanceof String) {
                return !((String) condition).isEmpty() ? thenExpr : elseExpr;
            }
            throw new RuntimeException("Invalid condition type for ternary operator");
        } catch (Exception e) {
            throw new RuntimeException("Ternary operator error: " + e.getMessage());
        }
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
}