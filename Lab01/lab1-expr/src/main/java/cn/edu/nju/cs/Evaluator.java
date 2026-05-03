package cn.edu.nju.cs;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lab 3：方法、重载、数组、var、内建函数与入口 main。
 */
public class Evaluator extends MiniJavaParserBaseVisitor<Object> {

    private static final Object VOID_RETURN = new Object();
    private static final String NULL_T = "null";

    private enum InferenceMode {
        ARGUMENT,
        GENERAL,
        VAR_INIT,
        RETURN_STMT
    }

    private static final class BreakSignal extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class ContinueSignal extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;

        ReturnSignal(Object value) {
            this.value = value;
        }

        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class CompiledMethod {
        final String returnType;
        final List<String> paramTypes;
        final List<String> paramNames;
        final MiniJavaParser.BlockContext body;

        CompiledMethod(String returnType, List<String> paramTypes, List<String> paramNames, MiniJavaParser.BlockContext body) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
            this.paramNames = paramNames;
            this.body = body;
        }
    }

    private final Map<String, List<CompiledMethod>> methods = new LinkedHashMap<>();
    private final List<Map<String, Object>> scopeStack = new ArrayList<>();
    private final List<Map<String, String>> typeStack = new ArrayList<>();

    private final Deque<String> currentReturnType = new ArrayDeque<>();
    private int loopDepth = 0;

    /** 解析局部变量初始化器 / 数组字面量时期望的完整类型 */
    private final Deque<String> expectedDeclType = new ArrayDeque<>();

    public Evaluator() {
        scopeStack.add(new LinkedHashMap<>());
        typeStack.add(new LinkedHashMap<>());
    }

    private static void exitWith(int code) {
        System.out.print("Process exits with " + code + ".\n");
        System.exit(code);
    }

    private static void exitAssertFail() {
        exitWith(33);
    }

    private static void exitRuntime() {
        exitWith(34);
    }

    // -------------------------------------------------------------------------
    // compilation & entry
    // -------------------------------------------------------------------------

    @Override
    public Object visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        try {
            methods.clear();
            for (MiniJavaParser.MethodDeclarationContext md : ctx.methodDeclaration()) {
                registerMethod(md);
            }
            CompiledMethod main = resolveEntryMain();
            Object ret = invokeUserMethod(main, List.of());
            if (!(ret instanceof Integer)) {
                throw new RuntimeException("main must return int");
            }
            int code = (Integer) ret;
            System.out.print("Process exits with " + code + ".\n");
            System.exit(code);
            return null;
        } catch (RuntimeException e) {
            exitRuntime();
            return null;
        }
    }

    private void registerMethod(MiniJavaParser.MethodDeclarationContext md) {
        String ret = md.VOID() != null ? "void" : typeTypeToString(md.typeType());
        String name = md.identifier().getText();
        List<String> pTypes = new ArrayList<>();
        List<String> pNames = new ArrayList<>();
        MiniJavaParser.FormalParameterListContext fpl = md.formalParameters().formalParameterList();
        if (fpl != null) {
            for (MiniJavaParser.FormalParameterContext fp : fpl.formalParameter()) {
                pTypes.add(typeTypeToString(fp.typeType()));
                pNames.add(fp.identifier().getText());
            }
        }
        CompiledMethod cm = new CompiledMethod(ret, pTypes, pNames, md.block());
        List<CompiledMethod> list = methods.computeIfAbsent(name, k -> new ArrayList<>());
        for (CompiledMethod existing : list) {
            if (existing.paramTypes.equals(pTypes)) {
                throw new RuntimeException("Duplicate method signature: " + name);
            }
        }
        list.add(cm);
    }

    private CompiledMethod resolveEntryMain() {
        List<CompiledMethod> mains = methods.getOrDefault("main", List.of());
        List<CompiledMethod> zero = new ArrayList<>();
        for (CompiledMethod m : mains) {
            if (m.paramTypes.isEmpty()) zero.add(m);
        }
        if (zero.isEmpty()) {
            throw new RuntimeException("No int main()");
        }
        boolean hasInt = false;
        boolean hasVoid = false;
        CompiledMethod intMain = null;
        for (CompiledMethod m : zero) {
            if ("int".equals(m.returnType)) {
                hasInt = true;
                intMain = m;
            }
            if ("void".equals(m.returnType)) {
                hasVoid = true;
            }
        }
        if (hasInt && hasVoid) {
            throw new RuntimeException("Ambiguous main");
        }
        if (!hasInt) {
            throw new RuntimeException("No int main()");
        }
        return intMain;
    }

    // -------------------------------------------------------------------------
    // block / statement
    // -------------------------------------------------------------------------

    @Override
    public Object visitBlock(MiniJavaParser.BlockContext ctx) {
        Map<String, Object> newScope = new LinkedHashMap<>();
        scopeStack.add(newScope);
        Map<String, String> newTypes = new LinkedHashMap<>();
        typeStack.add(newTypes);
        Object result = null;
        RuntimeException pending = null;
        try {
            for (MiniJavaParser.BlockStatementContext bs : ctx.blockStatement()) {
                result = visit(bs);
            }
        } catch (RuntimeException e) {
            pending = e;
            if (!(e instanceof BreakSignal) && !(e instanceof ContinueSignal) && !(e instanceof ReturnSignal)) {
                throw e;
            }
        } finally {
            scopeStack.remove(scopeStack.size() - 1);
            typeStack.remove(typeStack.size() - 1);
        }
        if (pending != null) throw pending;
        return result;
    }

    @Override
    public Object visitBlockStatement(MiniJavaParser.BlockStatementContext ctx) {
        if (ctx.localVariableDeclaration() != null) {
            return visit(ctx.localVariableDeclaration());
        }
        return visit(ctx.statement());
    }

    @Override
    public Object visitLocalVariableDeclaration(MiniJavaParser.LocalVariableDeclarationContext ctx) {
        if (ctx.VAR() != null) {
            String name = ctx.identifier().getText();
            MiniJavaParser.ExpressionContext init = ctx.expression();
            String inferred = inferExprType(init, InferenceMode.VAR_INIT);
            if (NULL_T.equals(inferred)) {
                throw new RuntimeException("var cannot infer from null");
            }
            Object value = visit(init);
            value = coerceValueToType(inferred, value);
            Map<String, Object> scope = scopeStack.get(scopeStack.size() - 1);
            if (scope.containsKey(name)) {
                throw new RuntimeException("Duplicate declaration: " + name);
            }
            scope.put(name, value);
            typeStack.get(typeStack.size() - 1).put(name, inferred);
            return value;
        }
        String fullType = typeTypeToString(ctx.typeType());
        MiniJavaParser.VariableDeclaratorContext vd = ctx.variableDeclarator();
        String variableName = vd.identifier().getText();
        Object value = defaultValueForType(fullType);
        if (vd.variableInitializer() != null) {
            expectedDeclType.push(fullType);
            try {
                value = visit(vd.variableInitializer());
            } finally {
                expectedDeclType.pop();
            }
            if ("char".equals(fullType) && vd.variableInitializer().expression() != null) {
                validateImplicitCharAssignment("char", "=", vd.variableInitializer().expression());
            }
            value = coerceValueToType(fullType, value);
        }
        Map<String, Object> currentScope = scopeStack.get(scopeStack.size() - 1);
        if (currentScope.containsKey(variableName)) {
            throw new RuntimeException("Duplicate declaration: " + variableName);
        }
        currentScope.put(variableName, value);
        typeStack.get(typeStack.size() - 1).put(variableName, fullType);
        return value;
    }

    @Override
    public Object visitVariableInitializer(MiniJavaParser.VariableInitializerContext ctx) {
        if (ctx.arrayInitializer() != null) {
            return visitArrayInitializer(ctx.arrayInitializer());
        }
        return visit(ctx.expression());
    }

    @Override
    public Object visitArrayInitializer(MiniJavaParser.ArrayInitializerContext ctx) {
        String expect = expectedDeclType.peek();
        if (expect == null) {
            throw new RuntimeException("Array initializer without context type");
        }
        return buildArrayFromInitializer(expect, ctx);
    }

    @Override
    public Object visitStatement(MiniJavaParser.StatementContext ctx) {
        if (ctx.getToken(MiniJavaParser.SEMI, 0) != null && ctx.getChildCount() == 1) {
            return null;
        }
        if (ctx.getToken(MiniJavaParser.BREAK, 0) != null) {
            if (loopDepth <= 0) throw new RuntimeException("break outside loop");
            throw new BreakSignal();
        }
        if (ctx.getToken(MiniJavaParser.CONTINUE, 0) != null) {
            if (loopDepth <= 0) throw new RuntimeException("continue outside loop");
            throw new ContinueSignal();
        }
        if (ctx.getToken(MiniJavaParser.RETURN, 0) != null) {
            String rt = currentReturnType.peek();
            if ("void".equals(rt)) {
                if (ctx.expression() != null) {
                    throw new RuntimeException("void return with value");
                }
                throw new ReturnSignal(VOID_RETURN);
            } else {
                if (ctx.expression() == null) {
                    throw new RuntimeException("missing return value");
                }
                Object v = visit(ctx.expression());
                v = coerceForReturn(rt, ctx.expression(), v);
                throw new ReturnSignal(v);
            }
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        if (ctx.getToken(MiniJavaParser.IF, 0) != null) {
            Object cond = visit(ctx.parExpression().expression());
            if (!(cond instanceof Boolean)) {
                throw new RuntimeException("if condition must be boolean");
            }
            if ((Boolean) cond) {
                return visit(ctx.statement(0));
            }
            if (ctx.statement().size() > 1) {
                return visit(ctx.statement(1));
            }
            return null;
        }
        if (ctx.getToken(MiniJavaParser.FOR, 0) != null) {
            Map<String, Object> forScope = new LinkedHashMap<>();
            scopeStack.add(forScope);
            Map<String, String> forTypes = new LinkedHashMap<>();
            typeStack.add(forTypes);
            try {
                MiniJavaParser.ForControlContext forControl = ctx.forControl();
                if (forControl != null) {
                    visit(forControl);
                } else {
                    MiniJavaParser.StatementContext body = ctx.statement(0);
                    loopDepth++;
                    try {
                        while (true) {
                            try {
                                if (body != null) visit(body);
                            } catch (ContinueSignal ignored) {
                            } catch (BreakSignal ignored) {
                                break;
                            }
                        }
                    } finally {
                        loopDepth--;
                    }
                }
            } finally {
                scopeStack.remove(scopeStack.size() - 1);
                typeStack.remove(typeStack.size() - 1);
            }
            return null;
        }
        if (ctx.getToken(MiniJavaParser.WHILE, 0) != null) {
            MiniJavaParser.ParExpressionContext parExpr = ctx.parExpression();
            MiniJavaParser.StatementContext body = ctx.statement(0);
            loopDepth++;
            try {
                while (true) {
                    Object condition = parExpr == null ? null : visit(parExpr.expression());
                    if (!(condition instanceof Boolean) || !((Boolean) condition)) {
                        break;
                    }
                    try {
                        if (body != null) visit(body);
                    } catch (ContinueSignal ignored) {
                    } catch (BreakSignal ignored) {
                        break;
                    }
                }
            } finally {
                loopDepth--;
            }
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Object visitForControl(MiniJavaParser.ForControlContext ctx) {
        MiniJavaParser.StatementContext body = ((MiniJavaParser.StatementContext) ctx.getParent()).statement(0);
        if (ctx.forInit() != null) {
            visit(ctx.forInit());
        }
        loopDepth++;
        try {
            while (true) {
                if (ctx.expression() != null) {
                    Object condition = visit(ctx.expression());
                    if (!(condition instanceof Boolean) || !((Boolean) condition)) {
                        break;
                    }
                }
                try {
                    if (body != null) visit(body);
                } catch (ContinueSignal ignored) {
                } catch (BreakSignal ignored) {
                    break;
                }
                if (ctx.expressionList() != null) {
                    visit(ctx.expressionList());
                }
            }
        } finally {
            loopDepth--;
        }
        return null;
    }

    @Override
    public Object visitForInit(MiniJavaParser.ForInitContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitParExpression(MiniJavaParser.ParExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitExpressionList(MiniJavaParser.ExpressionListContext ctx) {
        Object result = null;
        for (MiniJavaParser.ExpressionContext e : ctx.expression()) {
            result = visit(e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // method call / creator / type
    // -------------------------------------------------------------------------

    @Override
    public Object visitMethodCall(MiniJavaParser.MethodCallContext ctx) {
        String name = ctx.identifier().getText();
        List<MiniJavaParser.ExpressionContext> argExprs = new ArrayList<>();
        MiniJavaParser.ArgumentsContext ac = ctx.arguments();
        if (ac != null && ac.expressionList() != null) {
            argExprs.addAll(ac.expressionList().expression());
        }
        if (isBuiltin(name)) {
            return dispatchBuiltin(name, argExprs);
        }
        List<String> argTypes = new ArrayList<>();
        for (MiniJavaParser.ExpressionContext e : argExprs) {
            argTypes.add(inferExprType(e, InferenceMode.ARGUMENT));
        }
        CompiledMethod target = resolveOverload(name, argTypes, argExprs);
        List<Object> evalArgs = new ArrayList<>();
        for (MiniJavaParser.ExpressionContext e : argExprs) {
            evalArgs.add(visit(e));
        }
        for (int i = 0; i < evalArgs.size(); i++) {
            evalArgs.set(i, coerceArgToParam(target.paramTypes.get(i), evalArgs.get(i), argExprs.get(i)));
        }
        return invokeUserMethod(target, evalArgs);
    }

    @Override
    public Object visitTypeType(MiniJavaParser.TypeTypeContext ctx) {
        return defaultValueForType(typeTypeToString(ctx));
    }

    @Override
    public Object visitPrimitiveType(MiniJavaParser.PrimitiveTypeContext ctx) {
        if (ctx.INT() != null) return 0;
        if (ctx.CHAR() != null) return (char) 0;
        if (ctx.BOOLEAN() != null) return false;
        if (ctx.STRING() != null) return "";
        throw new RuntimeException("unknown primitive");
    }

    // -------------------------------------------------------------------------
    // expression
    // -------------------------------------------------------------------------

    @Override
    public Object visitExpression(MiniJavaParser.ExpressionContext ctx) {
        if (ctx.bop != null) {
            return visitBinaryOrAssign(ctx);
        }
        if (ctx.methodCall() != null) {
            return visit(ctx.methodCall());
        }
        if (ctx.NEW() != null) {
            return visit(ctx.creator());
        }
        if (isArraySubscript(ctx)) {
            return readArrayAccess(ctx);
        }
        if (ctx.prefix != null) {
            return visitPrefix(ctx);
        }
        if (ctx.postfix != null) {
            return visitPostfix(ctx);
        }
        if (ctx.LPAREN() != null && ctx.typeType() != null) {
            Object operand = visit(ctx.expression(0));
            return evaluateTypeCast(operand, ctx.typeType());
        }
        if (ctx.primary() != null) {
            return visit(ctx.primary());
        }
        return visitChildren(ctx);
    }

    private Object visitBinaryOrAssign(MiniJavaParser.ExpressionContext ctx) {
        String op = ctx.bop.getText();
        if (ctx.bop.getType() == MiniJavaParser.QUESTION) {
            Object condition = visit(ctx.expression(0));
            return evaluateTernaryOperator(condition, ctx.expression(1), ctx.expression(2));
        }
        if ("and".equals(op) || "or".equals(op)) {
            Object left = visit(ctx.expression(0));
            return evaluateLogicalOperatorWithShortCircuit(left, ctx.expression(1), op);
        }
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        Object result = evaluateBinaryOperator(left, right, op);
        if (isAssignmentOperator(op)) {
            MiniJavaParser.ExpressionContext lhs = ctx.expression(0);
            String declaredType = resolveLValueType(lhs);
            validateAssignmentOperatorForType(op, declaredType);
            validateImplicitCharAssignment(declaredType, op, ctx.expression(1));
            Object coerced = coerceValueToType(declaredType, result);
            assignToLValue(lhs, coerced);
            return coerced;
        }
        return result;
    }

    private Object visitPrefix(MiniJavaParser.ExpressionContext ctx) {
        String op = ctx.prefix.getText();
        if ("++".equals(op) || "--".equals(op)) {
            MiniJavaParser.ExpressionContext target = ctx.expression(0);
            if (target.primary() != null && target.primary().identifier() != null) {
                String variableName = target.primary().identifier().getText();
                return applyIncDec(variableName, op, true);
            }
            throw new RuntimeException("Invalid use of " + op);
        }
        Object operand = visit(ctx.expression(0));
        return evaluateUnaryPrefixOperator(operand, op);
    }

    private Object visitPostfix(MiniJavaParser.ExpressionContext ctx) {
        String op = ctx.postfix.getText();
        MiniJavaParser.ExpressionContext target = ctx.expression(0);
        if (target.primary() != null && target.primary().identifier() != null) {
            String variableName = target.primary().identifier().getText();
            return applyIncDec(variableName, op, false);
        }
        throw new RuntimeException("Invalid use of " + op);
    }

    @Override
    public Object visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        if (ctx.literal() != null) {
            return visit(ctx.literal());
        }
        if (ctx.identifier() != null) {
            return visit(ctx.identifier());
        }
        return null;
    }

    @Override
    public Object visitLiteral(MiniJavaParser.LiteralContext ctx) {
        if (ctx.NULL_LITERAL() != null) {
            return null;
        }
        if (ctx.DECIMAL_LITERAL() != null) {
            return Integer.parseInt(ctx.DECIMAL_LITERAL().getText().replace("_", ""));
        }
        if (ctx.BOOL_LITERAL() != null) {
            return "true".equals(ctx.getText());
        }
        if (ctx.CHAR_LITERAL() != null) {
            return ctx.CHAR_LITERAL().getText().charAt(1);
        }
        if (ctx.STRING_LITERAL() != null) {
            String lit = ctx.getText();
            return lit.substring(1, lit.length() - 1);
        }
        return null;
    }

    @Override
    public Object visitIdentifier(MiniJavaParser.IdentifierContext ctx) {
        String name = ctx.getText();
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            if (scopeStack.get(i).containsKey(name)) {
                return scopeStack.get(i).get(name);
            }
        }
        throw new RuntimeException("Undeclared variable: " + name);
    }

    // -------------------------------------------------------------------------
    // builtins
    // -------------------------------------------------------------------------

    private static boolean isBuiltin(String name) {
        return switch (name) {
            case "print", "println", "assert", "length", "to_char_array", "to_string", "atoi", "itoa" -> true;
            default -> false;
        };
    }

    private Object dispatchBuiltin(String name, List<MiniJavaParser.ExpressionContext> args) {
        return switch (name) {
            case "print" -> {
                if (args.size() != 1) throw new RuntimeException("print arity");
                printArg(visit(args.get(0)));
                yield null;
            }
            case "println" -> {
                if (args.isEmpty()) {
                    System.out.print("\n");
                } else if (args.size() == 1) {
                    printArg(visit(args.get(0)));
                    System.out.print("\n");
                } else {
                    throw new RuntimeException("println arity");
                }
                yield null;
            }
            case "assert" -> {
                if (args.size() != 1) throw new RuntimeException("assert arity");
                Object v = visit(args.get(0));
                if (!(v instanceof Boolean)) {
                    throw new RuntimeException("assert needs boolean");
                }
                if (!(Boolean) v) {
                    exitAssertFail();
                }
                yield null;
            }
            case "length" -> {
                if (args.size() != 1) throw new RuntimeException("length arity");
                Object v = visit(args.get(0));
                if (v == null) {
                    throw new RuntimeException("null length");
                }
                if (v instanceof String s) {
                    yield s.length();
                }
                if (v instanceof int[] a) {
                    yield a.length;
                }
                if (v instanceof char[] c) {
                    yield c.length;
                }
                if (v instanceof boolean[] b) {
                    yield b.length;
                }
                if (v instanceof String[] sa) {
                    yield sa.length;
                }
                if (v instanceof Object[] oa) {
                    yield oa.length;
                }
                throw new RuntimeException("length bad type");
            }
            case "to_char_array" -> {
                if (args.size() != 1) throw new RuntimeException("to_char_array arity");
                Object v = visit(args.get(0));
                if (!(v instanceof String s)) {
                    throw new RuntimeException("to_char_array needs string");
                }
                char[] out = new char[s.length()];
                s.getChars(0, s.length(), out, 0);
                yield out;
            }
            case "to_string" -> {
                if (args.size() != 1) throw new RuntimeException("to_string arity");
                Object v = visit(args.get(0));
                if (v == null) {
                    throw new RuntimeException("null to_string");
                }
                if (!(v instanceof char[] c)) {
                    throw new RuntimeException("to_string needs char[]");
                }
                yield new String(c);
            }
            case "atoi" -> {
                if (args.size() != 1) throw new RuntimeException("atoi arity");
                Object v = visit(args.get(0));
                if (!(v instanceof String s)) {
                    throw new RuntimeException("atoi needs string");
                }
                yield Integer.parseInt(s);
            }
            case "itoa" -> {
                if (args.size() != 1) throw new RuntimeException("itoa arity");
                Object v = visit(args.get(0));
                int n = toInt(v, "itoa");
                yield String.valueOf(n);
            }
            default -> throw new RuntimeException("unknown builtin");
        };
    }

    private void printArg(Object v) {
        System.out.print(formatPrintValue(v));
    }

    private String formatPrintValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof Character c) {
            return String.valueOf(c);
        }
        if (v instanceof Integer i) {
            return String.valueOf(i);
        }
        if (v instanceof Boolean b) {
            return String.valueOf(b);
        }
        if (v instanceof int[] a) {
            return formatIntArray(a);
        }
        if (v instanceof boolean[] b) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < b.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(b[i]);
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof char[] c) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < c.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(c[i]);
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof Object[] oa) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < oa.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatPrintValue(oa[i]));
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private static String formatIntArray(int[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // overload & invocation
    // -------------------------------------------------------------------------

    private CompiledMethod resolveOverload(String name, List<String> argTypes,
            List<MiniJavaParser.ExpressionContext> argExprs) {
        List<CompiledMethod> cands = methods.get(name);
        if (cands == null || cands.isEmpty()) {
            throw new RuntimeException("No method: " + name);
        }
        List<CompiledMethod> ok = new ArrayList<>();
        List<Integer> costs = new ArrayList<>();
        for (CompiledMethod m : cands) {
            if (m.paramTypes.size() != argTypes.size()) {
                continue;
            }
            int sum = 0;
            boolean bad = false;
            for (int i = 0; i < argTypes.size(); i++) {
                MiniJavaParser.ExpressionContext ex =
                        argExprs != null && i < argExprs.size() ? argExprs.get(i) : null;
                int c = conversionCost(m.paramTypes.get(i), argTypes.get(i), ex);
                if (c < 0) {
                    bad = true;
                    break;
                }
                sum += c;
            }
            if (!bad) {
                ok.add(m);
                costs.add(sum);
            }
        }
        if (ok.isEmpty()) {
            throw new RuntimeException("No matching overload");
        }
        int best = costs.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<CompiledMethod> bestList = new ArrayList<>();
        for (int i = 0; i < ok.size(); i++) {
            if (costs.get(i) == best) {
                bestList.add(ok.get(i));
            }
        }
        if (bestList.size() != 1) {
            throw new RuntimeException("Ambiguous overload");
        }
        return bestList.get(0);
    }

    /**
     * 返回值：每个参数隐式转换次数之和；不兼容返回 -1。
     */
    private static int conversionCost(String paramType, String argType, MiniJavaParser.ExpressionContext argExpr) {
        if (paramType.equals(argType)) {
            return 0;
        }
        if ("int".equals(paramType) && "char".equals(argType)) {
            return 1;
        }
        if ("char".equals(paramType) && "int".equals(argType)) {
            if (argExpr != null && narrowingLiteralIntForChar(argExpr)) {
                return 1;
            }
            return -1;
        }
        if (paramType.endsWith("[]") && NULL_T.equals(argType)) {
            return 1;
        }
        return -1;
    }

    private Object invokeUserMethod(CompiledMethod m, List<Object> args) {
        if (m.paramTypes.size() != args.size()) {
            throw new RuntimeException("bad arg count");
        }
        Map<String, Object> pScope = new LinkedHashMap<>();
        Map<String, String> pTypes = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            pScope.put(m.paramNames.get(i), args.get(i));
            pTypes.put(m.paramNames.get(i), m.paramTypes.get(i));
        }
        scopeStack.add(pScope);
        typeStack.add(pTypes);
        currentReturnType.push(m.returnType);
        try {
            visit(m.body);
            if (!"void".equals(m.returnType)) {
                throw new RuntimeException("missing return");
            }
            return null;
        } catch (ReturnSignal rs) {
            if ("void".equals(m.returnType)) {
                if (rs.value != VOID_RETURN) {
                    throw new RuntimeException("void return with value");
                }
                return null;
            }
            return coerceForReturnValue(m.returnType, rs.value);
        } finally {
            currentReturnType.pop();
            scopeStack.remove(scopeStack.size() - 1);
            typeStack.remove(typeStack.size() - 1);
        }
    }

    private Object coerceForReturnValue(String returnType, Object v) {
        return coerceForReturn(returnType, null, v);
    }

    private Object coerceForReturn(String returnType, MiniJavaParser.ExpressionContext exprCtx, Object v) {
        if ("int".equals(returnType)) {
            if (v instanceof Integer) {
                return v;
            }
            if (v instanceof Character) {
                return toInt(v, "return");
            }
            throw new RuntimeException("bad return type");
        }
        if ("char".equals(returnType)) {
            if (v instanceof Character) {
                return v;
            }
            if (v instanceof Integer i) {
                if (i < -128 || i > 127) {
                    throw new RuntimeException("return int out of char range");
                }
                return (char) (i & 0xFF);
            }
            throw new RuntimeException("bad return type");
        }
        if (returnType.endsWith("[]") || "string".equals(returnType) || "boolean".equals(returnType)) {
            return coerceValueToType(returnType, v);
        }
        throw new RuntimeException("bad return");
    }

    private Object coerceArgToParam(String paramType, Object value, MiniJavaParser.ExpressionContext argExpr) {
        if ("int".equals(paramType)) {
            if (value instanceof Integer) {
                return value;
            }
            if (value instanceof Character) {
                return toInt(value, "arg");
            }
            throw new RuntimeException("arg to int");
        }
        if ("char".equals(paramType)) {
            if (value instanceof Character) {
                return value;
            }
            if (value instanceof Integer && argExpr != null && narrowingLiteralIntForChar(argExpr)) {
                return (char) (((Integer) value) & 0xFF);
            }
            throw new RuntimeException("arg to char");
        }
        if (paramType.endsWith("[]")) {
            if (value == null) {
                return null;
            }
            if (!arrayValueAssignable(paramType, value)) {
                throw new RuntimeException("arg array mismatch");
            }
            return value;
        }
        if ("string".equals(paramType) && value instanceof String) {
            return value;
        }
        if ("boolean".equals(paramType) && value instanceof Boolean) {
            return value;
        }
        throw new RuntimeException("coerce arg");
    }

    // -------------------------------------------------------------------------
    // array helpers
    // -------------------------------------------------------------------------

    private static boolean isArraySubscript(MiniJavaParser.ExpressionContext ctx) {
        return ctx.LBRACK() != null
                && ctx.expression().size() == 2
                && ctx.bop == null && ctx.prefix == null && ctx.postfix == null
                && ctx.methodCall() == null
                && ctx.NEW() == null
                && ctx.typeType() == null;
    }

    /**
     * 在需要 {@code char} 的上下文中，由 {@code int} 字面量窄化而来时允许的 AST 形态：
     * 十进制字面量、括号、一元 {@code +}、一元 {@code -} 作用于字面量（最终值在 -128～127）；
     * 禁止一般 int 表达式与 {@code (int)} 等强转字面量形态。{@code (char)} 强转推断为 {@code char}，不走此判定。
     * 用于 {@code char[]} 初始化元素、{@code char} 的 {@code =} 赋值、以及 {@code char} 形参的实参匹配。
     */
    private static boolean narrowingLiteralIntForChar(MiniJavaParser.ExpressionContext ctx) {
        MiniJavaParser.ExpressionContext e = unwrapParenExpression(ctx);
        if (e == null) {
            return false;
        }
        if (e.prefix != null && e.expression(0) != null) {
            String op = e.prefix.getText();
            if ("+".equals(op)) {
                return narrowingLiteralIntForChar(e.expression(0));
            }
            if ("-".equals(op)) {
                MiniJavaParser.ExpressionContext inner = unwrapParenExpression(e.expression(0));
                if (inner != null && inner.bop == null && inner.prefix == null && inner.postfix == null
                        && inner.methodCall() == null && inner.NEW() == null && !isArraySubscript(inner)
                        && inner.LPAREN() == null && inner.primary() != null && inner.primary().literal() != null
                        && inner.primary().literal().DECIMAL_LITERAL() != null) {
                    int raw = Integer.parseInt(
                            inner.primary().literal().DECIMAL_LITERAL().getText().replace("_", ""));
                    int signed = -raw;
                    return signed >= -128 && signed <= 127;
                }
                return false;
            }
            return false;
        }
        if (e.bop != null || e.postfix != null || e.methodCall() != null || e.NEW() != null || isArraySubscript(e)) {
            return false;
        }
        if (e.LPAREN() != null && e.typeType() != null) {
            return false;
        }
        if (e.primary() != null && e.primary().literal() != null && e.primary().literal().DECIMAL_LITERAL() != null) {
            int v = Integer.parseInt(e.primary().literal().DECIMAL_LITERAL().getText().replace("_", ""));
            return v >= -128 && v <= 127;
        }
        return false;
    }

    /**
     * {@code char} 目标的简单赋值 {@code =}：禁止 int 变量/表达式隐式变 char；允许字面量窄化与 RHS 推断为 {@code char}（含 {@code (char)}）。
     * {@code +=} 等复合赋值不调用（允许按 int 演算后再截断存入 char）。
     */
    private void validateImplicitCharAssignment(String targetType, String assignOp, MiniJavaParser.ExpressionContext rhs) {
        if (!"char".equals(targetType) || !"=".equals(assignOp)) {
            return;
        }
        String rhsT = inferExprType(rhs, InferenceMode.GENERAL);
        if ("char".equals(rhsT)) {
            return;
        }
        if (NULL_T.equals(rhsT)) {
            throw new RuntimeException("null to char");
        }
        if ("int".equals(rhsT) && narrowingLiteralIntForChar(rhs)) {
            return;
        }
        throw new RuntimeException("implicit int to char");
    }

    private static MiniJavaParser.ExpressionContext unwrapParenExpression(MiniJavaParser.ExpressionContext ctx) {
        MiniJavaParser.ExpressionContext cur = ctx;
        while (cur != null && cur.primary() != null && cur.primary().expression() != null
                && cur.primary().literal() == null && cur.primary().identifier() == null) {
            cur = cur.primary().expression();
        }
        return cur;
    }

    private Object readArrayAccess(MiniJavaParser.ExpressionContext ctx) {
        Object arr = visit(ctx.expression(0));
        Object idxVal = visit(ctx.expression(1));
        int idx = toInt(idxVal, "index");
        if (arr == null) {
            throw new RuntimeException("null array");
        }
        if (arr instanceof int[] a) {
            if (idx < 0 || idx >= a.length) {
                throw new RuntimeException("oob");
            }
            return a[idx];
        }
        if (arr instanceof char[] c) {
            if (idx < 0 || idx >= c.length) {
                throw new RuntimeException("oob");
            }
            return c[idx];
        }
        if (arr instanceof boolean[] b) {
            if (idx < 0 || idx >= b.length) {
                throw new RuntimeException("oob");
            }
            return b[idx];
        }
        if (arr instanceof String[] sa) {
            if (idx < 0 || idx >= sa.length) {
                throw new RuntimeException("oob");
            }
            return sa[idx];
        }
        if (arr instanceof Object[] oa) {
            if (idx < 0 || idx >= oa.length) {
                throw new RuntimeException("oob");
            }
            return oa[idx];
        }
        throw new RuntimeException("not array");
    }

    private void writeArrayAccess(MiniJavaParser.ExpressionContext ctx, Object value) {
        Object arr = visit(ctx.expression(0));
        Object idxVal = visit(ctx.expression(1));
        int idx = toInt(idxVal, "index");
        if (arr == null) {
            throw new RuntimeException("null array");
        }
        String elemT = elementTypeOfArrayExpr(ctx.expression(0));
        Object coerced = coerceValueToType(elemT, value);
        if (arr instanceof int[] a) {
            if (idx < 0 || idx >= a.length) {
                throw new RuntimeException("oob");
            }
            a[idx] = (Integer) coerced;
            return;
        }
        if (arr instanceof char[] c) {
            if (idx < 0 || idx >= c.length) {
                throw new RuntimeException("oob");
            }
            c[idx] = (Character) coerced;
            return;
        }
        if (arr instanceof boolean[] b) {
            if (idx < 0 || idx >= b.length) {
                throw new RuntimeException("oob");
            }
            b[idx] = (Boolean) coerced;
            return;
        }
        if (arr instanceof String[] sa) {
            if (idx < 0 || idx >= sa.length) {
                throw new RuntimeException("oob");
            }
            sa[idx] = (String) coerced;
            return;
        }
        if (arr instanceof Object[] oa) {
            if (idx < 0 || idx >= oa.length) {
                throw new RuntimeException("oob");
            }
            oa[idx] = coerced;
            return;
        }
        throw new RuntimeException("not array");
    }

    private String elementTypeOfArrayExpr(MiniJavaParser.ExpressionContext arrExpr) {
        String t = inferExprType(arrExpr, InferenceMode.GENERAL);
        if (t == null || !t.endsWith("[]")) {
            throw new RuntimeException("not array type");
        }
        return t.substring(0, t.length() - 2);
    }

    private Object buildArrayFromInitializer(String fullType, MiniJavaParser.ArrayInitializerContext ctx) {
        List<MiniJavaParser.VariableInitializerContext> inits = ctx.variableInitializer();
        if (!fullType.endsWith("[]")) {
            throw new RuntimeException("not array type");
        }
        String inner = fullType.substring(0, fullType.length() - 2);
        if (inner.endsWith("[]")) {
            Object[] out = new Object[inits.size()];
            for (int i = 0; i < inits.size(); i++) {
                MiniJavaParser.VariableInitializerContext vi = inits.get(i);
                if (vi.arrayInitializer() != null) {
                    expectedDeclType.push(inner);
                    try {
                        out[i] = visit(vi.arrayInitializer());
                    } finally {
                        expectedDeclType.pop();
                    }
                } else {
                    out[i] = visit(vi.expression());
                    out[i] = coerceValueToType(inner, out[i]);
                }
            }
            return out;
        }
        // 叶子维度
        if ("int".equals(inner)) {
            int[] out = new int[inits.size()];
            for (int i = 0; i < inits.size(); i++) {
                Object v = visitInitializerLeaf(inits.get(i));
                out[i] = (Integer) coerceValueToType("int", v);
            }
            return out;
        }
        if ("char".equals(inner)) {
            char[] out = new char[inits.size()];
            for (int i = 0; i < inits.size(); i++) {
                MiniJavaParser.VariableInitializerContext vi = inits.get(i);
                if (vi.expression() == null) {
                    throw new RuntimeException("char[] element must be expression");
                }
                MiniJavaParser.ExpressionContext ex = vi.expression();
                Object v = visit(ex);
                if (v instanceof Integer && !narrowingLiteralIntForChar(ex)) {
                    throw new RuntimeException("int cannot implicitly convert to char in array init");
                }
                out[i] = (Character) coerceValueToType("char", v);
            }
            return out;
        }
        if ("boolean".equals(inner)) {
            boolean[] out = new boolean[inits.size()];
            for (int i = 0; i < inits.size(); i++) {
                Object v = visitInitializerLeaf(inits.get(i));
                out[i] = (Boolean) coerceValueToType("boolean", v);
            }
            return out;
        }
        if ("string".equals(inner)) {
            String[] out = new String[inits.size()];
            for (int i = 0; i < inits.size(); i++) {
                Object v = visitInitializerLeaf(inits.get(i));
                out[i] = (String) coerceValueToType("string", v);
            }
            return out;
        }
        throw new RuntimeException("unsupported array leaf");
    }

    private Object visitInitializerLeaf(MiniJavaParser.VariableInitializerContext vi) {
        if (vi.arrayInitializer() != null) {
            return visit(vi.arrayInitializer());
        }
        return visit(vi.expression());
    }

    @Override
    public Object visitCreator(MiniJavaParser.CreatorContext ctx) {
        String base = primitiveTypeToken(ctx.createdName().primitiveType());
        MiniJavaParser.ArrayCreatorRestContext rest = ctx.arrayCreatorRest();
        if (rest.arrayInitializer() != null) {
            String full = arrayTypeName(base, rest.LBRACK().size());
            expectedDeclType.push(full);
            try {
                return visit(rest.arrayInitializer());
            } finally {
                expectedDeclType.pop();
            }
        }
        List<Integer> sizes = new ArrayList<>();
        for (MiniJavaParser.ExpressionContext dim : rest.expression()) {
            int n = toInt(visit(dim), "dim");
            if (n < 0) {
                throw new RuntimeException("negative array size");
            }
            sizes.add(n);
        }
        int trailing = rest.LBRACK().size() - rest.expression().size();
        return allocateNewExprArray(base, sizes, trailing);
    }

    /**
     * new int[d0]...[expr]...[]...[] ：无尾 [] 时用 Array.newInstance；否则用 Object[] 嵌套 + null 叶子。
     */
    private static Object allocateNewExprArray(String base, List<Integer> exprDims, int trailing) {
        Class<?> comp = leafClassForArrayBase(base);
        int[] ed = exprDims.stream().mapToInt(Integer::intValue).toArray();
        if (ed.length == 0) {
            throw new RuntimeException("bad new");
        }
        if (trailing == 0) {
            return Array.newInstance(comp, ed);
        }
        return raggedNewArray(comp, ed, 0, trailing);
    }

    private static Class<?> leafClassForArrayBase(String base) {
        return switch (base) {
            case "int" -> int.class;
            case "char" -> char.class;
            case "boolean" -> boolean.class;
            case "string" -> String.class;
            default -> throw new RuntimeException("new array base");
        };
    }

    private static Object raggedNewArray(Class<?> leaf, int[] expr, int pos, int trailing) {
        if (pos < expr.length) {
            int d = expr[pos];
            boolean lastExpr = pos == expr.length - 1;
            if (lastExpr && trailing == 0) {
                return Array.newInstance(leaf, d);
            }
            Object[] out = new Object[d];
            for (int i = 0; i < d; i++) {
                if (lastExpr && trailing > 0) {
                    out[i] = raggedNewArray(leaf, expr, pos + 1, trailing - 1);
                } else {
                    out[i] = raggedNewArray(leaf, expr, pos + 1, trailing);
                }
            }
            return out;
        }
        return null;
    }

    private static int arrayRank(String t) {
        int r = 0;
        String s = t;
        while (s.endsWith("[]")) {
            r++;
            s = s.substring(0, s.length() - 2);
        }
        return r;
    }

    private static String arrayBasePrimitive(String arrayDecl) {
        String s = arrayDecl;
        while (s.endsWith("[]")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static boolean arrayValueAssignable(String declaredArrayType, Object v) {
        String base = arrayBasePrimitive(declaredArrayType);
        int rank = arrayRank(declaredArrayType);
        if (rank == 1) {
            return switch (base) {
                case "int" -> v instanceof int[];
                case "char" -> v instanceof char[];
                case "boolean" -> v instanceof boolean[];
                case "string" -> v instanceof String[];
                default -> false;
            };
        }
        if (v instanceof Object[]) {
            return true;
        }
        Class<?> c = v.getClass();
        if (!c.isArray()) {
            return false;
        }
        int d = 0;
        while (c.isArray()) {
            d++;
            c = c.getComponentType();
        }
        if (d != rank) {
            return false;
        }
        return switch (base) {
            case "int" -> c == int.class;
            case "char" -> c == char.class;
            case "boolean" -> c == boolean.class;
            case "string" -> c == String.class;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // types / inference
    // -------------------------------------------------------------------------

    private String inferExprType(MiniJavaParser.ExpressionContext ctx, InferenceMode mode) {
        if (ctx.methodCall() != null) {
            return inferMethodCallReturn(ctx.methodCall());
        }
        if (ctx.NEW() != null) {
            return inferNewType(ctx.creator());
        }
        if (isArraySubscript(ctx)) {
            String at = inferExprType(ctx.expression(0), InferenceMode.GENERAL);
            if (at == null || !at.endsWith("[]")) {
                throw new RuntimeException("subscript non-array");
            }
            return at.substring(0, at.length() - 2);
        }
        if (ctx.LPAREN() != null && ctx.typeType() != null) {
            return typeTypeToString(ctx.typeType());
        }
        if (ctx.prefix != null) {
            String op = ctx.prefix.getText();
            if ("not".equals(op)) {
                return "boolean";
            }
            return inferExprType(ctx.expression(0), mode);
        }
        if (ctx.postfix != null) {
            return inferExprType(ctx.expression(0), mode);
        }
        if (ctx.bop != null) {
            return inferBinaryExprType(ctx, mode);
        }
        if (ctx.primary() != null) {
            return inferPrimaryType(ctx.primary(), mode);
        }
        return "int";
    }

    private String inferBinaryExprType(MiniJavaParser.ExpressionContext ctx, InferenceMode mode) {
        String op = ctx.bop.getText();
        if ("?".equals(op)) {
            String t1 = inferExprType(ctx.expression(1), mode);
            String t2 = inferExprType(ctx.expression(2), mode);
            return mergeTernaryBranchTypes(t1, t2);
        }
        if ("and".equals(op) || "or".equals(op)) {
            return "boolean";
        }
        if ("==".equals(op) || "!=".equals(op)) {
            return "boolean";
        }
        if (isComparisonOp(op)) {
            return "boolean";
        }
        if ("+".equals(op)) {
            String t0 = inferExprType(ctx.expression(0), InferenceMode.GENERAL);
            String t1 = inferExprType(ctx.expression(1), InferenceMode.GENERAL);
            if ("string".equals(t0) || "string".equals(t1)) {
                return "string";
            }
            return "int";
        }
        if (List.of("-", "*", "/", "%", "&", "|", "^", "<<", ">>", ">>>").contains(op)) {
            return "int";
        }
        if (isAssignmentOperator(op)) {
            return inferExprType(ctx.expression(0), InferenceMode.GENERAL);
        }
        return "int";
    }

    /**
     * 三目分支类型合并：与 Java 数值提升一致（int/char → int），null 与数组类型取非 null 一侧。
     */
    private static String mergeTernaryBranchTypes(String t1, String t2) {
        if (t1.equals(t2)) {
            return t1;
        }
        if (NULL_T.equals(t1)) {
            return t2;
        }
        if (NULL_T.equals(t2)) {
            return t1;
        }
        boolean n1 = "int".equals(t1) || "char".equals(t1);
        boolean n2 = "int".equals(t2) || "char".equals(t2);
        if (n1 && n2) {
            return "int";
        }
        if ("string".equals(t1) && isTernaryStringable(t2)) {
            return "string";
        }
        if ("string".equals(t2) && isTernaryStringable(t1)) {
            return "string";
        }
        if (t1.endsWith("[]") && t2.endsWith("[]")) {
            int r1 = arrayRank(t1);
            int r2 = arrayRank(t2);
            if (r1 == r2) {
                String b1 = arrayBasePrimitive(t1);
                String b2 = arrayBasePrimitive(t2);
                if (("int".equals(b1) || "char".equals(b1)) && ("int".equals(b2) || "char".equals(b2))) {
                    return arrayTypeName("int", r1);
                }
            }
        }
        return t1;
    }

    private static boolean isTernaryStringable(String t) {
        return "string".equals(t) || "int".equals(t) || "char".equals(t) || "boolean".equals(t);
    }

    private static boolean isComparisonOp(String op) {
        return op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
    }

    private String inferPrimaryType(MiniJavaParser.PrimaryContext p, InferenceMode mode) {
        if (p.literal() != null) {
            return inferLiteralType(p.literal(), mode);
        }
        if (p.identifier() != null) {
            return Objects.requireNonNull(getDeclaredType(p.identifier().getText()), "undeclared");
        }
        if (p.expression() != null) {
            return inferExprType(p.expression(), mode);
        }
        return "int";
    }

    private String inferLiteralType(MiniJavaParser.LiteralContext lit, InferenceMode mode) {
        if (lit.NULL_LITERAL() != null) {
            return NULL_T;
        }
        if (lit.DECIMAL_LITERAL() != null) {
            if (mode == InferenceMode.ARGUMENT || mode == InferenceMode.VAR_INIT) {
                return "int";
            }
            int v = Integer.parseInt(lit.DECIMAL_LITERAL().getText().replace("_", ""));
            if (v < -128 || v > 127) {
                return "int";
            }
            return "int";
        }
        if (lit.CHAR_LITERAL() != null) {
            return "char";
        }
        if (lit.STRING_LITERAL() != null) {
            return "string";
        }
        if (lit.BOOL_LITERAL() != null) {
            return "boolean";
        }
        return "int";
    }

    private String inferMethodCallReturn(MiniJavaParser.MethodCallContext ctx) {
        String name = ctx.identifier().getText();
        List<MiniJavaParser.ExpressionContext> argExprs = new ArrayList<>();
        if (ctx.arguments() != null && ctx.arguments().expressionList() != null) {
            argExprs.addAll(ctx.arguments().expressionList().expression());
        }
        if (isBuiltin(name)) {
            return inferBuiltinReturn(name, argExprs);
        }
        List<String> argTypes = new ArrayList<>();
        for (MiniJavaParser.ExpressionContext e : argExprs) {
            argTypes.add(inferExprType(e, InferenceMode.ARGUMENT));
        }
        CompiledMethod m = resolveOverload(name, argTypes, argExprs);
        return m.returnType;
    }

    private String inferBuiltinReturn(String name, List<MiniJavaParser.ExpressionContext> args) {
        return switch (name) {
            case "print", "println", "assert" -> "void";
            case "length" -> "int";
            case "to_char_array" -> "char[]";
            case "to_string", "itoa" -> "string";
            case "atoi" -> "int";
            default -> "void";
        };
    }

    private String inferNewType(MiniJavaParser.CreatorContext ctx) {
        String base = primitiveTypeToken(ctx.createdName().primitiveType());
        MiniJavaParser.ArrayCreatorRestContext rest = ctx.arrayCreatorRest();
        int dim = rest.LBRACK().size();
        return arrayTypeName(base, dim);
    }

    private static String primitiveTypeToken(MiniJavaParser.PrimitiveTypeContext ctx) {
        if (ctx.INT() != null) {
            return "int";
        }
        if (ctx.CHAR() != null) {
            return "char";
        }
        if (ctx.BOOLEAN() != null) {
            return "boolean";
        }
        if (ctx.STRING() != null) {
            return "string";
        }
        throw new RuntimeException("primitive");
    }

    private static String typeTypeToString(MiniJavaParser.TypeTypeContext ctx) {
        StringBuilder sb = new StringBuilder(primitiveTypeToken(ctx.primitiveType()));
        int dims = ctx.LBRACK().size();
        sb.append("[]".repeat(dims));
        return sb.toString();
    }

    private static String arrayTypeName(String base, int dims) {
        return base + "[]".repeat(dims);
    }

    private static Object defaultValueForType(String t) {
        if (t.endsWith("[]")) {
            return null;
        }
        return switch (t) {
            case "int" -> 0;
            case "char" -> (char) 0;
            case "boolean" -> false;
            case "string" -> "";
            default -> null;
        };
    }

    private Object coerceValueToType(String type, Object value) {
        if (type == null) {
            return value;
        }
        if (type.endsWith("[]")) {
            if (value == null) {
                return null;
            }
            if (!arrayValueAssignable(type, value)) {
                throw new RuntimeException("array type mismatch");
            }
            return value;
        }
        return switch (type) {
            case "int" -> {
                if (value instanceof Integer) {
                    yield value;
                }
                if (value instanceof Character) {
                    yield toInt(value, "int");
                }
                throw new RuntimeException("to int");
            }
            case "char" -> {
                if (value instanceof Character) {
                    yield value;
                }
                if (value instanceof Integer i) {
                    yield (char) (i & 0xFF);
                }
                throw new RuntimeException("to char");
            }
            case "boolean" -> {
                if (value instanceof Boolean) {
                    yield value;
                }
                throw new RuntimeException("to boolean");
            }
            case "string" -> {
                if (value instanceof String) {
                    yield value;
                }
                throw new RuntimeException("to string");
            }
            default -> value;
        };
    }

    private String resolveLValueType(MiniJavaParser.ExpressionContext lhs) {
        if (isArraySubscript(lhs)) {
            return elementTypeOfArrayExpr(lhs.expression(0));
        }
        if (lhs.primary() != null && lhs.primary().identifier() != null) {
            String n = lhs.primary().identifier().getText();
            String t = getDeclaredType(n);
            if (t == null) {
                throw new RuntimeException("undeclared");
            }
            return t;
        }
        throw new RuntimeException("bad lvalue");
    }

    private void assignToLValue(MiniJavaParser.ExpressionContext lhs, Object value) {
        if (isArraySubscript(lhs)) {
            writeArrayAccess(lhs, value);
            return;
        }
        if (lhs.primary() != null && lhs.primary().identifier() != null) {
            String variableName = lhs.primary().identifier().getText();
            for (int i = scopeStack.size() - 1; i >= 0; i--) {
                if (scopeStack.get(i).containsKey(variableName)) {
                    scopeStack.get(i).put(variableName, value);
                    return;
                }
            }
        }
        throw new RuntimeException("assign");
    }

    private Object applyIncDec(String variableName, String op, boolean isPrefix) {
        Object value = null;
        int scopeIndex = -1;
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            if (scopeStack.get(i).containsKey(variableName)) {
                value = scopeStack.get(i).get(variableName);
                scopeIndex = i;
                break;
            }
        }
        if (value == null || scopeIndex < 0) {
            throw new RuntimeException("incdec");
        }
        String declaredType = getDeclaredType(variableName);
        if (value instanceof Integer iv) {
            int nv = op.contains("+") ? iv + 1 : iv - 1;
            Object coerced = coerceValueToType("int", nv);
            scopeStack.get(scopeIndex).put(variableName, coerced);
            return isPrefix ? coerced : iv;
        }
        if (value instanceof Character ch) {
            int intValue = ch.charValue();
            int nv = op.contains("+") ? intValue + 1 : intValue - 1;
            Object coerced = coerceValueToType(declaredType != null ? declaredType : "char", nv);
            scopeStack.get(scopeIndex).put(variableName, coerced);
            return isPrefix ? coerced : ch;
        }
        throw new RuntimeException("incdec type");
    }

    // -------------------------------------------------------------------------
    // operators (from Lab2)
    // -------------------------------------------------------------------------

    private boolean isAssignmentOperator(String op) {
        return op.equals("=")
                || op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=") || op.equals("%=")
                || op.equals("&=") || op.equals("|=") || op.equals("^=")
                || op.equals("<<=") || op.equals(">>=") || op.equals(">>>=");
    }

    private void validateAssignmentOperatorForType(String op, String declaredType) {
        if ("string".equals(declaredType)) {
            if (!op.equals("=") && !op.equals("+=")) {
                throw new RuntimeException("string assign op");
            }
            return;
        }
        if ("boolean".equals(declaredType)) {
            if (!op.equals("=")) {
                throw new RuntimeException("boolean assign op");
            }
            return;
        }
        if ("int".equals(declaredType) || "char".equals(declaredType) || declaredType.endsWith("[]")) {
            return;
        }
        throw new RuntimeException("bad assign target");
    }

    private int toInt(Object v, String op) {
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Character c) {
            int value = c.charValue();
            if ((c & 0x80) != 0) {
                value |= 0xFFFFFF00;
            }
            return value;
        }
        throw new RuntimeException("toInt " + op);
    }

    private Object evaluateLogicalOperatorWithShortCircuit(Object left, MiniJavaParser.ExpressionContext rightExpr, String op) {
        if (!(left instanceof Boolean)) {
            throw new RuntimeException("logic left");
        }
        if ("and".equals(op)) {
            if (!(Boolean) left) {
                return false;
            }
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("logic right");
            }
            return right;
        }
        if ("or".equals(op)) {
            if ((Boolean) left) {
                return true;
            }
            Object right = visit(rightExpr);
            if (!(right instanceof Boolean)) {
                throw new RuntimeException("logic right");
            }
            return right;
        }
        throw new RuntimeException("logic");
    }

    private Object evaluateBinaryOperator(Object left, Object right, String op) {
        try {
            switch (op) {
                case "+" -> {
                    if (left instanceof String || right instanceof String) {
                        return String.valueOf(left) + String.valueOf(right);
                    }
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "+") + toInt(right, "+");
                    }
                    throw new RuntimeException("+");
                }
                case "-" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "-") - toInt(right, "-");
                    }
                    throw new RuntimeException("-");
                }
                case "*" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "*") * toInt(right, "*");
                    }
                    throw new RuntimeException("*");
                }
                case "/" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        int r = toInt(right, "/");
                        if (r == 0) {
                            throw new ArithmeticException("div0");
                        }
                        return toInt(left, "/") / r;
                    }
                    throw new RuntimeException("/");
                }
                case "%" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        int r = toInt(right, "%");
                        if (r == 0) {
                            throw new ArithmeticException("mod0");
                        }
                        return toInt(left, "%") % r;
                    }
                    throw new RuntimeException("%");
                }
                case "<" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return compare(left, right) < 0;
                    }
                    throw new RuntimeException("<");
                }
                case ">" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return compare(left, right) > 0;
                    }
                    throw new RuntimeException(">");
                }
                case "<=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return compare(left, right) <= 0;
                    }
                    throw new RuntimeException("<=");
                }
                case ">=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return compare(left, right) >= 0;
                    }
                    throw new RuntimeException(">=");
                }
                case "==" -> {
                    return areEqual(left, right);
                }
                case "!=" -> {
                    return !areEqual(left, right);
                }
                case "&" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "&") & toInt(right, "&");
                    }
                    throw new RuntimeException("&");
                }
                case "|" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "|") | toInt(right, "|");
                    }
                    throw new RuntimeException("|");
                }
                case "^" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "^") ^ toInt(right, "^");
                    }
                    throw new RuntimeException("^");
                }
                case "<<" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "<<") << toInt(right, "<<");
                    }
                    throw new RuntimeException("<<");
                }
                case ">>" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, ">>") >> toInt(right, ">>");
                    }
                    throw new RuntimeException(">>");
                }
                case ">>>" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, ">>>") >>> toInt(right, ">>>");
                    }
                    throw new RuntimeException(">>>");
                }
                case "=" -> {
                    return right;
                }
                case "+=" -> {
                    if (left instanceof String || right instanceof String) {
                        return String.valueOf(left) + String.valueOf(right);
                    }
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "+=") + toInt(right, "+=");
                    }
                    throw new RuntimeException("+=");
                }
                case "-=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "-=") - toInt(right, "-=");
                    }
                    throw new RuntimeException("-=");
                }
                case "*=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "*=") * toInt(right, "*=");
                    }
                    throw new RuntimeException("*=");
                }
                case "/=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        int r = toInt(right, "/=");
                        if (r == 0) {
                            throw new ArithmeticException("div0");
                        }
                        return toInt(left, "/=") / r;
                    }
                    throw new RuntimeException("/=");
                }
                case "%=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        int r = toInt(right, "%=");
                        if (r == 0) {
                            throw new ArithmeticException("mod0");
                        }
                        return toInt(left, "%=") % r;
                    }
                    throw new RuntimeException("%=");
                }
                case "&=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "&=") & toInt(right, "&=");
                    }
                    throw new RuntimeException("&=");
                }
                case "|=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "|=") | toInt(right, "|=");
                    }
                    throw new RuntimeException("|=");
                }
                case "^=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "^=") ^ toInt(right, "^=");
                    }
                    throw new RuntimeException("^=");
                }
                case "<<=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, "<<=") << toInt(right, "<<=");
                    }
                    throw new RuntimeException("<<=");
                }
                case ">>=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, ">>=") >> toInt(right, ">>=");
                    }
                    throw new RuntimeException(">>=");
                }
                case ">>>=" -> {
                    if (isIntegral(left) && isIntegral(right)) {
                        return toInt(left, ">>>=") >>> toInt(right, ">>>=");
                    }
                    throw new RuntimeException(">>>=");
                }
                default -> {
                    throw new RuntimeException("op " + op);
                }
            }
        } catch (ArithmeticException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static boolean isIntegral(Object o) {
        return o instanceof Integer || o instanceof Character;
    }

    private Object evaluateUnaryPrefixOperator(Object operand, String op) {
        return switch (op) {
            case "+" -> {
                if (isIntegral(operand)) {
                    yield toInt(operand, "+");
                }
                throw new RuntimeException("+");
            }
            case "-" -> {
                if (isIntegral(operand)) {
                    yield -toInt(operand, "-");
                }
                throw new RuntimeException("-");
            }
            case "not" -> {
                if (operand instanceof Boolean b) {
                    yield !b;
                }
                throw new RuntimeException("not");
            }
            case "~" -> {
                if (isIntegral(operand)) {
                    yield ~toInt(operand, "~");
                }
                throw new RuntimeException("~");
            }
            default -> throw new RuntimeException("prefix");
        };
    }

    private Object evaluateTypeCast(Object operand, MiniJavaParser.TypeTypeContext tt) {
        String t = typeTypeToString(tt);
        if ("int".equals(t)) {
            return coerceValueToType("int", operand);
        }
        if ("char".equals(t)) {
            return coerceValueToType("char", operand);
        }
        if ("boolean".equals(t)) {
            return coerceValueToType("boolean", operand);
        }
        if ("string".equals(t)) {
            return coerceValueToType("string", operand);
        }
        if (t.endsWith("[]")) {
            return coerceValueToType(t, operand);
        }
        throw new RuntimeException("cast");
    }

    private Object evaluateTernaryOperator(Object condition, MiniJavaParser.ExpressionContext thenExpr, MiniJavaParser.ExpressionContext elseExpr) {
        if (!(condition instanceof Boolean)) {
            throw new RuntimeException("?: cond");
        }
        return (Boolean) condition ? visit(thenExpr) : visit(elseExpr);
    }

    private boolean areEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            if (left == null && right == null) {
                return true;
            }
            // null vs array: comparable
            if (left == null && isArrayLike(right)) {
                return false;
            }
            if (right == null && isArrayLike(left)) {
                return false;
            }
            return false;
        }
        if (isArrayLike(left) || isArrayLike(right)) {
            if (!isArrayLike(left) || !isArrayLike(right)) {
                throw new RuntimeException("array compare type");
            }
            return left == right;
        }
        if (left.getClass() != right.getClass()) {
            if (!((left instanceof Integer && right instanceof Character)
                    || (left instanceof Character && right instanceof Integer))) {
                throw new RuntimeException("compare types");
            }
        }
        if (left instanceof Integer li && right instanceof Character rc) {
            return li == toInt(rc, "eq");
        }
        if (left instanceof Character lc && right instanceof Integer ri) {
            return toInt(lc, "eq") == ri;
        }
        if (left instanceof Character lc2 && right instanceof Character rc2) {
            return lc2.charValue() == rc2.charValue();
        }
        if (left instanceof Integer && right instanceof Integer) {
            return left.equals(right);
        }
        if (left instanceof String && right instanceof String) {
            return left.equals(right);
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return left.equals(right);
        }
        throw new RuntimeException("eq");
    }

    private static boolean isArrayLike(Object o) {
        return o instanceof int[] || o instanceof char[] || o instanceof boolean[] || o instanceof String[]
                || o instanceof Object[];
    }

    private int compare(Object left, Object right) {
        if (isIntegral(left) && isIntegral(right)) {
            return Integer.compare(toInt(left, "cmp"), toInt(right, "cmp"));
        }
        if (left instanceof String s1 && right instanceof String s2) {
            return s1.compareTo(s2);
        }
        if (left instanceof Boolean b1 && right instanceof Boolean b2) {
            return Boolean.compare(b1, b2);
        }
        throw new RuntimeException("cmp");
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
