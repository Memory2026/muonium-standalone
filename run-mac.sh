#!/bin/bash
# Muonium Player 独立版 · macOS 启动脚本
# 前提：已安装 JDK 21（java 命令可用）。不启动 Minecraft，不下载额外依赖。

DIR="$(cd "$(dirname "$0")" && pwd)"
WEB="$DIR/web"

# 组装 classpath：编译类 + libs 下全部 jar（macOS 用冒号分隔）
CP="$DIR/classes"
for j in "$DIR"/libs/*.jar "$DIR"/libs/inline/*.jar "$DIR"/libs/kotlin/*.jar; do
  CP="$CP:$j"
done

# ---- 定位 java：PATH -> JAVA_HOME -> java_home 工具 -> 标准安装目录扫描 ----
JAVA=""
if command -v java >/dev/null 2>&1; then
  JAVA=java
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JH="$(/usr/libexec/java_home 2>/dev/null)"
  if [ -n "$JH" ] && [ -x "$JH/bin/java" ]; then JAVA="$JH/bin/java"; fi
fi
if [ -z "$JAVA" ]; then
  for d in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    if [ -x "$d/bin/java" ]; then JAVA="$d/bin/java"; break; fi
  done
fi
if [ -z "$JAVA" ]; then
  echo "[muonium] 未找到 java，请先安装 JDK 21（brew install temurin21 或官网 dmg）。"
  exit 1
fi

echo "[muonium] 使用 Java: $JAVA"
"$JAVA" -Dmuonium.web="$WEB" -cp "$CP" app.Server &
SERVER_PID=$!

# 后端起来后用默认浏览器打开界面
sleep 3
URL="http://127.0.0.1:18391"
if command -v open >/dev/null 2>&1; then
  open "$URL"
else
  echo "[muonium] 请手动打开浏览器访问 $URL"
fi

echo "[muonium] 后端 PID=$SERVER_PID，关闭此窗口或按 Ctrl+C 退出。"
wait $SERVER_PID
