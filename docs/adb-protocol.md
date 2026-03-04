# ADB 无线调试协议实现

本文档描述 MeowHub 中无线 ADB 协议栈的实现细节。

## 协议版本

MeowHub 使用 ADB 协议 v2（`A_VERSION = 0x01000001`），支持：

- **maxdata**: 256KB（`256 * 1024`），允许大数据包传输
- **TLS 加密**: 通过 `A_STLS` 升级到 TLS 连接
- **Checksum**: v2 协议下 TLS 已保证完整性，仅校验 magic 字段

## 连接流程

```
Client                          ADB Daemon (adbd)
  │                                  │
  │──── A_CNXN (v2, 256KB) ────────►│
  │                                  │
  │◄──── A_STLS ─────────────────────│
  │                                  │
  │──── A_STLS ────────────────────►│
  │                                  │
  │      ┌── TLS Handshake ──┐      │
  │      └───────────────────┘      │
  │                                  │
  │◄──── A_CNXN (v2, maxdata) ──────│
  │      (解析 arg1 获取对方 maxdata)  │
  │                                  │
  │   ── 连接建立，开始 Shell 会话 ──  │
```

## Shell 命令执行

```
Client                          ADB Daemon
  │                                  │
  │──── A_OPEN (shell:cmd) ────────►│
  │                                  │
  │◄──── A_OKAY ─────────────────────│
  │                                  │
  │◄──── A_WRTE (stdout data) ──────│
  │──── A_OKAY ────────────────────►│
  │          ... (重复) ...           │
  │                                  │
  │◄──── A_CLSE ─────────────────────│
  │──── A_CLSE ────────────────────►│
```

## 超时机制

| 阶段 | 超时 | 说明 |
|------|------|------|
| Socket 连接 | 10s | `socket.connect(addr, 10_000)` |
| TLS 握手 / 初始读 | 15s | `soTimeout = 15_000` |
| Shell 命令（常规） | 30s | `execShell` 默认 `timeoutMs=30_000` |
| Shell 命令（后台启动） | 2s | 发送 `setsid ... &` 后不等 `A_CLSE` |

## 配对流程（SPAKE2）

基于 Android 11+ 的无线调试配对协议：

1. mDNS 发现 `_adb-tls-pairing._tcp` 服务
2. 建立 TLS 连接到配对端口
3. 使用用户输入的 6 位配对码作为 SPAKE2 密码
4. 完成 SPAKE2 密钥交换（C++ JNI，依赖 BoringSSL）
5. 导出 TLS Keying Material 并验证
6. 交换对等信息（RSA 公钥）
7. 密钥存储到 SharedPreferences

## 文件推送

通过 base64 编码 + shell 命令分块推送文件到设备：

```bash
# 第一个 chunk（覆盖写入）
echo -n '<base64>' | base64 -d > /data/local/tmp/scrcpy-server.jar

# 后续 chunk（追加写入）
echo -n '<base64>' | base64 -d >> /data/local/tmp/scrcpy-server.jar
```

- 每个 chunk 原始大小 48KB（base64 后约 64KB）
- 推送前通过 MD5 校验避免重复推送
- 推送后验证远程文件大小

## 服务启动

```bash
export CLASSPATH=/data/local/tmp/scrcpy-server.jar && \
setsid app_process / com.tutu.guiserver.Server \
  3.3.4 scid=<random> log_level=info \
  video=false audio=false control=true \
  tunnel_forward=true \
  send_device_meta=false send_codec_meta=false send_frame_meta=false \
  cleanup=false \
  </dev/null >/dev/null 2>&1 &
```

- `setsid` 创建新会话，确保进程脱离 ADB shell
- 所有文件描述符重定向到 `/dev/null` 避免阻塞
- `&` 后台运行，ADB shell 命令通过 2 秒超时返回

## 进程管理

| 操作 | 命令 |
|------|------|
| 检查进程 | `pgrep -f guiserver.Server && echo running \|\| echo not_found` |
| 杀死进程 | `pkill -f guiserver.Server; sleep 1; pgrep -f guiserver.Server \| xargs kill -9` |
