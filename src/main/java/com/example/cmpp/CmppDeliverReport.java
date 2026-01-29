package com.example.cmpp;

public class CmppDeliverReport {
  private final long msgId;
  private final String stat;
  private final String submitTime;
  private final String doneTime;
  private final String destTerminalId;
  private final int smscSequence;

  public CmppDeliverReport(long msgId, String stat, String submitTime, String doneTime,
      String destTerminalId, int smscSequence) {
    this.msgId = msgId;
    this.stat = stat;
    this.submitTime = submitTime;
    this.doneTime = doneTime;
    this.destTerminalId = destTerminalId;
    this.smscSequence = smscSequence;
  }

  public long getMsgId() {
    return msgId;
  }

  public String getStat() {
    return stat;
  }

  public String getSubmitTime() {
    return submitTime;
  }

  public String getDoneTime() {
    return doneTime;
  }

  public String getDestTerminalId() {
    return destTerminalId;
  }

  public int getSmscSequence() {
    return smscSequence;
  }
}
