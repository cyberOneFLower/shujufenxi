# 在仓库根目录初始化 Git 并关联 GitHub（需已安装 Git，且 GitHub 上已创建空仓库 shujufenxi）
# 用法：在 PowerShell 中执行  .\scripts\init-github-repo.ps1
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# 刷新 PATH（安装 Git 后未重启终端时可用）
$machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = "$machinePath;$userPath"

function Find-GitExe {
  $candidates = @(
    (Get-Command git -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    "$env:ProgramFiles\Git\cmd\git.exe",
    "${env:ProgramFiles(x86)}\Git\cmd\git.exe"
  ) | Where-Object { $_ -and (Test-Path $_) }
  if ($candidates.Count -eq 0) { return $null }
  return $candidates[0]
}

$gitExe = Find-GitExe
if (-not $gitExe) {
  Write-Host "未检测到 Git。请先执行（管理员 PowerShell 可选）：winget install --id Git.Git -e --source winget" -ForegroundColor Yellow
  Write-Host "安装完成后关闭本窗口，重新打开 PowerShell，再运行本脚本。" -ForegroundColor Yellow
  exit 1
}

Write-Host "使用 Git: $gitExe" -ForegroundColor Green

if (-not (Test-Path ".git")) {
  & $gitExe init
} else {
  Write-Host "已存在 .git，跳过 git init" -ForegroundColor DarkYellow
}

& $gitExe add README.md
& $gitExe commit -m "first commit" 2>&1 | ForEach-Object { $_ }
if ($LASTEXITCODE -ne 0) {
  # 可能尚未配置 user.name / user.email
  Write-Host "若提示需配置身份，请执行：" -ForegroundColor Yellow
  Write-Host '  git config --global user.name "你的名字"' -ForegroundColor Cyan
  Write-Host '  git config --global user.email "你的邮箱"' -ForegroundColor Cyan
  Write-Host "然后重新运行本脚本。" -ForegroundColor Yellow
  exit 1
}

& $gitExe branch -M main

$origin = "https://github.com/cyberOneFLower/shujufenxi.git"
$hasOrigin = & $gitExe remote 2>$null | Where-Object { $_ -eq "origin" }
if ($hasOrigin) {
  & $gitExe remote set-url origin $origin
} else {
  & $gitExe remote add origin $origin
}

Write-Host "正在推送到 origin main（将提示 GitHub 登录或令牌）…" -ForegroundColor Green
& $gitExe push -u origin main
if ($LASTEXITCODE -ne 0) {
  Write-Host "推送失败。若远程已有提交，可尝试: git pull origin main --rebase 后再 push" -ForegroundColor Yellow
  exit $LASTEXITCODE
}
Write-Host "完成。" -ForegroundColor Green
