@echo off

rem 设置ANTLR依赖路径
set ANTLR_JAR=%USERPROFILE%\.m2\repository\org\antlr\antlr4-runtime\4.13.2\antlr4-runtime-4.13.2.jar

rem 运行测试文件
for %%f in (testcases\Public\*.mj) do (
    echo Running test: %%f
    java -cp "target\classes;%ANTLR_JAR%" cn.edu.nju.cs.Main %%f
    echo.
)

pause