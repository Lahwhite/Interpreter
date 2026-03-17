# Interpreter

## 一、MiniJava 解析器项目介绍

### 1. 项目概述
这是一个基于 ANTLR 4 实现的 MiniJava 语言解析器项目，位于 `Interpreter/Lab01/lab1-expr/` 目录。

### 2. 技术架构
- **开发语言**：Java 21
- **构建工具**：Maven
- **核心技术**：ANTLR 4.13.2（用于生成词法分析器和语法分析器）
- **项目结构**：遵循 Maven 标准目录结构

### 3. 项目功能
- **词法分析**：将 MiniJava 源代码转换为词法单元流
- **语法分析**：构建抽象语法树（AST）
- **语法树遍历**：使用访问者模式遍历和处理语法树

### 4. 主要文件结构
```
lab1-expr/
├── src/
│   └── main/
│       └── java/
│           └── cn/edu/nju/cs/
│               ├── Main.java           # 程序入口
│               ├── MiniJavaLexer.g4    # 词法分析器语法定义
│               ├── MiniJavaParser.g4   # 语法分析器语法定义
│               └── 生成的解析器文件     # ANTLR 自动生成
├── testcases/
│   └── Public/
│       ├── input-1.mj                 # 测试用例
│       ├── input-2.mj
│       └── input-3.mj
└── pom.xml                            # Maven 配置文件
```

### 5. 核心实现
1. **词法分析**：通过 `MiniJavaLexer.g4` 定义词法规则，生成 `MiniJavaLexer.java`
2. **语法分析**：通过 `MiniJavaParser.g4` 定义语法规则，生成 `MiniJavaParser.java`
3. **程序入口**：`Main.java` 读取 MiniJava 文件，执行解析过程
4. **语法树遍历**：使用 ANTLR 生成的访问者类（如 `MiniJavaParserBaseVisitor`）遍历语法树

### 6. 构建与运行
- **构建**：在项目根目录执行 `mvn clean package`
- **运行**：执行 `java -jar target/miniJava-interpreter-1.0-SNAPSHOT-jar-with-dependencies.jar <MiniJava文件路径>`

### 7. 技术特点
1. **使用 ANTLR 4**：利用 ANTLR 的强大语法分析能力，自动生成解析器代码
2. **访问者模式**：通过访问者模式处理语法树，便于扩展功能
3. **模块化设计**：词法分析和语法分析分离，结构清晰
4. **Maven 管理**：使用 Maven 管理依赖和构建过程，确保环境一致性
          
## 二、项目工作流程分析

### 1. 项目构建流程
1. **依赖管理**：Maven 读取 `pom.xml` 配置，下载 ANTLR 4.13.2 依赖
2. **代码生成**：ANTLR Maven 插件自动处理 `.g4` 文件，生成词法分析器和语法分析器代码
3. **编译打包**：Maven 编译 Java 代码并打包成可执行 JAR 文件

### 2. 程序执行流程
1. **输入处理**：
   - `Main.main()` 方法接收命令行参数（MiniJava 文件路径）
   - 调用 `run()` 方法处理输入文件

2. **词法分析**：
   - 使用 `CharStreams.fromFileName()` 读取文件内容
   - 创建 `MiniJavaLexer` 实例，将输入转换为词法单元流
   - 词法分析器根据 `MiniJavaLexer.g4` 中定义的规则识别词法单元

3. **语法分析**：
   - 创建 `CommonTokenStream` 包装词法单元流
   - 创建 `MiniJavaParser` 实例，解析词法单元流
   - 调用 `parser.compilationUnit()` 开始语法分析
   - 语法分析器根据 `MiniJavaParser.g4` 中定义的规则构建抽象语法树（AST）

4. **语法树遍历**：
   - 创建 `MiniJavaParserBaseVisitor` 实例
   - 调用 `visit()` 方法遍历抽象语法树
   - 目前仅执行遍历，未实现具体操作（可扩展）

### 3. 核心处理流程
```
输入文件 → 词法分析 → 令牌流 → 语法分析 → 抽象语法树 → 访问者遍历
```

### 4. 语法规则处理
- **词法规则**：`MiniJavaLexer.g4` 定义了关键字、字面量、运算符、分隔符等词法单元
- **语法规则**：`MiniJavaParser.g4` 定义了表达式的语法结构，包括：
  - 基本表达式（字面量、括号表达式）
  - 一元运算符（+, -, ++, --, ~, not）
  - 二元运算符（算术、关系、逻辑、位运算等）
  - 三元运算符（条件表达式）
  - 赋值运算符（=, +=, -= 等）

### 6. 可扩展性
- 可通过扩展 `MiniJavaParserBaseVisitor` 类实现具体的语义分析
- 可添加代码生成、解释执行等功能
- 可扩展语法规则以支持更复杂的 MiniJava 特性


## 三、ANTLR 生成的文件类型

### 1. 词法分析器
从 `MiniJavaLexer.g4` 生成：
- **`MiniJavaLexer.java`** - 词法分析器的 Java 实现代码
- **`MiniJavaLexer.interp`** - 解释器数据文件，包含词法分析器的内部状态机信息
- **`MiniJavaLexer.tokens`** - 词法单元定义文件，记录所有 token 类型和编号

### 2. 语法分析器
从 `MiniJavaParser.g4` 生成：
- **`MiniJavaParser.java`** - 语法分析器的 Java 实现代码
- **`MiniJavaParser.interp`** - 解释器数据文件，包含语法分析器的内部状态机信息
- **`MiniJavaParser.tokens`** - 词法单元定义文件
- **`MiniJavaParserBaseListener.java`** - 监听器模式的基类（空实现）
- **`MiniJavaParserListener.java`** - 监听器模式的接口
- **`MiniJavaParserBaseVisitor.java`** - 访问者模式的基类（空实现）
- **`MiniJavaParserVisitor.java`** - 访问者模式的接口

## 各文件的作用

| 文件类型 | 作用 |
|---------|------|
| `.java` | 可直接使用的解析器代码 |
| `.interp` | ANTLR 解释模式使用的数据，用于调试和分析 |
| `.tokens` | 词法单元定义，用于跨语法文件共享 token 类型 |
| `*Listener.java` | 监听器模式相关，用于遍历语法树 |
| `*Visitor.java` | 访问者模式相关，用于遍历并处理语法树 |

## 生成方式

### 通过 Maven 插件自动生成
在 `pom.xml` 中配置的 `antlr4-maven-plugin` 会在构建时自动执行：
```xml
<plugin>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-maven-plugin</artifactId>
    <version>4.13.2</version>
    <executions>
        <execution>
            <id>antlr</id>
            <goals>
                <goal>antlr4</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 通过命令行手动生成
```bash
antlr4 MiniJavaLexer.g4
antlr4 MiniJavaParser.g4
```

## 两种遍历模式

ANTLR 提供了两种遍历语法树的方式：

1. **监听器模式**
   - 自动遍历，无需控制遍历过程
   - 适用于简单的语法树处理
   - 通过继承 `MiniJavaParserBaseListener` 实现

2. **访问者模式**
   - 显式控制遍历过程
   - 适用于需要返回值或复杂处理的场景
   - 通过继承 `MiniJavaParserBaseVisitor` 实现
   - 本项目使用的是这种方式