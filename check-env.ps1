# .env 环境自检脚本（B4 脱敏版，2026-08-23）
# 用法：在项目根目录执行  pwsh ./check-env.ps1 [-EnvPath .env]
# 安全说明：绝不明文输出 JWT_SECRET——只显示长度、前 4 位与字节特征。
param(
    [string]$EnvPath = '.env'
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not (Test-Path -LiteralPath $EnvPath)) {
    Write-Host "未找到 $EnvPath —— 生产部署禁止从 .env.example 自动拷贝（见 deploy.yml B2 检查）。"
    exit 1
}

Write-Host "=== 文件基础信息 ==="
$bytes = [System.IO.File]::ReadAllBytes($EnvPath)
Write-Host ("文件大小: " + $bytes.Length + " bytes")
if ($bytes.Length -ge 3) {
    Write-Host ("前 3 字节(查 BOM): " + (($bytes[0..2] | ForEach-Object { '{0:X2}' -f $_ }) -join ' '))
}
$content = [System.Text.Encoding]::UTF8.GetString($bytes)

Write-Host ''
Write-Host '=== 关键变量检查（值脱敏）==='
foreach ($name in @('JWT_SECRET', 'DB_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'PGVECTOR_PASSWORD')) {
    $line = ($content -split "`r?`n") | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
    if (-not $line) { Write-Host ("{0}: 缺失" -f $name); continue }
    $val = ($line -replace "^$name=", '') -replace "`r", ''
    $masked = if ($val.Length -le 4) { '<too-short>' } else { $val.Substring(0, 4) + '****(' + $val.Length + ' chars)' }
    $valBytes = [System.Text.Encoding]::UTF8.GetBytes($val)
    $nonAscii = ($valBytes | Where-Object { $_ -gt 127 }).Count
    Write-Host ("{0}: {1} | 非ASCII字节: {2}" -f $name, $masked, $nonAscii)
}

Write-Host ''
Write-Host '=== 工作目录 ==='
Write-Host $PWD.Path
