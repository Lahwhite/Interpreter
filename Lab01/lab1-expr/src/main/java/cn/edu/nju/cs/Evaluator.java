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
            return Integer.parseInt(text);
        } 
        // else if (ctx.HEX_LITERAL() != null) {
        //     String text = ctx.HEX_LITERAL().getText().replace("_", "");
        //     return Integer.parseInt(text.substring(2), 16);
        // } else if (ctx.OCT_LITERAL() != null) {
        //     String text = ctx.OCT_LITERAL().getText().replace("_", "");
        //     return Integer.parseInt(text.substring(1), 8);
        // } else if (ctx.BINARY_LITERAL() != null) {
        //     String text = ctx.BINARY_LITERAL().getText().replace("_", "");
        //     return Integer.parseInt(text.substring(2), 2);
        // } 
        else if (ctx.BOOL_LITERAL() != null) {
            return Boolean.parseBoolean(ctx.BOOL_LITERAL().getText());
        } else if (ctx.CHAR_LITERAL() != null) {
            String text = ctx.CHAR_LITERAL().getText();
            return text.charAt(1);
        } else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        }
        return null;
    }

    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
        if (ctx.bop != null) {
            Object left = visit(ctx.expression(0));
            Object right = visit(ctx.expression(1));
            String op = ctx.bop.getText();
            return evaluateBinaryOperator(left, right, op);
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
        } else if (ctx.QUESTION() != null) {
            Object condition = visit(ctx.expression(0));
            Object thenExpr = visit(ctx.expression(1));
            Object elseExpr = visit(ctx.expression(2));
            return evaluateTernaryOperator(condition, thenExpr, elseExpr);
        }
        return visitChildren(ctx);
    }

    private Object evaluateBinaryOperator(Object left, Object right, String op) {
        switch (op) {
            case "+":
                if (left instanceof String || right instanceof String) {
                    return left.toString() + right.toString();
                }
                return (Integer) left + (Integer) right;
            case "-":
                return (Integer) left - (Integer) right;
            case "*":
                return (Integer) left * (Integer) right;
            case "/":
                if ((Integer) right == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return (Integer) left / (Integer) right;
            case "%":
                if ((Integer) right == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return (Integer) left % (Integer) right;
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
                return (Boolean) left && (Boolean) right;
            case "or":
                return (Boolean) left || (Boolean) right;
            case "&":
                return (Integer) left & (Integer) right;
            case "|":
                return (Integer) left | (Integer) right;
            case "^":
                return (Integer) left ^ (Integer) right;
            case "<<":
                return (Integer) left << (Integer) right;
            case ">>":
                return (Integer) left >> (Integer) right;
            case ">>>":
                return (Integer) left >>> (Integer) right;
            case "=":
                return right;
            case "+=":
                return (Integer) left + (Integer) right;
            case "-=":
                return (Integer) left - (Integer) right;
            case "*=":
                return (Integer) left * (Integer) right;
            case "/=":
                if ((Integer) right == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return (Integer) left / (Integer) right;
            case "%=":
                if ((Integer) right == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return (Integer) left % (Integer) right;
            case "&=":
                return (Integer) left & (Integer) right;
            case "|=":
                return (Integer) left | (Integer) right;
            case "^=":
                return (Integer) left ^ (Integer) right;
            case "<<=":
                return (Integer) left << (Integer) right;
            case ">>=":
                return (Integer) left >> (Integer) right;
            case ">>>=":
                return (Integer) left >>> (Integer) right;
            default:
                throw new RuntimeException("Unknown binary operator: " + op);
        }
    }

    private Object evaluateUnaryPrefixOperator(Object operand, String op) {
        switch (op) {
            case "+":
                return operand;
            case "-":
                return -(Integer) operand;
            case "not":
                return !(Boolean) operand;
            case "~":
                return ~(Integer) operand;
            case "++":
                return (Integer) operand + 1;
            case "--":
                return (Integer) operand - 1;
            default:
                throw new RuntimeException("Unknown unary prefix operator: " + op);
        }
    }

    private Object evaluateUnaryPostfixOperator(Object operand, String op) {
        switch (op) {
            case "++":
                return (Integer) operand + 1;
            case "--":
                return (Integer) operand - 1;
            default:
                throw new RuntimeException("Unknown unary postfix operator: " + op);
        }
    }

    private Object evaluateTypeCast(Object operand, MiniJavaParser.PrimitiveTypeContext type) {
        if (type.BOOLEAN() != null) {
            if (operand instanceof Integer) {
                return ((Integer) operand) != 0;
            }
            return operand;
        } else if (type.CHAR() != null) {
            if (operand instanceof Integer) {
                return (char) (int) operand;
            }
            return operand;
        } else if (type.INT() != null) {
            if (operand instanceof Boolean) {
                return ((Boolean) operand) ? 1 : 0;
            }
            return operand;
        } else if (type.STRING() != null) {
            return operand.toString();
        }
        return operand;
    }

    private Object evaluateTernaryOperator(Object condition, Object thenExpr, Object elseExpr) {
        return (Boolean) condition ? thenExpr : elseExpr;
    }

    private int compare(Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            return ((Integer) left).compareTo((Integer) right);
        } else if (left instanceof Boolean && right instanceof Boolean) {
            return ((Boolean) left).compareTo((Boolean) right);
        } else if (left instanceof String && right instanceof String) {
            return ((String) left).compareTo((String) right);
        } else if (left instanceof Character && right instanceof Character) {
            return ((Character) left).compareTo((Character) right);
        }
        throw new RuntimeException("Cannot compare " + left.getClass() + " and " + right.getClass());
    }
}