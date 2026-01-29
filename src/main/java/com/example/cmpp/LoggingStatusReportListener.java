package com.example.cmpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingStatusReportListener implements StatusReportListener {
  private static final Logger log = LoggerFactory.getLogger(LoggingStatusReportListener.class);

  @Override
  public void onReport(CmppDeliverReport report) {
    log.info("CMPP report: msgId={}, stat={}, submitTime={}, doneTime={}, destTerminalId={}, smscSeq={}",
        report.getMsgId(), report.getStat(), report.getSubmitTime(), report.getDoneTime(),
        report.getDestTerminalId(), report.getSmscSequence());
  }
}
