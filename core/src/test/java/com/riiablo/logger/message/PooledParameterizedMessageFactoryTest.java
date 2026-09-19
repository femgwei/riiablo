package com.riiablo.logger.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PooledParameterizedMessageFactoryTest {
  @Test
  void formatsMoreThanTenParameterizedArguments() {
    Message message = PooledParameterizedMessageFactory.INSTANCE.newMessage(
        "{} {} {} {} {} {} {} {} {} {} {}",
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    assertEquals("0 1 2 3 4 5 6 7 8 9 10", message.format());
  }
}
