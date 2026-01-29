# Spring Boot CMPP 2.0 短信接入示例

本项目提供基于 CMPP 2.0 协议的短信发送与状态报告接入示例，包含：

- CMPP 连接/登录
- 短信发送 (CMPP_SUBMIT)
- 状态报告 (CMPP_DELIVER + CMPP_DELIVER_RESP)
- 心跳 (CMPP_ACTIVE_TEST)

## 配置

`application.yml` 中可配置 CMPP 账号信息与连接信息：

```yaml
cmpp:
  host: 127.0.0.1
  port: 7890
  sp-id: "123456"
  shared-secret: "secret"
  service-id: ""
  source-addr: "10690000"
  src-id: "10690000"
  msg-format: "UCS2"
  reconnect-delay: 5s
  login-timeout: 10s
  keepalive-interval: 30s
  enable: true
```

## 发送短信

```bash
curl -X POST http://localhost:8080/sms/send \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","content":"你好，CMPP"}'
```

返回结果包含本地 sequenceId，提交结果/状态报告会在日志里输出。

## 状态报告

- 设置 `Registered_Delivery=1` 以请求状态报告。
- 收到 `CMPP_DELIVER` 时解析 `Report` 结构，记录 `stat`/`done_time` 等字段。

日志示例：

```
CMPP report: msgId=..., stat=DELIVRD, submitTime=..., doneTime=..., destTerminalId=..., smscSeq=...
```
