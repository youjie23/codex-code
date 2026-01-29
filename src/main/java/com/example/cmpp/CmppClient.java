package com.example.cmpp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class CmppClient implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(CmppClient.class);

  private final CmppProperties properties;
  private final CmppSequence sequence;
  private final StatusReportListener reportListener;
  private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Object writeLock = new Object();

  private volatile Socket socket;
  private volatile DataInputStream input;
  private volatile DataOutputStream output;
  private volatile boolean loggedIn;

  public CmppClient(CmppProperties properties, StatusReportListener reportListener) {
    this.properties = properties;
    this.reportListener = reportListener;
    this.sequence = new CmppSequence();
  }

  @Override
  public void start() {
    if (!properties.isEnable()) {
      log.info("CMPP client disabled by configuration.");
      return;
    }
    if (running.compareAndSet(false, true)) {
      ioExecutor.submit(this::connectLoop);
    }
  }

  @Override
  public void stop() {
    running.set(false);
    closeSession();
    ioExecutor.shutdownNow();
    scheduledExecutor.shutdownNow();
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  public long sendSms(String destTerminalId, String content) throws IOException {
    Objects.requireNonNull(destTerminalId, "destTerminalId");
    Objects.requireNonNull(content, "content");
    if (!loggedIn || output == null) {
      throw new IllegalStateException("CMPP client is not connected.");
    }
    byte[] body = packSubmitBody(destTerminalId, content);
    int seq = sequence.next();
    byte[] packet = CmppCodec.buildPacket(CmppCommandId.CMPP_SUBMIT, seq, body);
    synchronized (writeLock) {
      output.write(packet);
      output.flush();
    }
    return seq;
  }

  private void connectLoop() {
    while (running.get()) {
      try {
        connect();
        readLoop();
      } catch (Exception ex) {
        log.warn("CMPP connection failed: {}", ex.getMessage());
      } finally {
        closeSession();
      }
      sleep(properties.getReconnectDelay());
    }
  }

  private void connect() throws Exception {
    Socket newSocket = new Socket();
    newSocket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
        (int) properties.getLoginTimeout().toMillis());
    newSocket.setSoTimeout((int) Duration.ofSeconds(60).toMillis());
    this.socket = newSocket;
    this.input = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
    this.output = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
    login();
    scheduleActiveTest();
    log.info("CMPP client connected to {}:{}", properties.getHost(), properties.getPort());
  }

  private void login() throws Exception {
    int seq = sequence.next();
    byte[] body = CmppCodec.packConnectBody(properties);
    byte[] packet = CmppCodec.buildPacket(CmppCommandId.CMPP_CONNECT, seq, body);
    synchronized (writeLock) {
      output.write(packet);
      output.flush();
    }
    CmppHeader header = readHeader();
    if (header.commandId != CmppCommandId.CMPP_CONNECT_RESP) {
      throw new IllegalStateException("Unexpected response during login: " + header.commandId);
    }
    byte[] resp = readBody(header.totalLength);
    int status = bytesToInt(resp, 0);
    if (status != 0) {
      throw new IllegalStateException("CMPP login failed: status=" + status);
    }
    loggedIn = true;
  }

  private void scheduleActiveTest() {
    scheduledExecutor.scheduleAtFixedRate(() -> {
      if (!loggedIn || output == null) {
        return;
      }
      try {
        int seq = sequence.next();
        byte[] body = CmppCodec.packActiveTestBody();
        byte[] packet = CmppCodec.buildPacket(CmppCommandId.CMPP_ACTIVE_TEST, seq, body);
        synchronized (writeLock) {
          output.write(packet);
          output.flush();
        }
      } catch (IOException ex) {
        log.warn("Failed to send active test: {}", ex.getMessage());
      }
    }, properties.getKeepaliveInterval().toSeconds(),
        properties.getKeepaliveInterval().toSeconds(), TimeUnit.SECONDS);
  }

  private void readLoop() throws IOException {
    while (running.get() && socket != null && !socket.isClosed()) {
      try {
        CmppHeader header = readHeader();
        byte[] body = readBody(header.totalLength);
        handlePacket(header, body);
      } catch (EOFException eof) {
        log.info("CMPP connection closed by server.");
        break;
      }
    }
  }

  private void handlePacket(CmppHeader header, byte[] body) throws IOException {
    switch (header.commandId) {
      case CmppCommandId.CMPP_SUBMIT_RESP -> handleSubmitResp(body);
      case CmppCommandId.CMPP_DELIVER -> handleDeliver(header.sequenceId, body);
      case CmppCommandId.CMPP_ACTIVE_TEST -> handleActiveTest(header.sequenceId);
      case CmppCommandId.CMPP_ACTIVE_TEST_RESP -> log.debug("Received active test resp.");
      default -> log.debug("Unhandled command id {}", header.commandId);
    }
  }

  private void handleSubmitResp(byte[] body) {
    long msgId = bytesToLong(body, 0);
    int result = bytesToInt(body, 8);
    if (result != 0) {
      log.warn("CMPP submit failed: msgId={}, result={}", msgId, result);
    } else {
      log.info("CMPP submit success: msgId={}", msgId);
    }
  }

  private void handleDeliver(int sequenceId, byte[] body) throws IOException {
    long msgId = bytesToLong(body, 0);
    String destId = readFixedString(body, 8, 21);
    String serviceId = readFixedString(body, 29, 10);
    int registeredDelivery = body[44] & 0xff;
    int msgLength = body[45] & 0xff;
    int contentOffset = 46;
    if (registeredDelivery == 1) {
      CmppDeliverReport report = parseReport(body, contentOffset);
      reportListener.onReport(report);
    } else {
      Charset charset = CmppCodec.msgCharset(properties);
      String content = new String(body, contentOffset, msgLength, charset);
      log.info("CMPP deliver: msgId={}, src={}, dest={}, serviceId={}, content={}",
          msgId, readFixedString(body, 39, 21), destId, serviceId, content);
    }
    sendDeliverResp(sequenceId, msgId, 0);
  }

  private CmppDeliverReport parseReport(byte[] body, int offset) {
    long reportMsgId = bytesToLong(body, offset);
    String stat = readFixedString(body, offset + 8, 7);
    String submitTime = readFixedString(body, offset + 15, 10);
    String doneTime = readFixedString(body, offset + 25, 10);
    String destTerminalId = readFixedString(body, offset + 35, 21);
    int smscSequence = bytesToInt(body, offset + 56);
    return new CmppDeliverReport(reportMsgId, stat, submitTime, doneTime, destTerminalId,
        smscSequence);
  }

  private void handleActiveTest(int sequenceId) throws IOException {
    byte[] packet = CmppCodec.buildPacket(CmppCommandId.CMPP_ACTIVE_TEST_RESP, sequenceId,
        new byte[] {0});
    synchronized (writeLock) {
      output.write(packet);
      output.flush();
    }
  }

  private void sendDeliverResp(int sequenceId, long msgId, int result) throws IOException {
    byte[] body = new byte[12];
    longToBytes(msgId, body, 0);
    intToBytes(result, body, 8);
    byte[] packet = CmppCodec.buildPacket(CmppCommandId.CMPP_DELIVER_RESP, sequenceId, body);
    synchronized (writeLock) {
      output.write(packet);
      output.flush();
    }
  }

  private byte[] packSubmitBody(String destTerminalId, String content)
      throws IOException, NoSuchAlgorithmException {
    Charset charset = CmppCodec.msgCharset(properties);
    byte[] msgContent = content.getBytes(charset);
    if (msgContent.length > 140) {
      throw new IllegalArgumentException("Message content too long.");
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeLong(0L);
    dos.writeByte(1);
    dos.writeByte(1);
    dos.writeByte(1);
    dos.writeByte(1);
    dos.write(CmppCodec.writeFixed(properties.getServiceId(), 10, charset));
    dos.writeByte(2);
    dos.write(CmppCodec.writeFixed("", 21, charset));
    dos.writeByte(0);
    dos.writeByte(0);
    dos.writeByte(CmppCodec.msgFormat(properties));
    dos.write(CmppCodec.writeFixed(properties.getSpId(), 6, charset));
    dos.write(CmppCodec.writeFixed("01", 2, charset));
    dos.write(CmppCodec.writeFixed("000000", 6, charset));
    dos.write(CmppCodec.writeFixed("", 17, charset));
    dos.write(CmppCodec.writeFixed("", 17, charset));
    dos.write(CmppCodec.writeFixed(properties.getSrcId(), 21, charset));
    dos.writeByte(1);
    dos.write(CmppCodec.writeFixed(destTerminalId, 21, charset));
    dos.writeByte(msgContent.length);
    dos.write(msgContent);
    dos.write(CmppCodec.writeFixed("", 8, charset));
    dos.flush();
    return baos.toByteArray();
  }

  private CmppHeader readHeader() throws IOException {
    byte[] header = input.readNBytes(12);
    if (header.length < 12) {
      throw new EOFException("Incomplete header");
    }
    int totalLength = bytesToInt(header, 0);
    int commandId = bytesToInt(header, 4);
    int sequenceId = bytesToInt(header, 8);
    return new CmppHeader(totalLength, commandId, sequenceId);
  }

  private byte[] readBody(int totalLength) throws IOException {
    int bodyLength = totalLength - 12;
    if (bodyLength <= 0) {
      return new byte[0];
    }
    byte[] body = input.readNBytes(bodyLength);
    if (body.length < bodyLength) {
      throw new EOFException("Incomplete body");
    }
    return body;
  }

  private void closeSession() {
    loggedIn = false;
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ex) {
        log.debug("Failed to close socket", ex);
      }
    }
    socket = null;
    input = null;
    output = null;
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static int bytesToInt(byte[] data, int offset) {
    return ((data[offset] & 0xff) << 24)
        | ((data[offset + 1] & 0xff) << 16)
        | ((data[offset + 2] & 0xff) << 8)
        | (data[offset + 3] & 0xff);
  }

  private static long bytesToLong(byte[] data, int offset) {
    return ((long) (data[offset] & 0xff) << 56)
        | ((long) (data[offset + 1] & 0xff) << 48)
        | ((long) (data[offset + 2] & 0xff) << 40)
        | ((long) (data[offset + 3] & 0xff) << 32)
        | ((long) (data[offset + 4] & 0xff) << 24)
        | ((long) (data[offset + 5] & 0xff) << 16)
        | ((long) (data[offset + 6] & 0xff) << 8)
        | ((long) (data[offset + 7] & 0xff));
  }

  private static void longToBytes(long value, byte[] target, int offset) {
    target[offset] = (byte) (value >>> 56);
    target[offset + 1] = (byte) (value >>> 48);
    target[offset + 2] = (byte) (value >>> 40);
    target[offset + 3] = (byte) (value >>> 32);
    target[offset + 4] = (byte) (value >>> 24);
    target[offset + 5] = (byte) (value >>> 16);
    target[offset + 6] = (byte) (value >>> 8);
    target[offset + 7] = (byte) value;
  }

  private static void intToBytes(int value, byte[] target, int offset) {
    target[offset] = (byte) (value >>> 24);
    target[offset + 1] = (byte) (value >>> 16);
    target[offset + 2] = (byte) (value >>> 8);
    target[offset + 3] = (byte) value;
  }

  private static String readFixedString(byte[] data, int offset, int length) {
    byte[] slice = new byte[length];
    System.arraycopy(data, offset, slice, 0, length);
    int trimIndex = length;
    for (int i = 0; i < length; i++) {
      if (slice[i] == 0) {
        trimIndex = i;
        break;
      }
    }
    return new String(slice, 0, trimIndex).trim();
  }

  private record CmppHeader(int totalLength, int commandId, int sequenceId) {
  }
}
