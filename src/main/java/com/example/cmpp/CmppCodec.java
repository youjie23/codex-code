package com.example.cmpp;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CmppCodec {
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("MMddHHmmss");

  private CmppCodec() {
  }

  public static byte[] writeFixed(String value, int length, Charset charset) throws IOException {
    byte[] data = value == null ? new byte[0] : value.getBytes(charset);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if (data.length >= length) {
      baos.write(data, 0, length);
    } else {
      baos.write(data);
      baos.write(new byte[length - data.length]);
    }
    return baos.toByteArray();
  }

  public static byte[] authenticatorSource(String sourceAddr, String sharedSecret, String timestamp)
      throws NoSuchAlgorithmException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      baos.write(sourceAddr.getBytes(StandardCharsets.US_ASCII));
      baos.write(new byte[9]);
      baos.write(sharedSecret.getBytes(StandardCharsets.US_ASCII));
      baos.write(timestamp.getBytes(StandardCharsets.US_ASCII));
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to build authenticator", ex);
    }
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    return md5.digest(baos.toByteArray());
  }

  public static String nowTimestamp() {
    return LocalDateTime.now().format(TIMESTAMP);
  }

  public static byte msgFormat(CmppProperties properties) {
    if ("UCS2".equalsIgnoreCase(properties.getMsgFormat())) {
      return 8;
    }
    if ("GBK".equalsIgnoreCase(properties.getMsgFormat())) {
      return 15;
    }
    return 0;
  }

  public static Charset msgCharset(CmppProperties properties) {
    if ("UCS2".equalsIgnoreCase(properties.getMsgFormat())) {
      return StandardCharsets.UTF_16BE;
    }
    if ("GBK".equalsIgnoreCase(properties.getMsgFormat())) {
      return Charset.forName("GBK");
    }
    return StandardCharsets.US_ASCII;
  }

  public static ByteBuffer buildHeader(int commandId, int sequenceId, byte[] body) {
    int totalLength = 12 + (body == null ? 0 : body.length);
    ByteBuffer buffer = ByteBuffer.allocate(totalLength);
    buffer.putInt(totalLength);
    buffer.putInt(commandId);
    buffer.putInt(sequenceId);
    if (body != null) {
      buffer.put(body);
    }
    buffer.flip();
    return buffer;
  }

  public static byte[] buildPacket(int commandId, int sequenceId, byte[] body) {
    ByteBuffer buffer = buildHeader(commandId, sequenceId, body);
    byte[] packet = new byte[buffer.remaining()];
    buffer.get(packet);
    return packet;
  }

  public static byte[] packConnectBody(CmppProperties properties) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    String timestamp = nowTimestamp();
    dos.write(writeFixed(properties.getSpId(), 6, StandardCharsets.US_ASCII));
    dos.write(authenticatorSource(properties.getSpId(), properties.getSharedSecret(), timestamp));
    dos.writeByte(0x20);
    dos.writeInt(Integer.parseInt(timestamp));
    dos.flush();
    return baos.toByteArray();
  }

  public static byte[] packActiveTestBody() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeByte(0);
    dos.flush();
    return baos.toByteArray();
  }
}
