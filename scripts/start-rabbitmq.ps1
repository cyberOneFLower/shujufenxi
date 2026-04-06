# 在项目根目录执行：单独启动 RabbitMQ 容器（本机 mvn 调试时用；Docker 单镜像部署已内置 Broker，无需执行）
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host "未检测到 docker 命令。请先安装 Docker Desktop for Windows：" -ForegroundColor Yellow
  Write-Host "  https://www.docker.com/products/docker-desktop/" -ForegroundColor Cyan
  exit 1
}

$name = "arb-rabbitmq-standalone"
$exists = docker ps -aq --filter "name=$name"
if ($exists) {
  docker start $name 2>$null
  if ($LASTEXITCODE -eq 0) {
    Write-Host "已启动已有容器 $name" -ForegroundColor Green
    exit 0
  }
}

docker run -d --name $name --hostname rabbitmq `
  -p 5672:5672 -p 15672:15672 `
  -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest `
  rabbitmq:3-management-alpine
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "RabbitMQ 已启动（独立容器，供本机后端连接 localhost）。" -ForegroundColor Green
Write-Host "  AMQP:  localhost:5672  (guest/guest)"
Write-Host "  管理: http://localhost:15672  (guest/guest)"
Write-Host ""
Write-Host "后端启用队列：在 backend/application.yml 设置 arb.mq.enabled: true"
Write-Host ""
