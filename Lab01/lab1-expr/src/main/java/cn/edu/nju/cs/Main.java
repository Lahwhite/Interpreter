package cn.edu.nju.cs;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;

public class Main {
    public static void run(File mjFile) throws Exception {
        // 读取文件内容
        var input = CharStreams.fromFileName(mjFile.getAbsolutePath());
        // 词法分析
        MiniJavaLexer lexer = new MiniJavaLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        // 语法分析
        MiniJavaParser parser = new MiniJavaParser(tokenStream);
        // 解析编译单元
        ParseTree pt = parser.compilationUnit();

        // CODE
        // new MiniJavaParserBaseVisitor<>().visit(pt);
        Evaluator evaluator = new Evaluator();
        Object result = evaluator.visit(pt);
        System.out.println(result);
    }


    public static void main(String[] args) throws Exception  {
        if(args.length!= 1) {
            System.err.println("Only one argument is allowed: the path of MiniJava file.");
            throw new RuntimeException();
        }
        
        File mjFile = new File(args[0]);
        run(mjFile);
    }
}