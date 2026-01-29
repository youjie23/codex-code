package com.example.cmpp;

import org.springframework.stereotype.Service;

@Service
public class SmsService {
  private final CmppClient client;

  public SmsService(CmppClient client) {
    this.client = client;
  }

  public long sendSms(String phone, String content) throws Exception {
    return client.sendSms(phone, content);
  }
}
