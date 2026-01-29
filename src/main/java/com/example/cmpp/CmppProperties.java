package com.example.cmpp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cmpp")
public class CmppProperties {
  private String host;
  private int port;
  private String spId;
  private String sharedSecret;
  private String serviceId;
  private String sourceAddr;
  private String srcId;
  private String msgFormat;
  private Duration reconnectDelay = Duration.ofSeconds(5);
  private Duration loginTimeout = Duration.ofSeconds(10);
  private Duration keepaliveInterval = Duration.ofSeconds(30);
  private boolean enable = true;

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getSpId() {
    return spId;
  }

  public void setSpId(String spId) {
    this.spId = spId;
  }

  public String getSharedSecret() {
    return sharedSecret;
  }

  public void setSharedSecret(String sharedSecret) {
    this.sharedSecret = sharedSecret;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getSourceAddr() {
    return sourceAddr;
  }

  public void setSourceAddr(String sourceAddr) {
    this.sourceAddr = sourceAddr;
  }

  public String getSrcId() {
    return srcId;
  }

  public void setSrcId(String srcId) {
    this.srcId = srcId;
  }

  public String getMsgFormat() {
    return msgFormat;
  }

  public void setMsgFormat(String msgFormat) {
    this.msgFormat = msgFormat;
  }

  public Duration getReconnectDelay() {
    return reconnectDelay;
  }

  public void setReconnectDelay(Duration reconnectDelay) {
    this.reconnectDelay = reconnectDelay;
  }

  public Duration getLoginTimeout() {
    return loginTimeout;
  }

  public void setLoginTimeout(Duration loginTimeout) {
    this.loginTimeout = loginTimeout;
  }

  public Duration getKeepaliveInterval() {
    return keepaliveInterval;
  }

  public void setKeepaliveInterval(Duration keepaliveInterval) {
    this.keepaliveInterval = keepaliveInterval;
  }

  public boolean isEnable() {
    return enable;
  }

  public void setEnable(boolean enable) {
    this.enable = enable;
  }
}
