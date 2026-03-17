@echo off

rem 运行测试文件
for %%f in (testcases\Public\*.mj) do (
    echo Running test: %%f
    mvn exec:java -Dexec.mainClass="cn.edu.nju.cs.Main" -Dexec.args="%%f"
    echo.
)

pause