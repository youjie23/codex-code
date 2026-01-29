package com.example.cmpp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CmppProperties.class)
public class CmppApplication {
  public static void main(String[] args) {
    SpringApplication.run(CmppApplication.class, args);
  }
}
