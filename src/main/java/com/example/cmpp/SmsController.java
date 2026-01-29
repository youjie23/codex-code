package com.example.cmpp;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sms")
@Validated
public class SmsController {
  private final SmsService smsService;

  public SmsController(SmsService smsService) {
    this.smsService = smsService;
  }

  @PostMapping("/send")
  public ResponseEntity<SmsSendResponse> send(@RequestBody SmsSendRequest request) throws Exception {
    long sequenceId = smsService.sendSms(request.phone(), request.content());
    return ResponseEntity.ok(new SmsSendResponse(sequenceId));
  }

  public record SmsSendRequest(@NotBlank String phone, @NotBlank String content) {
  }

  public record SmsSendResponse(long sequenceId) {
  }
}
