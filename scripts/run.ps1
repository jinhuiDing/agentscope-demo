param(
    [string]$Port = "8080",
    [string]$Model = "qwen-plus"
)

$ErrorActionPreference = "Stop"

if (-not $env:DASHSCOPE_API_KEY -and -not $env:AGENT_API_KEY) {
    throw "请先设置 DASHSCOPE_API_KEY 或 AGENT_API_KEY。"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = $env:JAVA_HOME

if (-not $javaHome) {
    $ideaJbr = Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter java.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*\jbr\bin\java.exe" } |
        Select-Object -First 1
    if ($ideaJbr) {
        $javaHome = Split-Path -Parent (Split-Path -Parent $ideaJbr.FullName)
    }
}

if (-not $javaHome) {
    throw "未找到 JDK 17+。请安装 JDK 17+，或把 JAVA_HOME 指向可用 JDK。"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:PORT = $Port
$env:AGENT_MODEL = $Model

Set-Location $repoRoot
mvn spring-boot:run
