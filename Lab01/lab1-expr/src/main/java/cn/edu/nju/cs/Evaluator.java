package cn.edu.nju.cs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Evaluator extends MiniJavaParserBaseVisitor<Object> {
    // 作用域栈，用于管理变量
    private List<Map<String, Object>> scopeStack = new ArrayList<>();
    // 变量类型映射栈
    private List<Map<String, String>> typeStack = new ArrayList<>();

    private static final class BreakSignal extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class ContinueSignal extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private int loopDepth = 0;
    
    // 构造函数，初始化全局作用域
    public Evaluator() {
        Map<String, Object> globalScope = new LinkedHashMap<>();
        scopeStack.add(globalScope);
        Map<String, String> globalTypes = new LinkedHashMap<>();
        typeStack.add(globalTypes);
    }

    //****************************** 
    //********** 接口方法 ***********
    //******************************  
    
    //****************************** 
    //******* 本次Lab-不保证对 ******
    //******************************  

    @Override
    public Object visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        try {
            Object result = null;
            // 访问block节点
            var blockContext = ctx.block();
            // 遍历访问block中的所有blockStatement
            for (var blockStatement : blockContext.blockStatement()) {
                result = visit(blockStatement);
            }
            // 输出全局作用域中的变量
            printScope(0, scopeStack.get(0), typeStack.get(0));
            return result;
        } catch (RuntimeException e) {
            System.out.println("Process exits with 34.");
            System.exit(34);
            return null;
        }
    }
    
    @Override
    public Object visitBlock(MiniJavaParser.BlockContext ctx) {
        // 进入新作用域
        Map<String, Object> newScope = new LinkedHashMap<>();
        scopeStack.add(newScope);
        Map<String, String> newTypes = new LinkedHashMap<>();
        typeStack.add(newTypes);
        Object result = null;
        RuntimeException pending = null;
        boolean shouldPrint = true;
        try {
            // 遍历访问block中的所有blockStatement
            for (var blockStatement : ctx.blockStatement()) result = visit(blockStatement);
        } catch (RuntimeException e) {
            // break/continue 等控制流需要先完成作用域清理并打印；
            // 运行时错误不应额外打印“未正常退出”的作用域
            pending = e;
            if (!(e instanceof BreakSignal) && !(e instanceof ContinueSignal)) shouldPrint = false;
        } finally {
            // 退出作用域（按“层级”打印：同一层级可能出现多次）
            int level = scopeStack.size() - 1;
            Map<String, Object> scopeToPrint = scopeStack.remove(scopeStack.size() - 1);
            Map<String, String> typesToPrint = typeStack.remove(typeStack.size() - 1);
            if (shouldPrint) printScope(level, scopeToPrint, typesToPrint);
        }

        if (pending != null) throw pending;
        return result;
    }
    
    @Override
    public Object visitBlockStatement(MiniJavaParser.BlockStatementContext ctx) {
        // 处理局部变量声明
        if (ctx.localVariableDeclaration() != null) {
            return visit(ctx.localVariableDeclaration());
        }
        // 处理语句
        return visit(ctx.statement());
    }
    
    @Override
    public Object visitLocalVariableDeclaration(MiniJavaParser.LocalVariableDeclarationContext ctx) {
        String type = ctx.primitiveType().getText();
        String variableName = ctx.identifier().getText();
        Object value = null;
        
        if (ctx.expression() != null) {
            value = visit(ctx.expression());
        } else {
            value = visit(ctx.primitiveType());
        }

        value = coerceValueToType(type, value);
        
        // Note 1: 同一作用域重复声明报错
        Map<String, Object> currentScope = scopeStack.get(scopeStack.size() - 1);
        if (currentScope.containsKey(variableName)) {
            throw new RuntimeException("Duplicate declaration in same scope: " + variableName);
        }

        // 存储变量到当前作用域
        currentScope.put(variableName, value);
        // 存储变量类型
        Map<String, String> currentTypes = typeStack.get(typeStack.size() - 1);
        currentTypes.put(variableName, type);
        
        return value;
    }
    
    @Override
    public Object visitStatement(MiniJavaParser.StatementContext ctx) {
        // 空语句 ;
        if (ctx.getToken(MiniJavaParser.SEMI, 0) != null && ctx.getChildCount() == 1) return null;
        // break / continue
        if (ctx.getToken(MiniJavaParser.BREAK, 0) != null) {
            if (loopDepth <= 0) throw new RuntimeException("break used outside loop");
            throw new BreakSignal();
        }
        if (ctx.getToken(MiniJavaParser.CONTINUE, 0) != null) {
            if (loopDepth <= 0) throw new RuntimeException("continue used outside loop");
            throw new ContinueSignal();
        }
        // 处理表达式语句
        if (ctx.expression() != null) return visit(ctx.expression());
        // 处理 if 语句（必须按条件选择分支，不能 visitChildren）
        if (ctx.getToken(MiniJavaParser.IF, 0) != null) {
            Object cond = visit(ctx.parExpression().expression());
            if (!(cond instanceof Boolean)) throw new RuntimeException("Invalid type for if condition: expected boolean");
            if ((Boolean) cond) return visit(ctx.statement(0)); 
            else {
                // 有 else 分支时 statement(1) 存在
                if (ctx.statement().size() > 1) return visit(ctx.statement(1));
                return null;
            }
        }
        // 处理for循环语句
        if (ctx.getToken(MiniJavaParser.FOR, 0) != null) {
            // for 语句自身引入一层作用域：forInit 声明的变量属于这一层
            Map<String, Object> forScope = new LinkedHashMap<>();
            scopeStack.add(forScope);
            Map<String, String> forTypes = new LinkedHashMap<>();
            typeStack.add(forTypes);

            boolean completedNormally = false;
            try {
                MiniJavaParser.ForControlContext forControl = ctx.forControl();
                if (forControl != null) visit(forControl); 
                else {
                    // 如果没有 forControl，则作为死循环处理
                    MiniJavaParser.StatementContext body = ctx.statement(0);
                    loopDepth++;
                    try {
                        while (true) {
                            try {
                                if (body != null) visit(body);
                            } catch (ContinueSignal ignored) {
                            } catch (BreakSignal ignored) { break; }
                        }
                    } finally {
                        loopDepth--;
                    }
                }
                completedNormally = true;
            } finally {
                int level = scopeStack.size() - 1;
                Map<String, Object> scopeToPrint = scopeStack.remove(scopeStack.size() - 1);
                Map<String, String> typesToPrint = typeStack.remove(typeStack.size() - 1);
                if (completedNormally) {
                    printScope(level, scopeToPrint, typesToPrint);
                }
            }
            return null;
        }
        // 处理while循环语句
        if (ctx.getToken(MiniJavaParser.WHILE, 0) != null) {
            MiniJavaParser.ParExpressionContext parExpr = ctx.parExpression();
            MiniJavaParser.StatementContext body = ctx.statement(0);
            loopDepth++;
            try {
                while (true) {
                    Object condition = (parExpr == null) ? null : visit(parExpr.expression());
                    if (!(condition instanceof Boolean) || !((Boolean) condition)) break;
                    try {
                        if (body != null) visit(body);
                    } catch (ContinueSignal ignored) {
                    } catch (BreakSignal ignored) { break; }
                }
            } finally {
                loopDepth--;
            }
            return null;
        }
        // 处理其他类型的语句
        return visitChildren(ctx);
    }
    
    @Override
    public Object visitForControl(MiniJavaParser.ForControlContext ctx) {
        // 从父节点提取循环体
        MiniJavaParser.StatementContext body = ((MiniJavaParser.StatementContext) ctx.getParent()).statement(0);

        // 1. 访问for循环初始化部分
        if (ctx.forInit() != null) visit(ctx.forInit());

        loopDepth++;
        try {
            while (true) {
                // 2. 访问for循环条件部分
                if (ctx.expression() != null) {
                    Object condition = visit(ctx.expression());
                    if (!(condition instanceof Boolean) || !((Boolean) condition)) break;
                }

                // 3. 执行循环体
                try { if (body != null) visit(body); }
                catch (ContinueSignal ignored) {}   // continue: 跳过本次循环体，进入更新表达式
                catch (BreakSignal ignored) { break; }     // break: 跳出循环

                // 4. 访问for循环更新部分
                if (ctx.expressionList() != null) visit(ctx.expressionList());
            }
        } finally {
            loopDepth--;
        }
        return null;
    }
    
    @Override
    public Object visitParExpression(MiniJavaParser.ParExpressionContext ctx) {
        // 访问括号中的表达式
        return visit(ctx.expression());
    }
    
    @Override
    public Object visitForInit(MiniJavaParser.ForInitContext ctx) {
        // 访问for循环初始化部分的所有子节点
        return visitChildren(ctx);
    }
    
    @Override
    public Object visitExpressionList(MiniJavaParser.ExpressionListContext ctx) {
        Object result = null;
        // 遍历访问所有表达式，返回最后一个表达式的结果
        for (var expr : ctx.expression()) {
            result = visit(expr);
        }
        return result;
    }

    @Override
    public Object visitIdentifier(MiniJavaParser.IdentifierContext ctx) {
        String name = ctx.getText();
        // 从作用域栈中查找变量，从当前作用域开始向上查找
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            if (scopeStack.get(i).containsKey(name)) {
                return scopeStack.get(i).get(name);
            }
        }
        // 如果找不到变量，说明使用了未声明的变量，应该报错
        throw new RuntimeException("Undeclared variable: " + name);
    }

    //****************************** 
    //********** 检查过的 ***********
    //******************************  

    @Override
    public Object visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        } else if (ctx.literal() != null) {
            return visit(ctx.literal());
        } else if (ctx.identifier() != null) {
            return visit(ctx.identifier());
        }
        return null;
    }

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
                    Object result = evaluateBinaryOperator(left, right, op);
                    
                    // 处理赋值操作，更新变量值
                    if (isAssignmentOperator(op)) {
                        // Note 1/2: LHS 必须是标识符，且必须赋值给一个已存在变量
                        String variableName = null;
                        if (ctx.expression(0) instanceof MiniJavaParser.ExpressionContext leftExpr
                                && leftExpr.primary() != null
                                && leftExpr.primary().identifier() != null) {
                            variableName = leftExpr.primary().identifier().getText();
                        }
                        if (variableName == null) {
                            throw new RuntimeException("Invalid assignment: LHS must be an identifier");
                        }

                        String declaredType = getDeclaredType(variableName);
                        if (declaredType == null) {
                            throw new RuntimeException("Invalid assignment: undeclared variable " + variableName);
                        }
                        validateAssignmentOperatorForType(op, declaredType);

                        Object coercedResult = coerceValueToType(declaredType, result);

                        boolean updated = false;
                        for (int i = scopeStack.size() - 1; i >= 0; i--) {
                            if (scopeStack.get(i).containsKey(variableName)) {
                                scopeStack.get(i).put(variableName, coercedResult);
                                updated = true;
                                break;
                            }
                        }
                        if (!updated) {
                            throw new RuntimeException("Invalid assignment: can only assign to a variable");
                        }

                        // 赋值表达式的值是写回后的值
                        result = coercedResult;
                    }
                    
                    return result;
                }
            } else if (ctx.prefix != null) {
                // 前缀运算符
                String op = ctx.prefix.getText();
                // 处理前缀自增/自减运算符
                if (op.equals("++") || op.equals("--")) {
                    // 检查左侧是否为标识符（变量）
                    if (ctx.expression(0) instanceof MiniJavaParser.ExpressionContext) {
                        MiniJavaParser.ExpressionContext expr = (MiniJavaParser.ExpressionContext) ctx.expression(0);
                        if (expr.primary() != null && expr.primary().identifier() != null) {
                            String variableName = expr.primary().identifier().getText();
                            // 查找变量值
                            Object value = null;
                            int scopeIndex = -1;
                            for (int i = scopeStack.size() - 1; i >= 0; i--) {
                                if (scopeStack.get(i).containsKey(variableName)) {
                                    value = scopeStack.get(i).get(variableName);
                                    scopeIndex = i;
                                    break;
                                }
                            }
                            if (value != null && scopeIndex != -1) {
                                // 处理前缀自增/自减运算符
                                String declaredType = getDeclaredType(variableName);
                                if (value instanceof Integer) {
                                    int intValue = (Integer) value;
                                    int newValue = op.equals("++") ? (intValue + 1) : (intValue - 1);
                                    Object coerced = declaredType == null ? newValue : coerceValueToType(declaredType, newValue);
                                    scopeStack.get(scopeIndex).put(variableName, coerced);
                                    return coerced;
                                }
                                if (value instanceof Character c) {
                                    int intValue = c.charValue();
                                    int newValue = op.equals("++") ? (intValue + 1) : (intValue - 1);
                                    Object coerced = declaredType == null ? (char) (newValue & 0xFF) : coerceValueToType(declaredType, newValue);
                                    scopeStack.get(scopeIndex).put(variableName, coerced);
                                    return coerced;
                                }
                            }
                        }
                    }
                    // 如果不是变量，或者处理失败，抛出异常
                    throw new RuntimeException("Invalid use of " + op + " operator: can only be applied to variables");
                } else {
                    // 其他前缀运算符
                    Object operand = visit(ctx.expression(0));
                    return evaluateUnaryPrefixOperator(operand, op);
                }
            } else if (ctx.postfix != null) {
                // 后缀运算符
                String op = ctx.postfix.getText();
                // 检查左侧是否为标识符（变量）
                if (ctx.expression(0) instanceof MiniJavaParser.ExpressionContext) {
                    MiniJavaParser.ExpressionContext expr = (MiniJavaParser.ExpressionContext) ctx.expression(0);
                    if (expr.primary() != null && expr.primary().identifier() != null) {
                        String variableName = expr.primary().identifier().getText();
                        // 查找变量值
                        Object value = null;
                        int scopeIndex = -1;
                        for (int i = scopeStack.size() - 1; i >= 0; i--) {
                            if (scopeStack.get(i).containsKey(variableName)) {
                                value = scopeStack.get(i).get(variableName);
                                scopeIndex = i;
                                break;
                            }
                        }
                        if (value != null && scopeIndex != -1) {
                            // 处理后缀自增/自减运算符
                            if (op.equals("++")) {
                                String declaredType = getDeclaredType(variableName);
                                if (value instanceof Integer) {
                                    int intValue = (Integer) value;
                                    Object coercedNew = declaredType == null ? (intValue + 1) : coerceValueToType(declaredType, intValue + 1);
                                    scopeStack.get(scopeIndex).put(variableName, coercedNew);
                                    return value; // 返回自增前的值
                                }
                                if (value instanceof Character c) {
                                    int intValue = c.charValue();
                                    Object coercedNew = declaredType == null ? (char) ((intValue + 1) & 0xFF) : coerceValueToType(declaredType, intValue + 1);
                                    scopeStack.get(scopeIndex).put(variableName, coercedNew);
                                    return value; // 返回自增前的值
                                }
                            } else if (op.equals("--")) {
                                String declaredType = getDeclaredType(variableName);
                                if (value instanceof Integer) {
                                    int intValue = (Integer) value;
                                    Object coercedNew = declaredType == null ? (intValue - 1) : coerceValueToType(declaredType, intValue - 1);
                                    scopeStack.get(scopeIndex).put(variableName, coercedNew);
                                    return value; // 返回自减前的值
                                }
                                if (value instanceof Character c) {
                                    int intValue = c.charValue();
                                    Object coercedNew = declaredType == null ? (char) ((intValue - 1) & 0xFF) : coerceValueToType(declaredType, intValue - 1);
                                    scopeStack.get(scopeIndex).put(variableName, coercedNew);
                                    return value; // 返回自减前的值
                                }
                            }
                        }
                    }
                }
                // 如果不是变量，或者处理失败，抛出异常
                throw new RuntimeException("Invalid use of " + op + " operator: can only be applied to variables");
            } else if (ctx.primitiveType() != null) {
                // 类型转换
                Object operand = visit(ctx.expression(0));
                return evaluateTypeCast(operand, ctx.primitiveType());
            }
            return visitChildren(ctx);
        } catch (Exception e) {
            System.out.println("Process exits with 34.");
            System.exit(34);
            return null; // This line is unreachable, but required for compilation
        }
    }

    private boolean isAssignmentOperator(String op) {
        return op.equals("=")
                || op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=") || op.equals("%=")
                || op.equals("&=") || op.equals("|=") || op.equals("^=")
                || op.equals("<<=") || op.equals(">>=") || op.equals(">>>=");
    }

    private void validateAssignmentOperatorForType(String op, String declaredType) {
        // Note 4: string 只支持 = 和 +=
        if ("string".equals(declaredType)) {
            if (!op.equals("=") && !op.equals("+=")) {
                throw new RuntimeException("Invalid assignment operator " + op + " for string");
            }
            return;
        }
        // boolean 只支持 =
        if ("boolean".equals(declaredType)) {
            if (!op.equals("=")) {
                throw new RuntimeException("Invalid assignment operator " + op + " for boolean");
            }
            return;
        }
        // int/char：支持所有赋值运算符（运算阶段按 int 计算，最终由 coerceValueToType 收敛）
        if ("int".equals(declaredType) || "char".equals(declaredType)) {
            return;
        }
        throw new RuntimeException("Invalid assignment target type: " + declaredType);
    }

    private Object coerceValueToType(String type, Object value) {
        if (type == null) return value;
        return switch (type) {
            case "int" -> {
                if (value instanceof Integer) yield value;
                if (value instanceof Character) yield toInt(value, "int");
                throw new RuntimeException("Type mismatch: cannot assign " + value.getClass().getName() + " to int");
            }
            case "char" -> {
                if (value instanceof Character) yield value;
                if (value instanceof Integer i) yield (char) (i & 0xFF);
                throw new RuntimeException("Type mismatch: cannot assign " + value.getClass().getName() + " to char");
            }
            case "boolean" -> {
                if (value instanceof Boolean) yield value;
                throw new RuntimeException("Type mismatch: cannot assign " + value.getClass().getName() + " to boolean");
            }
            case "string" -> {
                if (value instanceof String) yield value;
                throw new RuntimeException("Type mismatch: cannot assign " + value.getClass().getName() + " to string");
            }
            default -> value;
        };
    }

    @Override
    public Object visitPrimitiveType(MiniJavaParser.PrimitiveTypeContext ctx) {
        String type = ctx.getText();
        Object value = "";
        if (type.equals("int") || type.equals("char")) {
            value = type.equals("char") ? (char) 0 : 0;
        } else if (type.equals("boolean")) {
            value = false;
        } 
        return value;
    }
    
    
    
    //******************************
    //********* 非接口方法 ***********
    //******************************  

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
            if (!((Boolean)left)) return false;
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for and operator: expected boolean, got " + right.getClass().getName());
            }
            return (Boolean)right;
        } else if (op.equals("or")) {
            if ((Boolean)left) return true;
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("Invalid type for or operator: expected boolean, got " + right.getClass().getName());
            }
            return (Boolean)right;
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

    // 类型转换，检查过
    private Object evaluateTypeCast(Object operand, MiniJavaParser.PrimitiveTypeContext type) {
        if (type.INT() != null) {
            if (operand instanceof Character c) {
                int value = (int) c;
                if ((c & 0x8000) != 0) value |= 0xFFFF0000;
                return value;
            } else if (operand instanceof Integer i) {
                return i;
            }
            throw new RuntimeException("Cannot cast " + operand.getClass() + " to int");
        }
        if (type.CHAR() != null) {
            if (operand instanceof Integer i) {
                return (char) (i & 0xFF);
            } else if (operand instanceof Character c) {
                return c;
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

    // 检查过
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
    
    private void printScope(int level, Map<String, Object> scope, Map<String, String> types) {
        scope.keySet().stream().sorted().forEach(variableName -> {
            Object value = scope.get(variableName);
            String type = types.getOrDefault(variableName, "unknown");
            System.out.println("Scope " + level + ": " + variableName + ": (" + type + ") " + value);
        });
    }

    private String getDeclaredType(String variableName) {
        for (int i = typeStack.size() - 1; i >= 0; i--) {
            if (typeStack.get(i).containsKey(variableName)) {
                return typeStack.get(i).get(variableName);
            }
        }
        return null;
    }
}