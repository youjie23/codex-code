package com.example.cmpp;

import java.util.concurrent.atomic.AtomicInteger;

public final class CmppSequence {
  private final AtomicInteger sequence = new AtomicInteger(0);

  public int next() {
    int value = sequence.incrementAndGet();
    if (value == Integer.MAX_VALUE) {
      sequence.set(0);
    }
    return value;
  }
}
