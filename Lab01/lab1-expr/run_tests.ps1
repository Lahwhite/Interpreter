# 设置ANTLR依赖路径
$antlrJar = "$env:USERPROFILE\.m2\repository\org\antlr\antlr4-runtime\4.13.2\antlr4-runtime-4.13.2.jar"

# 运行测试文件
Get-ChildItem "testcases\Public\*.mj" | ForEach-Object {
    Write-Host "Running test: $($_.Name)"
    java -cp "target\classes;$antlrJar" cn.edu.nju.cs.Main $_.FullName
    Write-Host ""
}

Read-Host "Press Enter to continue..."